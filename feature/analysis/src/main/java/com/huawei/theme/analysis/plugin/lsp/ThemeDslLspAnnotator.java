package com.huawei.theme.analysis.plugin.lsp;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

/**
 * Renders LSP diagnostics (pushed via {@code publishDiagnostics} and cached in
 * {@link DslLspLanguageClient}) as IntelliJ annotations.
 *
 * <p>The daemon is restarted by {@link DslLspLanguageClient#publishDiagnostics}
 * whenever the server pushes a new snapshot; this annotator then maps each
 * diagnostic's range to the enclosing {@link XmlTag} and, when it matches the
 * element being annotated, emits a {@code newAnnotation}.</p>
 */
public final class ThemeDslLspAnnotator implements Annotator {

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
            PsiElement target = mapToElement(d, file, doc);
            if (target == element) {
                holder.newAnnotation(severityOf(d.getSeverity()), messageOf(d))
                        .range(element)
                        .create();
            }
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
