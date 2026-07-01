package com.huawei.theme.analysis.core.syntaxanalysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

public class SyntaxChecker {

    private final RuleRepository ruleRepository;

    public SyntaxChecker(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public List<Diagnostic> check(String filePath, DslFileNode fileNode) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        DslElementNode root = fileNode.getRootElement();
        if (root == null || root.isHasError()) {
            return diagnostics;
        }
        checkRoot(filePath, root, diagnostics);
        walk(filePath, root, true, diagnostics);
        return diagnostics;
    }

    private void checkRoot(String filePath, DslElementNode root, List<Diagnostic> diagnostics) {
        if (!ruleRepository.getRootElementNames().contains(root.getTagName())) {
            diagnostics.add(diag("SYN-001", DiagnosticSeverity.ERROR,
                    "根元素标签错误: " + root.getTagName(), filePath, root));
        }
    }

    private void walk(String filePath, DslElementNode element, boolean isRoot, List<Diagnostic> diagnostics) {
        String tagName = element.getTagName();
        boolean known = ruleRepository.getAllElementNames().contains(tagName);

        if (!isRoot) {
            if (!known) {
                diagnostics.add(diag("SYN-003", DiagnosticSeverity.ERROR,
                        "未知元素标签: " + tagName, filePath, element));
            } else if (element.getParent() instanceof DslElementNode parent) {
                String parentTag = parent.getTagName();
                if (ruleRepository.getAllElementNames().contains(parentTag)
                        && !ruleRepository.getAllowedParents(tagName).contains(parentTag)) {
                    diagnostics.add(diag("SYN-002", DiagnosticSeverity.ERROR,
                            "标签嵌套违反父子约束: " + tagName + " 不允许作为 " + parentTag + " 的子元素",
                            filePath, element));
                }
            }
        }

        if (known) {
            checkAttributes(filePath, element, diagnostics);
        }

        if (element.getChildElements() != null) {
            for (DslElementNode child : element.getChildElements()) {
                walk(filePath, child, false, diagnostics);
            }
        }
    }

    private void checkAttributes(String filePath, DslElementNode element, List<Diagnostic> diagnostics) {
        String tagName = element.getTagName();
        Optional<DslElementRule> ruleOpt = ruleRepository.getElementRule(tagName);
        if (ruleOpt.isEmpty()) {
            return;
        }
        DslElementRule rule = ruleOpt.get();

        Set<String> presentCanonical = new HashSet<>();
        if (element.getAttributes() != null) {
            for (DslAttributeNode attr : element.getAttributes()) {
                String attrName = attr.getName();
                Optional<String> canonical = ruleRepository.resolveAttrAlias(tagName, attrName);
                if (canonical.isEmpty()) {
                    diagnostics.add(diag("SYN-004", DiagnosticSeverity.WARNING,
                            "未知属性: " + attrName, filePath, attr));
                } else {
                    presentCanonical.add(canonical.get());
                }
                checkAttrValue(filePath, tagName, attrName, attr, diagnostics);
            }
        }

        for (String required : rule.getRequiredAttrs()) {
            if (!presentCanonical.contains(required)) {
                diagnostics.add(diag("SYN-005", DiagnosticSeverity.ERROR,
                        "缺失必填属性: " + required, filePath, element));
            }
        }
    }

    private void checkAttrValue(String filePath, String tagName, String attrName,
            DslAttributeNode attr, List<Diagnostic> diagnostics) {
        Optional<AttrTypeSpec> specOpt = ruleRepository.getAttrTypeSpec(tagName, attrName);
        if (specOpt.isEmpty()) {
            return;
        }
        AttrTypeSpec spec = specOpt.get();
        DslAttributeValueNode value = attr.getValue();
        if (value == null || !value.isLiteral()) {
            return;
        }
        String rawValue = value.getRawValue();
        if (rawValue == null) {
            return;
        }

        if ("number".equals(spec.getType())) {
            try {
                Double.parseDouble(rawValue);
            } catch (NumberFormatException e) {
                diagnostics.add(diag("SYN-006", DiagnosticSeverity.ERROR,
                        "属性值类型错误: " + attrName + " 期望 number, 实际 " + rawValue,
                        filePath, attr));
            }
        }

        if (spec.getEnumValues() != null && !spec.getEnumValues().isEmpty()) {
            if (!spec.getEnumValues().contains(rawValue)) {
                diagnostics.add(diag("SYN-007", DiagnosticSeverity.ERROR,
                        "枚举值错误: " + attrName + "=" + rawValue + ", 合法值: " + spec.getEnumValues(),
                        filePath, attr));
            }
        }
    }

    private Diagnostic diag(String ruleId, DiagnosticSeverity severity, String message,
            String filePath, DslAstNode node) {
        Diagnostic.DiagnosticBuilder b = Diagnostic.builder()
                .severity(severity)
                .ruleId(ruleId)
                .message(message)
                .filePath(filePath)
                .line(node.getLine())
                .column(node.getColumn());
        Optional<RuleSource> src = ruleRepository.getRuleSource(ruleId);
        if (src.isPresent()) {
            b.ruleDocUrl(src.get().getDocUrl());
        }
        return b.build();
    }
}
