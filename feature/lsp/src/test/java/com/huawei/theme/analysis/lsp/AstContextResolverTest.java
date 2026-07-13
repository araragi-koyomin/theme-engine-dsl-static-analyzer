package com.huawei.theme.analysis.lsp;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AstContextResolverTest {

    private static DslFileNode parse(String text) {
        return new AstBuilder(null).getDslAst("test.xml", text);
    }

    private static ContextResolver.Context resolve(String text, int offset) {
        DslFileNode ast = parse(text);
        return new AstContextResolver(text).resolve(offset, ast);
    }

    @Test
    void elementNamePosition() {
        String text = "<Widget></Widget>";
        // cursor after "<Widg" -> offset 5
        ContextResolver.Context ctx = resolve(text, 5);
        assertEquals(ContextResolver.PositionType.ELEMENT_NAME, ctx.type);
        assertEquals("Widget", ctx.tagName);
        assertEquals("Widg", ctx.word);
        assertNull(ctx.attrName);
    }

    @Test
    void elementNameRightAfterOpenAngle() {
        String text = "<Widget/>";
        // cursor right after '<' -> offset 1
        ContextResolver.Context ctx = resolve(text, 1);
        assertEquals(ContextResolver.PositionType.ELEMENT_NAME, ctx.type);
        assertEquals("Widget", ctx.tagName);
        assertEquals("", ctx.word);
    }

    @Test
    void elementNameAtEndOfName() {
        String text = "<Widget/>";
        // cursor right after "Widget" (at '/') -> offset 7 -> OTHER
        ContextResolver.Context ctx = resolve(text, 7);
        assertEquals(ContextResolver.PositionType.OTHER, ctx.type);
        assertEquals("Widget", ctx.tagName);
    }

    @Test
    void attributeNamePosition() {
        // "<Widget name=\"x\"/>"
        // '<'(0)W(1)i(2)d(3)g(4)e(5)t(6)' '(7)n(8)a(9)m(10)e(11)...
        // cursor after "nam" -> offset 11
        String text = "<Widget name=\"x\"/>";
        ContextResolver.Context ctx = resolve(text, 11);
        assertEquals(ContextResolver.PositionType.ATTRIBUTE_NAME, ctx.type);
        assertEquals("Widget", ctx.tagName);
        assertEquals("nam", ctx.word);
        assertEquals("name", ctx.attrName);
    }

    @Test
    void attributeValueIsOther() {
        // "<Widget attr=\"val\"/>"
        // cursor inside the quoted value -> offset 16 (after 'l')
        String text = "<Widget attr=\"val\"/>";
        ContextResolver.Context ctx = resolve(text, 16);
        assertEquals(ContextResolver.PositionType.OTHER, ctx.type);
        assertEquals("Widget", ctx.tagName);
        assertEquals("attr", ctx.attrName);
    }

    @Test
    void attributeEqualsSignIsOther() {
        // "<Widget attr=\"val\"/>"
        // '<'(0)...t(7)' '(8)a(9)t(10)t(11)r(12)'='(13)...
        // cursor on '=' -> offset 13
        String text = "<Widget attr=\"val\"/>";
        ContextResolver.Context ctx = resolve(text, 13);
        assertEquals(ContextResolver.PositionType.OTHER, ctx.type);
        assertEquals("Widget", ctx.tagName);
        assertEquals("attr", ctx.attrName);
    }

    @Test
    void closingTagFallsBack() {
        String text = "</Widget>";
        // cursor inside closing tag name -> offset 5; no start tag contains it
        ContextResolver.Context ctx = resolve(text, 5);
        assertNull(ctx);
    }

    @Test
    void textContentFallsBack() {
        String text = "<Widget>some text";
        // cursor in text content -> offset 12; outside any start tag range
        ContextResolver.Context ctx = resolve(text, 12);
        assertNull(ctx);
    }

    @Test
    void nestedElementInnerTagName() {
        String text = "<Outer><Inner/></Outer>";
        // cursor in "Inner" tag name -> offset 9 (after 'I')
        ContextResolver.Context ctx = resolve(text, 9);
        assertEquals(ContextResolver.PositionType.ELEMENT_NAME, ctx.type);
        assertEquals("Inner", ctx.tagName);
        assertEquals("I", ctx.word);
    }

    @Test
    void nestedElementInnerAttribute() {
        // "<Outer><Inner attr=\"v\"/>"
        // '<'(7)I(8)n(9)n(10)e(11)r(12)' '(13)a(14)t(15)t(16)r(17)...
        // cursor after "att" -> offset 17
        String text = "<Outer><Inner attr=\"v\"/></Outer>";
        ContextResolver.Context ctx = resolve(text, 17);
        assertEquals(ContextResolver.PositionType.ATTRIBUTE_NAME, ctx.type);
        assertEquals("Inner", ctx.tagName);
        assertEquals("att", ctx.word);
        assertEquals("attr", ctx.attrName);
    }

    @Test
    void nestedElementOuterAttribute() {
        String text = "<Outer o=\"1\"><Inner/></Outer>";
        // cursor in outer "o" value -> offset 10 (inside "1")
        ContextResolver.Context ctx = resolve(text, 10);
        assertEquals(ContextResolver.PositionType.OTHER, ctx.type);
        assertEquals("Outer", ctx.tagName);
        assertEquals("o", ctx.attrName);
    }

    @Test
    void selfClosingTagWithAttributes() {
        String text = "<Widget name=\"x\" count=\"2\"/>";
        // cursor in "count" name -> offset 20 (after 'cou')
        ContextResolver.Context ctx = resolve(text, 20);
        assertEquals(ContextResolver.PositionType.ATTRIBUTE_NAME, ctx.type);
        assertEquals("Widget", ctx.tagName);
        assertEquals("cou", ctx.word);
        assertEquals("count", ctx.attrName);
    }

    @Test
    void nullAstReturnsNull() {
        ContextResolver.Context ctx = new AstContextResolver("text").resolve(0, null);
        assertNull(ctx);
    }

    @Test
    void malformedXmlFallsBack() {
        // Unclosed tag: AstBuilder produces an error node with a zero-width
        // range, so no start tag contains the cursor -> null (fallback signal).
        String text = "<Widget";
        ContextResolver.Context ctx = resolve(text, 4);
        assertNull(ctx);
    }
}
