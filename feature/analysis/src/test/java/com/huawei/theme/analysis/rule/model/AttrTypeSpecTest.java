package com.huawei.theme.analysis.rule.model;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttrTypeSpecTest {

    @Test
    void builder_shouldCreateAttrTypeSpec_withAllFields() {
        AttrTypeSpec spec = AttrTypeSpec.builder()
                .type("enum")
                .enumValues(List.of("number", "string", "number[]", "string[]"))
                .aliases(List.of("w", "width"))
                .build();
        assertEquals("enum", spec.getType());
        assertEquals(4, spec.getEnumValues().size());
        assertEquals("number", spec.getEnumValues().get(0));
        assertEquals(2, spec.getAliases().size());
    }

    @Test
    void builder_shouldCreateAttrTypeSpec_withNullOptionalFields() {
        AttrTypeSpec spec = AttrTypeSpec.builder()
                .type("string")
                .build();
        assertEquals("string", spec.getType());
        assertNull(spec.getEnumValues());
        assertNull(spec.getAliases());
    }

    @Test
    void data_shouldGenerateEqualsAndHashCode() {
        AttrTypeSpec spec1 = AttrTypeSpec.builder().type("string").build();
        AttrTypeSpec spec2 = AttrTypeSpec.builder().type("string").build();
        assertEquals(spec1, spec2);
    }
}
