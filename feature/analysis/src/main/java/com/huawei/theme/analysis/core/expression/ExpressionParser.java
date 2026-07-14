package com.huawei.theme.analysis.core.expression;

import java.util.regex.Pattern;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import com.huawei.theme.analysis.core.expression.generated.DslExpressionLexer;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionParser;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;

public final class ExpressionParser {

    private static final Pattern HEX_COLOR =
            Pattern.compile("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$");

    private ExpressionParser() {
    }

    public static boolean hasExpressionSyntax(String value, String attrName) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (value.indexOf('@') >= 0
                || value.indexOf('\'') >= 0
                || value.indexOf('(') >= 0
                || value.indexOf('{') >= 0
                || value.indexOf('+') >= 0
                || value.indexOf('-') >= 0
                || value.indexOf('*') >= 0
                || value.indexOf('/') >= 0
                || value.indexOf('%') >= 0) {
            return true;
        }
        if (value.indexOf('#') >= 0) {
            return !(isColorAttribute(attrName) && isHexColor(value));
        }
        if (isPlainNumeric(value)) {
            return true;
        }
        if (value.matches(".*[a-zA-Z_].*")) {
            return true;
        }
        return false;
    }

    private static boolean isPlainNumeric(String value) {
        return value.matches("^[+-]?\\d+(\\.\\d+)?$");
    }

    public static ExpressionNode parseExpression(String value, String expressionKind) {
        ParseOutcome o = doParse(value, expressionKind);
        if (o.antlrError() || o.leftoverTokens() || o.node() == null
                || containsInvalidUnaryMinusVar(o.node())) {
            return null;
        }
        return o.node();
    }

    public static ParseOutcome doParse(String value, String expressionKind) {
        try {
            DslExpressionLexer lexer = new DslExpressionLexer(CharStreams.fromString(value));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            DslExpressionParser parser = new DslExpressionParser(tokens);
            ErrorCollector collector = new ErrorCollector();
            lexer.removeErrorListeners();
            parser.removeErrorListeners();
            lexer.addErrorListener(collector);
            parser.addErrorListener(collector);
            DslExpressionVisitorAdapter adapter = new DslExpressionVisitorAdapter();
            ExpressionNode node;
            if ("string".equals(expressionKind)) {
                node = adapter.visit(parser.stringExpression());
            } else if ("number".equals(expressionKind)) {
                node = adapter.visit(parser.numericExpression());
            } else {
                node = adapter.visit(parser.expression());
            }
            tokens.fill();
            boolean leftover = tokens.index() < tokens.size() - 1;
            return new ParseOutcome(node, collector.hasErrors, leftover);
        } catch (Exception e) {
            return new ParseOutcome(null, true, false);
        }
    }

    public static boolean containsInvalidUnaryMinusVar(ExpressionNode node) {
        if (node == null) {
            return false;
        }
        if (node.getKind() == ExpressionKind.UNARY_EXPR && "-".equals(node.getOperator())
                && node.getChildren() != null && !node.getChildren().isEmpty()) {
            ExpressionNode child = node.getChildren().get(0);
            if ((child.getKind() == ExpressionKind.VARIABLE_REF
                    || child.getKind() == ExpressionKind.ARRAY_ACCESS)
                    && "#".equals(child.getPrefix())) {
                return true;
            }
        }
        if (node.getChildren() != null) {
            for (ExpressionNode c : node.getChildren()) {
                if (containsInvalidUnaryMinusVar(c)) {
                    return true;
                }
            }
        }
        if (node.getIndexExpression() != null && containsInvalidUnaryMinusVar(node.getIndexExpression())) {
            return true;
        }
        return false;
    }

    private static boolean isColorAttribute(String attrName) {
        return "color".equals(attrName) || "shadowColor".equals(attrName);
    }

    private static boolean isHexColor(String value) {
        return HEX_COLOR.matcher(value).matches();
    }

    public record ParseOutcome(ExpressionNode node, boolean antlrError, boolean leftoverTokens) {
    }

    private static final class ErrorCollector extends BaseErrorListener {
        private boolean hasErrors;

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                int line, int charPositionInLine, String msg, RecognitionException e) {
            hasErrors = true;
        }
    }
}
