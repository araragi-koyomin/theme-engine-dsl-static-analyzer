package com.huawei.theme.analysis.core.expression;

import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Data;

import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;

@Data
@Builder
public class ExpressionNode implements ExpressionAstNode {
    ExpressionKind kind;
    @Builder.Default String text = "";
    @Builder.Default int line = 0;
    @Builder.Default int column = 0;

    @Builder.Default String operator = null;
    @Builder.Default List<ExpressionNode> children = Collections.emptyList();
    @Builder.Default String functionName = null;
    @Builder.Default String variableName = null;
    @Builder.Default String literalValue = null;
    @Builder.Default String prefix = null;
    @Builder.Default ExpressionNode indexExpression = null;

    @Override
    public String getText() { return text; }

    @Override
    public int getLine() { return line; }

    @Override
    public int getColumn() { return column; }

    @Override
    public ExpressionKind getKind() { return kind; }

    public static ExpressionNode literal(String value, String text, int line, int column) {
        return ExpressionNode.builder()
                .kind(ExpressionKind.LITERAL)
                .literalValue(value)
                .text(text)
                .line(line)
                .column(column)
                .build();
    }

    public static ExpressionNode variableRef(String prefix, String varName, String text, int line, int column) {
        return ExpressionNode.builder()
                .kind(ExpressionKind.VARIABLE_REF)
                .prefix(prefix)
                .variableName(varName)
                .text(text)
                .line(line)
                .column(column)
                .build();
    }

    public static ExpressionNode arrayAccess(String prefix, String varName, ExpressionNode indexExpr, String text, int line, int column) {
        return ExpressionNode.builder()
                .kind(ExpressionKind.ARRAY_ACCESS)
                .prefix(prefix)
                .variableName(varName)
                .indexExpression(indexExpr)
                .text(text)
                .line(line)
                .column(column)
                .build();
    }

    public static ExpressionNode functionCall(String funcName, List<ExpressionNode> args, String text, int line, int column) {
        return ExpressionNode.builder()
                .kind(ExpressionKind.FUNCTION_CALL)
                .functionName(funcName)
                .children(args)
                .text(text)
                .line(line)
                .column(column)
                .build();
    }

    public static ExpressionNode binaryExpr(String op, ExpressionNode left, ExpressionNode right, String text, int line, int column) {
        return ExpressionNode.builder()
                .kind(ExpressionKind.BINARY_EXPR)
                .operator(op)
                .children(List.of(left, right))
                .text(text)
                .line(line)
                .column(column)
                .build();
    }

    public static ExpressionNode unaryExpr(String op, ExpressionNode operand, String text, int line, int column) {
        return ExpressionNode.builder()
                .kind(ExpressionKind.UNARY_EXPR)
                .operator(op)
                .children(List.of(operand))
                .text(text)
                .line(line)
                .column(column)
                .build();
    }
}
