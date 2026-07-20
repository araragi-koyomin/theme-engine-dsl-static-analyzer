package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.semanticanalysis.analyzers.EnumValueAnalyzer;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumValueAnalyzerTest {

    private static final String DOC_URL =
            "https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-general-0000002504354839";
    private final EnumValueAnalyzer analyzer = new EnumValueAnalyzer();

    @Test
    void invalidEnumValueProducesSEM_ENUM_001() {
        DslElementNode node = AnalyzerTestFixtures.element("Image", Map.of("scaleType", "invalid"));
        DslFileNode file = AnalyzerTestFixtures.file("Lockscreen");
        DslElementRule rule = AnalyzerTestFixtures.rule("Image",
                null, Map.of("scaleType", AnalyzerTestFixtures.enumSpec("fill", "center")), null);
        RuleRepository repo = AnalyzerTestFixtures.stubRepo(
                Map.of("Image", rule),
                Map.of("SEM-ENUM-001", AnalyzerTestFixtures.source("SEM-ENUM-001", DOC_URL)));

        List<Diagnostic> diags = analyzer.analyze(node, AnalyzerTestFixtures.context(repo, file));

        assertEquals(1, diags.size());
        Diagnostic d = diags.get(0);
        assertEquals(DiagnosticSeverity.ERROR, d.getSeverity());
        assertEquals("SEM-ENUM-001", d.getRuleId());
        assertEquals("枚举值错误: scaleType=invalid, 合法值: [fill, center]", d.getMessage());
        assertEquals("test.xml", d.getFilePath());
        assertEquals(10, d.getLine());
        assertEquals(5, d.getColumn());
        assertEquals(DOC_URL, d.getRuleDocUrl());
    }

    @Test
    void validEnumValueNoDiagnostic() {
        DslElementNode node = AnalyzerTestFixtures.element("Image", Map.of("scaleType", "fill"));
        DslFileNode file = AnalyzerTestFixtures.file("Lockscreen");
        DslElementRule rule = AnalyzerTestFixtures.rule("Image",
                null, Map.of("scaleType", AnalyzerTestFixtures.enumSpec("fill", "center")), null);
        RuleRepository repo = AnalyzerTestFixtures.stubRepo(
                Map.of("Image", rule),
                Map.of("SEM-ENUM-001", AnalyzerTestFixtures.source("SEM-ENUM-001", DOC_URL)));

        List<Diagnostic> diags = analyzer.analyze(node, AnalyzerTestFixtures.context(repo, file));

        assertTrue(diags.isEmpty());
    }

    @Test
    void nonEnumAttrSkipped() {
        DslElementNode node = AnalyzerTestFixtures.element("Lockscreen", Map.of("frameRate", "abc"));
        DslFileNode file = AnalyzerTestFixtures.file("Lockscreen");
        DslElementRule rule = AnalyzerTestFixtures.rule("Lockscreen",
                null, Map.of("frameRate", AnalyzerTestFixtures.numberSpec()), null);
        RuleRepository repo = AnalyzerTestFixtures.stubRepo(
                Map.of("Lockscreen", rule),
                Map.of("SEM-ENUM-001", AnalyzerTestFixtures.source("SEM-ENUM-001", DOC_URL)));

        List<Diagnostic> diags = analyzer.analyze(node, AnalyzerTestFixtures.context(repo, file));

        assertTrue(diags.isEmpty());
    }
}
