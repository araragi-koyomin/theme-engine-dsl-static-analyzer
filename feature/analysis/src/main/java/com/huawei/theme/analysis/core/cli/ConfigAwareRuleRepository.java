package com.huawei.theme.analysis.core.cli;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

public class ConfigAwareRuleRepository implements RuleRepository {

    private final RuleRepository delegate;
    private final InspectionConfig config;

    public ConfigAwareRuleRepository(RuleRepository delegate, InspectionConfig config) {
        this.delegate = delegate;
        this.config = config;
    }

    @Override
    public List<String> getRootElementNames() {
        if (config.getRootElementNames() != null && !config.getRootElementNames().isEmpty()) {
            return config.getRootElementNames();
        }
        return delegate.getRootElementNames();
    }

    @Override
    public Optional<DslElementRule> getElementRule(String elementName) {
        return delegate.getElementRule(elementName)
                .map(this::filterElementRule);
    }

    @Override
    public List<DslElementRule> getAllElementRules() {
        return delegate.getAllElementRules().stream()
                .map(this::filterElementRule)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getAllElementNames() {
        return delegate.getAllElementNames();
    }

    @Override
    public List<RuleConstraint> getConstraints(String elementName) {
        List<RuleConstraint> constraints = delegate.getConstraints(elementName);
        return filterConstraints(constraints);
    }

    @Override
    public Optional<AttrTypeSpec> getAttrTypeSpec(String elementName, String attrName) {
        return delegate.getAttrTypeSpec(elementName, attrName);
    }

    @Override
    public Optional<String> resolveAttrAlias(String elementName, String attrName) {
        return delegate.resolveAttrAlias(elementName, attrName);
    }

    @Override
    public Set<String> getCanonicalAttrNames(String elementName) {
        return delegate.getCanonicalAttrNames(elementName);
    }

    @Override
    public List<String> getAllowedParents(String elementName) {
        return delegate.getAllowedParents(elementName);
    }

    @Override
    public List<String> getAllowedChildren(String elementName) {
        return delegate.getAllowedChildren(elementName);
    }

    @Override
    public Optional<DslGlobalVar> getGlobalVar(String varName) {
        return delegate.getGlobalVar(varName);
    }

    @Override
    public List<DslGlobalVar> getAllGlobalVars() {
        return delegate.getAllGlobalVars();
    }

    @Override
    public Optional<RuleSource> getRuleSource(String ruleId) {
        return delegate.getRuleSource(ruleId);
    }

    private DslElementRule filterElementRule(DslElementRule original) {
        List<RuleConstraint> filtered = filterConstraints(original.getConstraints());
        List<RuleConstraint> overridden = overrideSeverity(filtered);
        return DslElementRule.builder()
                .elementName(original.getElementName())
                .category(original.getCategory())
                .requiredAttrs(original.getRequiredAttrs())
                .optionalAttrs(original.getOptionalAttrs())
                .attrTypes(original.getAttrTypes())
                .allowedParents(original.getAllowedParents())
                .inherits(original.getInherits())
                .scope(original.getScope())
                .deviceSupport(original.getDeviceSupport())
                .constraints(overridden)
                .build();
    }

    private List<RuleConstraint> filterConstraints(List<RuleConstraint> constraints) {
        if (config.getEnabledRuleIds() != null && !config.getEnabledRuleIds().isEmpty()) {
            Set<String> enabled = Set.copyOf(config.getEnabledRuleIds());
            return constraints.stream()
                    .filter(c -> c.getRuleId() != null && enabled.contains(c.getRuleId()))
                    .collect(Collectors.toList());
        }
        if (config.getDisabledRuleIds() != null && !config.getDisabledRuleIds().isEmpty()) {
            Set<String> disabled = Set.copyOf(config.getDisabledRuleIds());
            return constraints.stream()
                    .filter(c -> c.getRuleId() == null || !disabled.contains(c.getRuleId()))
                    .collect(Collectors.toList());
        }
        return constraints;
    }

    private List<RuleConstraint> overrideSeverity(List<RuleConstraint> constraints) {
        if (config.getSeverityOverrides() == null || config.getSeverityOverrides().isEmpty()) {
            return constraints;
        }
        return constraints.stream()
                .map(c -> {
                    DiagnosticSeverity override = config.getSeverityOverrides().get(c.getRuleId());
                    if (override != null) {
                        return RuleConstraint.builder()
                                .ruleId(c.getRuleId())
                                .condition(c.getCondition())
                                .message(c.getMessage())
                                .severity(override)
                                .suggestedFixes(c.getSuggestedFixes())
                                .build();
                    }
                    return c;
                })
                .collect(Collectors.toList());
    }
}
