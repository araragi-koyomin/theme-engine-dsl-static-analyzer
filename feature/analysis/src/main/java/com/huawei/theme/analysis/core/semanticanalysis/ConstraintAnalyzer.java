package com.huawei.theme.analysis.core.semanticanalysis;

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
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

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
        String filePath = context.getFilePath();

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
                diagnostics.add(buildDiagnostic(constraint, elementNode, filePath, ruleRepo));
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
                .build();
    }

    private Diagnostic buildDiagnostic(RuleConstraint constraint, DslElementNode elementNode, String filePath, RuleRepository ruleRepo) {
        String ruleDocUrl = null;
        Optional<RuleSource> ruleSourceOpt = ruleRepo.getRuleSource(constraint.getRuleId());
        if (ruleSourceOpt.isPresent()) {
            ruleDocUrl = ruleSourceOpt.get().getDocUrl();
        }

        return Diagnostic.builder()
                .severity(constraint.getSeverity())
                .ruleId(constraint.getRuleId())
                .message(constraint.getMessage())
                .filePath(filePath)
                .line(elementNode.getLine())
                .column(elementNode.getColumn())
                .suggestedFixes(constraint.getSuggestedFixes())
                .ruleDocUrl(ruleDocUrl)
                .build();
    }
}
