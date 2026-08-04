package com.huawei.theme.analysis.plugin.editor.themedsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.plugin.ast.DslAstService;

public class ThemeDslDiagnosticAnnotator implements Annotator {

    private static final Logger LOG = Logger.getInstance(ThemeDslDiagnosticAnnotator.class);
    private static final Key<CachedAnalysis> CACHE_KEY =
            Key.create("ThemeDslDiagnosticAnnotator.contextCache");

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        PsiFile file = element.getContainingFile();
        if (!(file instanceof XmlFile xmlFile)) {
            return;
        }
        CachedAnalysis cached = getCached(xmlFile);
        List<Diagnostic> diagnostics = cached.diagnosticsByElement.get(element);
        if (diagnostics == null || diagnostics.isEmpty()) {
            return;
        }
        for (Diagnostic diagnostic : diagnostics) {
            int start = lineColToOffset(file.getFileDocument(), diagnostic.getLine(), diagnostic.getColumn());
            int end = lineColToOffset(file.getFileDocument(), diagnostic.getEndLine(), diagnostic.getEndColumn());
            String message = diagnostic.getMessage() == null ? "" : diagnostic.getMessage();
            holder.newAnnotation(severityOf(diagnostic.getSeverity()), message)
                    .range(TextRange.create(start, Math.max(start, end)))
                    .create();
        }
    }

    private static CachedAnalysis getCached(@NotNull XmlFile file) {
        DslAstService service = DslAstService.getInstance(file.getProject());
        long version = service.getContextVersion(file);
        CachedAnalysis cached = file.getUserData(CACHE_KEY);
        if (cached != null && cached.version == version) {
            return cached;
        }
        cached = build(file, service, version);
        file.putUserData(CACHE_KEY, cached);
        return cached;
    }

    private static CachedAnalysis build(@NotNull XmlFile file,
                                        @NotNull DslAstService service,
                                        long version) {
        try {
            Document document = file.getFileDocument();
            Map<PsiElement, List<Diagnostic>> map = new HashMap<>();
            for (Diagnostic diagnostic : service.getProjectedDiagnostics(file)) {
                int offset = lineColToOffset(document, diagnostic.getLine(), diagnostic.getColumn());
                PsiElement leaf = file.findElementAt(offset);
                PsiElement target = leaf == null ? null : walkUpToTarget(leaf);
                if (target != null) {
                    map.computeIfAbsent(target, ignored -> new ArrayList<>()).add(diagnostic);
                }
            }
            return new CachedAnalysis(version, map);
        } catch (Exception e) {
            if (e instanceof ControlFlowException || e instanceof CancellationException) {
                throw e;
            }
            LOG.warn("ThemeDSL context analysis failed for " + file.getName(), e);
            return new CachedAnalysis(version, Collections.emptyMap());
        }
    }

    private static HighlightSeverity severityOf(@Nullable DiagnosticSeverity severity) {
        if (severity == DiagnosticSeverity.ERROR) {
            return HighlightSeverity.ERROR;
        }
        if (severity == DiagnosticSeverity.WARNING) {
            return HighlightSeverity.WARNING;
        }
        return HighlightSeverity.INFORMATION;
    }

    private static PsiElement walkUpToTarget(@NotNull PsiElement leaf) {
        PsiElement current = leaf;
        while (current != null && !(current instanceof XmlTag) && !(current instanceof XmlAttribute)) {
            current = current.getParent();
        }
        return current != null ? current : leaf;
    }

    private static int lineColToOffset(@Nullable Document document, int line1Based, int column0Based) {
        if (document == null) {
            return 0;
        }
        int lineIndex = Math.max(line1Based - 1, 0);
        if (lineIndex >= document.getLineCount()) {
            return document.getTextLength();
        }
        int lineStart = document.getLineStartOffset(lineIndex);
        int lineEnd = document.getLineEndOffset(lineIndex);
        return Math.min(lineStart + Math.max(column0Based, 0), lineEnd);
    }

    private static final class CachedAnalysis {
        final long version;
        final Map<PsiElement, List<Diagnostic>> diagnosticsByElement;

        CachedAnalysis(long version, @NotNull Map<PsiElement, List<Diagnostic>> diagnosticsByElement) {
            this.version = version;
            this.diagnosticsByElement = diagnosticsByElement;
        }
    }
}
