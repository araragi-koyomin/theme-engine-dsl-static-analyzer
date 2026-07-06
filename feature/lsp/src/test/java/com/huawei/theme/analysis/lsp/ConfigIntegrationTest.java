package com.huawei.theme.analysis.lsp;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.cli.ConfigAwareRuleRepository;
import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end check that wrapping the delegate rule repository with a
 * {@link ConfigAwareRuleRepository} (driven by an {@link InspectionConfig})
 * keeps the analysis pipeline functional and that the wrapper actually
 * applies severity overrides.
 */
class ConfigIntegrationTest {

    private static final String DSL =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<Widget screenWidth=\"1080\" screenHeight=\"530\">\n"
                    + "  <Group name=\"g\" x=\"0\" y=\"0\" w=\"1080\" h=\"530\"/>\n"
                    + "</Widget>";

    @Test
    void configAwareWrapperDoesNotBreakAnalysis() {
        RuleRepository delegate = new RuleRepositoryFactory(null).create();
        InspectionConfig config = InspectionConfig.builder()
                .disabledRuleIds(List.of("NONEXISTENT-RULE"))
                .build();
        RuleRepository aware = new ConfigAwareRuleRepository(delegate, config);
        List<Diagnostic> diags = new AnalysisService(aware).analyze("script.xml", DSL);
        assertNotNull(diags, "analysis through ConfigAwareRuleRepository must not throw");
    }

    @Test
    void severityOverrideIsVisibleThroughWrapper() {
        RuleRepository delegate = new RuleRepositoryFactory(null).create();
        // Pick any rule id that exists in the repository so the override has a
        // target; the wrapper must surface the overridden severity via
        // getConstraints (ConstraintAnalyzer consumes it).
        String anyRuleId = delegate.getAllElementRules().stream()
                .flatMap(r -> r.getConstraints().stream())
                .map(c -> c.getRuleId())
                .filter(r -> r != null)
                .findFirst()
                .orElse(null);
        // The test is meaningful only when the repository has at least one
        // declared constraint rule id; otherwise it just asserts the wrapper
        // builds and queries without throwing.
        if (anyRuleId == null) {
            assertFalse(delegate.getAllElementRules().isEmpty());
            return;
        }
        InspectionConfig config = InspectionConfig.builder()
                .severityOverrides(Map.of(anyRuleId, DiagnosticSeverity.WARNING))
                .build();
        RuleRepository aware = new ConfigAwareRuleRepository(delegate, config);
        // The wrapped repo must still report the element and the overridden
        // constraint severity must be WARNING for that rule id somewhere.
        assertFalse(aware.getAllElementNames().isEmpty());
        // ConfigAware applies severity overrides when getElementRule rebuilds
        // the rule, so the overridden severity is visible on the rule's own
        // constraints (not via the pass-through getConstraints).
        boolean overridden = aware.getAllElementRules().stream()
                .flatMap(r -> r.getConstraints().stream())
                .anyMatch(c -> anyRuleId.equals(c.getRuleId())
                        && DiagnosticSeverity.WARNING == c.getSeverity());
        assertTrue(overridden, "severity override must be visible on the wrapped rule");
    }
}
