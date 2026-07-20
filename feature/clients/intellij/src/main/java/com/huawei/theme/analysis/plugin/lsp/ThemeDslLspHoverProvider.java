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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
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
 *
 * <p>For completion lookup items, {@link #getDocumentationElementForLookupItem}
 * recognizes the {@link DslLookupDoc} marker carried on each lookup (sourced
 * from the server's {@code CompletionItem.documentation}) and returns a
 * {@link DslLookupDocElement}; {@link #generateDoc} then returns that markup
 * directly, so the completion documentation panel shows the element/attribute
 * documentation with no extra server round-trip.</p>
 */
public final class ThemeDslLspHoverProvider implements DocumentationProvider {

    private static final Logger LOG = Logger.getLogger(ThemeDslLspHoverProvider.class.getName());

    @Override
    public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor,
                                                                @NotNull PsiFile file,
                                                                @Nullable PsiElement element,
                                                                int offset) {
        // Capture the exact caret/hover offset. Return a DslHoverElement so
        // generateDoc can read the precise position — instead of the PSI
        // element's getTextOffset() which for XmlAttributeValue is the value
        // start, not the token the user is hovering on.
        return new DslHoverElement(element, offset);
    }

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        // Completion lookup items carry pre-fetched markup; surface it directly.
        if (element instanceof DslLookupDocElement) {
            return markdownToHtml(((DslLookupDocElement) element).getMarkup());
        }
        // Hover: DslHoverElement carries the exact cursor offset.
        if (element instanceof DslHoverElement he) {
            return fetchHoverAtOffset(he.getOriginal(), he.getOffset());
        }
        return fetchHover(originalElement, element);
    }

    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element,
                                                    @Nullable PsiElement originalElement) {
        if (element instanceof DslLookupDocElement) {
            return markdownToHtml(((DslLookupDocElement) element).getMarkup());
        }
        if (element instanceof DslHoverElement he) {
            return fetchHoverAtOffset(he.getOriginal(), he.getOffset());
        }
        return fetchHover(originalElement, element);
    }

    @Override
    public @Nullable PsiElement getDocumentationElementForLookupItem(PsiManager manager,
                                                                      Object object,
                                                                      PsiElement element) {
        if (object instanceof DslLookupDoc) {
            DslLookupDoc d = (DslLookupDoc) object;
            LOG.info("getDocumentationElementForLookupItem: label=" + d.label
                    + " markupLen=" + d.markup.length());
            return new DslLookupDocElement(d.label, d.markup, element);
        }
        return null;
    }

    private @Nullable String fetchHover(@Nullable PsiElement preferred, @Nullable PsiElement fallback) {
        PsiElement target = pickWithVirtualFile(preferred, fallback);
        if (target == null) {
            return null;
        }
        return fetchHoverAtOffset(target, target.getTextOffset());
    }

    /**
     * Sends {@code textDocument/hover} to the server with the given exact
     * offset (not the PSI element's start offset), converts the markdown
     * response to HTML for IntelliJ's documentation panel.
     */
    private @Nullable String fetchHoverAtOffset(@Nullable PsiElement element, int offset) {
        if (element == null) {
            return null;
        }
        PsiFile file = element.getContainingFile();
        VirtualFile vf = file != null ? file.getVirtualFile() : null;
        if (vf == null) {
            return null;
        }
        Project project = element.getProject();
        DslLspServerService service = project.getService(DslLspServerService.class);
        LanguageServer server = service.getServerProxy();
        if (server == null) {
            return null;
        }
        Document doc = FileDocumentManager.getInstance().getDocument(vf);
        if (doc == null) {
            return null;
        }
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
            LOG.log(Level.WARNING, "fetchHoverAtOffset: hover request failed", e);
            return null;
        }
        if (hover == null || hover.getContents() == null) {
            return null;
        }
        MarkupContent mc = hover.getContents().getRight();
        if (mc == null) {
            return null;
        }
        return markdownToHtml(mc.getValue());
    }

    /**
     * Converts the Markdown produced by the LSP server (hover and completion
     * documentation) into the HTML IntelliJ's documentation panel renders.
     * The server emits Markdown so standard LSP clients (VS Code) render it
     * natively; IntelliJ's documentation panel is HTML-based, so without
     * this conversion the raw Markdown ({@code ###}, {@code **}, {@code `})
     * would show literally. Covers the small Markdown subset the server
     * generates: ATX headings, inline code, bold, and hard/soft line breaks.
     */
    static @Nullable String markdownToHtml(@Nullable String md) {
        if (md == null || md.isEmpty()) {
            return md;
        }
        // Escape HTML special chars in raw text first; tags added below are
        // inserted after escaping so they aren't re-escaped.
        String s = md.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        s = s.replaceAll("(?m)^### (.+)$", "<h3>$1</h3>");
        s = s.replaceAll("`([^`]+)`", "<code>$1</code>");
        s = s.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        s = s.replace("  \n", "\n");   // markdown hard break (two trailing spaces)
        s = s.replace("\n\n", "<br>");
        s = s.replace("\n", "<br>");
        return s;
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
