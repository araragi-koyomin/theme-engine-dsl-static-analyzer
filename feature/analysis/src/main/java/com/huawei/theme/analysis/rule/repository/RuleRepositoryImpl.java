package com.huawei.theme.analysis.rule.repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.huawei.theme.analysis.rule.loader.JsonRuleLoader;
import com.huawei.theme.analysis.rule.model.AttrTypeSpec;
import com.huawei.theme.analysis.rule.model.DslElementRule;
import com.huawei.theme.analysis.rule.model.RuleSource;

/**
 * 默认规则仓库实现。
 *
 * 该实现将从 JSON 规则源加载 DSL 元素规则和规则来源，
 * 并提供按元素名、属性名、父子关系等方式查询的能力。
 */
public class RuleRepositoryImpl implements RuleRepository {

    /** 元素名 -> 元素规则 映射 */
    private final Map<String, DslElementRule> elementRuleMap;

    /** 规则 id -> 规则来源 映射 */
    private final Map<String, RuleSource> ruleSourceMap;

    /**
     * 通过 JSON 规则加载器和资源路径构造仓库。
     *
     * @param loader JSON 规则加载器
     * @param resourcePath JSON 规则资源路径
     */
    public RuleRepositoryImpl(JsonRuleLoader loader, String resourcePath) {
        this.elementRuleMap = loader.buildElementRuleMap(resourcePath);
        this.ruleSourceMap = loader.buildRuleSourceMap(resourcePath);
    }

    /**
     * 直接使用已有映射构造仓库，通常用于测试或缓存场景。
     *
     * @param elementRuleMap 元素规则映射
     * @param ruleSourceMap 规则来源映射
     */
    public RuleRepositoryImpl(Map<String, DslElementRule> elementRuleMap, Map<String, RuleSource> ruleSourceMap) {
        this.elementRuleMap = elementRuleMap;
        this.ruleSourceMap = ruleSourceMap;
    }

    /**
     * 根据元素名查找 DSL 元素规则。
     *
     * @param elementName DSL 元素名称
     * @return 目标元素规则的 Optional，如果未找到则为空
     */
    @Override
    public Optional<DslElementRule> getElementRule(String elementName) {
        return Optional.ofNullable(elementRuleMap.get(elementName));
    }

    /**
     * 获取所有已加载的 DSL 元素规则。
     *
     * @return 所有元素规则列表（不可修改）
     */
    @Override
    public List<DslElementRule> getAllElementRules() {
        return List.copyOf(elementRuleMap.values());
    }

    /**
     * 获取所有 DSL 元素名称。
     *
     * @return 元素名称列表（不可修改）
     */
    @Override
    public List<String> getAllElementNames() {
        return List.copyOf(elementRuleMap.keySet());
    }

    /**
     * 获取顶层根元素名称列表。
     *
     * 根元素定义为 allowedParents 不为 null 且为空的元素。
     *
     * @return 根元素名称列表
     */
    @Override
    public List<String> getRootElementNames() {
        return elementRuleMap.values().stream()
                .filter(rule -> rule.getAllowedParents() != null && rule.getAllowedParents().isEmpty())
                .map(DslElementRule::getElementName)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定元素、指定属性的属性类型规范。
     *
     * @param elementName 元素名称
     * @param attrName 属性名称
     * @return 属性类型规范，若元素或属性不存在则返回空
     */
    @Override
    public Optional<AttrTypeSpec> getAttrTypeSpec(String elementName, String attrName) {
        DslElementRule rule = elementRuleMap.get(elementName);
        if (rule == null || rule.getAttrTypes() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(rule.getAttrTypes().get(attrName));
    }

    /**
     * 获取指定元素允许的父元素列表。
     *
     * @param elementName 元素名称
     * @return 允许的父元素列表，元素不存在时返回空列表
     */
    @Override
    public List<String> getAllowedParents(String elementName) {
        DslElementRule rule = elementRuleMap.get(elementName);
        if (rule == null || rule.getAllowedParents() == null) {
            return Collections.emptyList();
        }
        return rule.getAllowedParents();
    }

    /**
     * 获取指定元素允许的子元素列表。
     *
     * @param elementName 元素名称
     * @return 允许的子元素列表，元素不存在时返回空列表
     */
    @Override
    public List<String> getAllowedChildren(String elementName) {
        DslElementRule rule = elementRuleMap.get(elementName);
        if (rule == null || rule.getAllowedChildren() == null) {
            return Collections.emptyList();
        }
        return rule.getAllowedChildren();
    }

    /**
     * 根据规则 id 查找规则来源信息。
     *
     * @param ruleId 规则唯一标识
     * @return 规则来源，若不存在则返回空
     */
    @Override
    public Optional<RuleSource> getRuleSource(String ruleId) {
        return Optional.ofNullable(ruleSourceMap.get(ruleId));
    }
}
