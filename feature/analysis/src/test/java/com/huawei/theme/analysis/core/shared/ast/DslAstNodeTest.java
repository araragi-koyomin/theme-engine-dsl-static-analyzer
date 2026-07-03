package com.huawei.theme.analysis.core.shared.ast;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslAstNodeTest {

    @Test
    void dslAstNodeBaseFields() {
        DslElementNode node = new DslElementNode();
        node.setText("<Var>");
        node.setLine(5);
        node.setColumn(10);
        node.setTagName("Var");
        node.setAttributes(List.of());
        node.setChildElements(List.of());
        node.setSelfClosing(false);
        node.setHasError(false);
        assertEquals("<Var>", node.getText());
        assertEquals(5, node.getLine());
        assertEquals(10, node.getColumn());
    }

    @Test
    void dslFileNodeStructure() {
        DslElementNode root = new DslElementNode();
        root.setText("<Lockscreen>");
        root.setLine(1);
        root.setColumn(0);
        root.setTagName("Lockscreen");
        root.setAttributes(List.of());
        root.setChildElements(List.of());
        root.setSelfClosing(false);
        root.setHasError(false);
        DslFileNode fileNode = new DslFileNode();
        fileNode.setText("<Lockscreen>");
        fileNode.setLine(1);
        fileNode.setColumn(0);
        fileNode.setXmlDeclaration("<?xml version=\"1.0\"?>");
        fileNode.setRootElement(root);
        assertEquals("<?xml version=\"1.0\"?>", fileNode.getXmlDeclaration());
        assertEquals("Lockscreen", fileNode.getRootElement().getTagName());
    }

    @Test
    void dslAttributeValueNodeExpressionIsOptional() {
        DslAttributeValueNode literal = new DslAttributeValueNode();
        literal.setText("\"hello\"");
        literal.setLine(3);
        literal.setColumn(5);
        literal.setRawValue("hello");
        literal.setExpression(Optional.empty());
        literal.setLiteral(true);
        assertFalse(literal.getExpression().isPresent());

        DslAttributeValueNode expr = new DslAttributeValueNode();
        expr.setText("#screen_width/2");
        expr.setLine(4);
        expr.setColumn(8);
        expr.setRawValue("#screen_width/2");
        expr.setExpression(Optional.of(new StubExpressionAstNode()));
        expr.setLiteral(false);
        assertTrue(expr.getExpression().isPresent());
        assertEquals("#screen_width/2", expr.getExpression().get().getText());
    }

    @Test
    void expressionKindEnumValues() {
        assertEquals(8, ExpressionKind.values().length);
        assertEquals(ExpressionKind.LITERAL, ExpressionKind.valueOf("LITERAL"));
        assertEquals(ExpressionKind.VARIABLE_REF, ExpressionKind.valueOf("VARIABLE_REF"));
        assertEquals(ExpressionKind.FUNCTION_CALL, ExpressionKind.valueOf("FUNCTION_CALL"));
        assertEquals(ExpressionKind.BINARY_EXPR, ExpressionKind.valueOf("BINARY_EXPR"));
        assertEquals(ExpressionKind.UNARY_EXPR, ExpressionKind.valueOf("UNARY_EXPR"));
        assertEquals(ExpressionKind.CONDITIONAL, ExpressionKind.valueOf("CONDITIONAL"));
        assertEquals(ExpressionKind.ARRAY_ACCESS, ExpressionKind.valueOf("ARRAY_ACCESS"));
        assertEquals(ExpressionKind.UNKNOWN, ExpressionKind.valueOf("UNKNOWN"));
    }

    private static class StubExpressionAstNode implements ExpressionAstNode {
        @Override public String getText() { return "#screen_width/2"; }

        @Override public int getLine() { return 4; }

        @Override public int getColumn() { return 8; }

        @Override public int getEndLine() { return 4; }

        @Override public int getEndColumn() { return 8; }

        @Override public ExpressionKind getKind() { return ExpressionKind.BINARY_EXPR; }
    }
}
