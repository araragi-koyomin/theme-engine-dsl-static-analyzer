package com.huawei.theme.analysis.core.rulelibrary.model;

import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Data;

import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

/**
 * 声明式约束条件，定义一条可零代码新增的检测规则。
 *
 * <p>condition字段使用M0 DslRuleCondition.g4定义的规则DSL语法，
 * 由M4 ConstraintAnalyzer调用M0 RuleDslEvaluator解释执行。
 * 新增检测逻辑只需在JSON的constraints数组中追加条目，无需编写Analyzer代码。</p>
 *
 * <p>示例：SEM-CMD-001的condition为"element.attrs['play'] != null AND element.attrs['sound'] != null"</p>
 */
@Data
@Builder
public class RuleConstraint {
    /** 规则唯一标识，格式[类别]-[子类]-[编号]，如SEM-CMD-001。Diagnostic.ruleId引用此值 */
    String ruleId;
    /** 声明式条件表达式，使用规则DSL语法（属性存在性、值比较、集合包含、逻辑组合） */
    String condition;
    /** 条件满足时的诊断消息模板，直接进入Diagnostic.message */
    String message;
    /** 诊断严重级别，JSON中存储为小写字符串("error"/"warning"/"info")，通过DiagnosticSeverityAdapter映射为枚举 */
    DiagnosticSeverity severity;
    /** 修复建议文本列表，M5 FixAction直接消费 */
    @Builder.Default List<String> suggestedFixes = Collections.emptyList();
}
