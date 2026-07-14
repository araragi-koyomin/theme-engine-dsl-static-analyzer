package com.huawei.theme.analysis.lsp;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.huawei.theme.analysis.core.quickfix.CandidateItem;
import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.QuickFixProvider;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;

/**
 * Maps core {@link QuickFixProvider} fix actions to LSP {@code textDocument/codeAction}
 * responses.
 *
 * <p>For each cached core diagnostic whose range overlaps the requested range,
 * the registered fix generator (if any) is asked for {@link FixAction}s. Each
 * action becomes one or more {@link CodeAction}s of kind {@code quickfix}:
 * <ul>
 *   <li>when {@link FixAction#getCandidates()} is empty, a single action using
 *       {@link FixAction#getReplacementText()} and
 *       {@link FixAction#getDescription()};</li>
 *   <li>otherwise one action per {@link CandidateItem} (title =
 *       candidate description, new text = candidate preview), so the user can
 *       pick the best match (e.g. typo suggestions).</li>
 * </ul>
 * The action carries a {@link WorkspaceEdit} with a {@link TextEdit} whose
 * range is derived from {@link FixAction#getTargetRange()} (1-based line /
 * 0-based column, end exclusive, same convention as core diagnostics), and the
 * associated LSP diagnostic so clients group the fix under it.</p>
 *
 * <p>Production rule generators are registered into
 * {@code FixActionRegistry} at runtime; with none registered this returns an
 * empty list, which is the expected baseline until generators are added.</p>
 */
final class CodeActionProvider {

    private final QuickFixProvider quickFixProvider;

    CodeActionProvider(QuickFixProvider quickFixProvider) {
        this.quickFixProvider = quickFixProvider;
    }

    List<Either<Command, CodeAction>> build(String uri, PositionMapper mapper,
            List<Diagnostic> diagnostics, Range requestedRange,
            DiagnosticPublisher diagnosticPublisher) {
        List<Either<Command, CodeAction>> result = new ArrayList<>();
        if (diagnostics == null || diagnostics.isEmpty() || requestedRange == null) {
            return result;
        }
        for (Diagnostic d : diagnostics) {
            Range diagRange = mapper.toRange(d);
            if (!overlaps(diagRange, requestedRange)) {
                continue;
            }
            List<FixAction> actions = quickFixProvider.getFixActions(d);
            if (actions == null || actions.isEmpty()) {
                continue;
            }
            org.eclipse.lsp4j.Diagnostic lspDiag = toLspDiagnostic(d, mapper, diagnosticPublisher);
            for (FixAction action : actions) {
                result.addAll(toCodeActions(uri, mapper, action, lspDiag));
            }
        }
        return result;
    }

    private List<Either<Command, CodeAction>> toCodeActions(String uri, PositionMapper mapper,
            FixAction action, org.eclipse.lsp4j.Diagnostic lspDiag) {
        List<Either<Command, CodeAction>> result = new ArrayList<>();
        TextRange target = action.getTargetRange();
        if (target == null) {
            // Without a target range we cannot build a TextEdit; skip silently.
            return result;
        }
        Range editRange = toLspRange(mapper, target);
        if (action.getCandidates() == null || action.getCandidates().isEmpty()) {
            if (action.getReplacementText() == null) {
                return result;
            }
            result.add(Either.forRight(buildOne(uri, editRange,
                    action.getReplacementText(), titleOf(action, null), lspDiag)));
        } else {
            for (CandidateItem c : action.getCandidates()) {
                String newText = c.getPreviewText() != null ? c.getPreviewText() : action.getReplacementText();
                if (newText == null) {
                    continue;
                }
                result.add(Either.forRight(buildOne(uri, editRange,
                        newText, titleOf(action, c), lspDiag)));
            }
        }
        return result;
    }

    private static CodeAction buildOne(String uri, Range range, String newText,
            String title, org.eclipse.lsp4j.Diagnostic lspDiag) {
        TextEdit te = new TextEdit(range, newText);
        WorkspaceEdit edit = new WorkspaceEdit();
        VersionedTextDocumentIdentifier id = new VersionedTextDocumentIdentifier(uri, 0);
        edit.setDocumentChanges(List.of(Either.forLeft(new TextDocumentEdit(id, List.of(te)))));
        CodeAction ca = new CodeAction(title);
        ca.setKind(CodeActionKind.QuickFix);
        ca.setEdit(edit);
        if (lspDiag != null) {
            ca.setDiagnostics(List.of(lspDiag));
        }
        return ca;
    }

    private static String titleOf(FixAction action, CandidateItem candidate) {
        if (candidate != null && candidate.getDescription() != null && !candidate.getDescription().isEmpty()) {
            return candidate.getDescription();
        }
        if (action.getDescription() != null && !action.getDescription().isEmpty()) {
            return action.getDescription();
        }
        return action.getFixType() != null ? action.getFixType() : "Quick fix";
    }

    private static org.eclipse.lsp4j.Diagnostic toLspDiagnostic(Diagnostic d, PositionMapper mapper,
            DiagnosticPublisher diagnosticPublisher) {
        List<org.eclipse.lsp4j.Diagnostic> list =
                diagnosticPublisher.toLspDiagnostics(List.of(d), mapper);
        return list.isEmpty() ? null : list.get(0);
    }

    private static Range toLspRange(PositionMapper mapper, TextRange tr) {
        return new Range(
                mapper.toPosition(tr.getStartLine(), tr.getStartColumn()),
                mapper.toPosition(tr.getEndLine(), tr.getEndColumn()));
    }

    /** Two LSP ranges overlap iff each starts strictly before the other ends. */
    private static boolean overlaps(Range a, Range b) {
        return a != null && b != null
                && before(a.getStart(), b.getEnd())
                && before(b.getStart(), a.getEnd());
    }

    private static boolean before(Position p, Position q) {
        int c = Integer.compare(p.getLine(), q.getLine());
        return c != 0 ? c < 0 : p.getCharacter() < q.getCharacter();
    }
}
