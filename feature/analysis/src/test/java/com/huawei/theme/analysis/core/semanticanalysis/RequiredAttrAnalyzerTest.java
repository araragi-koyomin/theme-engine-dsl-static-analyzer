package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.semanticanalysis.analyzers.RequiredAttrAnalyzer;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequiredAttrAnalyzerTest {

    private static final String DOC_URL =
            "https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-0000002279818413";
    private final RequiredAttrAnalyzer analyzer = new RequiredAttrAnalyzer();

    @Test
    void missingRequiredAttrProducesSEM_REQ_001() {
        DslElementNode varNode = AnalyzerTestFixtures.element("Var", Map.of());
        DslFileNode file = AnalyzerTestFixtures.file("Lockscreen");
        DslElementRule varRule = AnalyzerTestFixtures.rule("Var", List.of("name"), null, null);
        RuleRepository repo = AnalyzerTestFixtures.stubRepo(
                Map.of("Var", varRule),
                Map.of("SEM-REQ-001", AnalyzerTestFixtures.source("SEM-REQ-001", DOC_URL)));

        List<Diagnostic> diags = analyzer.analyze(varNode, AnalyzerTestFixtures.context(repo, file));

        assertEquals(1, diags.size());
        Diagnostic d = diags.get(0);
        assertEquals(DiagnosticSeverity.ERROR, d.getSeverity());
        assertEquals("SEM-REQ-001", d.getRuleId());
        assertEquals("缺失必填属性: name", d.getMessage());
        assertEquals("test.xml", d.getFilePath());
        assertEquals(10, d.getLine());
        assertEquals(5, d.getColumn());
        assertEquals(DOC_URL, d.getRuleDocUrl());
    }

    @Test
    void requiredAttrPresentNoDiagnostic() {
        DslElementNode varNode = AnalyzerTestFixtures.element("Var", Map.of("name", "x"));
        DslFileNode file = AnalyzerTestFixtures.file("Lockscreen");
        DslElementRule varRule = AnalyzerTestFixtures.rule("Var", List.of("name"), null, null);
        RuleRepository repo = AnalyzerTestFixtures.stubRepo(
                Map.of("Var", varRule),
                Map.of("SEM-REQ-001", AnalyzerTestFixtures.source("SEM-REQ-001", DOC_URL)));

        List<Diagnostic> diags = analyzer.analyze(varNode, AnalyzerTestFixtures.context(repo, file));

        assertTrue(diags.isEmpty());
    }

    @Test
    void unknownElementSkipped() {
        DslElementNode node = AnalyzerTestFixtures.element("Unknown", Map.of());
        DslFileNode file = AnalyzerTestFixtures.file("Lockscreen");
        RuleRepository repo = AnalyzerTestFixtures.stubRepo(
                Map.of(),
                Map.of("SEM-REQ-001", AnalyzerTestFixtures.source("SEM-REQ-001", DOC_URL)));

        List<Diagnostic> diags = analyzer.analyze(node, AnalyzerTestFixtures.context(repo, file));

        assertTrue(diags.isEmpty());
    }
}
