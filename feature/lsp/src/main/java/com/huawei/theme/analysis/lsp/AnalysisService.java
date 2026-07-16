package com.huawei.theme.analysis.lsp;

import java.util.List;
import java.util.concurrent.CancellationException;

import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.cli.PipelineMode;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProvider;
import com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProviderImpl;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilder;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilderImpl;
import com.huawei.theme.analysis.core.semanticanalysis.VerboseCollector;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;
import com.huawei.theme.analysis.core.syntaxanalysis.DslAstProvider;

/**
 * Runs the full core analysis pipeline (AST build + semantic analysis) for a
 * document. Stateless aside from the injected rule repository; safe to call
 * concurrently for different files.
 */
final class AnalysisService {

    private final RuleRepository ruleRepository;
    private final DiagnosticProvider diagnosticProvider;
    private final SymbolTableBuilder symbolTableBuilder;

    AnalysisService(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
        this.diagnosticProvider = new DiagnosticProviderImpl();
        this.symbolTableBuilder = new SymbolTableBuilderImpl();
    }

    /**
     * Analyzes the document text and returns core diagnostics.
     *
     * @param filePath file URI/path used for AST node filePath and error context
     * @param content full document text
     * @return list of core diagnostics; empty on failure
     */
    List<Diagnostic> analyze(String filePath, String content) {
        DslAstProvider astProvider = new AstBuilder(ruleRepository);
        DslFileNode ast;
        try {
            ast = astProvider.getDslAst(filePath, content);
        } catch (RuntimeException e) {
            // AstBuilder wraps parse errors into an error node; guard against
            // unexpected runtime failures so a single file never breaks the server.
            return List.of();
        }
        try {
            return diagnosticProvider.analyze(ast, ruleRepository, symbolTableBuilder,
                    PipelineMode.FULL, InspectionConfig.builder().build(), null);
        } catch (CancellationException e) {
            throw e;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /**
     * Builds the AST for the document text without running semantic analysis.
     *
     * <p>Used by context resolution (completion/hover) to locate the cursor's
     * structural position precisely via AST node ranges. Returns {@code null}
     * on parse failure so callers can fall back to the text-scanning
     * {@link ContextResolver}.</p>
     */
    DslFileNode parse(String filePath, String content) {
        try {
            return new AstBuilder(ruleRepository).getDslAst(filePath, content);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
