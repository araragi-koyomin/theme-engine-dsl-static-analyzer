package com.huawei.theme.analysis.lsp;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.huawei.theme.analysis.core.quickfix.CandidateItem;
import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionGenerator;
import com.huawei.theme.analysis.core.quickfix.FixActionRegistry;
import com.huawei.theme.analysis.core.quickfix.QuickFixProviderImpl;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeActionProviderTest {

    private static final String URI = "file:///test.xml";
    private static final String TEXT = "<Widget name=\"x\"/>";

    private final DiagnosticPublisher diagnosticPublisher = new DiagnosticPublisher();
    private final CodeActionProvider provider =
            new CodeActionProvider(new QuickFixProviderImpl());

    // Note: FixActionRegistry.clear() is package-private and cannot be invoked
    // from this module. Each test uses a unique ruleId so registrations never
    // collide across tests, and the registry is unused by any other test class
    // in :feature:lsp:test, so leftover registrations are harmless.

    @Test
    void singleReplacementAction() {
        // Diagnostic at "name" (line 0, cols 8..12). Fix replaces it with "w".
        FixActionRegistry.register(generator("TEST-RULE-001", diagnostic -> List.of(
                FixAction.builder()
                        .fixType("replace_attr")
                        .description("Replace with alias 'w'")
                        .targetRange(TextRange.builder()
                                .startLine(1).startColumn(8).endLine(1).endColumn(12).build())
                        .replacementText("w")
                        .build()
        )));
        Diagnostic diag = diag("TEST-RULE-001", "name", 1, 8, 12);
        List<Either<Command, CodeAction>> actions = build(diag, range(0, 8, 0, 12));
        assertEquals(1, actions.size());
        CodeAction ca = actions.get(0).getRight();
        assertEquals("Replace with alias 'w'", ca.getTitle());
        assertEquals(CodeActionKind.QuickFix, ca.getKind());
        assertNotNull(ca.getEdit());
        TextEdit te = ca.getEdit().getDocumentChanges().get(0).getLeft().getEdits().get(0);
        assertEquals("w", te.getNewText());
        assertEquals(new Position(0, 8), te.getRange().getStart());
        assertEquals(new Position(0, 12), te.getRange().getEnd());
        // Action is associated with the matching LSP diagnostic.
        assertNotNull(ca.getDiagnostics());
        assertEquals(1, ca.getDiagnostics().size());
    }

    @Test
    void candidateActionsProduceOnePerCandidate() {
        FixActionRegistry.register(generator("TEST-RULE-002", diagnostic -> List.of(
                FixAction.builder()
                        .fixType("suggest_attr")
                        .description("Did you mean")
                        .targetRange(TextRange.builder()
                                .startLine(1).startColumn(8).endLine(1).endColumn(12).build())
                        .replacementText("width")
                        .candidates(List.of(
                                CandidateItem.builder().description("Use 'width'").previewText("width").build(),
                                CandidateItem.builder().description("Use 'w'").previewText("w").build()
                        ))
                        .build()
        )));
        Diagnostic diag = diag("TEST-RULE-002", "name", 1, 8, 12);
        List<Either<Command, CodeAction>> actions = build(diag, range(0, 8, 0, 12));
        assertEquals(2, actions.size());
        assertEquals("Use 'width'", actions.get(0).getRight().getTitle());
        assertEquals("Use 'w'", actions.get(1).getRight().getTitle());
        // Each candidate's previewText becomes the replacement.
        TextEdit te0 = actions.get(0).getRight().getEdit().getDocumentChanges().get(0).getLeft().getEdits().get(0);
        assertEquals("width", te0.getNewText());
    }

    @Test
    void noOverlapReturnsEmpty() {
        FixActionRegistry.register(generator("TEST-RULE-003", diagnostic -> List.of(
                FixAction.builder()
                        .fixType("x")
                        .description("d")
                        .targetRange(TextRange.builder()
                                .startLine(1).startColumn(8).endLine(1).endColumn(12).build())
                        .replacementText("w")
                        .build()
        )));
        Diagnostic diag = diag("TEST-RULE-003", "name", 1, 8, 12);
        // Requested range on a different line — no overlap.
        List<Either<Command, CodeAction>> actions = build(diag, range(5, 0, 5, 5));
        assertTrue(actions.isEmpty());
    }

    @Test
    void unregisteredRuleReturnsEmpty() {
        Diagnostic diag = diag("UNREGISTERED-001", "name", 1, 8, 12);
        List<Either<Command, CodeAction>> actions = build(diag, range(0, 8, 0, 12));
        assertTrue(actions.isEmpty());
    }

    @Test
    void actionWithoutTargetRangeIsSkipped() {
        FixActionRegistry.register(generator("TEST-RULE-004", diagnostic -> List.of(
                FixAction.builder()
                        .fixType("noop")
                        .description("no range")
                        .replacementText("w")
                        .build()
        )));
        Diagnostic diag = diag("TEST-RULE-004", "name", 1, 8, 12);
        List<Either<Command, CodeAction>> actions = build(diag, range(0, 8, 0, 12));
        assertTrue(actions.isEmpty());
    }

    private List<Either<Command, CodeAction>> build(Diagnostic diag, Range requested) {
        PositionMapper mapper = new PositionMapper(TEXT);
        CodeActionParams params = new CodeActionParams();
        params.setTextDocument(new TextDocumentIdentifier(URI));
        params.setRange(requested);
        return provider.build(URI, mapper, List.of(diag), requested, diagnosticPublisher);
    }

    private static Range range(int sl, int sc, int el, int ec) {
        return new Range(new Position(sl, sc), new Position(el, ec));
    }

    private static Diagnostic diag(String ruleId, String nodeText, int line, int col, int endCol) {
        DslElementNode node = new DslElementNode();
        node.setText(nodeText);
        node.setLine(line);
        node.setColumn(col);
        node.setEndLine(line);
        node.setEndColumn(endCol);
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(ruleId)
                .message("unknown " + nodeText)
                .filePath(URI)
                .astNode(node)
                .build();
    }

    private static FixActionGenerator generator(String ruleId,
            java.util.function.Function<Diagnostic, List<FixAction>> generate) {
        return new FixActionGenerator() {
            @Override
            public String getRuleId() {
                return ruleId;
            }

            @Override
            public List<FixAction> generate(Diagnostic diagnostic) {
                return generate.apply(diagnostic);
            }
        };
    }
}
