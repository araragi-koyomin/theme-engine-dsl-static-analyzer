package com.huawei.theme.analysis.core.semanticanalysis;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
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
import com.huawei.theme.analysis.core.syntaxanalysis.DslAstProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 语义分析端到端集成测试：真实规则库 + 函数签名库 + AstBuilder + SymbolTableBuilderImpl +
 * DiagnosticProviderImpl，验证 ConstraintAnalyzer/VarRefAnalyzer/TypeAnalyzer 三者协同。
 */
class SemanticAnalysisIntegrationTest {

    private static RuleRepository ruleRepo;
    private static DslAstProvider astProvider;
    private static SymbolTableBuilder symbolTableBuilder;
    private static DiagnosticProvider provider;

    @BeforeAll
    static void setup() {
        String rulesDir = System.getProperty("user.dir") + "/src/main/resources/rules";
        FunctionSignatureLibrary functionLibrary = new JsonFunctionSignatureLoader().loadFromClasspath();
        ruleRepo = new JsonRuleLoader().loadFromDirectory(rulesDir, functionLibrary);
        astProvider = new AstBuilder(ruleRepo);
        symbolTableBuilder = new SymbolTableBuilderImpl();
        provider = new DiagnosticProviderImpl();
        AnalyzerRegistry.init();
    }

    private List<Diagnostic> analyze(String xml) {
        DslFileNode ast = astProvider.getDslAst("test.xml", xml);
        return provider.analyze(ast, ruleRepo, symbolTableBuilder,
                PipelineMode.FULL, InspectionConfig.builder().build(), null);
    }

