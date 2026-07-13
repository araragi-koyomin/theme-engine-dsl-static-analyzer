package com.huawei.theme.analysis.plugin.lsp;

import java.util.List;
import java.util.logging.Logger;
import java.awt.Font;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

/**
 * Renders LSP diagnostics (pushed via {@code publishDiagnostics} and cached in
 * {@link DslLspLanguageClient}) as IntelliJ annotations.
 *
 * <p>The daemon is restarted by {@link DslLspLanguageClient#publishDiagnostics}
 * whenever the server pushes a new snapshot; this annotator then, for each
 * diagnostic, finds the innermost {@link XmlTag} enclosing it (used only as a
 * gate so the annotation is emitted exactly once, on the relevant tag) and
 * highlights the diagnostic's precise LSP range — so attribute-level
 * diagnostics underline just the attribute name/value, not the whole tag.</p>
 */
public final class ThemeDslLspAnnotator implements Annotator {

    private static final Logger LOG = Logger.getLogger(ThemeDslLspAnnotator.class.getName());

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof XmlTag)) {
            return;
        }
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return;
        }
        VirtualFile vf = file.getVirtualFile();
        if (vf == null) {
            return;
        }
        Project project = element.getProject();
        DslLspServerService service = project.getService(DslLspServerService.class);
        DslLspLanguageClient client = service.getClient();
        String uri = vf.getUrl();
        List<Diagnostic> diags = client.getDiagnostics(uri);
        if (diags.isEmpty()) {
            return;
        }
        Document doc = FileDocumentManager.getInstance().getDocument(vf);
        if (doc == null) {
            return;
        }
        for (Diagnostic d : diags) {
            // Gate: only emit on the innermost XmlTag enclosing the diagnostic,
            // so an attribute/value diagnostic isn't duplicated on every
            // ancestor tag. mapToElement walks PSI parents up to the XmlTag.
            PsiElement target = mapToElement(d, file, doc);
            if (target != element) {
                continue;
            }
            // Highlight the precise LSP range rather than the whole tag, so
            // attribute-level diagnostics underline just the attribute
            // name/value instead of the entire element.
            int start = positionToOffset(d.getRange().getStart(), doc);
            int end = positionToOffset(d.getRange().getEnd(), doc);
            if (start < 0 || end <= start) {
                continue;
            }
            end = Math.min(end, doc.getTextLength());
            holder.newAnnotation(severityOf(d.getSeverity()), messageOf(d))
                    .range(new TextRange(start, end))
                    .create();
        }
    }

    private static void renderSemanticTokens(PsiElement element, AnnotationHolder holder,
                                             String uri, Document doc, DslLspLanguageClient client) {
        List<Integer> tokens = client.getSemanticTokens(uri);
        if (tokens.isEmpty()) {
            return;
        }
        TextRange tagRange = element.getTextRange();
        int line = 0;
        int col = 0;
        for (int i = 0; i + 4 < tokens.size(); i += 5) {
            int deltaLine = tokens.get(i);
            int deltaStart = tokens.get(i + 1);
            int length = tokens.get(i + 2);
            int type = tokens.get(i + 3);
            line += deltaLine;
            col = (deltaLine == 0) ? col + deltaStart : deltaStart;
            if (line < 0 || line >= doc.getLineCount()) {
                continue;
            }
            int start = doc.getLineStartOffset(line) + col;
            int end = Math.min(start + length, doc.getTextLength());
            if (start >= end || !tagRange.containsRange(start, end)) {
                continue;
            }
            TextAttributesKey key = keyForType(type);
            if (key == null) {
                continue;
            }
            TextAttributes attrs = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(key);
            if (attrs == null || attrs.getForegroundColor() == null) {
                attrs = defaultAttrsForType(type);
            }
            if (attrs == null) {
                continue;
            }
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(new TextRange(start, end))
                    .enforcedTextAttributes(attrs)
                    .create();
        }
    }

    private static TextAttributesKey keyForType(int type) {
        switch (type) {
            case 0:
                return DefaultLanguageHighlighterColors.IDENTIFIER;
            case 1:
                return DefaultLanguageHighlighterColors.FUNCTION_CALL;
            case 2:
                return DefaultLanguageHighlighterColors.NUMBER;
            case 3:
                return DefaultLanguageHighlighterColors.STRING;
            default:
                return null;
        }
    }

    private static TextAttributes defaultAttrsForType(int type) {
        switch (type) {
            case 0:
                return new TextAttributes(JBColor.BLUE, null, null, null, Font.PLAIN);
            case 1:
                return new TextAttributes(JBColor.YELLOW, null, null, null, Font.ITALIC);
            case 2:
                return new TextAttributes(JBColor.CYAN, null, null, null, Font.BOLD);
            case 3:
                return new TextAttributes(JBColor.GREEN, null, null, null, Font.PLAIN);
            default:
                return null;
        }
    }

    private static PsiElement mapToElement(Diagnostic d, PsiFile file, Document doc) {
        Range range = d.getRange();
        if (range == null) {
            return null;
        }
        int offset = positionToOffset(range.getStart(), doc);
        if (offset < 0) {
            return null;
        }
        PsiElement leaf = file.findElementAt(offset);
        if (leaf == null) {
            return null;
        }
        PsiElement e = leaf;
        while (e != null && !(e instanceof XmlTag)) {
            e = e.getParent();
        }
        return e;
    }

    private static int positionToOffset(Position pos, Document doc) {
        if (pos == null) {
            return -1;
        }
        int line = pos.getLine();
        int col = pos.getCharacter();
        if (line < 0 || line >= doc.getLineCount()) {
            return -1;
        }
        return Math.min(doc.getLineStartOffset(line) + col, doc.getTextLength());
    }

    private static HighlightSeverity severityOf(DiagnosticSeverity s) {
        if (s == null) {
            return HighlightSeverity.WARNING;
        }
        switch (s) {
            case Error:
                return HighlightSeverity.ERROR;
            case Warning:
                return HighlightSeverity.WARNING;
            case Information:
            case Hint:
            default:
                return HighlightSeverity.INFORMATION;
        }
    }

    private static String messageOf(Diagnostic d) {
        String msg = d.getMessage();
        return msg == null ? "" : msg;
    }
}
