package com.huawei.theme.analysis.core.semanticanalysis;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.syntaxanalysis.ExpressionSyntaxChecker;

import java.util.ArrayList;
import java.util.List;

public class DiagnosticProviderImpl implements DiagnosticProvider {

    @Override
    public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder symbolTableBuilder) {
        List<Diagnostic> diagnostics =
                new DiagnosticProviderImplInner(ast, ruleRepo, symbolTableBuilder).getDiagnostics();
        diagnostics.addAll(new ExpressionSyntaxChecker(ruleRepo).check(ast.getFilePath(), ast));
        return diagnostics;
    }

    static class DiagnosticProviderImplInner {

        DslFileNode root;
        RuleRepository ruleRepo;
        SymbolTable globalTable;
        SymbolTableBuilder symbolTableBuilder;
        List<Diagnostic> diagnostics = new ArrayList<>();

        public DiagnosticProviderImplInner(DslFileNode root, RuleRepository ruleRepo,
                                           SymbolTableBuilder symbolTableBuilder) {
            this.root = root;
            this.ruleRepo = ruleRepo;
            this.symbolTableBuilder = symbolTableBuilder;
            globalTable = symbolTableBuilder.buildGlobal(root, ruleRepo);
        }

        List<Diagnostic> getDiagnostics() {
            analyze(root.getRootElement(), globalTable);
            return diagnostics;
        }

        private void analyze(DslElementNode elementNode, SymbolTable symbolTable) {
            for (var analyzer : AnalyzerRegistry.getAnalyzers()) {
                try {
                    var list = analyzer.analyze(elementNode,
                            new DslContext(ruleRepo, symbolTable, root.getFilePath(), root));
                    diagnostics.addAll(list);
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
