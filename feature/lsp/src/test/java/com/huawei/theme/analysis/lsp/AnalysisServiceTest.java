package com.huawei.theme.analysis.lsp;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AnalysisServiceTest {

    @Test
    void loadsRulesAndAnalyzesWithoutException() {
        RuleRepository repo = new RuleRepositoryFactory(null).create();
        assertFalse(repo.getAllElementNames().isEmpty(),
                "built-in rule repository should load element rules");

        AnalysisService svc = new AnalysisService(repo);
        String content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<Widget screenWidth=\"1080\" screenHeight=\"530\">\n"
                + "  <Group name=\"g\" x=\"0\" y=\"0\" w=\"1080\" h=\"530\"/>\n"
                + "</Widget>";
        List<Diagnostic> diags = svc.analyze("script.xml", content);
        assertNotNull(diags, "analysis must return a non-null diagnostic list");
    }

    @Test
    void analyzesMalformedXmlGracefully() {
        RuleRepository repo = new RuleRepositoryFactory(null).create();
        AnalysisService svc = new AnalysisService(repo);
        // Unclosed <Var> and <Lockscreen>; the SAX parser surfaces this as an
        // error node. AnalysisService must not throw.
        String content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<Lockscreen frameRate=\"60\" screenWidth=\"1080\">\n"
                + "  <Var name=\"testVar\" expression=\"1\" type=\"number\">\n"
                + "  <Group name=\"testGroup\" x=\"0\" y=\"0\" w=\"1080\" h=\"1920\"/>\n";
        List<Diagnostic> diags = svc.analyze("script_error.xml", content);
        assertNotNull(diags, "malformed XML must not crash the analyzer");
    }
}
