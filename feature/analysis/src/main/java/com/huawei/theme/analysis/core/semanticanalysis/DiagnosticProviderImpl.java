package com.huawei.theme.analysis.core.semanticanalysis;

import com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

import java.util.ArrayList;
import java.util.List;

public class DiagnosticProviderImpl implements DiagnosticProvider{

    private final FunctionSignatureLibrary functionLibrary;

    public DiagnosticProviderImpl() {
        this(null);
    }

    public DiagnosticProviderImpl(FunctionSignatureLibrary functionLibrary) {
        this.functionLibrary = functionLibrary;
    }

    @Override
    public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder symbolTableBuilder) {
        return new DiagnosticProviderImplInner(ast, ruleRepo, symbolTableBuilder, functionLibrary).getDiagnostics();
    }

    static class DiagnosticProviderImplInner{

        DslFileNode root;
        RuleRepository ruleRepo;
        SymbolTable globalTable;
        SymbolTableBuilder symbolTableBuilder;
        FunctionSignatureLibrary functionLibrary;
        List<Diagnostic> diagnostics = new ArrayList<>();

        public DiagnosticProviderImplInner(DslFileNode root, RuleRepository ruleRepo,
                                           SymbolTableBuilder symbolTableBuilder,
                                           FunctionSignatureLibrary functionLibrary) {
            this.root = root;
            this.ruleRepo = ruleRepo;
            this.symbolTableBuilder = symbolTableBuilder;
            this.functionLibrary = functionLibrary;

            globalTable=symbolTableBuilder.buildGlobal(root, ruleRepo);
        }

        List<Diagnostic> getDiagnostics(){
            analyze(root.getRootElement(), globalTable);

            return diagnostics;
        }

        private void analyze(DslElementNode elementNode, SymbolTable symbolTable){
            for(var analyzer: AnalyzerRegistry.getAnalyzers()){
                var list = analyzer.analyze(elementNode,
                        new DslContext(ruleRepo, symbolTable, root.getFilePath(), functionLibrary));
                diagnostics.addAll(list);
            }

            for(var child: elementNode.getChildElements()){
                analyze(child, symbolTableBuilder.build(elementNode, symbolTable, ruleRepo));
            }
        }
    }
}
