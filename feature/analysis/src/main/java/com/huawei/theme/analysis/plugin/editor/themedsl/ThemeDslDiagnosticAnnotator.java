package com.huawei.theme.analysis.plugin.editor.themedsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;

import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.cli.PipelineMode;
import com.huawei.theme.analysis.core.macro.DiagnosticDedup;
import com.huawei.theme.analysis.core.macro.DemacroedAst;
import com.huawei.theme.analysis.core.macro.MacroExpander;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProvider;
import com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProviderImpl;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilder;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilderImpl;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;
import com.huawei.theme.analysis.core.syntaxanalysis.DslAstProvider;
import com.huawei.theme.analysis.plugin.rule.RuleRepositoryService;

/**
 * Displays ThemeDSL semantic diagnostics ({@link DiagnosticProvider}) as editor
 * annotations (error/warning/info underlines) on the host XML PSI.
 *
 * <p>For each file (ThemeDSL language), once per PSI modification:</p>
 * <ol>
 *     <li>build a {@link DslFileNode} from the file text via {@link AstBuilder}
 *         (cached on the {@link PsiFile} keyed by {@link PsiFile#getModificationStamp()},
 *         so the SAX re-parse + analysis only runs when the PSI changed);</li>
 *     <li>run {@link DiagnosticProviderImpl#analyze} with the rule repository and
 *         {@link SymbolTableBuilderImpl} to obtain {@link Diagnostic}s;</li>
 *     <li>resolve each diagnostic's {@code line} (1-based) / {@code column} (0-based)
 *         to a document offset and find the enclosing {@link PsiElement}
 *         (walked up to the nearest {@link XmlTag}/{@link XmlAttribute});</li>
 *     <li>annotate that element when the platform visits it.</li>
 * </ol>
 *
 * <p>The diagnostics-to-element map is precomputed during the cache build, so each
 * {@link #annotate} call is a cheap map lookup.</p>
 */
public class ThemeDslDiagnosticAnnotator implements Annotator {

    private static final Logger LOG = Logger.getInstance(ThemeDslDiagnosticAnnotator.class);

    private static final Key<CachedAnalysis> CACHE_KEY =
            Key.create("ThemeDslDiagnosticAnnotator.cache");

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return;
        }
        CachedAnalysis cached = getCached(file);
        List<Diagnostic> diagnostics = cached.diagnosticsByElement.get(element);
        if (diagnostics == null || diagnostics.isEmpty()) {
            return;
        }
        for (Diagnostic diagnostic : diagnostics) {
            var off1 = lineColToOffset(file.getFileDocument(), diagnostic.getLine(), diagnostic.getColumn());
            var off2 = lineColToOffset(file.getFileDocument(), diagnostic.getEndLine(), diagnostic.getEndColumn());

            String message = diagnostic.getMessage() == null ? "" : diagnostic.getMessage();
            holder.newAnnotation(severityOf(diagnostic.getSeverity()), message)
                    .range(TextRange.create(off1, off2))
                    .create();
        }
    }

    private static HighlightSeverity severityOf(@Nullable DiagnosticSeverity severity) {
        if (severity == null) {
            return HighlightSeverity.INFORMATION;
        }
        switch (severity) {
            case ERROR:
                return HighlightSeverity.ERROR;
            case WARNING:
                return HighlightSeverity.WARNING;
            case INFO:
            default:
                return HighlightSeverity.INFORMATION;
        }
    }

    private static CachedAnalysis getCached(PsiFile file) {
        long modStamp = file.getModificationStamp();
        CachedAnalysis cached = file.getUserData(CACHE_KEY);
        if (cached != null && cached.modStamp == modStamp) {
            return cached;
        }
        cached = build(file, modStamp);
        file.putUserData(CACHE_KEY, cached);
        return cached;
    }

    private static CachedAnalysis build(PsiFile file, long modStamp) {
        try {
            Project project = file.getProject();
            RuleRepository repo = RuleRepositoryService.getInstance().getRuleRepository();
            DslAstProvider astProvider = new AstBuilder(repo);
            MacroExpander macroExpander = new MacroExpander(repo);
            DiagnosticProvider diagnosticProvider = new DiagnosticProviderImpl();
            SymbolTableBuilder symbolTableBuilder = new SymbolTableBuilderImpl();

            Document document = PsiDocumentManager.getInstance(project).getDocument(file);
            String content = file.getText();
            String filePath = file.getVirtualFile() != null
                    ? file.getVirtualFile().getPath() : file.getName();

            DslFileNode normalAst = astProvider.getDslAst(filePath, content);
            DemacroedAst demacroed = macroExpander.expand(normalAst);
            DslFileNode analysisAst = demacroed.getDemacroed();
            List<Diagnostic> diagnostics = diagnosticProvider.analyze(
                    analysisAst, repo, symbolTableBuilder,
                    PipelineMode.FULL,
                    InspectionConfig.builder().build(),
                    null);
            if (!demacroed.getMacroDiagnostics().isEmpty()) {
                List<Diagnostic> merged = new ArrayList<>(demacroed.getMacroDiagnostics());
                merged.addAll(diagnostics);
                diagnostics = merged;
            }
            diagnostics = DiagnosticDedup.dedup(diagnostics);
            Map<PsiElement, List<Diagnostic>> map = new HashMap<>();
            for (Diagnostic diagnostic : diagnostics) {
                int offset = lineColToOffset(document, diagnostic.getLine(), diagnostic.getColumn());
                PsiElement leaf = file.findElementAt(offset);
                PsiElement target = leaf == null ? null : walkUpToTarget(leaf);
                if (target == null) {
                    continue;
                }
                map.computeIfAbsent(target, t -> new ArrayList<>()).add(diagnostic);
            }
            return new CachedAnalysis(modStamp, map);
        } catch (Exception e) {
            // Control-flow exceptions (ProcessCanceledException et al.) must be rethrown,
            // never logged - they signal the daemon pass was canceled (e.g. user kept typing)
            // and the infrastructure handles them. Logging them is an error in IntelliJ.
            if (e instanceof ControlFlowException || e instanceof CancellationException) {
                throw e;
            }
            LOG.warn("ThemeDSL diagnostic analysis failed for " + file.getName(), e);
            return new CachedAnalysis(modStamp, Collections.emptyMap());
        }
    }

    private static PsiElement walkUpToTarget(PsiElement leaf) {
        PsiElement e = leaf;
        while (e != null && !(e instanceof XmlTag) && !(e instanceof XmlAttribute)) {
            e = e.getParent();
        }
        return e != null ? e : leaf;
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
        int offset = lineStart + Math.max(column0Based, 0);
        return Math.min(offset, lineEnd);
    }

    private static final class CachedAnalysis {
        final long modStamp;
        final Map<PsiElement, List<Diagnostic>> diagnosticsByElement;

        CachedAnalysis(long modStamp, Map<PsiElement, List<Diagnostic>> diagnosticsByElement) {
            this.modStamp = modStamp;
            this.diagnosticsByElement = diagnosticsByElement;
        }
    }
}
