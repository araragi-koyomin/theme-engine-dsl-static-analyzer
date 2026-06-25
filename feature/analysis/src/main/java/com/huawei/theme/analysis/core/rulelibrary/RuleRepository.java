package com.huawei.theme.analysis.core.rulelibrary;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;

/**
 * 规则库查询接口，M2的核心对外契约。
 *
 * <p>所有消费方（M1/M3/M4/M5/M7）依赖此接口而非实现，遵循依赖倒置原则。
 * 此设计带来三个实际好处：</p>
 * <ul>
 *   <li>测试可替换：M4单元测试可注入MockRuleRepository，不需要加载真实JSON文件</li>
 *   <li>多数据源：CLI --rule-dir指定外部目录时，JsonRuleLoader从不同目录加载，接口不变</li>
 *   <li>缓存包装：Extension层RuleCacheManager可包装DefaultRuleRepository，对外仍是此接口</li>
 * </ul>
 *
 * <p>单元素查询方法返回Optional<T>而非null，让"不存在"成为显式、类型安全的结果，
 * 避免消费方if-null检查。集合查询方法返回不可变List。</p>
 */
public interface RuleRepository {
    /**
     * 查询指定元素的规则条目。
     *
     * @param elementName 元素标签名，如"Var"/"VideoCommand"
     * @return Optional包含对应规则条目，不存在时返回Optional.empty()
     */
    Optional<DslElementRule> getElementRule(String elementName);

    /**
     * 获取所有元素规则条目列表。
     *
     * @return 不可变的全部规则条目列表
     */
    List<DslElementRule> getAllElementRules();

    /**
     * 获取所有元素标签名列表。
     *
     * @return 不可变的全部标签名列表，M3 SYN-003未知元素检测使用此集合比对
     */
    List<String> getAllElementNames();

    /**
     * 获取合法根元素标签名列表。
     *
     * <p>通过遍历所有规则条目，筛选category="root"的元素得出。
     * 不硬编码["Lockscreen","Wallpaper","Widget","ChargingSkin"]，
     * 以支持--rule-dir新增自定义根元素时代码自动感知。</p>
     *
     * @return 根元素标签名列表，M1文件识别使用此集合做双重识别的根元素标签匹配
     */
    List<String> getRootElementNames();

    /**
     * 查询指定元素指定属性的类型规范。
     *
     * <p>自动处理别名：当attrName是别名（如"h"）时，内部resolve到规范名（如"height"）
     * 后返回规范名的AttrTypeSpec。消费方无需关心attrName是规范名还是别名。</p>
     *
     * @param elementName 元素标签名
     * @param attrName 属性名，可以是规范名或别名（如"width"/"w"均可）
     * @return Optional包含AttrTypeSpec，属性不存在时返回Optional.empty()
     */
    Optional<AttrTypeSpec> getAttrTypeSpec(String elementName, String attrName);

    /**
     * 将属性别名resolve为规范名。
     *
     * <p>当attrName本身就是规范名时直接返回attrName。
     * 当attrName是别名时，查找该元素中aliases字段包含attrName的规范名。
     * 用于M3属性名规范化、M5 QuickFix别名替换建议。</p>
     *
     * @param elementName 元素标签名
     * @param attrName 属性名（规范名或别名）
     * @return 规范名，不存在时返回Optional.empty()
     */
    Optional<String> resolveAttrAlias(String elementName, String attrName);

    /**
     * 获取指定元素的规范属性名集合（不含别名）。
     *
     * <p>返回requiredAttrs + optionalAttrs的合并集合，全部为规范名。
     * M3 SYN-004未知属性检测使用此集合：先resolveAttrAlias，再比对规范名集合。</p>
     *
     * @param elementName 元素标签名
     * @return 规范属性名集合，元素不存在时返回空Set
     */
    Set<String> getCanonicalAttrNames(String elementName);

    /**
     * 获取指定元素的合法父元素标签名列表。
     *
     * @param elementName 元素标签名，不存在时返回空列表
     * @return 允许的父元素标签名列表
     */
    List<String> getAllowedParents(String elementName);

    /**
     * 获取指定元素的合法子元素标签名列表。
     *
     * @param elementName 元素标签名，不存在时返回空列表
     * @return 允许的子元素标签名列表
     */
    List<String> getAllowedChildren(String elementName);

    /**
     * 获取指定元素的声明式约束条件列表。
     *
     * @param elementName 元素标签名，不存在时返回空列表
     * @return 约束条件列表，M4 ConstraintAnalyzer消费
     */
    List<RuleConstraint> getConstraints(String elementName);

    /**
     * 查询指定全局变量条目。
     *
     * @param varName 变量名，如"battery_level"
     * @return Optional包含全局变量条目，不存在时返回Optional.empty()
     */
    Optional<DslGlobalVar> getGlobalVar(String varName);

    /**
     * 获取全部全局变量条目列表。
     *
     * @return 不可变的全部全局变量列表，M4 SymbolTableBuilder使用此列表构建符号表
     */
    List<DslGlobalVar> getAllGlobalVars();

    /**
     * 查询指定规则的来源追溯条目。
     *
     * @param ruleId 规则ID，如"SEM-CMD-001"
     * @return Optional包含RuleSource，不存在时返回Optional.empty()
     */
    Optional<RuleSource> getRuleSource(String ruleId);
}
