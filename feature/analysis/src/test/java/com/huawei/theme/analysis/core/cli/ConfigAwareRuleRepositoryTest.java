package com.huawei.theme.analysis.core.cli;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

class ConfigAwareRuleRepositoryTest {

    private RuleRepository delegate;
    private Map<String, DslElementRule> elementRules;

    @BeforeEach
    void setUp() {
        elementRules = new HashMap<>();

        elementRules.put("Lockscreen", DslElementRule.builder()
                .elementName("Lockscreen")
                .category("root")
                .allowedParents(Collections.emptyList())
                .constraints(List.of(
                        RuleConstraint.builder().ruleId("SYN-001").condition("c1").message("m1").severity(DiagnosticSeverity.ERROR).build(),
                        RuleConstraint.builder().ruleId("SYN-002").condition("c2").message("m2").severity(DiagnosticSeverity.WARNING).build(),
                        RuleConstraint.builder().ruleId("SEM-REF-001").condition("c3").message("m3").severity(DiagnosticSeverity.INFO).build()
                ))
                .build());

        elementRules.put("Var", DslElementRule.builder()
                .elementName("Var")
                .category("variable")
                .allowedParents(List.of("Lockscreen"))
                .constraints(List.of(
                        RuleConstraint.builder().ruleId("SYN-003").condition("c4").message("m4").severity(DiagnosticSeverity.ERROR).build(),
                        RuleConstraint.builder().ruleId("SEM-CMD-001").condition("c5").message("m5").severity(DiagnosticSeverity.WARNING).build()
                ))
                .build());

        delegate = new MockRuleRepository(elementRules);
    }

    @Test
    void getRootElementNamesReturnsConfigOverride() {
        InspectionConfig config = InspectionConfig.builder()
                .rootElementNames(List.of("Lockscreen", "Wallpaper"))
                .build();
        ConfigAwareRuleRepository repo = new ConfigAwareRuleRepository(delegate, config);

        List<String> roots = repo.getRootElementNames();
        assertEquals(2, roots.size());
        assertTrue(roots.contains("Lockscreen"));
        assertTrue(roots.contains("Wallpaper"));
    }

    @Test
    void getRootElementNamesReturnsDefaultWhenConfigNull() {
        InspectionConfig config = InspectionConfig.builder().build();
        ConfigAwareRuleRepository repo = new ConfigAwareRuleRepository(delegate, config);

        List<String> roots = repo.getRootElementNames();
        assertEquals(1, roots.size());
        assertTrue(roots.contains("Lockscreen"));
    }

    @Test
    void getConstraintsFiltersByEnabledRuleIds() {
        InspectionConfig config = InspectionConfig.builder()
                .enabledRuleIds(List.of("SYN-001", "SEM-REF-001"))
                .build();
        ConfigAwareRuleRepository repo = new ConfigAwareRuleRepository(delegate, config);

        List<RuleConstraint> constraints = repo.getConstraints("Lockscreen");
        assertEquals(2, constraints.size());
        assertTrue(constraints.stream().anyMatch(c -> "SYN-001".equals(c.getRuleId())));
        assertTrue(constraints.stream().anyMatch(c -> "SEM-REF-001".equals(c.getRuleId())));
    }

    @Test
    void getConstraintsFiltersByDisabledRuleIds() {
        InspectionConfig config = InspectionConfig.builder()
                .disabledRuleIds(List.of("SYN-002"))
                .build();
        ConfigAwareRuleRepository repo = new ConfigAwareRuleRepository(delegate, config);

        List<RuleConstraint> constraints = repo.getConstraints("Lockscreen");
        assertEquals(2, constraints.size());
        assertTrue(constraints.stream().anyMatch(c -> "SYN-001".equals(c.getRuleId())));
        assertTrue(constraints.stream().anyMatch(c -> "SEM-REF-001".equals(c.getRuleId())));
    }

