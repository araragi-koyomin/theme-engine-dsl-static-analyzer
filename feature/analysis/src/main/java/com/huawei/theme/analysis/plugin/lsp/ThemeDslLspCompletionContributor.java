package com.huawei.theme.analysis.plugin.lsp;

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Completion contributor that delegates to the LSP server: converts the
 * IntelliJ cursor offset to an LSP {@link Position}, calls
 * {@code textDocument/completion}, and wraps each returned
 * {@link CompletionItem} as a {@link LookupElementBuilder}.
 */
public final class ThemeDslLspCompletionContributor extends CompletionContributor {

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
        int offset = parameters.getOffset();
        int line = doc.getLineNumber(offset);
        int col = offset - doc.getLineStartOffset(line);

        CompletionParams params = new CompletionParams();
        params.setTextDocument(new TextDocumentIdentifier(vf.getUrl()));
        params.setPosition(new Position(line, col));

        Either<List<CompletionItem>, CompletionList> res;
        try {
            res = server.getTextDocumentService().completion(params).join();
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
            LookupElementBuilder lookup = LookupElementBuilder.create(item.getLabel());
            if (item.getDetail() != null) {
                lookup = lookup.withTypeText(item.getDetail());
            }
            if (item.getSortText() != null) {
                lookup = lookup.withLookupString(item.getSortText());
            }
            result.addElement(lookup);
        }
    }
}
