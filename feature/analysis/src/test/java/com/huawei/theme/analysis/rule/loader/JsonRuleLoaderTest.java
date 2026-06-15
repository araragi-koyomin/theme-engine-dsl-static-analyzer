package com.huawei.theme.analysis.rule.loader;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.rule.model.AttrTypeSpec;
import com.huawei.theme.analysis.rule.model.DslElementRule;
import com.huawei.theme.analysis.rule.model.RuleSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonRuleLoaderTest {

    @Test
    void loadFromResource_validFile_shouldReturnElementRules() {
        JsonRuleLoader loader = new JsonRuleLoader();
        List<DslElementRule> rules = loader.loadElementRules("rules/test_rules.json");
        assertNotNull(rules);
        assertEquals(5, rules.size());
        assertEquals("Var", rules.get(0).getElementName());
    }

    @Test
    void loadFromResource_validFile_shouldReturnRuleSources() {
        JsonRuleLoader loader = new JsonRuleLoader();
        List<RuleSource> sources = loader.loadRuleSources("rules/test_rules.json");
        assertNotNull(sources);
        assertEquals(2, sources.size());
        assertEquals("SYN-001", sources.get(0).getRuleId());
    }

    @Test
    void loadFromResource_validFile_shouldParseAttrTypeSpecs() {
        JsonRuleLoader loader = new JsonRuleLoader();
        List<DslElementRule> rules = loader.loadElementRules("rules/test_rules.json");
        DslElementRule varRule = rules.get(0);
        Map<String, com.huawei.theme.analysis.rule.model.AttrTypeSpec> attrTypes = varRule.getAttrTypes();

        assertEquals("string", attrTypes.get("name").getType());
        assertEquals("enum", attrTypes.get("type").getType());
        assertEquals(4, attrTypes.get("type").getEnumValues().size());
        assertEquals("number", attrTypes.get("threshold").getType());
        assertEquals("enum", attrTypes.get("persist").getType());
        assertEquals(2, attrTypes.get("persist").getEnumValues().size());
    }

    @Test
    void loadFromResource_validFile_shouldParseVarFieldsCorrectly() {
        JsonRuleLoader loader = new JsonRuleLoader();
        List<DslElementRule> rules = loader.loadElementRules("rules/test_rules.json");
        DslElementRule varRule = rules.get(0);

        assertEquals("Var", varRule.getElementName());
        assertEquals(List.of("name"), varRule.getRequiredAttrs());
        assertEquals(8, varRule.getOptionalAttrs().size());
        assertEquals(5, varRule.getAllowedParents().size());
        assertEquals(List.of("Trigger", "VariableAnimation"), varRule.getAllowedChildren());
        assertTrue(varRule.getInherits() == null);
    }

    @Test
    void loadFromResource_validFile_shouldIdentifyRootElementEmptyParents() {
        JsonRuleLoader loader = new JsonRuleLoader();
        List<DslElementRule> rules = loader.loadElementRules("rules/test_rules.json");
        DslElementRule lockscreen = rules.stream()
                .filter(r -> "Lockscreen".equals(r.getElementName()))
                .findFirst().orElse(null);
        assertNotNull(lockscreen);
        assertTrue(lockscreen.getAllowedParents().isEmpty());
    }

    @Test
    void loadFromResource_missingFile_shouldThrowRuntimeException() {
        JsonRuleLoader loader = new JsonRuleLoader();
        assertThrows(RuntimeException.class, () -> {
            loader.loadElementRules("rules/nonexistent_file.json");
        });
    }
}
