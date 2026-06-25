package com.huawei.theme.analysis.core.expression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;

class ExpressionNodeTest {

    @Test
    void expressionNodeImplementsExpressionAstNode() {
        ExpressionNode node = new StubExpressionNode("5", 1, 0, ExpressionKind.LITERAL);
        assertEquals("5", node.getText());
        assertEquals(1, node.getLine());
        assertEquals(0, node.getColumn());
        assertEquals(ExpressionKind.LITERAL, node.getKind());
    }

    private static class StubExpressionNode extends ExpressionNode {
        private final ExpressionKind kind;

        StubExpressionNode(String text, int line, int column, ExpressionKind kind) {
            this.kind = kind;
            this.text = text;
            this.line = line;
            this.column = column;
        }

        @Override
        public ExpressionKind getKind() {
            return kind;
        }
    }
}
