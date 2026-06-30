package com.huawei.theme.analysis.core.rulelibrary.model;

import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * 全局变量条目，定义引擎预置变量的类型和访问信息。
 *
 * <p>全局变量不是XML树中的元素，是运行时预置变量（如#battery_level、@ishour12），
 * 数据结构与DslElementRule完全不同（无标签名、无属性列表、无嵌套关系），因此独立建模。</p>
 *
 * <p>消费方：M4 VarRefAnalyzer（按变量名查找类型判断引用合法性）、
 * M4 SymbolTableBuilder（将全局变量加入符号表供变量引用检查使用）、
 * M8导航（悬浮显示变量信息）。</p>
 */
@Data
@Builder
public class DslGlobalVar {
    /** 变量名，如"battery_level"。VarRefAnalyzer按#name/@name查找时使用 */
    String name;
    /** 变量类型："number"/"string"/"number[]"/"string[]"。M4 TypeAnalyzer推断#varName→DslNumberType、@varName→DslStringType时使用 */
    String type;
    /** 变量作用域："global"(全局预置)/"local"(局部自定义)/"context"(上下文限定，如name.move_x只在Unlocker内有效) */
    String scope;
    /** 变量描述，IDEA悬浮提示可展示 */
    String description;

//    /** 访问模式，如"#battery_level"(数值访问)/"@ishour12"(字符串访问)。M3识别变量引用时使用 */
//    String accessPattern;
    /** 全局变量的约束条件列表，如battery_level范围1-100 */
    @Builder.Default List<RuleConstraint> constraints = Collections.emptyList();
}
