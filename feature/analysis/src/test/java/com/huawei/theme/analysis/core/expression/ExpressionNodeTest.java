package com.huawei.theme.analysis.core.expression;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionNodeTest {

    @Test
    void literalFactoryCreatesLiteralKindWithCorrectFields() {
        ExpressionNode node = ExpressionNode.literal("42", "42", 1, 5);
        assertEquals(ExpressionKind.LITERAL, node.getKind());
        assertEquals("42", node.getLiteralValue());
        assertEquals("42", node.getText());
        assertEquals(1, node.getLine());
        assertEquals(5, node.getColumn());
        assertNull(node.getOperator());
        assertNull(node.getFunctionName());
        assertNull(node.getVariableName());
        assertNull(node.getPrefix());
        assertNull(node.getIndexExpression());
        assertTrue(node.getChildren().isEmpty());
    }

    @Test
    void variableRefFactoryCreatesVariableRefKindWithPrefixAndName() {
        ExpressionNode node = ExpressionNode.variableRef("$", "theme", "$theme", 3, 10);
        assertEquals(ExpressionKind.VARIABLE_REF, node.getKind());
        assertEquals("$", node.getPrefix());
        assertEquals("theme", node.getVariableName());
        assertEquals("$theme", node.getText());
        assertEquals(3, node.getLine());
        assertEquals(10, node.getColumn());
        assertNull(node.getLiteralValue());
        assertNull(node.getOperator());
        assertNull(node.getFunctionName());
        assertNull(node.getIndexExpression());
        assertTrue(node.getChildren().isEmpty());
    }

    @Test
    void arrayAccessFactoryCreatesArrayAccessKindWithIndexExpression() {
        ExpressionNode index = ExpressionNode.literal("0", "0", 2, 8);
        ExpressionNode node = ExpressionNode.arrayAccess("$", "colors", index, "$colors[0]", 2, 4);
        assertEquals(ExpressionKind.ARRAY_ACCESS, node.getKind());
        assertEquals("$", node.getPrefix());
        assertEquals("colors", node.getVariableName());
        assertEquals(index, node.getIndexExpression());
        assertEquals("$colors[0]", node.getText());
        assertEquals(2, node.getLine());
        assertEquals(4, node.getColumn());
        assertNull(node.getLiteralValue());
        assertNull(node.getOperator());
        assertNull(node.getFunctionName());
        assertTrue(node.getChildren().isEmpty());
    }

    @Test
    void functionCallFactoryCreatesFunctionCallKindWithFunctionNameAndChildren() {
        ExpressionNode arg1 = ExpressionNode.literal("10", "10", 4, 12);
        ExpressionNode arg2 = ExpressionNode.literal("20", "20", 4, 16);
        ExpressionNode node = ExpressionNode.functionCall("abs", List.of(arg1, arg2), "abs(10, 20)", 4, 8);
        assertEquals(ExpressionKind.FUNCTION_CALL, node.getKind());
        assertEquals("abs", node.getFunctionName());
        assertEquals(2, node.getChildren().size());
        assertEquals(arg1, node.getChildren().get(0));
        assertEquals(arg2, node.getChildren().get(1));
        assertEquals("abs(10, 20)", node.getText());
        assertEquals(4, node.getLine());
        assertEquals(8, node.getColumn());
        assertNull(node.getOperator());
        assertNull(node.getVariableName());
        assertNull(node.getPrefix());
        assertNull(node.getLiteralValue());
        assertNull(node.getIndexExpression());
    }

    @Test
    void binaryExprFactoryCreatesBinaryExprKindWithOperatorAndLeftRight() {
        ExpressionNode left = ExpressionNode.literal("1", "1", 5, 0);
        ExpressionNode right = ExpressionNode.literal("2", "2", 5, 4);
        ExpressionNode node = ExpressionNode.binaryExpr("+", left, right, "1 + 2", 5, 2);
        assertEquals(ExpressionKind.BINARY_EXPR, node.getKind());
        assertEquals("+", node.getOperator());
        assertEquals(2, node.getChildren().size());
        assertEquals(left, node.getChildren().get(0));
        assertEquals(right, node.getChildren().get(1));
        assertEquals("1 + 2", node.getText());
        assertEquals(5, node.getLine());
        assertEquals(2, node.getColumn());
        assertNull(node.getFunctionName());
        assertNull(node.getVariableName());
        assertNull(node.getLiteralValue());
        assertNull(node.getPrefix());
        assertNull(node.getIndexExpression());
    }

    @Test
    void unaryExprFactoryCreatesUnaryExprKindWithOperatorAndSingleChild() {
        ExpressionNode operand = ExpressionNode.literal("5", "5", 6, 2);
        ExpressionNode node = ExpressionNode.unaryExpr("-", operand, "-5", 6, 0);
        assertEquals(ExpressionKind.UNARY_EXPR, node.getKind());
        assertEquals("-", node.getOperator());
        assertEquals(1, node.getChildren().size());
        assertEquals(operand, node.getChildren().get(0));
        assertEquals("-5", node.getText());
        assertEquals(6, node.getLine());
        assertEquals(0, node.getColumn());
        assertNull(node.getFunctionName());
        assertNull(node.getVariableName());
        assertNull(node.getLiteralValue());
        assertNull(node.getPrefix());
        assertNull(node.getIndexExpression());
    }

    @Test
    void builderCreatesNodeWithCorrectDefaultValues() {
        ExpressionNode node = ExpressionNode.builder()
                .kind(ExpressionKind.UNKNOWN)
                .build();
        assertEquals(ExpressionKind.UNKNOWN, node.getKind());
        assertEquals("", node.getText());
        assertEquals(0, node.getLine());
        assertEquals(0, node.getColumn());
        assertNull(node.getOperator());
        assertTrue(node.getChildren().isEmpty());
        assertNull(node.getFunctionName());
        assertNull(node.getVariableName());
        assertNull(node.getLiteralValue());
        assertNull(node.getPrefix());
        assertNull(node.getIndexExpression());
    }

    @Test
    void expressionNodeImplementsExpressionAstNodeInterface() {
        ExpressionNode node = ExpressionNode.literal("hello", "hello", 10, 20);
        assertTrue(node instanceof com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode);
        assertEquals("hello", node.getText());
        assertEquals(10, node.getLine());
        assertEquals(20, node.getColumn());
        assertEquals(ExpressionKind.LITERAL, node.getKind());
    }
}