    private List<Diagnostic> analyzeResource(String resourcePath) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            assertTrue(is != null, "fixture not found: " + resourcePath);
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return analyze(content);
        }
    }

    private static List<Diagnostic> refAndTypeDiagnostics(List<Diagnostic> all) {
        return all.stream()
                .filter(d -> {
                    String id = d.getRuleId();
                    return id != null && (id.startsWith("SEM-REF") || id.startsWith("SEM-TYPE"));
                })
                .collect(Collectors.toList());
    }

    private static void assertHasRule(List<Diagnostic> diagnostics, String ruleId) {
        long count = diagnostics.stream().filter(d -> ruleId.equals(d.getRuleId())).count();
        assertTrue(count > 0, "expected diagnostic with ruleId " + ruleId + " but found: "
                + diagnostics.stream().map(Diagnostic::getRuleId).collect(Collectors.joining(",")));
    }

    // --- 合法 fixture 无 SEM-REF/SEM-TYPE 错误 ---

    @Test
    void validWidgetHasNoRefOrTypeErrors() throws Exception {
        List<Diagnostic> diagnostics = analyzeResource("/dsl/valid_widget.xml");
        List<Diagnostic> refType = refAndTypeDiagnostics(diagnostics);
        assertTrue(refType.isEmpty(), "unexpected ref/type diagnostics: "
                + refType.stream().map(Diagnostic::getMessage).collect(Collectors.joining("; ")));
    }

    @Test
    void validLockscreenHasNoRefOrTypeErrors() throws Exception {
        List<Diagnostic> diagnostics = analyzeResource("/dsl/valid_lockscreen.xml");
        List<Diagnostic> refType = refAndTypeDiagnostics(diagnostics);
        assertTrue(refType.isEmpty(), "unexpected ref/type diagnostics: "
                + refType.stream().map(Diagnostic::getMessage).collect(Collectors.joining("; ")));
    }

    // --- SEM-REF-001: 未定义变量引用 ---

    @Test
    void undefinedVarRefProducesSEM_REF_001() {
        List<Diagnostic> refType = refAndTypeDiagnostics(analyze(
                "<Lockscreen><Image name=\"img\" x=\"#undefinedVar\"/></Lockscreen>"));
        assertEquals(1, refType.size());
        assertHasRule(refType, "SEM-REF-001");
        assertEquals("引用未定义变量 #undefinedVar", refType.get(0).getMessage());
    }

    // --- SEM-REF-001/002: 元素 name 引用 ---

    @Test
    void commandTargetUndefinedElementProducesSEM_REF_002() {
        List<Diagnostic> refType = refAndTypeDiagnostics(analyze(
                "<Lockscreen><Command target=\"missing.visibility\" value=\"false\"/></Lockscreen>"));
        assertHasRule(refType, "SEM-REF-002");
    }

    @Test
    void elementPropertyRefUndefinedProducesSEM_REF_001() {
        List<Diagnostic> refType = refAndTypeDiagnostics(analyze(
                "<Lockscreen><Image name=\"img\" x=\"#missing.move_x\"/></Lockscreen>"));
        assertHasRule(refType, "SEM-REF-001");
    }

    @Test
    void commandTargetInvalidPropertyProducesSEM_REF_002() {
        List<Diagnostic> refType = refAndTypeDiagnostics(analyze(
                "<Lockscreen><Image name=\"img\"/>"
                + "<Command target=\"img.unknown\" value=\"false\"/></Lockscreen>"));
        assertHasRule(refType, "SEM-REF-002");
    }

    // --- SEM-REF-003: 重复变量定义 ---

    @Test
    void duplicateVarProducesSEM_REF_003() {
        List<Diagnostic> refType = refAndTypeDiagnostics(analyze(
                "<Lockscreen><Var name=\"v\" type=\"number\"/><Var name=\"v\" type=\"number\"/></Lockscreen>"));
        assertHasRule(refType, "SEM-REF-003");
    }

    // --- SEM-TYPE-001: 类型不匹配 ---

    @Test
    void varStringTypeWithNumericGlobalProducesSEM_TYPE_001() {
        List<Diagnostic> refType = refAndTypeDiagnostics(analyze(
                "<Lockscreen><Var name=\"v\" expression=\"#battery_level\" type=\"string\"/></Lockscreen>"));
        assertHasRule(refType, "SEM-TYPE-001");
    }

    @Test
    void stringVarInNumericAttrProducesSEM_TYPE_001() {
        List<Diagnostic> refType = refAndTypeDiagnostics(analyze(
                "<Lockscreen><Var name=\"s\" type=\"string\"/><Image name=\"img\" x=\"#s\"/></Lockscreen>"));
        assertHasRule(refType, "SEM-TYPE-001");
    }

    @Test
    void crossContextFunctionParamMismatchProducesSEM_TYPE_002() {
        List<Diagnostic> refType = refAndTypeDiagnostics(analyze(
                "<Lockscreen><Text name=\"t\" textExp=\"sin('1')\"/></Lockscreen>"));
        assertHasRule(refType, "SEM-TYPE-002");
    }

    // --- SEM-TYPE-002: 函数参数不匹配 ---

    @Test
    void functionParamMismatchProducesSEM_TYPE_002() {
        List<Diagnostic> refType = refAndTypeDiagnostics(analyze(
                "<Lockscreen><Image name=\"img\" x=\"sin('hello')\"/></Lockscreen>"));
        assertHasRule(refType, "SEM-TYPE-002");
    }

    // --- 合法构造无错误 ---

    @Test
    void validVarExpressionNoRefOrTypeError() {
        List<Diagnostic> refType = refAndTypeDiagnostics(analyze(
                "<Lockscreen><Var name=\"v\" expression=\"#battery_level\" type=\"number\"/></Lockscreen>"));
        assertTrue(refType.isEmpty(), "unexpected: "
                + refType.stream().map(Diagnostic::getMessage).collect(Collectors.joining("; ")));
    }

    @Test
    void validElementPropertyRefNoError() {
        List<Diagnostic> refType = refAndTypeDiagnostics(analyze(
                "<Lockscreen>"
                + "<Unlocker name=\"unlocker\" x=\"0\" y=\"0\" w=\"100\" h=\"100\"/>"
                + "<Image name=\"img\" x=\"#unlocker.move_x\"/>"
                + "</Lockscreen>"));
        assertTrue(refType.isEmpty(), "unexpected: "
                + refType.stream().map(Diagnostic::getMessage).collect(Collectors.joining("; ")));
    }

    @Test
    void validCommandTargetNoError() {
        List<Diagnostic> refType = refAndTypeDiagnostics(analyze(
                "<Lockscreen>"
                + "<Image name=\"img\" x=\"0\" y=\"0\"/>"
                + "<Command target=\"img.visibility\" value=\"false\"/>"
                + "</Lockscreen>"));
        assertTrue(refType.isEmpty(), "unexpected: "
                + refType.stream().map(Diagnostic::getMessage).collect(Collectors.joining("; ")));
    }

    // --- 多错误混合 ---

    @Test
    void multipleErrorsReportedIndependently() {
        List<Diagnostic> refType = refAndTypeDiagnostics(analyze(
                "<Lockscreen>"
                + "<Var name=\"v\" expression=\"#undefinedVar\" type=\"string\"/>"
                + "<Var name=\"v\" type=\"number\"/>"
                + "</Lockscreen>"));
        // #undefinedVar → SEM-REF-001; type=string expression=number(undefined→null 跳过);
        // 重复 v → SEM-REF-003
        assertHasRule(refType, "SEM-REF-001");
        assertHasRule(refType, "SEM-REF-003");
    }
}
