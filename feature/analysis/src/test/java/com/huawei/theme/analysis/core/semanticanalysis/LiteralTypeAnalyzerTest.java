package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.semanticanalysis.analyzers.LiteralTypeAnalyzer;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiteralTypeAnalyzerTest {

    private static final String DOC_URL =
            "https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-general-0000002504354839";
    private final LiteralTypeAnalyzer analyzer = new LiteralTypeAnalyzer();

    @Test
    void nonNumericLiteralProducesSEM_TYPE_003() {
        DslElementNode node = AnalyzerTestFixtures.element("Lockscreen", Map.of("frameRate", "abc"));
        DslFileNode file = AnalyzerTestFixtures.file("Lockscreen");
        DslElementRule rule = AnalyzerTestFixtures.rule("Lockscreen",
                null, Map.of("frameRate", AnalyzerTestFixtures.numberSpec()), null);
        RuleRepository repo = AnalyzerTestFixtures.stubRepo(
                Map.of("Lockscreen", rule),
                Map.of("SEM-TYPE-003", AnalyzerTestFixtures.source("SEM-TYPE-003", DOC_URL)));

        List<Diagnostic> diags = analyzer.analyze(node, AnalyzerTestFixtures.context(repo, file));

        assertEquals(1, diags.size());
        Diagnostic d = diags.get(0);
        assertEquals(DiagnosticSeverity.ERROR, d.getSeverity());
        assertEquals("SEM-TYPE-003", d.getRuleId());
        assertEquals("属性值类型错误: frameRate 期望 number, 实际 abc", d.getMessage());
        assertEquals("test.xml", d.getFilePath());
        assertEquals(10, d.getLine());
        assertEquals(5, d.getColumn());
        assertEquals(DOC_URL, d.getRuleDocUrl());
    }

    @Test
    void numericLiteralNoDiagnostic() {
        DslElementNode node = AnalyzerTestFixtures.element("Lockscreen", Map.of("frameRate", "60"));
        DslFileNode file = AnalyzerTestFixtures.file("Lockscreen");
        DslElementRule rule = AnalyzerTestFixtures.rule("Lockscreen",
                null, Map.of("frameRate", AnalyzerTestFixtures.numberSpec()), null);
        RuleRepository repo = AnalyzerTestFixtures.stubRepo(
                Map.of("Lockscreen", rule),
                Map.of("SEM-TYPE-003", AnalyzerTestFixtures.source("SEM-TYPE-003", DOC_URL)));

        List<Diagnostic> diags = analyzer.analyze(node, AnalyzerTestFixtures.context(repo, file));

        assertTrue(diags.isEmpty());
    }

    @Test
    void nonNumberTypeAttrSkipped() {
        DslElementNode node = AnalyzerTestFixtures.element("Image", Map.of("scaleType", "fill"));
        DslFileNode file = AnalyzerTestFixtures.file("Lockscreen");
        DslElementRule rule = AnalyzerTestFixtures.rule("Image",
                null, Map.of("scaleType", AnalyzerTestFixtures.enumSpec("fill", "center")), null);
        RuleRepository repo = AnalyzerTestFixtures.stubRepo(
                Map.of("Image", rule),
                Map.of("SEM-TYPE-003", AnalyzerTestFixtures.source("SEM-TYPE-003", DOC_URL)));

        List<Diagnostic> diags = analyzer.analyze(node, AnalyzerTestFixtures.context(repo, file));

        assertTrue(diags.isEmpty());
    }
}
