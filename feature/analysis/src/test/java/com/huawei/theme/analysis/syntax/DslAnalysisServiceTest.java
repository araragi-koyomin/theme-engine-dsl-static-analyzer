package com.huawei.theme.analysis.syntax;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.DslAnalysisService;
import com.huawei.theme.analysis.rule.repository.RuleRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslAnalysisServiceTest {

    private DslAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new DslAnalysisService();
    }

    @Test
    void service_shouldProvideRuleRepository() {
        RuleRepository repo = service.getRuleRepository();
        assertNotNull(repo);
        assertFalse(repo.getAllElementNames().isEmpty());
    }

    @Test
    void service_ruleRepositoryShouldContainRootElementNames() {
        RuleRepository repo = service.getRuleRepository();
        java.util.List<String> rootNames = repo.getRootElementNames();
        assertTrue(rootNames.contains("Lockscreen"));
        assertTrue(rootNames.contains("Wallpaper"));
        assertTrue(rootNames.contains("Widget"));
        assertTrue(rootNames.contains("ChargingSkin"));
    }

    @Test
    void service_shouldProvideFileMatcher() {
        assertNotNull(service.getFileMatcher());
    }

    @Test
    void service_fileMatcherShouldRejectNullVirtualFile() {
        assertFalse(service.getFileMatcher().isDslFile((com.intellij.openapi.vfs.VirtualFile) null));
    }

    @Test
    void service_ruleRepositoryShouldReturnSyn001Source() {
        RuleRepository repo = service.getRuleRepository();
        assertTrue(repo.getRuleSource("SYN-001").isPresent());
        assertEquals("SYN-001", repo.getRuleSource("SYN-001").get().getRuleId());
    }

    @Test
    void service_ruleRepositoryShouldReturnElementRules() {
        RuleRepository repo = service.getRuleRepository();
        assertTrue(repo.getElementRule("Var").isPresent());
        assertTrue(repo.getElementRule("Lockscreen").isPresent());
        assertFalse(repo.getElementRule("UnknownElement").isPresent());
    }
}
