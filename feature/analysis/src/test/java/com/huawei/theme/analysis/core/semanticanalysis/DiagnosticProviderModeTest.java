package com.huawei.theme.analysis.core.semanticanalysis;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.cli.PipelineMode;
import com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary;
import com.huawei.theme.analysis.core.function.JsonFunctionSignatureLoader;
import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates SPEC-1 (mode-aware dispatch) and SPEC-5 (TypeAnalyzer filtering) at the
 * DiagnosticProvider level. Each test exercises a different PipelineMode / config combo
 * and asserts the ruleId prefix guarantees from the spec.
 */
class DiagnosticProviderModeTest {

    private static RuleRepository ruleRepo;
    private static AstBuilder astBuilder;
    private static SymbolTableBuilderImpl symbolTableBuilder;
    private DiagnosticProviderImpl provider;

    @BeforeAll
    static void setupClass() {
        String rulesDir = System.getProperty("user.dir") + "/src/main/resources/rules";
        FunctionSignatureLibrary functionLibrary = new JsonFunctionSignatureLoader().loadFromClasspath();
        ruleRepo = new JsonRuleLoader().loadFromDirectory(rulesDir, functionLibrary);
        astBuilder = new AstBuilder(ruleRepo);
        symbolTableBuilder = new SymbolTableBuilderImpl();
        AnalyzerRegistry.init();
    }

    @BeforeEach
    void setUp() {
        provider = new DiagnosticProviderImpl();
        AnalyzerRegistry.init();
    }

    private List<Diagnostic> analyze(String xml, PipelineMode mode, boolean typeCheck) {
        DslFileNode ast = astBuilder.getDslAst("test.xml", xml);
        InspectionConfig config = InspectionConfig.builder()
                .pipelineMode(mode)
                .typeCheck(typeCheck)
                .build();
        return provider.analyze(ast, ruleRepo, symbolTableBuilder, mode, config, null);
    }

    private List<Diagnostic> analyzeResource(String resourcePath, PipelineMode mode, boolean typeCheck)
            throws Exception {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            assertTrue(is != null, "fixture not found: " + resourcePath);
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return analyze(content, mode, typeCheck);
        }
    }

    private static List<Diagnostic> onlyByPrefix(List<Diagnostic> diags, String prefix) {
        return diags.stream()
                .filter(d -> d.getRuleId() != null && d.getRuleId().startsWith(prefix))
                .collect(Collectors.toList());
    }

    private static String ruleIds(List<Diagnostic> diags) {
        return diags.stream().map(Diagnostic::getRuleId)
                .collect(Collectors.joining(","));
    }

    @Test
    void fullModeWiresSyntaxCheckerAndProducesSyn003ForUnknownElement() {
        List<Diagnostic> diags = analyze(
                "<Lockscreen><TotallyBogusElement123 xyz=\"1\"/></Lockscreen>",
                PipelineMode.FULL, true);
        boolean hasSyn003 = diags.stream().anyMatch(d -> "SYN-003".equals(d.getRuleId()));
        assertTrue(hasSyn003, "FULL mode should produce SYN-003 for unknown element; got: "
                + ruleIds(diags));
    }

    @Test
    void syntaxOnlyProducesOnlySynDiagnostics() throws Exception {
        List<Diagnostic> diags = analyzeResource(
                "/fixtures/complex/deep_nesting_violations.xml",
                PipelineMode.SYNTAX_ONLY, true);
        List<Diagnostic> sem = onlyByPrefix(diags, "SEM-");
        assertTrue(sem.isEmpty(), "SYNTAX_ONLY must not produce SEM-* diagnostics; found: "
                + ruleIds(sem));
    }

    @Test
    void semanticOnlyProducesOnlySemDiagnostics() throws Exception {
        List<Diagnostic> diags = analyzeResource(
                "/fixtures/complex/deep_nesting_violations.xml",
                PipelineMode.SEMANTIC_ONLY, true);
        List<Diagnostic> syn = onlyByPrefix(diags, "SYN-");
        assertTrue(syn.isEmpty(), "SEMANTIC_ONLY must not produce SYN-* diagnostics; found: "
                + ruleIds(syn));
    }

    @Test
    void fullModeTypeCheckFalseExcludesSemTypeDiagnostics() throws Exception {
        List<Diagnostic> diags = analyzeResource(
                "/fixtures/complex/type_inference_edge_cases.xml",
                PipelineMode.FULL, false);
        List<Diagnostic> semType = onlyByPrefix(diags, "SEM-TYPE-");
        assertTrue(semType.isEmpty(),
                "FULL + typeCheck=false must not produce SEM-TYPE-*; found: " + ruleIds(semType));
    }

    @Test
    void fullModeTypeCheckTrueIncludesSemTypeDiagnostics() throws Exception {
        List<Diagnostic> diags = analyzeResource(
                "/fixtures/complex/type_inference_edge_cases.xml",
                PipelineMode.FULL, true);
        List<Diagnostic> semType = onlyByPrefix(diags, "SEM-TYPE-");
        assertFalse(semType.isEmpty(),
                "FULL + typeCheck=true should produce SEM-TYPE-* diagnostics; got: " + ruleIds(diags));
    }
}
