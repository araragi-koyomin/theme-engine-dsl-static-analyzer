package com.huawei.theme.analysis.core.rulelibrary.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;

/**
 * DSL元素/命令规则条目，定义一个XML标签的完整约束规范。
 *
 * <p>每个DSL XML标签对应一条DslElementRule，是M2规则库的核心数据模型。
 * 视图元素(Text/Image/Video)和命令元素(VideoCommand/SoundCommand)共用此模型，
 * 仅通过category字段区分。</p>
 *
 * <p>消费方：M3语法分析（属性合法性+嵌套约束比对）、M4语义分析（scope+类型+约束检查）、
 * M5修复逻辑（修复建议数据）、M7批量检查（全量规则扫描）。</p>
 */
@Data
@Builder
public class DslElementRule {
    /** 元素标签名，如"Var"/"VideoCommand"/"Lockscreen"。作为规则库的主键，M3通过tagName查询 */
    @SerializedName("element")
    String elementName;
    /** 元素分类标签，已知分类：root/view/layout/variable/control/command/animation/effect/three_d/trigger。开放式字符串，不硬编码为枚举 */
    String category;
    /** 必填属性名列表，如Var的["name"]。M4 SEM-REQ-001必填缺失检测使用 */
    @Builder.Default List<String> requiredAttrs = Collections.emptyList();
    /** 选填属性名列表，如Var的["expression","type","threshold",...]。与requiredAttrs合并为全部合法属性名集合，M3 SYN-004未知属性检测使用 */
    @Builder.Default List<String> optionalAttrs = Collections.emptyList();
    /** 属性类型规范映射，key为属性名，value为AttrTypeSpec。M3表达式嵌入判断和M4类型推断的核心数据来源 */
    @Builder.Default Map<String, AttrTypeSpec> attrTypes = Collections.emptyMap();
    /** 合法父元素标签名列表。M4 SEM-NEST-001父子嵌套约束检测使用 */
    @Builder.Default List<String> allowedParents = Collections.emptyList();
    /** 继承声明，如VideoCommand inherits="CommandBase"表示继承通用命令属性。Optional层继承链分析使用，Core层仅存储声明 */
    String inherits;
    /** 应用位置支持矩阵，key为应用位置名(Lockscreen/Wallpaper/LongTake/Widget/ChargingSkin)，
     * value为是否支持。M4 ScopeAnalyzer使用scope.get("Wallpaper")查询 */
    @Builder.Default Map<String, Boolean> scope = Collections.emptyMap();
    /** 设备类型支持矩阵，key为设备类型(barPhone/foldable/tablet)，value为是否支持。M4 ScopeAnalyzer SEM-SCOPE-002使用 */
    @Builder.Default Map<String, Boolean> deviceSupport = Collections.emptyMap();
    /** 声明式约束条件列表，M4 ConstraintAnalyzer消费，RuleDslEvaluator解释执行condition字段 */
    @Builder.Default List<RuleConstraint> constraints = Collections.emptyList();
}
