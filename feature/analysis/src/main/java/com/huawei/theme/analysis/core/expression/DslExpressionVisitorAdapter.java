package com.huawei.theme.analysis.core.expression;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import com.huawei.theme.analysis.core.expression.generated.DslExpressionBaseVisitor;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionParser;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;
import com.huawei.theme.analysis.core.shared.ast.SourceRange;

public class DslExpressionVisitorAdapter extends DslExpressionBaseVisitor<ExpressionNode> {

    @Override
    public ExpressionNode visitExpression(DslExpressionParser.ExpressionContext ctx) {
        return visit(ctx.comparisonExpr());
    }

    @Override
    public ExpressionNode visitComparisonExpr(DslExpressionParser.ComparisonExprContext ctx) {
        List<DslExpressionParser.AdditiveExprContext> operands = ctx.additiveExpr();
        if (operands.size() == 1) {
            return visit(operands.get(0));
        }
        return buildBinaryChain(operands, ctx);
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
                    ctx.getText(), rangeOf(ctx));
        }
        return result;
    }

    @Override
    public ExpressionNode visitStringTerm(DslExpressionParser.StringTermContext ctx) {
        if (ctx.STRING() != null) {
            return ExpressionNode.literal(
                    stripQuotes(ctx.STRING().getText()), ctx.getText(), rangeOf(ctx));
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
                    ctx.NUMBER().getText(), ctx.getText(), rangeOf(ctx));
        }
        if (ctx.numericExpression() != null) {
            return ExpressionNode.bracedExpr(visit(ctx.numericExpression()), ctx.getText(), rangeOf(ctx));
        }
        return unknown(ctx.getText(), rangeOf(ctx));
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
                    ctx.NUMBER().getText(), ctx.getText(), rangeOf(ctx));
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
                    ctx.getText(), rangeOf(ctx));
        }
        if (ctx.numericExpression() != null) {
            return visit(ctx.numericExpression());
        }
        return unknown(ctx.getText(), rangeOf(ctx));
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
                    ctx.getText(), rangeOf(ctx));
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
            ExpressionNode inner = visit(ctx.expression());
            if (ctx.getChild(0) != null && "{".equals(ctx.getChild(0).getText())) {
                return ExpressionNode.bracedExpr(inner, ctx.getText(), rangeOf(ctx));
            }
            return inner;
        }
        return unknown(ctx.getText(), rangeOf(ctx));
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
                ctx.getText(), rangeOf(ctx));
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
                literalValue, ctx.getText(), rangeOf(ctx));
    }

    @Override
    public ExpressionNode visitExprList(DslExpressionParser.ExprListContext ctx) {
        List<ExpressionNode> expressions = collectExpressions(ctx);
        if (expressions.size() == 1) {
            return expressions.get(0);
        }
        SourceRange range = rangeOf(ctx);
        return ExpressionNode.builder()
                .kind(ExpressionKind.UNKNOWN)
                .children(expressions)
                .text(ctx.getText())
                .line(range.getStartLine())
                .column(range.getStartColumn())
                .endLine(range.getEndLine())
                .endColumn(range.getEndColumn())
                .build();
    }

    private ExpressionNode buildVarRef(String prefix, String variableName,
            DslExpressionParser.ExpressionContext indexCtx, ParserRuleContext ctx) {
        SourceRange range = rangeOf(ctx);
        if (indexCtx != null) {
            return ExpressionNode.arrayAccess(
                    prefix, variableName, visit(indexCtx), ctx.getText(), range);
        }
        return ExpressionNode.variableRef(prefix, variableName, ctx.getText(), range);
    }

    private <T extends ParserRuleContext> ExpressionNode buildBinaryChain(
            List<T> operands, ParserRuleContext ctx) {
        ExpressionNode result = visit(operands.get(0));
        SourceRange range = rangeOf(ctx);
        for (int i = 1; i < operands.size(); i++) {
            String op = ctx.getChild(2 * i - 1).getText();
            ExpressionNode right = visit(operands.get(i));
            result = ExpressionNode.binaryExpr(
                    op, result, right,
                    ctx.getText(), range);
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

    private ExpressionNode unknown(String text, SourceRange range) {
        return ExpressionNode.builder()
                .kind(ExpressionKind.UNKNOWN)
                .text(text)
                .line(range.getStartLine())
                .column(range.getStartColumn())
                .endLine(range.getEndLine())
                .endColumn(range.getEndColumn())
                .build();
    }

    /**
     * 从ANTLR上下文计算源码区间：start为ctx起始token，end为ctx末尾token之后(开区间)。
     * stop为null时退化为点位置。行1-based(ANTLR getLine)，列0-based(getCharPositionInLine)。
     */
    private static SourceRange rangeOf(ParserRuleContext ctx) {
        Token start = ctx.getStart();
        int startLine = start.getLine();
        int startCol = start.getCharPositionInLine();
        Token stop = ctx.getStop();
        if (stop == null) {
            return SourceRange.point(startLine, startCol);
        }
        int endLine = stop.getLine();
        int len = stop.getText() != null ? stop.getText().length() : 0;
        int endCol = stop.getCharPositionInLine() + len;
        return SourceRange.of(startLine, startCol, endLine, endCol);
    }

    private String stripQuotes(String s) {
        if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
