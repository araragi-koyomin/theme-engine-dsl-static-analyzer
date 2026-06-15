package com.huawei.theme.analysis.rule.repository;

import java.util.List;
import java.util.Optional;

import com.huawei.theme.analysis.rule.model.AttrTypeSpec;
import com.huawei.theme.analysis.rule.model.DslElementRule;
import com.huawei.theme.analysis.rule.model.RuleSource;

public interface RuleRepository {
    Optional<DslElementRule> getElementRule(String elementName);
    List<DslElementRule> getAllElementRules();
    List<String> getAllElementNames();
    List<String> getRootElementNames();
    Optional<AttrTypeSpec> getAttrTypeSpec(String elementName, String attrName);
    List<String> getAllowedParents(String elementName);
    List<String> getAllowedChildren(String elementName);
    Optional<RuleSource> getRuleSource(String ruleId);
}
