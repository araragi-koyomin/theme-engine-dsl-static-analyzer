package com.huawei.theme.analysis.core.syntaxanalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

/**
 * DSL结构语法检查器，产出SYN-001/003/004诊断。
 *
 * <p>仅负责纯语法层检查：根元素合法性(SYN-001)、未知元素标签(SYN-003)、未知属性名(SYN-004)。
 * 嵌套约束/必填缺失/类型/枚举等语义检查由M4 Analyzers承担：ParentChildAnalyzer(SEM-NEST-001)、
 * RequiredAttrAnalyzer(SEM-REQ-001)、LiteralTypeAnalyzer(SEM-TYPE-003)、EnumValueAnalyzer(SEM-ENUM-001)。</p>
 */
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

        if (!isRoot && !known) {
            diagnostics.add(diag("SYN-003", DiagnosticSeverity.ERROR,
                    "未知元素标签: " + tagName, filePath, element));
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
        if (element.getAttributes() == null) {
            return;
        }
        for (DslAttributeNode attr : element.getAttributes()) {
            String attrName = attr.getName();
            if (ruleRepository.resolveAttrAlias(tagName, attrName).isEmpty()) {
                diagnostics.add(diag("SYN-004", DiagnosticSeverity.WARNING,
                        "未知属性: " + attrName, filePath, attr));
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
