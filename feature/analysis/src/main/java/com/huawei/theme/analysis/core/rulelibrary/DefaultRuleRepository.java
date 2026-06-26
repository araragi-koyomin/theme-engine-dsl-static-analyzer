package com.huawei.theme.analysis.core.rulelibrary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;

/**
 * RuleRepository的默认实现，使用Map作为内部存储提供O(1)查询性能。
 *
 * <p>构造函数接收三个Map参数（由JsonRuleLoader构建提供），实现职责分离：
 * DefaultRuleRepository只负责查询，JsonRuleLoader只负责加载。</p>
 *
 * <p>此设计支持：1）不同数据源（内置规则库vs --rule-dir外部规则库）；
 * 2）测试注入预构建Map（无需文件系统依赖）；3）未来Extension层缓存包装。</p>
 */
public class DefaultRuleRepository implements RuleRepository {
    /** 元素规则条目存储，key为elementName */
    private final Map<String, DslElementRule> elementRules;
    /** 全局变量条目存储，key为变量名 */
    private final Map<String, DslGlobalVar> globalVars;
    /** 规则来源追溯条目存储，key为ruleId */
    private final Map<String, RuleSource> ruleSources;
    /** 别名→规范名映射，key为"elementName.aliasName"，value为规范名 */
    private final Map<String, String> aliasToCanonicalMap;
    /** allowedChildren反向索引，key为父元素标签名，value为该父元素的合法子元素标签名列表。从所有规则的allowedParents反向推导构建 */
    private final Map<String, List<String>> childrenMap;

    /**
     * 构造DefaultRuleRepository，接收JsonRuleLoader构建的三个Map。
     *
     * @param elementRules 元素规则条目映射，key为elementName
     * @param globalVars 全局变量条目映射，key为变量名
     * @param ruleSources 规则来源追溯条目映射，key为ruleId
     */
    public DefaultRuleRepository(
            Map<String, DslElementRule> elementRules,
            Map<String, DslGlobalVar> globalVars,
            Map<String, RuleSource> ruleSources) {
        this.elementRules = elementRules;
        this.globalVars = globalVars;
        this.ruleSources = ruleSources;
        this.aliasToCanonicalMap = buildAliasMap(elementRules);
        this.childrenMap = buildChildrenMap(elementRules);
    }

    private static Map<String, String> buildAliasMap(Map<String, DslElementRule> elementRules) {
        Map<String, String> aliasMap = new HashMap<>();
        for (Map.Entry<String, DslElementRule> entry : elementRules.entrySet()) {
            String elementName = entry.getKey();
            DslElementRule rule = entry.getValue();
            for (Map.Entry<String, AttrTypeSpec> attrEntry : rule.getAttrTypes().entrySet()) {
                String canonicalName = attrEntry.getKey();
                List<String> aliases = attrEntry.getValue().getAliases();
                if (aliases != null) {
                    for (String alias : aliases) {
                        aliasMap.put(elementName + "." + alias, canonicalName);
                    }
                }
            }
        }
        return Collections.unmodifiableMap(aliasMap);
    }

    private static Map<String, List<String>> buildChildrenMap(Map<String, DslElementRule> elementRules) {
        Map<String, List<String>> map = new java.util.HashMap<>();
        for (DslElementRule rule : elementRules.values()) {
            for (String parent : rule.getAllowedParents()) {
                map.computeIfAbsent(parent, k -> new ArrayList<>()).add(rule.getElementName());
            }
        }
        Map<String, List<String>> immutableMap = new java.util.HashMap<>();
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            immutableMap.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(immutableMap);
    }

    @Override
    public Optional<DslElementRule> getElementRule(String elementName) {
        return Optional.ofNullable(elementRules.get(elementName));
    }

    @Override
    public List<DslElementRule> getAllElementRules() {
        return List.copyOf(elementRules.values());
    }

    @Override
    public List<String> getAllElementNames() {
        return List.copyOf(elementRules.keySet());
    }

    @Override
    public List<String> getRootElementNames() {
        return elementRules.values().stream()
                .filter(r -> r.getAllowedParents() == null || r.getAllowedParents().isEmpty())
                .map(DslElementRule::getElementName)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<AttrTypeSpec> getAttrTypeSpec(String elementName, String attrName) {
        String canonicalName = resolveAttrAlias(elementName, attrName).orElse(attrName);
        return getElementRule(elementName)
                .map(rule -> rule.getAttrTypes().get(canonicalName));
    }

    @Override
    public Optional<String> resolveAttrAlias(String elementName, String attrName) {
        DslElementRule rule = elementRules.get(elementName);
        if (rule == null) {
            return Optional.empty();
        }
        if (rule.getAttrTypes().containsKey(attrName)) {
            return Optional.of(attrName);
        }
        String canonical = aliasToCanonicalMap.get(elementName + "." + attrName);
        return Optional.ofNullable(canonical);
    }

    @Override
    public Set<String> getCanonicalAttrNames(String elementName) {
        DslElementRule rule = elementRules.get(elementName);
        if (rule == null) {
            return Collections.emptySet();
        }
        Set<String> names = new HashSet<>();
        if (rule.getRequiredAttrs() != null) {
            names.addAll(rule.getRequiredAttrs());
        }
        if (rule.getOptionalAttrs() != null) {
            names.addAll(rule.getOptionalAttrs());
        }
        return Collections.unmodifiableSet(names);
    }

    @Override
    public List<String> getAllowedParents(String elementName) {
        return getElementRule(elementName)
                .map(DslElementRule::getAllowedParents)
                .orElse(Collections.emptyList());
    }

    @Override
    public List<String> getAllowedChildren(String elementName) {
        return childrenMap.getOrDefault(elementName, Collections.emptyList());
    }

    @Override
    public List<RuleConstraint> getConstraints(String elementName) {
        return getElementRule(elementName)
                .map(DslElementRule::getConstraints)
                .orElse(Collections.emptyList());
    }

    @Override
    public Optional<DslGlobalVar> getGlobalVar(String varName) {
        return Optional.ofNullable(globalVars.get(varName));
    }

    @Override
    public List<DslGlobalVar> getAllGlobalVars() {
        return List.copyOf(globalVars.values());
    }

    @Override
    public Optional<RuleSource> getRuleSource(String ruleId) {
        return Optional.ofNullable(ruleSources.get(ruleId));
    }
}
