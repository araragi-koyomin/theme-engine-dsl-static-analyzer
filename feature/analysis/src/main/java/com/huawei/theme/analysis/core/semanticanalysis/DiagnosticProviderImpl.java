package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.cli.PipelineMode;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.analyzers.SyntaxErrorAnalyzer;
import com.huawei.theme.analysis.core.semanticanalysis.analyzers.TypeAnalyzer;
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.syntaxanalysis.ExpressionSyntaxChecker;
import com.huawei.theme.analysis.core.syntaxanalysis.SyntaxChecker;

public class DiagnosticProviderImpl implements DiagnosticProvider {

    @Override
    public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo,
                                    SymbolTableBuilder symbolTableBuilder,
                                    PipelineMode mode, InspectionConfig config,
                                    VerboseCollector collector) {
        Objects.requireNonNull(ruleRepo, "ruleRepo must not be null");
        if (mode == null) {
            mode = PipelineMode.FULL;
        }
        if (config == null) {
            config = InspectionConfig.builder().build();
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        if (mode != PipelineMode.SYNTAX_ONLY) {
            diagnostics.addAll(analyzeSemantic(ast, ruleRepo, symbolTableBuilder, config, mode, collector));
        }
        if (mode != PipelineMode.SEMANTIC_ONLY) {
            diagnostics.addAll(analyzeSyntax(ast, ruleRepo));
        }
        return diagnostics;
    }

    private List<Diagnostic> analyzeSyntax(DslFileNode ast, RuleRepository ruleRepo) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        DslElementNode root = ast.getRootElement();
        if (root == null || root.isHasError()) {
            return diagnostics;
        }
        diagnostics.addAll(new SyntaxChecker(ruleRepo).check(ast.getFilePath(), ast));
        diagnostics.addAll(new ExpressionSyntaxChecker(ruleRepo).check(ast.getFilePath(), ast));
        return diagnostics;
    }

    private List<Diagnostic> analyzeSemantic(DslFileNode ast, RuleRepository ruleRepo,
                                            SymbolTableBuilder symbolTableBuilder,
                                            InspectionConfig config, PipelineMode mode,
                                            VerboseCollector collector) {
        DslElementNode root = ast.getRootElement();
        if (root == null) {
            return List.of();
        }
        return new DiagnosticProviderImplInner(ast, ruleRepo, symbolTableBuilder,
                config, mode, collector).getDiagnostics();
    }

    static class DiagnosticProviderImplInner {

        DslFileNode root;
        RuleRepository ruleRepo;
        SymbolTable globalTable;
        SymbolTableBuilder symbolTableBuilder;
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<DslAnalyzer> filteredAnalyzers;
        VerboseCollector collector;

        public DiagnosticProviderImplInner(DslFileNode root, RuleRepository ruleRepo,
                                           SymbolTableBuilder symbolTableBuilder,
                                           InspectionConfig config, PipelineMode mode,
                                           VerboseCollector collector) {
            this.root = root;
            this.ruleRepo = ruleRepo;
            this.symbolTableBuilder = symbolTableBuilder;
            this.collector = collector;
            this.filteredAnalyzers = filterAnalyzers(config, mode);
            globalTable = symbolTableBuilder.buildGlobal(root, ruleRepo);
        }

        private List<DslAnalyzer> filterAnalyzers(InspectionConfig config, PipelineMode mode) {
            if (mode == PipelineMode.SYNTAX_ONLY) {
                return new ArrayList<>();
            }
            boolean removeType = mode == PipelineMode.SEMANTIC_ONLY
                    || (config != null && !config.isTypeCheck());
            boolean removeSyntaxError = mode == PipelineMode.SEMANTIC_ONLY;
            List<DslAnalyzer> filtered = new ArrayList<>();
            for (DslAnalyzer analyzer : AnalyzerRegistry.getAnalyzers()) {
                if (removeType && analyzer instanceof TypeAnalyzer) {
                    continue;
                }
                if (removeSyntaxError && analyzer instanceof SyntaxErrorAnalyzer) {
                    continue;
                }
                filtered.add(analyzer);
            }
            return filtered;
        }

        List<Diagnostic> getDiagnostics() {
            analyze(root.getRootElement(), globalTable);
            return diagnostics;
        }

        private void analyze(DslElementNode elementNode, SymbolTable symbolTable) {
            for (var analyzer : filteredAnalyzers) {
                try {
                    var list = analyzer.analyze(elementNode,
                            new DslContext(ruleRepo, symbolTable, root.getFilePath(), root));
                    diagnostics.addAll(list);
                    if (collector != null) {
                        collector.recordAnalyzerCount(analyzer.getClass().getSimpleName(), list.size());
                    }
                } catch (Exception e) {
                    diagnostics.add(Diagnostic.builder()
                            .severity(DiagnosticSeverity.WARNING)
                            .ruleId("INTERNAL-ANALYZER-ERROR")
                            .message("Analyzer " + analyzer.getClass().getSimpleName() + " failed: " + e.getMessage())
                            .filePath(root.getFilePath())
                            .positionFrom(elementNode)
                            .build());
                }
            }

            for (var child : elementNode.getChildElements()) {
                try {
                    SymbolTable childTable = symbolTableBuilder.build(elementNode, symbolTable, ruleRepo);
                    analyze(child, childTable);
                } catch (Exception e) {
                    diagnostics.add(Diagnostic.builder()
                            .severity(DiagnosticSeverity.WARNING)
                            .ruleId("INTERNAL-SYMBOLTABLE-ERROR")
                            .message("SymbolTable build failed for child element: " + e.getMessage())
                            .filePath(root.getFilePath())
                            .positionFrom(child)
                            .build());
                    break;
                }
            }
        }
    }
}
