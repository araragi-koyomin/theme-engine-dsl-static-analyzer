package com.huawei.theme.analysis.core.expression;

import java.util.ArrayList;
import java.util.List;

import com.huawei.theme.analysis.core.expression.generated.DslExpressionBaseVisitor;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionParser;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;

public class DslExpressionVisitorAdapter extends DslExpressionBaseVisitor<ExpressionNode> {

    @Override
    public ExpressionNode visitExpression(DslExpressionParser.ExpressionContext ctx) {
        return visit(ctx.additiveExpr());
    }

    @Override
    public ExpressionNode visitStringExpression(DslExpressionParser.StringExpressionContext ctx) {
        if (ctx.stringConcat() != null) {
            return visit(ctx.stringConcat());
        }
        return visit(ctx.numericExpression());
    }

    @Override
    public ExpressionNode visitStringConcat(DslExpressionParser.StringConcatContext ctx) {
        List<DslExpressionParser.StringTermContext> terms = ctx.stringTerm();
        if (terms.size() == 1) {
            return visit(terms.get(0));
        }
        ExpressionNode result = visit(terms.get(0));
        for (int i = 1; i < terms.size(); i++) {
            ExpressionNode right = visit(terms.get(i));
            result = ExpressionNode.binaryExpr(
                    "+", result, right,
                    ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        return result;
    }

    @Override
    public ExpressionNode visitStringTerm(DslExpressionParser.StringTermContext ctx) {
        if (ctx.STRING() != null) {
            return ExpressionNode.literal(
                    stripQuotes(ctx.STRING().getText()), ctx.getText(),
                    ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        if (ctx.atVarRef() != null) {
            return visit(ctx.atVarRef());
        }
        if (ctx.functionCall() != null) {
            return visit(ctx.functionCall());
        }
        if (ctx.hashVarRef() != null) {
            return visit(ctx.hashVarRef());
        }
        if (ctx.NUMBER() != null) {
            return ExpressionNode.literal(
                    ctx.NUMBER().getText(), ctx.getText(),
                    ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        if (ctx.numericExpression() != null) {
            return visit(ctx.numericExpression());
        }
        return unknown(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public ExpressionNode visitNumericExpression(DslExpressionParser.NumericExpressionContext ctx) {
        List<DslExpressionParser.NumericMultiplicativeContext> operands = ctx.numericMultiplicative();
        if (operands.size() == 1) {
            return visit(operands.get(0));
        }
        return buildBinaryChain(operands, ctx);
    }

    @Override
    public ExpressionNode visitNumericMultiplicative(DslExpressionParser.NumericMultiplicativeContext ctx) {
        List<DslExpressionParser.NumericTermContext> operands = ctx.numericTerm();
        if (operands.size() == 1) {
            return visit(operands.get(0));
        }
        return buildBinaryChain(operands, ctx);
    }

    @Override
    public ExpressionNode visitNumericTerm(DslExpressionParser.NumericTermContext ctx) {
        if (ctx.NUMBER() != null) {
            return ExpressionNode.literal(
                    ctx.NUMBER().getText(), ctx.getText(),
                    ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        if (ctx.hashVarRef() != null) {
            return visit(ctx.hashVarRef());
        }
        if (ctx.functionCall() != null) {
            return visit(ctx.functionCall());
        }
        if (ctx.numericTerm() != null) {
            return ExpressionNode.unaryExpr(
                    "-", visit(ctx.numericTerm()),
                    ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
        if (ctx.numericExpression() != null) {
            return visit(ctx.numericExpression());
        }
        return unknown(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public ExpressionNode visitAdditiveExpr(DslExpressionParser.AdditiveExprContext ctx) {
        List<DslExpressionParser.MultiplicativeExprContext> operands = ctx.multiplicativeExpr();
        if (operands.size() == 1) {
            return visit(operands.get(0));
        }
        return buildBinaryChain(operands, ctx);
    }

    @Override
    public ExpressionNode visitMultiplicativeExpr(DslExpressionParser.MultiplicativeExprContext ctx) {
        List<DslExpressionParser.PrimaryExprContext> operands = ctx.primaryExpr();
        if (operands.size() == 1) {
            return visit(operands.get(0));
        }
        return buildBinaryChain(operands, ctx);
    }

    @Override
    public ExpressionNode visitPrimaryExpr(DslExpressionParser.PrimaryExprContext ctx) {
        if (ctx.primaryExpr() != null) {
            return ExpressionNode.unaryExpr(
                    "-", visit(ctx.primaryExpr()),
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
        return unknown(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public ExpressionNode visitVariableRef(DslExpressionParser.VariableRefContext ctx) {
        if (ctx.hashVarRef() != null) {
            return visit(ctx.hashVarRef());
        }
        return visit(ctx.atVarRef());
    }

    @Override
    public ExpressionNode visitHashVarRef(DslExpressionParser.HashVarRefContext ctx) {
        return buildVarRef("#", ctx.varName().getText(), ctx.expression(), ctx);
    }

    @Override
    public ExpressionNode visitAtVarRef(DslExpressionParser.AtVarRefContext ctx) {
        return buildVarRef("@", ctx.varName().getText(), ctx.expression(), ctx);
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
    public ExpressionNode visitLiteral(DslExpressionParser.LiteralContext ctx) {
        String literalValue;
        if (ctx.NUMBER() != null) {
            literalValue = ctx.NUMBER().getText();
        } else if (ctx.STRING() != null) {
            literalValue = stripQuotes(ctx.STRING().getText());
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

    private ExpressionNode buildVarRef(String prefix, String variableName,
            DslExpressionParser.ExpressionContext indexCtx, org.antlr.v4.runtime.ParserRuleContext ctx) {
        int line = ctx.getStart().getLine();
        int column = ctx.getStart().getCharPositionInLine();
        if (indexCtx != null) {
            return ExpressionNode.arrayAccess(
                    prefix, variableName, visit(indexCtx), ctx.getText(), line, column);
        }
        return ExpressionNode.variableRef(prefix, variableName, ctx.getText(), line, column);
    }

    private <T extends org.antlr.v4.runtime.ParserRuleContext> ExpressionNode buildBinaryChain(
            List<T> operands, org.antlr.v4.runtime.ParserRuleContext ctx) {
        ExpressionNode result = visit(operands.get(0));
        for (int i = 1; i < operands.size(); i++) {
            String op = ctx.getChild(2 * i - 1).getText();
            ExpressionNode right = visit(operands.get(i));
            result = ExpressionNode.binaryExpr(
                    op, result, right,
                    ctx.getText(), ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        }
        return result;
    }

    private List<ExpressionNode> collectExpressions(DslExpressionParser.ExprListContext ctx) {
        List<ExpressionNode> expressions = new ArrayList<>();
        for (DslExpressionParser.ExpressionContext exprCtx : ctx.expression()) {
            expressions.add(visit(exprCtx));
        }
        return expressions;
    }

    private ExpressionNode unknown(String text, int line, int column) {
        return ExpressionNode.builder()
                .kind(ExpressionKind.UNKNOWN)
                .text(text)
                .line(line)
                .column(column)
                .build();
    }

    private String stripQuotes(String s) {
        if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
