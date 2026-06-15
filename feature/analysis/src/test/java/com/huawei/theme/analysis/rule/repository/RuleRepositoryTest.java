package com.huawei.theme.analysis.rule.repository;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.rule.loader.JsonRuleLoader;
import com.huawei.theme.analysis.rule.model.AttrTypeSpec;
import com.huawei.theme.analysis.rule.model.DslElementRule;
import com.huawei.theme.analysis.rule.model.RuleSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleRepositoryTest {

    private RuleRepository repository;

    @BeforeEach
    void setUp() {
        JsonRuleLoader loader = new JsonRuleLoader();
        repository = new RuleRepositoryImpl(loader, "rules/test_rules.json");
    }

    @Test
    void getElementRule_knownElement_shouldReturnVarRule() {
        Optional<DslElementRule> rule = repository.getElementRule("Var");
        assertTrue(rule.isPresent());
        assertEquals("Var", rule.get().getElementName());
    }

    @Test
    void getElementRule_unknownElement_shouldReturnEmpty() {
        Optional<DslElementRule> rule = repository.getElementRule("UnknownElement");
        assertFalse(rule.isPresent());
    }

    @Test
    void getAllElementRules_shouldReturnAllLoadedRules() {
        List<DslElementRule> rules = repository.getAllElementRules();
        assertNotNull(rules);
        assertEquals(5, rules.size());
    }

    @Test
    void getAllElementNames_shouldReturnAllElementNames() {
        List<String> names = repository.getAllElementNames();
        assertNotNull(names);
        assertEquals(5, names.size());
        assertTrue(names.contains("Var"));
        assertTrue(names.contains("Lockscreen"));
    }

    @Test
    void getRootElementNames_shouldReturnRootElements() {
        List<String> roots = repository.getRootElementNames();
        assertNotNull(roots);
        assertEquals(4, roots.size());
        assertTrue(roots.contains("Lockscreen"));
        assertTrue(roots.contains("Wallpaper"));
        assertTrue(roots.contains("Widget"));
        assertTrue(roots.contains("ChargingSkin"));
    }

    @Test
    void getAttrTypeSpec_stringAttr_shouldReturnStringType() {
        Optional<AttrTypeSpec> spec = repository.getAttrTypeSpec("Var", "name");
        assertTrue(spec.isPresent());
        assertEquals("string", spec.get().getType());
        assertTrue(spec.get().getEnumValues() == null);
    }

    @Test
    void getAttrTypeSpec_enumAttr_shouldReturnEnumWithValues() {
        Optional<AttrTypeSpec> spec = repository.getAttrTypeSpec("Var", "type");
        assertTrue(spec.isPresent());
        assertEquals("enum", spec.get().getType());
        assertEquals(4, spec.get().getEnumValues().size());
        assertEquals("number", spec.get().getEnumValues().get(0));
        assertEquals("string[]", spec.get().getEnumValues().get(3));
    }

    @Test
    void getAttrTypeSpec_numberAttr_shouldReturnNumberType() {
        Optional<AttrTypeSpec> spec = repository.getAttrTypeSpec("Var", "threshold");
        assertTrue(spec.isPresent());
        assertEquals("number", spec.get().getType());
        assertTrue(spec.get().getEnumValues() == null);
    }

    @Test
    void getAttrTypeSpec_unknownAttr_shouldReturnEmpty() {
        Optional<AttrTypeSpec> spec = repository.getAttrTypeSpec("Var", "nonexistent");
        assertFalse(spec.isPresent());
    }

    @Test
    void getAttrTypeSpec_unknownElement_shouldReturnEmpty() {
        Optional<AttrTypeSpec> spec = repository.getAttrTypeSpec("UnknownElement", "any");
        assertFalse(spec.isPresent());
    }

    @Test
    void getAllowedParents_knownElement_shouldReturnParentList() {
        List<String> parents = repository.getAllowedParents("Var");
        assertNotNull(parents);
        assertEquals(5, parents.size());
        assertTrue(parents.contains("Lockscreen"));
        assertTrue(parents.contains("Group"));
    }

    @Test
    void getAllowedParents_unknownElement_shouldReturnEmptyList() {
        List<String> parents = repository.getAllowedParents("UnknownElement");
        assertNotNull(parents);
        assertTrue(parents.isEmpty());
    }

    @Test
    void getAllowedChildren_knownElement_shouldReturnChildrenList() {
        List<String> children = repository.getAllowedChildren("Var");
        assertNotNull(children);
        assertEquals(2, children.size());
        assertTrue(children.contains("Trigger"));
        assertTrue(children.contains("VariableAnimation"));
    }

    @Test
    void getAllowedChildren_unknownElement_shouldReturnEmptyList() {
        List<String> children = repository.getAllowedChildren("UnknownElement");
        assertNotNull(children);
        assertTrue(children.isEmpty());
    }

    @Test
    void getRuleSource_knownId_shouldReturnRuleSource() {
        Optional<RuleSource> source = repository.getRuleSource("SYN-001");
        assertTrue(source.isPresent());
        assertEquals("SYN-001", source.get().getRuleId());
        assertEquals("SYN", source.get().getCategory());
        assertEquals("XML标签未闭合", source.get().getDescription());
    }

    @Test
    void getRuleSource_unknownId_shouldReturnEmpty() {
        Optional<RuleSource> source = repository.getRuleSource("UNKNOWN-999");
        assertFalse(source.isPresent());
    }

    @Test
    void getAllElementRules_containsLockscreenWithEmptyParents() {
        Optional<DslElementRule> lockscreen = repository.getElementRule("Lockscreen");
        assertTrue(lockscreen.isPresent());
        assertTrue(lockscreen.get().getAllowedParents().isEmpty());
    }

    @Test
    void getRootElementNames_doesNotContainVar() {
        List<String> roots = repository.getRootElementNames();
        assertFalse(roots.contains("Var"));
    }

    @Test
    void dslElementRule_fieldCompleteness_shouldVerifyAllVarFields() {
        Optional<DslElementRule> varOpt = repository.getElementRule("Var");
        assertTrue(varOpt.isPresent());
        DslElementRule varRule = varOpt.get();
        assertEquals("Var", varRule.getElementName());
        assertEquals(List.of("name"), varRule.getRequiredAttrs());
        assertEquals(8, varRule.getOptionalAttrs().size());
        assertEquals(9, varRule.getAttrTypes().size());
        assertEquals(5, varRule.getAllowedParents().size());
        assertEquals(List.of("Trigger", "VariableAnimation"), varRule.getAllowedChildren());
        assertTrue(varRule.getInherits() == null);
    }

    @Test
    void attrTypeSpec_enumValues_shouldVerifyEnumContent() {
        Optional<AttrTypeSpec> typeSpec = repository.getAttrTypeSpec("Var", "type");
        assertTrue(typeSpec.isPresent());
        List<String> values = typeSpec.get().getEnumValues();
        assertNotNull(values);
        assertTrue(values.contains("number"));
        assertTrue(values.contains("string"));
        assertTrue(values.contains("number[]"));
        assertTrue(values.contains("string[]"));
    }
}
