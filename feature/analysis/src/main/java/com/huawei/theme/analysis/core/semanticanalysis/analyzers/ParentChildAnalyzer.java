package com.huawei.theme.analysis.core.semanticanalysis.analyzers;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

/**
 * 父子嵌套约束分析器，规则SEM-NEST-001。
 *
 * <p>元素的直接父元素必须位于M2规则库的allowedParents列表中。
 * allowedParents为空表示元素为根元素，跳过检查。
 * 根元素的parent指针指向DslFileNode而非DslElementNode，故通过instanceof
 * 判定父元素标签名；父元素缺失或不在允许列表则报告错误。</p>
 */
public class ParentChildAnalyzer extends BaseXmlAnalyzer {

    private static final String RULE_ID = "SEM-NEST-001";

    public ParentChildAnalyzer() {
        super(RULE_ID, DiagnosticSeverity.ERROR);
    }

    @Override
    protected List<Diagnostic> doAnalyze(DslElementNode elementNode, DslContext context) {
        RuleRepository ruleRepo = context.getRuleRepository();
        String tagName = elementNode.getTagName();
        List<String> allowedParents = ruleRepo.getAllowedParents(tagName);
        if (allowedParents.isEmpty()) {
            return Collections.emptyList();
        }

        DslAstNode parentNode = elementNode.getParent();
        String parentTagName = null;
        if (parentNode instanceof DslElementNode parentElement) {
            parentTagName = parentElement.getTagName();
        }

        if (parentTagName != null && allowedParents.contains(parentTagName)) {
            return Collections.emptyList();
        }

        if (!isAllowedInScope(elementNode, context)) {
            java.util.Set<String> rootNames = new java.util.HashSet<>(ruleRepo.getRootElementNames());
            for (String allowedParent : allowedParents) {
                if (rootNames.contains(allowedParent)) {
                    return Collections.emptyList();
                }
            }
        }

        if (parentTagName != null
                && context.getRootNode() != null
                && context.getRootNode().getRootElement() != null
                && !allowedParents.contains(parentTagName)) {
            java.util.Set<String> rootNames = new java.util.HashSet<>(ruleRepo.getRootElementNames());
            if (rootNames.containsAll(allowedParents)
                    && rootNames.size() == allowedParents.size()) {
                return Collections.emptyList();
            }
        }

        String allowed = allowedParents.stream().collect(Collectors.joining(", "));
        String message;
        if (parentTagName == null) {
            message = "标签嵌套违反父子约束：'" + tagName + "'缺少父元素，合法父元素为[" + allowed + "]";
        } else {
            message = "标签嵌套违反父子约束：'" + tagName + "'的父元素'" + parentTagName
                    + "'不在允许列表[" + allowed + "]中";
        }
        return List.of(createDiagnostic(context, elementNode, message));
    }

    private boolean isAllowedInScope(DslElementNode elementNode, DslContext context) {
        RuleRepository ruleRepo = context.getRuleRepository();
        String tagName = elementNode.getTagName();
        java.util.Optional<com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule> ruleOpt =
                ruleRepo.getElementRule(tagName);
        if (ruleOpt.isEmpty()) {
            return true;
        }
        java.util.Map<String, Boolean> scope = ruleOpt.get().getScope();
        if (scope == null || scope.isEmpty()) {
            return true;
        }
        if (context.getRootNode() == null || context.getRootNode().getRootElement() == null) {
            return true;
        }
        String currentScope = context.getRootNode().getRootElement().getTagName();
        return Boolean.TRUE.equals(scope.get(currentScope));
    }
}
