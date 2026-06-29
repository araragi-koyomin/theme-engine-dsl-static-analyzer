package com.huawei.theme.analysis.core.expression;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import com.huawei.theme.analysis.core.expression.generated.DslExpressionLexer;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionParser;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslExpressionVisitorAdapterTest {

    private ExpressionNode parse(String input) {
        DslExpressionLexer lexer = new DslExpressionLexer(CharStreams.fromString(input));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DslExpressionParser parser = new DslExpressionParser(tokens);
        parser.removeErrorListeners();
        DslExpressionVisitorAdapter adapter = new DslExpressionVisitorAdapter();
        return adapter.visit(parser.expression());
    }

    @Test
    void parseIntegerLiteral() {
        ExpressionNode node = parse("0");
        assertEquals(ExpressionKind.LITERAL, node.getKind());
        assertEquals("0", node.getLiteralValue());
        assertEquals("0", node.getText());
    }

    @Test
    void parsePositiveIntegerLiteral() {
        ExpressionNode node = parse("1");
        assertEquals(ExpressionKind.LITERAL, node.getKind());
        assertEquals("1", node.getLiteralValue());
    }

    @Test
    void parseMultiDigitInteger() {
        ExpressionNode node = parse("100");
        assertEquals(ExpressionKind.LITERAL, node.getKind());
        assertEquals("100", node.getLiteralValue());
    }

    @Test
    void parseDecimalLiteral() {
        ExpressionNode node = parse("3.14");
        assertEquals(ExpressionKind.LITERAL, node.getKind());
        assertEquals("3.14", node.getLiteralValue());
    }

    @Test
    void parseStringLiteral() {
        ExpressionNode node = parse("'hello'");
        assertEquals(ExpressionKind.LITERAL, node.getKind());
        assertEquals("hello", node.getLiteralValue());
        assertEquals("'hello'", node.getText());
    }

    @Test
    void parseStringLiteralTrue() {
        ExpressionNode node = parse("'true'");
        assertEquals(ExpressionKind.LITERAL, node.getKind());
        assertEquals("true", node.getLiteralValue());
    }

    @Test
    void parseHashVariableRef() {
        ExpressionNode node = parse("#varName");
        assertEquals(ExpressionKind.VARIABLE_REF, node.getKind());
        assertEquals("#", node.getPrefix());
        assertEquals("varName", node.getVariableName());
        assertNull(node.getIndexExpression());
    }

    @Test
    void parseAtVariableRef() {
        ExpressionNode node = parse("@varName");
        assertEquals(ExpressionKind.VARIABLE_REF, node.getKind());
        assertEquals("@", node.getPrefix());
        assertEquals("varName", node.getVariableName());
    }

    @Test
    void parseDottedVariableName() {
        ExpressionNode node = parse("#system.time.hour1");
        assertEquals(ExpressionKind.VARIABLE_REF, node.getKind());
        assertEquals("#", node.getPrefix());
        assertEquals("system.time.hour1", node.getVariableName());
    }

    @Test
    void parseDottedScenarioVariableName() {
        ExpressionNode node = parse("#Scenarios.topId");
        assertEquals(ExpressionKind.VARIABLE_REF, node.getKind());
        assertEquals("#", node.getPrefix());
        assertEquals("Scenarios.topId", node.getVariableName());
    }

    @Test
    void parseArrayAccessWithLiteral() {
        ExpressionNode node = parse("#arr[0]");
        assertEquals(ExpressionKind.ARRAY_ACCESS, node.getKind());
        assertEquals("#", node.getPrefix());
        assertEquals("arr", node.getVariableName());
        assertEquals(ExpressionKind.LITERAL, node.getIndexExpression().getKind());
        assertEquals("0", node.getIndexExpression().getLiteralValue());
    }

    @Test
    void parseArrayAccessWithVariableIndex() {
        ExpressionNode node = parse("#arr[#index]");
        assertEquals(ExpressionKind.ARRAY_ACCESS, node.getKind());
        assertEquals("#", node.getPrefix());
        assertEquals("arr", node.getVariableName());
        assertEquals(ExpressionKind.VARIABLE_REF, node.getIndexExpression().getKind());
        assertEquals("#", node.getIndexExpression().getPrefix());
        assertEquals("index", node.getIndexExpression().getVariableName());
    }

    @Test
    void parseSimpleFunctionCall() {
        ExpressionNode node = parse("sin(#x)");
        assertEquals(ExpressionKind.FUNCTION_CALL, node.getKind());
        assertEquals("sin", node.getFunctionName());
        assertEquals(1, node.getChildren().size());
        assertEquals(ExpressionKind.VARIABLE_REF, node.getChildren().get(0).getKind());
        assertEquals("#", node.getChildren().get(0).getPrefix());
        assertEquals("x", node.getChildren().get(0).getVariableName());
    }

    @Test
    void parseFunctionCallWithUnaryMinusArg() {
        ExpressionNode node = parse("abs(-5)");
        assertEquals(ExpressionKind.FUNCTION_CALL, node.getKind());
        assertEquals("abs", node.getFunctionName());
        assertEquals(1, node.getChildren().size());
        assertEquals(ExpressionKind.UNARY_EXPR, node.getChildren().get(0).getKind());
        assertEquals("-", node.getChildren().get(0).getOperator());
        assertEquals(ExpressionKind.LITERAL, node.getChildren().get(0).getChildren().get(0).getKind());
        assertEquals("5", node.getChildren().get(0).getChildren().get(0).getLiteralValue());
    }

    @Test
    void parseFunctionCallWithThreeArgs() {
        ExpressionNode node = parse("ifelse(#cond,#y,#z)");
        assertEquals(ExpressionKind.FUNCTION_CALL, node.getKind());
        assertEquals("ifelse", node.getFunctionName());
        assertEquals(3, node.getChildren().size());
        assertEquals(ExpressionKind.VARIABLE_REF, node.getChildren().get(0).getKind());
        assertEquals("cond", node.getChildren().get(0).getVariableName());
        assertEquals(ExpressionKind.VARIABLE_REF, node.getChildren().get(1).getKind());
        assertEquals("y", node.getChildren().get(1).getVariableName());
        assertEquals(ExpressionKind.VARIABLE_REF, node.getChildren().get(2).getKind());
        assertEquals("z", node.getChildren().get(2).getVariableName());
    }

    @Test
    void parseFunctionCallWithBinaryExprArgs() {
        ExpressionNode node = parse("max(#w/1080,#h/2400)");
        assertEquals(ExpressionKind.FUNCTION_CALL, node.getKind());
        assertEquals("max", node.getFunctionName());
        assertEquals(2, node.getChildren().size());

        ExpressionNode arg1 = node.getChildren().get(0);
        assertEquals(ExpressionKind.BINARY_EXPR, arg1.getKind());
        assertEquals("/", arg1.getOperator());
        assertEquals(ExpressionKind.VARIABLE_REF, arg1.getChildren().get(0).getKind());
        assertEquals("w", arg1.getChildren().get(0).getVariableName());
        assertEquals(ExpressionKind.LITERAL, arg1.getChildren().get(1).getKind());
        assertEquals("1080", arg1.getChildren().get(1).getLiteralValue());

        ExpressionNode arg2 = node.getChildren().get(1);
        assertEquals(ExpressionKind.BINARY_EXPR, arg2.getKind());
        assertEquals("/", arg2.getOperator());
        assertEquals(ExpressionKind.VARIABLE_REF, arg2.getChildren().get(0).getKind());
        assertEquals("h", arg2.getChildren().get(0).getVariableName());
        assertEquals(ExpressionKind.LITERAL, arg2.getChildren().get(1).getKind());
        assertEquals("2400", arg2.getChildren().get(1).getLiteralValue());
    }

    @Test
    void parseBinaryAddition() {
        ExpressionNode node = parse("#x+1");
        assertEquals(ExpressionKind.BINARY_EXPR, node.getKind());
        assertEquals("+", node.getOperator());
        assertEquals(ExpressionKind.VARIABLE_REF, node.getChildren().get(0).getKind());
        assertEquals("x", node.getChildren().get(0).getVariableName());
        assertEquals(ExpressionKind.LITERAL, node.getChildren().get(1).getKind());
        assertEquals("1", node.getChildren().get(1).getLiteralValue());
    }

    @Test
    void parseBinaryMultiplication() {
        ExpressionNode node = parse("2*3");
        assertEquals(ExpressionKind.BINARY_EXPR, node.getKind());
        assertEquals("*", node.getOperator());
        assertEquals(ExpressionKind.LITERAL, node.getChildren().get(0).getKind());
        assertEquals("2", node.getChildren().get(0).getLiteralValue());
        assertEquals(ExpressionKind.LITERAL, node.getChildren().get(1).getKind());
        assertEquals("3", node.getChildren().get(1).getLiteralValue());
    }

    @Test
    void parsePrecedenceAddBeforeMultiply() {
        ExpressionNode node = parse("2+3*4");
        assertEquals(ExpressionKind.BINARY_EXPR, node.getKind());
        assertEquals("+", node.getOperator());

        ExpressionNode left = node.getChildren().get(0);
        assertEquals(ExpressionKind.LITERAL, left.getKind());
        assertEquals("2", left.getLiteralValue());

        ExpressionNode right = node.getChildren().get(1);
        assertEquals(ExpressionKind.BINARY_EXPR, right.getKind());
        assertEquals("*", right.getOperator());
        assertEquals(ExpressionKind.LITERAL, right.getChildren().get(0).getKind());
        assertEquals("3", right.getChildren().get(0).getLiteralValue());
        assertEquals(ExpressionKind.LITERAL, right.getChildren().get(1).getKind());
        assertEquals("4", right.getChildren().get(1).getLiteralValue());
    }

    @Test
    void parseUnaryMinusNumber() {
        ExpressionNode node = parse("-1");
        assertEquals(ExpressionKind.UNARY_EXPR, node.getKind());
        assertEquals("-", node.getOperator());
        assertEquals(1, node.getChildren().size());
        assertEquals(ExpressionKind.LITERAL, node.getChildren().get(0).getKind());
        assertEquals("1", node.getChildren().get(0).getLiteralValue());
    }

    @Test
    void parseUnaryMinusVariable() {
        ExpressionNode node = parse("-#varName");
        assertEquals(ExpressionKind.UNARY_EXPR, node.getKind());
        assertEquals("-", node.getOperator());
        assertEquals(1, node.getChildren().size());
        assertEquals(ExpressionKind.VARIABLE_REF, node.getChildren().get(0).getKind());
        assertEquals("#", node.getChildren().get(0).getPrefix());
        assertEquals("varName", node.getChildren().get(0).getVariableName());
    }

    @Test
    void parseParenthesizedExpression() {
        ExpressionNode node = parse("(#x+1)");
        assertEquals(ExpressionKind.BINARY_EXPR, node.getKind());
        assertEquals("+", node.getOperator());
        assertEquals(ExpressionKind.VARIABLE_REF, node.getChildren().get(0).getKind());
        assertEquals("x", node.getChildren().get(0).getVariableName());
        assertEquals(ExpressionKind.LITERAL, node.getChildren().get(1).getKind());
        assertEquals("1", node.getChildren().get(1).getLiteralValue());
    }

    @Test
    void parseParenthesizedWithModulo() {
        ExpressionNode node = parse("(#categoryIndex+1)%5");
        assertEquals(ExpressionKind.BINARY_EXPR, node.getKind());
        assertEquals("%", node.getOperator());

        ExpressionNode left = node.getChildren().get(0);
        assertEquals(ExpressionKind.BINARY_EXPR, left.getKind());
        assertEquals("+", left.getOperator());
        assertEquals(ExpressionKind.VARIABLE_REF, left.getChildren().get(0).getKind());
        assertEquals("categoryIndex", left.getChildren().get(0).getVariableName());
        assertEquals(ExpressionKind.LITERAL, left.getChildren().get(1).getKind());
        assertEquals("1", left.getChildren().get(1).getLiteralValue());

        ExpressionNode right = node.getChildren().get(1);
        assertEquals(ExpressionKind.LITERAL, right.getKind());
        assertEquals("5", right.getLiteralValue());
    }

    @Test
    void parseEqFunctionCallWithDottedVarAndNegativeArg() {
        ExpressionNode node = parse("eq(#Scenarios.10000013.state,-1)");
        assertEquals(ExpressionKind.FUNCTION_CALL, node.getKind());
        assertEquals("eq", node.getFunctionName());
        assertEquals(2, node.getChildren().size());

        ExpressionNode arg1 = node.getChildren().get(0);
        assertEquals(ExpressionKind.VARIABLE_REF, arg1.getKind());
        assertEquals("#", arg1.getPrefix());
        assertEquals("Scenarios.10000013.state", arg1.getVariableName());

        ExpressionNode arg2 = node.getChildren().get(1);
        assertEquals(ExpressionKind.UNARY_EXPR, arg2.getKind());
        assertEquals("-", arg2.getOperator());
        assertEquals(ExpressionKind.LITERAL, arg2.getChildren().get(0).getKind());
        assertEquals("1", arg2.getChildren().get(0).getLiteralValue());
    }

    @Test
    void parseComplexSubtractionMultiplication() {
        ExpressionNode node = parse("#w/2-1080*#ratio/2");
        assertEquals(ExpressionKind.BINARY_EXPR, node.getKind());
        assertEquals("-", node.getOperator());

        ExpressionNode left = node.getChildren().get(0);
        assertEquals(ExpressionKind.BINARY_EXPR, left.getKind());
        assertEquals("/", left.getOperator());
        assertEquals(ExpressionKind.VARIABLE_REF, left.getChildren().get(0).getKind());
        assertEquals("w", left.getChildren().get(0).getVariableName());
        assertEquals(ExpressionKind.LITERAL, left.getChildren().get(1).getKind());
        assertEquals("2", left.getChildren().get(1).getLiteralValue());

        ExpressionNode right = node.getChildren().get(1);
        assertEquals(ExpressionKind.BINARY_EXPR, right.getKind());
        assertEquals("/", right.getOperator());

        ExpressionNode rightLeft = right.getChildren().get(0);
        assertEquals(ExpressionKind.BINARY_EXPR, rightLeft.getKind());
        assertEquals("*", rightLeft.getOperator());
        assertEquals(ExpressionKind.LITERAL, rightLeft.getChildren().get(0).getKind());
        assertEquals("1080", rightLeft.getChildren().get(0).getLiteralValue());
        assertEquals(ExpressionKind.VARIABLE_REF, rightLeft.getChildren().get(1).getKind());
        assertEquals("ratio", rightLeft.getChildren().get(1).getVariableName());

        assertEquals(ExpressionKind.LITERAL, right.getChildren().get(1).getKind());
        assertEquals("2", right.getChildren().get(1).getLiteralValue());
    }

    @Test
    void parseLeftAssociativeAddSub() {
        ExpressionNode node = parse("1+2-3");
        assertEquals(ExpressionKind.BINARY_EXPR, node.getKind());
        assertEquals("-", node.getOperator());

        ExpressionNode left = node.getChildren().get(0);
        assertEquals(ExpressionKind.BINARY_EXPR, left.getKind());
        assertEquals("+", left.getOperator());
        assertEquals(ExpressionKind.LITERAL, left.getChildren().get(0).getKind());
        assertEquals("1", left.getChildren().get(0).getLiteralValue());
        assertEquals(ExpressionKind.LITERAL, left.getChildren().get(1).getKind());
        assertEquals("2", left.getChildren().get(1).getLiteralValue());

        ExpressionNode right = node.getChildren().get(1);
        assertEquals(ExpressionKind.LITERAL, right.getKind());
        assertEquals("3", right.getLiteralValue());
    }

    @Test
    void parseLeftAssociativeMulDiv() {
        ExpressionNode node = parse("6/2*3");
        assertEquals(ExpressionKind.BINARY_EXPR, node.getKind());
        assertEquals("*", node.getOperator());

        ExpressionNode left = node.getChildren().get(0);
        assertEquals(ExpressionKind.BINARY_EXPR, left.getKind());
        assertEquals("/", left.getOperator());
        assertEquals(ExpressionKind.LITERAL, left.getChildren().get(0).getKind());
        assertEquals("6", left.getChildren().get(0).getLiteralValue());
        assertEquals(ExpressionKind.LITERAL, left.getChildren().get(1).getKind());
        assertEquals("2", left.getChildren().get(1).getLiteralValue());

        ExpressionNode right = node.getChildren().get(1);
        assertEquals(ExpressionKind.LITERAL, right.getKind());
        assertEquals("3", right.getLiteralValue());
    }

    @Test
    void parseFunctionCallWithDottedVarArg() {
        ExpressionNode node = parse("eq(#Scenarios.topId,1)");
        assertEquals(ExpressionKind.FUNCTION_CALL, node.getKind());
        assertEquals("eq", node.getFunctionName());
        assertEquals(2, node.getChildren().size());

        ExpressionNode arg1 = node.getChildren().get(0);
        assertEquals(ExpressionKind.VARIABLE_REF, arg1.getKind());
        assertEquals("#", arg1.getPrefix());
        assertEquals("Scenarios.topId", arg1.getVariableName());

        ExpressionNode arg2 = node.getChildren().get(1);
        assertEquals(ExpressionKind.LITERAL, arg2.getKind());
        assertEquals("1", arg2.getLiteralValue());
    }

    @Test
    void parseLineNumberAndColumn() {
        ExpressionNode node = parse("42");
        assertEquals(1, node.getLine());
        assertEquals(0, node.getColumn());
    }

    @Test
    void parseAtVariableRefWithDottedName() {
        ExpressionNode node = parse("@Scenarios.topId");
        assertEquals(ExpressionKind.VARIABLE_REF, node.getKind());
        assertEquals("@", node.getPrefix());
        assertEquals("Scenarios.topId", node.getVariableName());
    }

    @Test
    void parseAtArrayAccess() {
        ExpressionNode node = parse("@arr[0]");
        assertEquals(ExpressionKind.ARRAY_ACCESS, node.getKind());
        assertEquals("@", node.getPrefix());
        assertEquals("arr", node.getVariableName());
        assertEquals(ExpressionKind.LITERAL, node.getIndexExpression().getKind());
        assertEquals("0", node.getIndexExpression().getLiteralValue());
    }

    @Test
    void parseNestedUnaryMinus() {
        ExpressionNode node = parse("--5");
        assertEquals(ExpressionKind.UNARY_EXPR, node.getKind());
        assertEquals("-", node.getOperator());
        ExpressionNode inner = node.getChildren().get(0);
        assertEquals(ExpressionKind.UNARY_EXPR, inner.getKind());
        assertEquals("-", inner.getOperator());
        assertEquals(ExpressionKind.LITERAL, inner.getChildren().get(0).getKind());
        assertEquals("5", inner.getChildren().get(0).getLiteralValue());
    }

    @Test
    void parseParenthesizedOverridesPrecedence() {
        ExpressionNode node = parse("(2+3)*4");
        assertEquals(ExpressionKind.BINARY_EXPR, node.getKind());
        assertEquals("*", node.getOperator());

        ExpressionNode left = node.getChildren().get(0);
        assertEquals(ExpressionKind.BINARY_EXPR, left.getKind());
        assertEquals("+", left.getOperator());
        assertEquals(ExpressionKind.LITERAL, left.getChildren().get(0).getKind());
        assertEquals("2", left.getChildren().get(0).getLiteralValue());
        assertEquals(ExpressionKind.LITERAL, left.getChildren().get(1).getKind());
        assertEquals("3", left.getChildren().get(1).getLiteralValue());

        ExpressionNode right = node.getChildren().get(1);
        assertEquals(ExpressionKind.LITERAL, right.getKind());
        assertEquals("4", right.getLiteralValue());
    }

    @Test
    void parseModuloOperator() {
        ExpressionNode node = parse("10%3");
        assertEquals(ExpressionKind.BINARY_EXPR, node.getKind());
        assertEquals("%", node.getOperator());
        assertEquals(ExpressionKind.LITERAL, node.getChildren().get(0).getKind());
        assertEquals("10", node.getChildren().get(0).getLiteralValue());
        assertEquals(ExpressionKind.LITERAL, node.getChildren().get(1).getKind());
        assertEquals("3", node.getChildren().get(1).getLiteralValue());
    }

    @Test
    void parseSubtractionOperator() {
        ExpressionNode node = parse("10-3");
        assertEquals(ExpressionKind.BINARY_EXPR, node.getKind());
        assertEquals("-", node.getOperator());
        assertEquals(ExpressionKind.LITERAL, node.getChildren().get(0).getKind());
        assertEquals("10", node.getChildren().get(0).getLiteralValue());
        assertEquals(ExpressionKind.LITERAL, node.getChildren().get(1).getKind());
        assertEquals("3", node.getChildren().get(1).getLiteralValue());
    }

    @Test
    void parseDivisionOperator() {
        ExpressionNode node = parse("10/3");
        assertEquals(ExpressionKind.BINARY_EXPR, node.getKind());
        assertEquals("/", node.getOperator());
    }

    @Test
    void parseFunctionCallNoArgs() {
        ExpressionNode node = parse("rand()");
        assertEquals(ExpressionKind.FUNCTION_CALL, node.getKind());
        assertEquals("rand", node.getFunctionName());
        assertTrue(node.getChildren().isEmpty());
    }
}
