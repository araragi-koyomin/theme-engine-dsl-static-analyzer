package com.huawei.theme.analysis.core.syntaxanalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.huawei.theme.analysis.core.expression.ExpressionParser;
import com.huawei.theme.analysis.core.expression.ExpressionNode;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

public class ExpressionSyntaxChecker {

    private static final Pattern PRECISEEVAL_SUFFIX =
            Pattern.compile("preciseeval\\s*\\([^)]*\\)\\s*[+\\-*/%]");

    private final RuleRepository ruleRepository;

    public ExpressionSyntaxChecker(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public List<Diagnostic> check(String filePath, DslFileNode fileNode) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        DslElementNode root = fileNode.getRootElement();
        if (root == null || root.isHasError()) {
            return diagnostics;
        }
        walk(filePath, root, diagnostics);
        return diagnostics;
    }

    private void walk(String filePath, DslElementNode element, List<Diagnostic> diagnostics) {
        if (element.getAttributes() != null) {
            for (DslAttributeNode attr : element.getAttributes()) {
                checkAttr(filePath, element.getTagName(), attr, diagnostics);
            }
        }
        if (element.getChildElements() != null) {
            for (DslElementNode child : element.getChildElements()) {
                walk(filePath, child, diagnostics);
            }
        }
    }

    private void checkAttr(String filePath, String tagName, DslAttributeNode attr, List<Diagnostic> diagnostics) {
        Optional<AttrTypeSpec> specOpt = ruleRepository.getAttrTypeSpec(tagName, attr.getName());
        if (specOpt.isEmpty() || !specOpt.get().isSupportsExpression()) {
            return;
        }
        String expressionKind = specOpt.get().getExpressionKind();
        DslAttributeValueNode value = attr.getValue();
        if (value == null || value.getRawValue() == null) {
            return;
        }
        String rawValue = value.getRawValue();
        if (!ExpressionParser.hasExpressionSyntax(rawValue, attr.getName())) {
            return;
        }

        ExpressionParser.ParseOutcome outcome = ExpressionParser.doParse(rawValue, expressionKind);
        boolean isString = "string".equals(expressionKind);
        boolean parseFailed = outcome.antlrError() || outcome.leftoverTokens() || outcome.node() == null;

        if (outcome.node() != null) {
            if (ExpressionParser.containsInvalidUnaryMinusVar(outcome.node())) {
                diagnostics.add(diag("SYN-EXPR-001", DiagnosticSeverity.ERROR,
                        "数值表达式使用 -#var 语法: " + rawValue, filePath, attr));
            }
            checkPrecision(outcome.node(), filePath, attr, rawValue, diagnostics);
        }

        if (isString && rawValue.startsWith("#")
                && (rawValue.indexOf('*') >= 0 || rawValue.indexOf('/') >= 0
                        || rawValue.indexOf('%') >= 0 || rawValue.indexOf('-') >= 0)) {
            diagnostics.add(diag("SYN-EXPR-003", DiagnosticSeverity.ERROR,
                    "字符串表达式中数值计算以#开头: " + rawValue, filePath, attr));
        }

        Matcher m = PRECISEEVAL_SUFFIX.matcher(rawValue);
        if (m.find()) {
            diagnostics.add(diag("SYN-EXPR-006", DiagnosticSeverity.ERROR,
                    "preciseeval 后使用运算符或+连接符: " + rawValue, filePath, attr));
        }

        if (parseFailed && isString) {
            if (hasBareWordInConcat(rawValue)) {
                diagnostics.add(diag("SYN-EXPR-004", DiagnosticSeverity.ERROR,
                        "字符串表达式未使用单引号: " + rawValue, filePath, attr));
            } else if (hasMissingBraces(rawValue)) {
                diagnostics.add(diag("SYN-EXPR-005", DiagnosticSeverity.ERROR,
                        "字符串表达式嵌入数值表达式缺少花括号: " + rawValue, filePath, attr));
            } else {
                diagnostics.add(diag("SYN-EXPR-ANTLR", DiagnosticSeverity.ERROR,
                        "表达式语法错误: " + rawValue, filePath, attr));
            }
        } else if (parseFailed) {
            diagnostics.add(diag("SYN-EXPR-ANTLR", DiagnosticSeverity.ERROR,
                    "表达式语法错误: " + rawValue, filePath, attr));
        }
    }

    private void checkPrecision(ExpressionNode node, String filePath, DslAttributeNode attr,
            String rawValue, List<Diagnostic> diagnostics) {
        if (node == null) {
            return;
        }
        if (node.getKind() == ExpressionKind.LITERAL && node.getLiteralValue() != null) {
            String lv = node.getLiteralValue();
            try {
                Double.parseDouble(lv);
                int digits = 0;
                for (int i = 0; i < lv.length(); i++) {
                    if (Character.isDigit(lv.charAt(i))) {
                        digits++;
                    }
                }
                if (digits > 7) {
                    diagnostics.add(diag("SYN-EXPR-002", DiagnosticSeverity.WARNING,
                            "数值表达式值超过7位精度限制: " + lv, filePath, attr));
                }
            } catch (NumberFormatException ignored) {
                // not a numeric literal
            }
        }
        if (node.getChildren() != null) {
            for (ExpressionNode c : node.getChildren()) {
                checkPrecision(c, filePath, attr, rawValue, diagnostics);
            }
        }
        if (node.getIndexExpression() != null) {
            checkPrecision(node.getIndexExpression(), filePath, attr, rawValue, diagnostics);
        }
    }

    private static boolean hasBareWordInConcat(String rawValue) {
        String[] terms = rawValue.split("\\+");
        for (String term : terms) {
            String t = term.trim();
            if (t.matches("^[a-zA-Z_]\\w*$")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMissingBraces(String rawValue) {
        String withoutBraces = rawValue.replaceAll("\\{[^}]*}", "");
        return withoutBraces.indexOf('+') >= 0
                && (withoutBraces.indexOf('*') >= 0 || withoutBraces.indexOf('/') >= 0
                        || withoutBraces.indexOf('%') >= 0);
    }

    private Diagnostic diag(String ruleId, DiagnosticSeverity severity, String message,
            String filePath, DslAttributeNode attr) {
        Diagnostic.DiagnosticBuilder b = Diagnostic.builder()
                .severity(severity)
                .ruleId(ruleId)
                .message(message)
                .filePath(filePath)
                .astNode(attr);
        Optional<RuleSource> src = ruleRepository.getRuleSource(ruleId);
        if (src.isPresent()) {
            b.ruleDocUrl(src.get().getDocUrl());
        }
        return b.build();
    }
}
