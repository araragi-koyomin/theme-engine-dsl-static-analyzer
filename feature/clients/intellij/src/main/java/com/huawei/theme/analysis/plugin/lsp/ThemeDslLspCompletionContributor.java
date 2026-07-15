package com.huawei.theme.analysis.plugin.lsp;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/**
 * Completion contributor that delegates to the LSP server: converts the
 * IntelliJ cursor offset to an LSP {@link Position}, calls
 * {@code textDocument/completion}, and wraps each returned
 * {@link CompletionItem} as a {@link LookupElementBuilder}.
 *
 * <p>Before issuing the request, the current document text is flushed to the
 * server via a synchronous {@code didChange} so the server's cached document
 * (which otherwise lags behind by the didChange debounce) reflects what the
 * user actually typed and the cursor resolves to the right context.</p>
 *
 * <p>The server's {@code sortText} is intentionally NOT used as a lookup
 * string — that was the source of the prior chaos (typing digits/underscores
 * matched unrelated items via "0_align"/"1_left" lookup strings). Only the
 * label is matchable; the detail text conveys required/optional. Attribute-name
 * items (kind Field/Property) get an insert handler that appends {@code =""}
 * and places the caret between the quotes.</p>
 */
public final class ThemeDslLspCompletionContributor extends CompletionContributor {

    private static final InsertHandler<LookupElement> ATTR_INSERT_HANDLER = new AttrInsertHandler();

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        PsiFile file = parameters.getOriginalFile();
        VirtualFile vf = file.getVirtualFile();
        if (vf == null) {
            return;
        }
        Project project = file.getProject();
        DslLspServerService service = project.getService(DslLspServerService.class);
        LanguageServer server = service.getServerProxy();
        if (server == null) {
            return;
        }
        Document doc = FileDocumentManager.getInstance().getDocument(vf);
        if (doc == null) {
            return;
        }
        String uri = vf.getUrl();
        String text = doc.getText();
        int offset = parameters.getOffset();
        int line = doc.getLineNumber(offset);
        int col = offset - doc.getLineStartOffset(line);

        // Flush current text so the server resolves the cursor context against
        // the document the user actually sees, not a debounce-lagged snapshot.
        sendDidChange(server, uri, text);

        CompletionParams params = new CompletionParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        params.setPosition(new Position(line, col));

        Either<List<CompletionItem>, CompletionList> res;
        try {
            res = server.getTextDocumentService().completion(params)
                    .get(500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return;
        }
        List<CompletionItem> items;
        if (res == null) {
            items = List.of();
        } else if (res.isLeft()) {
            items = res.getLeft();
        } else {
            items = res.getRight().getItems();
        }
        if (items == null) {
            return;
        }
        for (CompletionItem item : items) {
            LookupElement lookup = toLookup(item);
            if (lookup != null) {
                result.addElement(lookup);
            }
        }
    }

    private static LookupElement toLookup(CompletionItem item) {
        String label = item.getLabel();
        if (label == null || label.isEmpty()) {
            return null;
        }
        String docMarkup = documentationOf(item);
        // Carry the server-supplied documentation as the lookup's object so the
        // ThemeDslLspHoverProvider can show it in the completion documentation
        // panel via getDocumentationElementForLookupItem.
        LookupElementBuilder lookup = (docMarkup != null)
                ? LookupElementBuilder.create(new DslLookupDoc(label, docMarkup), label)
                : LookupElementBuilder.create(label);
        if (item.getDetail() != null && !item.getDetail().isEmpty()) {
            lookup = lookup.withTypeText(item.getDetail());
        }
        // Attribute-name items: insert as name="" with the caret between the
        // quotes so the user can type the value immediately. Element names and
        // enum values insert as the bare label.
        CompletionItemKind kind = item.getKind();
        if (kind == CompletionItemKind.Field || kind == CompletionItemKind.Property) {
            lookup = lookup.withInsertHandler(ATTR_INSERT_HANDLER);
        }
        Icon icon = iconForKind(kind);
        if (icon != null) {
            lookup = lookup.withIcon(icon);
        }
        // The server's sortText ("0_"/"1_") is intentionally NOT used as a
        // lookup string: that would let digits/underscores match unrelated
        // items (the prior chaos). IntelliJ orders by label; the detail text
        // ("required"/"optional") conveys priority visually.
        return lookup;
    }

    /** Extracts the markup string from the LSP CompletionItem.documentation. */
    private static String documentationOf(CompletionItem item) {
        org.eclipse.lsp4j.jsonrpc.messages.Either<String, org.eclipse.lsp4j.MarkupContent> doc =
                item.getDocumentation();
        if (doc == null) {
            return null;
        }
        if (doc.isLeft()) {
            return doc.getLeft();
        }
        org.eclipse.lsp4j.MarkupContent mc = doc.getRight();
        return (mc != null && mc.getValue() != null && !mc.getValue().isEmpty()) ? mc.getValue() : null;
    }

    /**
     * Maps an LSP {@link CompletionItemKind} to an IntelliJ icon so the
     * completion list visually distinguishes element names (class/tag),
     * attribute names (field/property) and enum values.
     */
    private static Icon iconForKind(CompletionItemKind kind) {
        if (kind == null) {
            return null;
        }
        switch (kind) {
            case Class:
                return AllIcons.Nodes.Class;
            case Field:
                return AllIcons.Nodes.Field;
            case Property:
                return AllIcons.Nodes.Property;
            case EnumMember:
                return AllIcons.Nodes.Enum;
            case Enum:
                return AllIcons.Nodes.Enum;
            default:
                return null;
        }
    }

    private static void sendDidChange(LanguageServer server, String uri, String text) {
        try {
            DidChangeTextDocumentParams p = new DidChangeTextDocumentParams();
            p.setTextDocument(new VersionedTextDocumentIdentifier(uri, (int) System.currentTimeMillis()));
            p.setContentChanges(List.of(new TextDocumentContentChangeEvent(text)));
            server.getTextDocumentService().didChange(p);
        } catch (Exception ignored) {
            // Best-effort flush; completion will still use whatever text the
            // server last received via the debounce path.
        }
    }

    /**
     * Appends {@code =""} after an inserted attribute name and moves the caret
     * between the quotes.
     */
    private static final class AttrInsertHandler implements InsertHandler<LookupElement> {
        @Override
        public void handleInsert(@NotNull InsertionContext ctx, @NotNull LookupElement item) {
            Editor editor = ctx.getEditor();
            Document doc = editor.getDocument();
            int offset = editor.getCaretModel().getOffset();
            // Skip if an '=' already follows the inserted name (user typed it).
            if (offset < doc.getTextLength() && doc.getCharsSequence().charAt(offset) == '=') {
                return;
            }
            doc.insertString(offset, "=\"\"");
            editor.getCaretModel().moveToOffset(offset + 2);
        }
    }
}
