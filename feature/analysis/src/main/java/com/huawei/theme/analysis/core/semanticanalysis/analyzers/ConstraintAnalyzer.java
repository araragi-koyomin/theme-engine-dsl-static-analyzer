package com.huawei.theme.analysis.core.semanticanalysis.analyzers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.huawei.theme.analysis.core.ruledsl.DefaultRuleDslEvaluator;
import com.huawei.theme.analysis.core.ruledsl.EvaluationContext;
import com.huawei.theme.analysis.core.ruledsl.RuleDslEvaluator;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;
import com.huawei.theme.analysis.core.semanticanalysis.DslAnalyzer;
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

public class ConstraintAnalyzer implements DslAnalyzer {

    private final RuleDslEvaluator evaluator;

    public ConstraintAnalyzer() {
        this.evaluator = new DefaultRuleDslEvaluator();
    }

    public ConstraintAnalyzer(RuleDslEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public List<Diagnostic> analyze(DslAstNode element, DslContext context) {
        if (!(element instanceof DslElementNode elementNode)) {
            return Collections.emptyList();
        }

        String tagName = elementNode.getTagName();
        RuleRepository ruleRepo = context.getRuleRepository();

        List<RuleConstraint> constraints = ruleRepo.getConstraints(tagName);
        if (constraints.isEmpty()) {
            return Collections.emptyList();
        }

        Optional<DslElementRule> elementRuleOpt = ruleRepo.getElementRule(tagName);

        EvaluationContext evalContext = buildEvaluationContext(elementNode, elementRuleOpt);

        List<Diagnostic> diagnostics = new ArrayList<>();
        for (RuleConstraint constraint : constraints) {
            boolean violated = evaluator.evaluate(constraint.getCondition(), evalContext);
            if (violated) {
                diagnostics.add(buildDiagnostic(constraint, elementNode, context));
            }
        }

        return diagnostics;
    }

    private EvaluationContext buildEvaluationContext(DslElementNode elementNode, Optional<DslElementRule> elementRuleOpt) {
        Map<String, String> elementAttrs = new HashMap<>();
        for (DslAttributeNode attr : elementNode.getAttributes()) {
            DslAttributeValueNode valueNode = attr.getValue();
            elementAttrs.put(attr.getName(), valueNode != null ? valueNode.getRawValue() : null);
        }

        String elementName = elementNode.getTagName();
        String elementCategory = null;
        Map<String, Boolean> scope = null;
        Map<String, Boolean> deviceSupport = null;
        String parentTagName = null;
        if (elementNode.getParent() instanceof DslElementNode parentElement) {
            parentTagName = parentElement.getTagName();
        }

        if (elementRuleOpt.isPresent()) {
            DslElementRule elementRule = elementRuleOpt.get();
            elementCategory = elementRule.getCategory();
            scope = elementRule.getScope();
            deviceSupport = elementRule.getDeviceSupport();
        }

        return EvaluationContext.builder()
                .elementAttrs(elementAttrs)
                .elementName(elementName)
                .elementCategory(elementCategory)
                .scope(scope)
                .deviceSupport(deviceSupport)
                .parentTagName(parentTagName)
                .childElements(buildChildElementInfos(elementNode))
                .build();
    }

    private List<Map<String, Object>> buildChildElementInfos(DslElementNode elementNode) {
        List<Map<String, Object>> infos = new ArrayList<>();
        if (elementNode.getChildElements() != null) {
            for (DslElementNode child : elementNode.getChildElements()) {
                Map<String, Object> childInfo = new HashMap<>();
                childInfo.put("tagName", child.getTagName());
                Map<String, String> childAttrs = new HashMap<>();
                if (child.getAttributes() != null) {
                    for (DslAttributeNode attr : child.getAttributes()) {
                        DslAttributeValueNode valueNode = attr.getValue();
                        childAttrs.put(attr.getName(), valueNode != null ? valueNode.getRawValue() : null);
                    }
                }
                childInfo.put("attrs", childAttrs);
                infos.add(childInfo);
            }
        }
        return infos;
    }

    private Diagnostic buildDiagnostic(RuleConstraint constraint, DslElementNode elementNode, DslContext context) {
        String ruleDocUrl = null;
        RuleRepository ruleRepo = context.getRuleRepository();
        if (ruleRepo != null) {
            Optional<RuleSource> ruleSourceOpt = ruleRepo.getRuleSource(constraint.getRuleId());
            if (ruleSourceOpt.isPresent()) {
                ruleDocUrl = ruleSourceOpt.get().getDocUrl();
            }
        }

        return Diagnostic.builder()
                .severity(constraint.getSeverity())
                .ruleId(constraint.getRuleId())
                .message(constraint.getMessage())
                .filePath(context.getFilePath())
                .astNode(elementNode)
                .suggestedFixes(constraint.getSuggestedFixes())
                .ruleDocUrl(ruleDocUrl)
                .build();
    }

    private static boolean isNumeric(String value) {
        if (value == null) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
