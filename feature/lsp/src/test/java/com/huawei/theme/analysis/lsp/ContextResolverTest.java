package com.huawei.theme.analysis.lsp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContextResolverTest {

    @Test
    void elementNamePosition() {
        String text = "<Widget></Widget>";
        // cursor after "<Widg" -> offset 5
        ContextResolver.Context ctx = new ContextResolver(text).resolve(5);
        assertEquals(ContextResolver.PositionType.ELEMENT_NAME, ctx.type);
        assertEquals("Widg", ctx.word);
    }

    @Test
    void elementNameRightAfterOpenAngle() {
        String text = "<Widget/>";
        // cursor right after '<' -> offset 1
        ContextResolver.Context ctx = new ContextResolver(text).resolve(1);
        assertEquals(ContextResolver.PositionType.ELEMENT_NAME, ctx.type);
        assertEquals("", ctx.word);
    }

    @Test
    void attributeNamePosition() {
        String text = "<Widget na";
        // cursor after "na" -> offset 10
        ContextResolver.Context ctx = new ContextResolver(text).resolve(10);
        assertEquals(ContextResolver.PositionType.ATTRIBUTE_NAME, ctx.type);
        assertEquals("Widget", ctx.tagName);
        assertEquals("na", ctx.word);
    }

    @Test
    void attributeNameAfterSpaceFollowingTagName() {
        // "<Var " (trailing space) — typing the space should ready attribute
        // completion even though no attribute char has been typed yet.
        String text = "<Var ";
        ContextResolver.Context ctx = new ContextResolver(text).resolve(5);
        assertEquals(ContextResolver.PositionType.ATTRIBUTE_NAME, ctx.type);
        assertEquals("Var", ctx.tagName);
        assertEquals("", ctx.word);
    }

    @Test
    void attributeValuePosition() {
        // "<Widget attr=\"val" : '"'(13) v(14) a(15) l(16) ; cursor after 'l' -> offset 17
        String text = "<Widget attr=\"val";
        ContextResolver.Context ctx = new ContextResolver(text).resolve(17);
        assertEquals(ContextResolver.PositionType.ATTRIBUTE_VALUE, ctx.type);
        assertEquals("Widget", ctx.tagName);
        assertEquals("attr", ctx.attrName);
        assertEquals("val", ctx.word);
    }

    @Test
    void attributeValueEmptyAfterOpeningQuote() {
        // cursor right after the opening quote of an (unclosed) value
        // "<Widget attr=\"" -> offset 14
        String text = "<Widget attr=\"";
        ContextResolver.Context ctx = new ContextResolver(text).resolve(14);
        assertEquals(ContextResolver.PositionType.ATTRIBUTE_VALUE, ctx.type);
        assertEquals("Widget", ctx.tagName);
        assertEquals("attr", ctx.attrName);
        assertEquals("", ctx.word);
    }

    @Test
    void attributeValueBetweenBalancedQuotes() {
        // cursor inside a balanced value of a closed tag -> offset 14
        // "<Widget attr=\"center\"/>" : '='(12) '"'(13) 'c'(14)
        String text = "<Widget attr=\"center\"/>";
        ContextResolver.Context ctx = new ContextResolver(text).resolve(14);
        assertEquals(ContextResolver.PositionType.ATTRIBUTE_VALUE, ctx.type);
        assertEquals("attr", ctx.attrName);
    }

    @Test
    void closingTagIsOther() {
        String text = "</Widget>";
        // cursor inside closing tag name -> offset 5
        ContextResolver.Context ctx = new ContextResolver(text).resolve(5);
        assertEquals(ContextResolver.PositionType.OTHER, ctx.type);
    }

    @Test
    void outsideAnyTagIsOther() {
        String text = "<Widget>some text";
        // cursor in text content -> offset 12
        ContextResolver.Context ctx = new ContextResolver(text).resolve(12);
        assertEquals(ContextResolver.PositionType.OTHER, ctx.type);
        assertNull(ctx.tagName);
    }
}
