package com.huawei.theme.analysis.core.semanticanalysis.analyzers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

/**
 * 必填属性缺失分析器，规则SEM-REQ-001。
 *
 * <p>属性名先经resolveAttrAlias归一化为规范名，再与DslElementRule.requiredAttrs比对，
 * 缺失则报告错误。元素不在规则库中时跳过（未知元素由SYN-003处理）。</p>
 */
public class RequiredAttrAnalyzer extends BaseXmlAnalyzer {

    private static final String RULE_ID = "SEM-REQ-001";

    public RequiredAttrAnalyzer() {
        super(RULE_ID, DiagnosticSeverity.ERROR);
    }

    @Override
    protected List<Diagnostic> doAnalyze(DslElementNode elementNode, DslContext context) {
        RuleRepository ruleRepo = context.getRuleRepository();
        String tagName = elementNode.getTagName();
        Optional<DslElementRule> ruleOpt = ruleRepo.getElementRule(tagName);
        if (ruleOpt.isEmpty()) {
            return Collections.emptyList();
        }
        DslElementRule rule = ruleOpt.get();

        Set<String> presentCanonical = new HashSet<>();
        if (elementNode.getAttributes() != null) {
            for (DslAttributeNode attr : elementNode.getAttributes()) {
                ruleRepo.resolveAttrAlias(tagName, attr.getName()).ifPresent(presentCanonical::add);
            }
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        for (String required : rule.getRequiredAttrs()) {
            if (!presentCanonical.contains(required)) {
                diagnostics.add(createDiagnostic(context, elementNode, "缺失必填属性: " + required));
            }
        }
        return diagnostics;
    }
}
