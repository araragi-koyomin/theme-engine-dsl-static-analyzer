package com.huawei.theme.analysis.syntax;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DslLanguageTest {

    @Test
    void instance_shouldBeSingleton() {
        assertSame(DslLanguage.INSTANCE, DslLanguage.INSTANCE);
    }

    @Test
    void instance_idShouldBeExactlyDsl() {
        assertEquals("Dsl", DslLanguage.INSTANCE.getID());
    }

    @Test
    void instance_shouldBeDistinctFromXmlLanguage() {
        assertNotEquals(XMLLanguage.INSTANCE.getID(), DslLanguage.INSTANCE.getID());
        assertNotEquals(XMLLanguage.INSTANCE, DslLanguage.INSTANCE);
    }

    @Test
    void instance_shouldBeRegisteredInLanguageRegistry() {
        Language found = Language.findLanguageByID("Dsl");
        assertNotNull(found);
        assertSame(DslLanguage.INSTANCE, found);
    }

    @Test
    void instance_constructorShouldBePrivate() {
        java.lang.reflect.Constructor<?>[] constructors = DslLanguage.class.getDeclaredConstructors();
        for (java.lang.reflect.Constructor<?> c : constructors) {
            assertEquals(0, c.getModifiers() & java.lang.reflect.Modifier.PUBLIC,
                    "DslLanguage should not have a public constructor");
        }
    }
}
