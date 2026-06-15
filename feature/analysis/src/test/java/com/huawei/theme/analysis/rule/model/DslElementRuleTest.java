package com.huawei.theme.analysis.rule.model;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DslElementRuleTest {

    @Test
    void builder_shouldCreateDslElementRule_withAllFields() {
        AttrTypeSpec nameSpec = AttrTypeSpec.builder().type("string").build();
        AttrTypeSpec typeSpec = AttrTypeSpec.builder()
                .type("enum")
                .enumValues(List.of("number", "string", "number[]", "string[]"))
                .build();

        DslElementRule rule = DslElementRule.builder()
                .elementName("Var")
                .requiredAttrs(List.of("name"))
                .optionalAttrs(List.of("expression", "type", "threshold", "persist", "index", "values", "size", "const"))
                .attrTypes(Map.of("name", nameSpec, "type", typeSpec))
                .allowedParents(List.of("Lockscreen", "Wallpaper", "Widget", "ChargingSkin", "Group"))
                .allowedChildren(List.of("Trigger", "VariableAnimation"))
                .inherits(null)
                .build();

        assertEquals("Var", rule.getElementName());
        assertEquals(1, rule.getRequiredAttrs().size());
        assertEquals("name", rule.getRequiredAttrs().get(0));
        assertEquals(8, rule.getOptionalAttrs().size());
        assertEquals(2, rule.getAttrTypes().size());
        assertEquals("string", rule.getAttrTypes().get("name").getType());
        assertEquals(5, rule.getAllowedParents().size());
        assertEquals(2, rule.getAllowedChildren().size());
        assertNull(rule.getInherits());
    }

    @Test
    void builder_shouldCreateDslElementRule_withNullInherits() {
        DslElementRule rule = DslElementRule.builder()
                .elementName("Var")
                .requiredAttrs(List.of())
                .optionalAttrs(List.of())
                .attrTypes(Map.of())
                .allowedParents(List.of())
                .allowedChildren(List.of())
                .inherits(null)
                .build();
        assertNull(rule.getInherits());
    }
}
