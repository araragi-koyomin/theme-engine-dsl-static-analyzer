package com.huawei.theme.analysis.core.shared.type;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DslTypeTest {

    @Test
    void dslNumberTypeName() {
        DslType type = new DslNumberType();
        assertEquals("number", type.getName());
    }

    @Test
    void dslStringTypeName() {
        DslType type = new DslStringType();
        assertEquals("string", type.getName());
    }

    @Test
    void dslArrayTypeWithBaseType() {
        DslArrayType type = DslArrayType.builder()
                .baseType("number")
                .build();
        assertEquals("array", type.getName());
        assertEquals("number", type.getBaseType());
    }

    @Test
    void dslUndefinedTypeName() {
        DslType type = new DslUndefinedType();
        assertEquals("undefine", type.getName());
    }

    @Test
    void dslUnknownTypeName() {
        DslType type = new DslUnknownType();
        assertEquals("unknown", type.getName());
    }
}
