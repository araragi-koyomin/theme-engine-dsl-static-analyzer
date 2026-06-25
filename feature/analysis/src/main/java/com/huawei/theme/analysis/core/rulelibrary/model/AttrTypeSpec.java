package com.huawei.theme.analysis.core.rulelibrary.model;

import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * 属性类型规范，定义DSL元素单个属性的完整类型信息。
 *
 * <p>作为DslElementRule.attrTypes的Map值类型，为M3语法分析（决定是否调用ANTLR4解析器）
 * 和M4类型推断（推断属性期望类型）提供关键判断依据。</p>
 *
 * <p>type字段设计为开放式字符串而非硬编码枚举，以支持未来新增属性类型无需修改数据模型。</p>
 */
@Data
@Builder
public class AttrTypeSpec {
    /** 属性类型标识，已知类型：string/number/boolean/enum/expression/action/object/reference */
    String type;
    /** 枚举类型的合法取值集合，非枚举类型时为空列表。M3 SYN-007枚举检测使用 */
    @Builder.Default List<String> enumValues = Collections.emptyList();
    /** 属性别名列表，如width的别名w、rotation的别名angle。M5 QuickFix替换建议使用 */
    @Builder.Default List<String> aliases = Collections.emptyList();
    /** 是否支持DSL表达式语法(#var/@var/函数调用)。M3据此决定是否调用ANTLR4解析器，false时纯字面量直接验证 */
    boolean supportsExpression;
    /** 表达式类别："number"→期望数值表达式，"string"→期望字符串表达式，"auto"→根据上下文(如Var的type属性)动态推断。M4 TypeInferenceEngine推断期望类型时使用 */
    String expressionKind;
    /** 属性默认值，省略该属性时引擎使用的隐式值。null表示无默认值(省略=属性不存在，不影响分析)。M4 TypeAnalyzer推断省略属性的类型时使用，如Var.type省略→默认"number" */
    String defaultValue;
}
