package com.huawei.theme.analysis.syntax;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DslElementTypesTest {

    @Test
    void dslFile_languageShouldBeDslLanguage() {
        assertSame(DslLanguage.INSTANCE, DslElementTypes.DSL_FILE.getLanguage());
    }

    @Test
    void dslFile_shouldBeRegisteredAsFileElementType() {
        assertNotNull(DslElementTypes.DSL_FILE);
        assertEquals("Dsl", DslElementTypes.DSL_FILE.getLanguage().getID());
    }

    @Test
    void dslFile_typeIdShouldReflectLanguage() {
        String typeId = DslElementTypes.DSL_FILE.toString();
        assertNotNull(typeId);
    }
}
