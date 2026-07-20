package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary;
import com.huawei.theme.analysis.core.function.JsonFunctionSignatureLoader;
import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.cli.PipelineMode;
import com.huawei.theme.analysis.core.semanticanalysis.analyzers.ConstraintAnalyzer;
import com.huawei.theme.analysis.core.semanticanalysis.analyzers.RequiredAttrAnalyzer;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticProviderDegradationTest {

    private RuleRepository ruleRepo;
    private DiagnosticProviderImpl provider;
    private SymbolTableBuilderImpl symbolTableBuilder;
    private AstBuilder astBuilder;

    @BeforeEach
    void setUp() throws Exception {
        String rulesDir = System.getProperty("user.dir") + "/src/main/resources/rules";
        FunctionSignatureLibrary functionLibrary = new JsonFunctionSignatureLoader().loadFromClasspath();
        ruleRepo = new JsonRuleLoader().loadFromDirectory(rulesDir, functionLibrary);
        provider = new DiagnosticProviderImpl();
        symbolTableBuilder = new SymbolTableBuilderImpl();
        astBuilder = new AstBuilder(ruleRepo);
    }

    @AfterEach
    void tearDown() {
        AnalyzerRegistry.clear();
        AnalyzerRegistry.init();
    }

    @Test
    void analyzeWithNormalAnalyzersProducesDiagnostics() {
        AnalyzerRegistry.init();
        DslFileNode ast = astBuilder.getDslAst("test.xml", "<Lockscreen><Var/></Lockscreen>");
        List<Diagnostic> diagnostics = provider.analyze(ast, ruleRepo, symbolTableBuilder,
                PipelineMode.FULL, InspectionConfig.builder().build(), null);
        assertTrue(diagnostics != null);
    }

    @Test
    void analyzeContinuesWhenSingleAnalyzerThrows() {
        AnalyzerRegistry.clear();
        AnalyzerRegistry.register(new RequiredAttrAnalyzer());
        AnalyzerRegistry.register(new ThrowingAnalyzer());
        AnalyzerRegistry.register(new ConstraintAnalyzer());

        DslFileNode ast = astBuilder.getDslAst("test.xml", "<Lockscreen><Var/></Lockscreen>");
        List<Diagnostic> diagnostics = provider.analyze(ast, ruleRepo, symbolTableBuilder,
                PipelineMode.FULL, InspectionConfig.builder().build(), null);

        assertTrue(diagnostics != null);
        boolean hasAnalyzerError = diagnostics.stream()
                .anyMatch(d -> d.getRuleId().equals("INTERNAL-ANALYZER-ERROR"));
        assertTrue(hasAnalyzerError, "INTERNAL-ANALYZER-ERROR should be present from ThrowingAnalyzer");
        boolean hasRequiredAttrDiags = diagnostics.stream()
                .anyMatch(d -> d.getRuleId().equals("SEM-REQ-001"));
        assertTrue(hasRequiredAttrDiags, "RequiredAttrAnalyzer results should still be present despite ThrowingAnalyzer failure");
    }
}
