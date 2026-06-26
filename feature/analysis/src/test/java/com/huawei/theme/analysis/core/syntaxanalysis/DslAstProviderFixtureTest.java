package com.huawei.theme.analysis.core.syntaxanalysis;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslAstProviderFixtureTest {

    private final DslAstProvider provider = new AstBuilder();

    private String loadResource(String name) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/dsl/" + name)) {
            assertNotNull(is, "resource not found: /dsl/" + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private DslAttributeNode attr(DslElementNode node, String name) {
        return node.getAttributes().stream()
                .filter(a -> name.equals(a.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("attr not found: " + name));
    }

    private void assertLiteralValue(DslAttributeNode attr, String expectedRaw) {
        DslAttributeValueNode value = attr.getValue();
        assertEquals(expectedRaw, value.getRawValue());
        assertTrue(value.isLiteral());
        assertEquals(Optional.empty(), value.getExpression());
    }

    @Test
    void parsesValidLockscreen() throws Exception {
        DslFileNode ast = provider.getDslAst("valid_lockscreen.xml", loadResource("valid_lockscreen.xml"));

        assertNotNull(ast.getXmlDeclaration());
        assertTrue(ast.getXmlDeclaration().startsWith("<?xml"));

        DslElementNode root = ast.getRootElement();
        assertNotNull(root);
        assertEquals("Lockscreen", root.getTagName());
        assertEquals(2, root.getAttributes().size());
        assertLiteralValue(attr(root, "frameRate"), "60");
        assertLiteralValue(attr(root, "screenWidth"), "1080");

        List<DslElementNode> children = root.getChildElements();
        assertEquals(2, children.size());

        DslElementNode var = children.get(0);
        assertEquals("Var", var.getTagName());
        assertTrue(var.isSelfClosing());
        assertEquals(3, var.getAttributes().size());
        assertLiteralValue(attr(var, "name"), "testVar");
        assertLiteralValue(attr(var, "expression"), "1");
        assertLiteralValue(attr(var, "type"), "number");

        DslElementNode group = children.get(1);
        assertEquals("Group", group.getTagName());
        assertEquals(5, group.getAttributes().size());
        assertLiteralValue(attr(group, "w"), "1080");
        assertLiteralValue(attr(group, "h"), "1920");
        assertEquals(1, group.getChildElements().size());

        DslElementNode text = group.getChildElements().get(0);
        assertEquals("Text", text.getTagName());
        assertTrue(text.isSelfClosing());
        assertEquals(5, text.getAttributes().size());
        assertLiteralValue(attr(text, "color"), "#FFFFFF");
        assertLiteralValue(attr(text, "size"), "24");
    }

    @Test
    void parsesValidWidget() throws Exception {
        DslFileNode ast = provider.getDslAst("valid_widget.xml", loadResource("valid_widget.xml"));

        DslElementNode root = ast.getRootElement();
        assertEquals("Widget", root.getTagName());
        assertEquals(2, root.getAttributes().size());
        assertLiteralValue(attr(root, "screenWidth"), "1080");
        assertLiteralValue(attr(root, "screenHeight"), "530");

        List<DslElementNode> children = root.getChildElements();
        assertEquals(2, children.size());

        DslElementNode var = children.get(0);
        assertEquals("Var", var.getTagName());
        assertLiteralValue(attr(var, "expression"), "#battery_level");

        DslElementNode group = children.get(1);
        assertEquals("Group", group.getTagName());
        assertEquals(1, group.getChildElements().size());
        assertEquals("Text", group.getChildElements().get(0).getTagName());
    }

    @Test
    void parsesRegularConfigAsXml() throws Exception {
        DslFileNode ast = provider.getDslAst("regular_config.xml", loadResource("regular_config.xml"));

        DslElementNode root = ast.getRootElement();
        assertEquals("configuration", root.getTagName());
        assertTrue(root.getAttributes().isEmpty());

        List<DslElementNode> properties = root.getChildElements();
        assertEquals(2, properties.size());
        assertEquals("property", properties.get(0).getTagName());
        assertEquals("property", properties.get(1).getTagName());
        assertLiteralValue(attr(properties.get(0), "name"), "server.host");
        assertLiteralValue(attr(properties.get(0), "value"), "localhost");
        assertLiteralValue(attr(properties.get(1), "name"), "server.port");
        assertLiteralValue(attr(properties.get(1), "value"), "8080");
    }

    @Test
    void capturesLineNumbersFromLockscreenFixture() throws Exception {
        DslFileNode ast = provider.getDslAst("valid_lockscreen.xml", loadResource("valid_lockscreen.xml"));

        DslElementNode root = ast.getRootElement();
        assertEquals(2, root.getLine());

        DslElementNode var = root.getChildElements().get(0);
        assertEquals(3, var.getLine());

        DslElementNode group = root.getChildElements().get(1);
        assertEquals(4, group.getLine());

        DslElementNode text = group.getChildElements().get(0);
        assertEquals(5, text.getLine());
    }

    @Test
    void handlesErrorQuotes() throws Exception {
        DslFileNode ast = provider.getDslAst("error_quotes.xml", loadResource("error_quotes.xml"));

        DslElementNode root = ast.getRootElement();
        assertTrue(root.isHasError());
        assertNotNull(root.getErrorMessage());
        assertEquals(2, root.getLine());
    }

    @Test
    void handlesErrorUnclosed() throws Exception {
        DslFileNode ast = provider.getDslAst("error_unclosed.xml", loadResource("error_unclosed.xml"));

        DslElementNode root = ast.getRootElement();
        assertTrue(root.isHasError());
        assertNotNull(root.getErrorMessage());
        assertTrue(root.getLine() > 0);
    }

    @Test
    void allFixturesLoadWithoutException() throws Exception {
        String[] names = {
                "valid_lockscreen.xml", "valid_widget.xml", "regular_config.xml",
                "error_quotes.xml", "error_unclosed.xml"
        };
        for (String name : names) {
            String content = loadResource(name);
            DslFileNode ast = provider.getDslAst(name, content);
            assertNotNull(ast);
        }
    }
}