    @Test
    void getElementRuleReturnsFilteredConstraints() {
        InspectionConfig config = InspectionConfig.builder()
                .enabledRuleIds(List.of("SYN-001"))
                .build();
        ConfigAwareRuleRepository repo = new ConfigAwareRuleRepository(delegate, config);

        Optional<DslElementRule> rule = repo.getElementRule("Lockscreen");
        assertTrue(rule.isPresent());
        assertEquals(1, rule.get().getConstraints().size());
        assertEquals("SYN-001", rule.get().getConstraints().get(0).getRuleId());
    }

    @Test
    void getAllElementRulesReturnsFilteredConstraints() {
        InspectionConfig config = InspectionConfig.builder()
                .disabledRuleIds(List.of("SEM-REF-001", "SEM-CMD-001"))
                .build();
        ConfigAwareRuleRepository repo = new ConfigAwareRuleRepository(delegate, config);

        List<DslElementRule> rules = repo.getAllElementRules();
        assertEquals(2, rules.size());
        for (DslElementRule rule : rules) {
            for (RuleConstraint c : rule.getConstraints()) {
                assertTrue(!"SEM-REF-001".equals(c.getRuleId()) && !"SEM-CMD-001".equals(c.getRuleId()));
            }
        }
    }

    @Test
    void otherMethodsDelegateWithoutFiltering() {
        InspectionConfig config = InspectionConfig.builder()
                .enabledRuleIds(List.of("SYN-001"))
                .build();
        ConfigAwareRuleRepository repo = new ConfigAwareRuleRepository(delegate, config);

        List<String> names = repo.getAllElementNames();
        assertEquals(2, names.size());

        Optional<DslElementRule> originalRule = delegate.getElementRule("Lockscreen");
        assertTrue(originalRule.isPresent());
        assertEquals(3, originalRule.get().getConstraints().size());
        assertEquals(3, delegate.getConstraints("Lockscreen").size());
    }

    private static class MockRuleRepository implements RuleRepository {
        private final Map<String, DslElementRule> rules;

        MockRuleRepository(Map<String, DslElementRule> rules) {
            this.rules = rules;
        }

        @Override
        public Optional<DslElementRule> getElementRule(String elementName) {
            return Optional.ofNullable(rules.get(elementName));
        }

        @Override
        public List<DslElementRule> getAllElementRules() {
            return List.copyOf(rules.values());
        }

        @Override
        public List<String> getAllElementNames() {
            return List.copyOf(rules.keySet());
        }

        @Override
        public List<String> getRootElementNames() {
            return rules.values().stream()
                    .filter(r -> r.getAllowedParents() == null || r.getAllowedParents().isEmpty())
                    .map(DslElementRule::getElementName)
                    .toList();
        }

        @Override
        public List<RuleConstraint> getConstraints(String elementName) {
            return getElementRule(elementName)
                    .map(DslElementRule::getConstraints)
                    .orElse(Collections.emptyList());
        }

        @Override
        public Optional<com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec> getAttrTypeSpec(String elementName, String attrName) {
            return Optional.empty();
        }

        @Override
        public Optional<String> resolveAttrAlias(String elementName, String attrName) {
            return Optional.empty();
        }

        @Override
        public Set<String> getCanonicalAttrNames(String elementName) {
            return Collections.emptySet();
        }

        @Override
        public List<String> getAllowedParents(String elementName) {
            return getElementRule(elementName)
                    .map(DslElementRule::getAllowedParents)
                    .orElse(Collections.emptyList());
        }

        @Override
        public List<String> getAllowedChildren(String elementName) {
            return Collections.emptyList();
        }

        @Override
        public Optional<com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar> getGlobalVar(String varName) {
            return Optional.empty();
        }

        @Override
        public List<com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar> getAllGlobalVars() {
            return Collections.emptyList();
        }

        @Override
        public Optional<com.huawei.theme.analysis.core.rulelibrary.model.RuleSource> getRuleSource(String ruleId) {
            return Optional.empty();
        }
    }
}
