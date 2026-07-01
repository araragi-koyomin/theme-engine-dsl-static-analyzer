package com.huawei.theme.analysis.core.syntaxanalysis;

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

class DslAstProviderTest {

    private final DslAstProvider provider = new AstBuilder();

    @Test
    void buildsSimpleTree() {
        DslFileNode ast = provider.getDslAst("test.xml", "<Lockscreen><Image x=\"0\"/></Lockscreen>");

        assertNotNull(ast.getRootElement());
        DslElementNode root = ast.getRootElement();
        assertEquals("Lockscreen", root.getTagName());
        assertEquals(1, root.getChildElements().size());

        DslElementNode image = root.getChildElements().get(0);
        assertEquals("Image", image.getTagName());
        assertTrue(image.isSelfClosing());
        assertEquals(1, image.getAttributes().size());

        DslAttributeNode x = image.getAttributes().get(0);
        assertEquals("x", x.getName());
        DslAttributeValueNode value = x.getValue();
        assertEquals("0", value.getRawValue());
        assertTrue(value.isLiteral());
        assertEquals(Optional.empty(), value.getExpression());
    }

    @Test
    void buildsNestedTree() {
        DslFileNode ast = provider.getDslAst("test.xml",
                "<Lockscreen><Group><Image/></Group></Lockscreen>");

        DslElementNode root = ast.getRootElement();
        assertEquals("Lockscreen", root.getTagName());
        assertEquals(1, root.getChildElements().size());

        DslElementNode group = root.getChildElements().get(0);
        assertEquals("Group", group.getTagName());
        assertEquals(1, group.getChildElements().size());

        DslElementNode image = group.getChildElements().get(0);
        assertEquals("Image", image.getTagName());
        assertTrue(image.getChildElements().isEmpty());
    }

    @Test
    void buildsMultipleAttributes() {
        DslFileNode ast = provider.getDslAst("test.xml",
                "<Image x=\"0\" y=\"1\" src=\"a.png\"/>");

        DslElementNode image = ast.getRootElement();
        assertEquals("Image", image.getTagName());
        List<DslAttributeNode> attrs = image.getAttributes();
        assertEquals(3, attrs.size());

        assertEquals("x", attrs.get(0).getName());
        assertEquals("0", attrs.get(0).getValue().getRawValue());
        assertEquals("y", attrs.get(1).getName());
        assertEquals("1", attrs.get(1).getValue().getRawValue());
        assertEquals("src", attrs.get(2).getName());
        assertEquals("a.png", attrs.get(2).getValue().getRawValue());

        for (DslAttributeNode attr : attrs) {
            assertTrue(attr.getValue().isLiteral());
            assertEquals(Optional.empty(), attr.getValue().getExpression());
        }
    }

    @Test
    void capturesLineNumber() {
        DslFileNode ast = provider.getDslAst("test.xml",
                "<?xml version=\"1.0\"?>\n<Lockscreen>\n  <Image/>\n</Lockscreen>");

        DslElementNode root = ast.getRootElement();
        assertTrue(root.getLine() >= 2);
        DslElementNode image = root.getChildElements().get(0);
        assertTrue(image.getLine() >= 3);
        assertTrue(image.getColumn() >= 0);
    }

    @Test
    void parsesXmlDeclaration() {
        DslFileNode ast = provider.getDslAst("test.xml",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?><Lockscreen/>");

        assertNotNull(ast.getXmlDeclaration());
        assertTrue(ast.getXmlDeclaration().startsWith("<?xml"));
    }

    @Test
    void handlesMalformedXml() {
        DslFileNode ast = provider.getDslAst("test.xml",
                "<Lockscreen><Image></Lockscreen>");

        DslElementNode root = ast.getRootElement();
        assertTrue(root.isHasError());
        assertNotNull(root.getErrorMessage());
        assertTrue(root.getLine() > 0);
    }

    @Test
    void handlesEmptyInput() {
        DslFileNode ast = provider.getDslAst("test.xml", "");

        assertNull(ast.getXmlDeclaration());
        DslElementNode root = ast.getRootElement();
        assertTrue(root == null || root.isHasError());
    }
}
