package com.huawei.theme.analysis.core.expression;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;

import com.huawei.theme.analysis.core.expression.generated.DslExpressionBaseVisitor;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionParser;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;

public class DslExpressionVisitorAdapter extends DslExpressionBaseVisitor<ExpressionNode> {

    @Override
    public ExpressionNode visitExpression(DslExpressionParser.ExpressionContext ctx) {
        return visit(ctx.additiveExpr());
    }

    @Override
    public ExpressionNode visitAdditiveExpr(DslExpressionParser.AdditiveExprContext ctx) {
        List<DslExpressionParser.MultiplicativeExprContext> operands = ctx.multiplicativeExpr();
        if (operands.size() == 1) {
            return visit(operands.get(0));
        }

        ExpressionNode result = visit(operands.get(0));
        for (int i = 1; i < operands.size(); i++) {
            String op = ctx.getChild(2 * i - 1).getText();
            ExpressionNode right = visit(operands.get(i));
            result = ExpressionNode.binaryExpr(
                    op, result, right,
                    ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        return result;
    }

    @Override
    public ExpressionNode visitMultiplicativeExpr(DslExpressionParser.MultiplicativeExprContext ctx) {
        List<DslExpressionParser.PrimaryExprContext> operands = ctx.primaryExpr();
        if (operands.size() == 1) {
            return visit(operands.get(0));
        }

        ExpressionNode result = visit(operands.get(0));
        for (int i = 1; i < operands.size(); i++) {
            String op = ctx.getChild(2 * i - 1).getText();
            ExpressionNode right = visit(operands.get(i));
            result = ExpressionNode.binaryExpr(
                    op, result, right,
                    ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        return result;
    }

    @Override
    public ExpressionNode visitPrimaryExpr(DslExpressionParser.PrimaryExprContext ctx) {
        if (ctx.primaryExpr() != null) {
            ExpressionNode operand = visit(ctx.primaryExpr());
            return ExpressionNode.unaryExpr(
                    "-", operand,
                    ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        if (ctx.functionCall() != null) {
            return visit(ctx.functionCall());
        }
        if (ctx.variableRef() != null) {
            return visit(ctx.variableRef());
        }
        if (ctx.literal() != null) {
            return visit(ctx.literal());
        }
        if (ctx.expression() != null) {
            return visit(ctx.expression());
        }
        return ExpressionNode.builder()
                .kind(com.huawei.theme.analysis.core.shared.ast.ExpressionKind.UNKNOWN)
                .text(ctx.getText())
                .line(ctx.start.getLine())
                .column(ctx.start.getCharPositionInLine())
                .build();
    }

    @Override
    public ExpressionNode visitFunctionCall(DslExpressionParser.FunctionCallContext ctx) {
        String functionName = ctx.ID().getText();
        List<ExpressionNode> args;
        if (ctx.exprList() != null) {
            args = collectExpressions(ctx.exprList());
        } else {
            args = new ArrayList<>();
        }
        return ExpressionNode.functionCall(
                functionName, args,
                ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public ExpressionNode visitVariableRef(DslExpressionParser.VariableRefContext ctx) {
        String prefix;
        ParseTree firstChild = ctx.getChild(0);
        prefix = firstChild.getText();

        String variableName = ctx.varName().getText();

        if (ctx.expression() != null) {
            ExpressionNode indexExpr = visit(ctx.expression());
            return ExpressionNode.arrayAccess(
                    prefix, variableName, indexExpr,
                    ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        return ExpressionNode.variableRef(
                prefix, variableName,
                ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public ExpressionNode visitVarName(DslExpressionParser.VarNameContext ctx) {
        if (ctx.ID() != null) {
            return ExpressionNode.literal(
                    ctx.ID().getText(), ctx.getText(),
                    ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        if (ctx.VAR_ID() != null) {
            return ExpressionNode.literal(
                    ctx.VAR_ID().getText(), ctx.getText(),
                    ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        return ExpressionNode.builder()
                .kind(com.huawei.theme.analysis.core.shared.ast.ExpressionKind.UNKNOWN)
                .text(ctx.getText())
                .line(ctx.start.getLine())
                .column(ctx.start.getCharPositionInLine())
                .build();
    }

    @Override
    public ExpressionNode visitLiteral(DslExpressionParser.LiteralContext ctx) {
        String literalValue;
        if (ctx.NUMBER() != null) {
            literalValue = ctx.NUMBER().getText();
        } else if (ctx.STRING() != null) {
            String raw = ctx.STRING().getText();
            literalValue = stripQuotes(raw);
        } else {
            literalValue = ctx.getText();
        }
        return ExpressionNode.literal(
                literalValue, ctx.getText(),
                ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public ExpressionNode visitExprList(DslExpressionParser.ExprListContext ctx) {
        List<ExpressionNode> expressions = collectExpressions(ctx);
        if (expressions.size() == 1) {
            return expressions.get(0);
        }
        return ExpressionNode.builder()
                .kind(ExpressionKind.UNKNOWN)
                .children(expressions)
                .text(ctx.getText())
                .line(ctx.start.getLine())
                .column(ctx.start.getCharPositionInLine())
                .build();
    }

    private List<ExpressionNode> collectExpressions(DslExpressionParser.ExprListContext ctx) {
        List<ExpressionNode> expressions = new ArrayList<>();
        for (DslExpressionParser.ExpressionContext exprCtx : ctx.expression()) {
            expressions.add(visit(exprCtx));
        }
        return expressions;
    }

    private String stripQuotes(String s) {
        if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
