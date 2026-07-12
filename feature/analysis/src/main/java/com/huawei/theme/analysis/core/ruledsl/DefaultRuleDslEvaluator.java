package com.huawei.theme.analysis.core.ruledsl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import com.huawei.theme.analysis.core.ruledsl.generated.DslRuleConditionBaseVisitor;
import com.huawei.theme.analysis.core.ruledsl.generated.DslRuleConditionLexer;
import com.huawei.theme.analysis.core.ruledsl.generated.DslRuleConditionParser;

public class DefaultRuleDslEvaluator extends DslRuleConditionBaseVisitor<Boolean> implements RuleDslEvaluator {

    private static final Pattern CHILDREN_FILTER_TAGNAME =
            Pattern.compile("c\\.tagName\\s*==\\s*'([^']*)'");
    private static final Pattern CHILDREN_SIZE_EXPR =
            Pattern.compile("element\\.children\\.(?:filter|where)\\(c\\s*->\\s*c\\.tagName\\s*==\\s*'([^']*)'\\)\\.size\\(\\)\\s*(>=?|<=?|==|!=)\\s*(\\d+)");

    private EvaluationContext context;

    @Override
    public boolean evaluate(String condition, EvaluationContext context) {
        this.context = context;
        String processed = preprocessChildrenExpressions(condition, context);
        return parseAndEvaluate(processed);
    }

    private String preprocessChildrenExpressions(String condition, EvaluationContext context) {
        if (condition == null || !condition.contains("children.")) {
            return condition;
        }
        if (!condition.contains("children.filter") && !condition.contains("children.where")) {
            return condition;
        }
        String result = condition;
        Matcher m = CHILDREN_SIZE_EXPR.matcher(result);
        while (m.find()) {
            String tagName = m.group(1);
            String operator = m.group(2);
            int threshold = Integer.parseInt(m.group(3));
            String fullMatch = m.group(0);
            int count = 0;
            List<Map<String, Object>> childElements = context.getChildElements();
            if (childElements != null) {
                for (Map<String, Object> child : childElements) {
                    Object childTag = child.get("tagName");
                    if (tagName.equals(childTag)) {
                        count++;
                    }
                }
            }
            boolean evalResult = false;
            switch (operator) {
                case ">":
                    evalResult = count > threshold;
                    break;
                case ">=":
                    evalResult = count >= threshold;
                    break;
                case "<":
                    evalResult = count < threshold;
                    break;
                case "<=":
                    evalResult = count <= threshold;
                    break;
                case "==":
                    evalResult = count == threshold;
                    break;
                case "!=":
                    evalResult = count != threshold;
                    break;
                default:
                    evalResult = false;
                    break;
            }
            result = result.replace(fullMatch, String.valueOf(evalResult));
            m = CHILDREN_SIZE_EXPR.matcher(result);
        }
        return result;
    }

    private boolean parseAndEvaluate(String condition) {
        try {
            DslRuleConditionLexer lexer = new DslRuleConditionLexer(CharStreams.fromString(condition));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            DslRuleConditionParser parser = new DslRuleConditionParser(tokens);
            lexer.removeErrorListeners();
            parser.removeErrorListeners();
            return visit(parser.condition());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Boolean visitCondition(DslRuleConditionParser.ConditionContext ctx) {
        return visit(ctx.logicExpr());
    }

    @Override
    public Boolean visitLogicExpr(DslRuleConditionParser.LogicExprContext ctx) {
        if (ctx.AND() != null) {
            return visit(ctx.logicExpr(0)) && visit(ctx.logicExpr(1));
        }
        if (ctx.OR() != null) {
            return visit(ctx.logicExpr(0)) || visit(ctx.logicExpr(1));
        }
        if (ctx.NOT() != null) {
            return !visit(ctx.logicExpr(0));
        }
        if (ctx.compareExpr() != null) {
            return visit(ctx.compareExpr());
        }
        if (!ctx.logicExpr().isEmpty()) {
            return visit(ctx.logicExpr(0));
        }
        return false;
    }

    @Override
    public Boolean visitCompareExpr(DslRuleConditionParser.CompareExprContext ctx) {
        if (ctx.NOT() != null && ctx.IN() != null) {
            String value = resolveValue(ctx.valueExpr(0));
            Set<String> set = resolveSet(ctx.setLiteral());
            if (value == null) return true;
            return !set.contains(value);
        }
        if (ctx.IN() != null) {
            String value = resolveValue(ctx.valueExpr(0));
            Set<String> set = resolveSet(ctx.setLiteral());
            if (value == null) return false;
            return set.contains(value);
        }
        if (ctx.MATCHES() != null) {
            String left = resolveValue(ctx.valueExpr(0));
            String right = resolveLiteral(ctx.literal());
            if (left == null || right == null) return false;
            return java.util.regex.Pattern.compile(right).matcher(left).matches();
        }
        String left = resolveValue(ctx.valueExpr(0));
        String right = resolveValue(ctx.valueExpr(1));
        if (ctx.EQ() != null) {
            if (left == null && right == null) return true;
            if (left == null || right == null) return false;
            return left.equals(right);
        }
        if (ctx.NEQ() != null) {
            if (left == null && right == null) return false;
            if (left == null || right == null) return true;
            return !left.equals(right);
        }
        if (left == null || right == null) return false;
        if (ctx.GT() != null) return compareNumeric(left, right) > 0;
        if (ctx.LT() != null) return compareNumeric(left, right) < 0;
        if (ctx.GEQ() != null) return compareNumeric(left, right) >= 0;
        if (ctx.LEQ() != null) return compareNumeric(left, right) <= 0;
        return false;
    }

    private int compareNumeric(String left, String right) {
        try {
            double l = Double.parseDouble(left);
            double r = Double.parseDouble(right);
            return Double.compare(l, r);
        } catch (NumberFormatException e) {
            return left.compareTo(right);
        }
    }

    private String resolveValue(DslRuleConditionParser.ValueExprContext ctx) {
        if (ctx.NULL() != null) return null;
        if (ctx.elementAttr() != null) return resolveElementAttr(ctx.elementAttr());
        if (ctx.literal() != null) return resolveLiteral(ctx.literal());
        return null;
    }

    private String resolveElementAttr(DslRuleConditionParser.ElementAttrContext ctx) {
        if (ctx.ELEMENT_ATTRS_OPEN() != null) {
            String attrName = stripQuotes(ctx.STRING().getText());
            Map<String, String> attrs = context.getElementAttrs();
            if (attrs == null) return null;
            return attrs.get(attrName);
        }
        if (ctx.ELEMENT_TAG_NAME() != null) {
            return context.getElementName();
        }
        return null;
    }

    private String resolveLiteral(DslRuleConditionParser.LiteralContext ctx) {
        if (ctx.NUMBER() != null) return ctx.NUMBER().getText();
        if (ctx.STRING() != null) return stripQuotes(ctx.STRING().getText());
        return null;
    }

    private String stripQuotes(String text) {
        if (text != null && text.length() >= 2 && text.startsWith("'") && text.endsWith("'")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private Set<String> resolveSet(DslRuleConditionParser.SetLiteralContext ctx) {
        Set<String> result = new HashSet<>();
        for (DslRuleConditionParser.LiteralContext lit : ctx.literal()) {
            result.add(resolveLiteral(lit));
        }
        return result;
    }
}
