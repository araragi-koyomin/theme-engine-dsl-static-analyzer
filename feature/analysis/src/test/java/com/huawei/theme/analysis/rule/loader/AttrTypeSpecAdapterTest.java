package com.huawei.theme.analysis.rule.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.rule.model.AttrTypeSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttrTypeSpecAdapterTest {

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(AttrTypeSpec.class, new AttrTypeSpecAdapter())
            .create();

    @Test
    void deserialize_pureStringValue_shouldCreateAttrTypeSpecWithType() {
        String json = "\"string\"";
        AttrTypeSpec spec = gson.fromJson(json, AttrTypeSpec.class);
        assertEquals("string", spec.getType());
        assertNull(spec.getEnumValues());
        assertNull(spec.getAliases());
    }

    @Test
    void deserialize_numberType_shouldCreateAttrTypeSpecWithType() {
        String json = "\"number\"";
        AttrTypeSpec spec = gson.fromJson(json, AttrTypeSpec.class);
        assertEquals("number", spec.getType());
        assertNull(spec.getEnumValues());
    }

    @Test
    void deserialize_enumObject_shouldCreateAttrTypeSpecWithEnumValues() {
        String json = "{\"enum\":[\"number\",\"string\",\"number[]\",\"string[]\"]}";
        AttrTypeSpec spec = gson.fromJson(json, AttrTypeSpec.class);
        assertEquals("enum", spec.getType());
        assertEquals(4, spec.getEnumValues().size());
        assertEquals("number", spec.getEnumValues().get(0));
        assertEquals("string[]", spec.getEnumValues().get(3));
    }

    @Test
    void deserialize_objectWithTypeField_shouldCreateAttrTypeSpecWithType() {
        String json = "{\"type\":\"expression\"}";
        AttrTypeSpec spec = gson.fromJson(json, AttrTypeSpec.class);
        assertEquals("expression", spec.getType());
        assertNull(spec.getEnumValues());
    }

    @Test
    void deserialize_objectWithAliases_shouldCreateAttrTypeSpecWithAliases() {
        String json = "{\"type\":\"number\",\"aliases\":[\"w\",\"width\"]}";
        AttrTypeSpec spec = gson.fromJson(json, AttrTypeSpec.class);
        assertEquals("number", spec.getType());
        assertEquals(2, spec.getAliases().size());
        assertEquals("w", spec.getAliases().get(0));
    }

    @Test
    void serialize_stringType_shouldOutputPureStringValue() {
        AttrTypeSpec spec = AttrTypeSpec.builder().type("string").build();
        String json = gson.toJson(spec);
        assertEquals("\"string\"", json);
    }

    @Test
    void serialize_enumType_shouldOutputEnumObject() {
        AttrTypeSpec spec = AttrTypeSpec.builder()
                .type("enum")
                .enumValues(java.util.List.of("true", "false"))
                .build();
        String json = gson.toJson(spec);
        assertEquals("{\"enum\":[\"true\",\"false\"]}", json);
    }

    @Test
    void serialize_numberWithAliases_shouldOutputObjectWithAliases() {
        AttrTypeSpec spec = AttrTypeSpec.builder()
                .type("number")
                .aliases(java.util.List.of("w", "width"))
                .build();
        String json = gson.toJson(spec);
        assertEquals("{\"type\":\"number\",\"aliases\":[\"w\",\"width\"]}", json);
    }
}
