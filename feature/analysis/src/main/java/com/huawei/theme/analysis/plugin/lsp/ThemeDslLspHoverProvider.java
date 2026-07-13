package com.huawei.theme.analysis.plugin.lsp;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageServer;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * Documentation provider that delegates to {@code textDocument/hover} on the
 * LSP server.
 *
 * <p>IntelliJ may pass the <em>documentation target</em> as {@code element}
 * (e.g. an {@code XmlElementDecl} looked up from a DTD), whose containing file
 * has no VirtualFile. The {@code originalElement} is the cursor PSI (an
 * {@code XmlTag}/token inside the real file), so it is preferred for resolving
 * the document URI and offset.</p>
 */
public final class ThemeDslLspHoverProvider implements DocumentationProvider {

    private static final Logger LOG = Logger.getLogger(ThemeDslLspHoverProvider.class.getName());

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        return fetchHover(originalElement, element);
    }

    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element,
                                                 @Nullable PsiElement originalElement) {
        return fetchHover(originalElement, element);
    }

    private @Nullable String fetchHover(@Nullable PsiElement preferred, @Nullable PsiElement fallback) {
        // Try the cursor element first (has a real VirtualFile), then the
        // documentation target.
        PsiElement target = pickWithVirtualFile(preferred, fallback);
        if (target == null) {
            LOG.info("fetchHover: no candidate with virtual file");
            return null;
        }
        PsiFile file = target.getContainingFile();
        VirtualFile vf = file.getVirtualFile();
        Project project = target.getProject();
        DslLspServerService service = project.getService(DslLspServerService.class);
        LanguageServer server = service.getServerProxy();
        if (server == null) {
            LOG.info("fetchHover: no server proxy");
            return null;
        }
        Document doc = FileDocumentManager.getInstance().getDocument(vf);
        if (doc == null) {
            LOG.info("fetchHover: no document");
            return null;
        }
        int offset = target.getTextOffset();
        int line = doc.getLineNumber(offset);
        int col = offset - doc.getLineStartOffset(line);

        HoverParams params = new HoverParams();
        params.setTextDocument(new TextDocumentIdentifier(vf.getUrl()));
        params.setPosition(new Position(line, col));

        Hover hover;
        try {
            hover = server.getTextDocumentService().hover(params)
                    .get(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "fetchHover: hover request failed", e);
            return null;
        }
        if (hover == null || hover.getContents() == null) {
            LOG.info("fetchHover: hover null or no contents");
            return null;
        }
        MarkupContent mc = hover.getContents().getRight();
        if (mc == null) {
            LOG.info("fetchHover: no markup content (left side)");
            return null;
        }
        LOG.info("fetchHover: returning markdown, length=" + mc.getValue().length());
        return mc.getValue();
    }

    private static @Nullable PsiElement pickWithVirtualFile(PsiElement... candidates) {
        for (PsiElement c : candidates) {
            if (c == null) {
                continue;
            }
            PsiFile f = c.getContainingFile();
            if (f != null && f.getVirtualFile() != null) {
                return c;
            }
        }
        return null;
    }
}
