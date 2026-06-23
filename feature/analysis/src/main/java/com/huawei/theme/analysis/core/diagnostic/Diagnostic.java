package com.huawei.theme.analysis.core.diagnostic;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * DSL分析诊断结果，跨模块共享的核心数据模型。
 *
 * <p>Core层与Plugin层共用此模型定位诊断问题。定位使用filePath+line+column，
 * 不依赖PsiElement，确保Core层无IDEA SDK依赖。</p>
 *
 * <p>消费方：M5修复逻辑（生成FixAction）、M7批量检查（报告导出）、
 * PSI Adapter（Diagnostic→Annotation映射）、CLI（终端/JSON/Markdown输出）。</p>
 */
@Data
@Builder
public class Diagnostic {
    /** 诊断严重级别：ERROR/WARNING/INFO */
    DiagnosticSeverity severity;
    /** 规则ID，格式[类别]-[子类]-[编号]，如SEM-REF-001、SYN-003 */
    String ruleId;
    /** 诊断描述消息，如"引用未定义变量 #steps_value" */
    String message;
    /** 被诊断的DSL文件路径 */
    String filePath;
    /** 诊断位置行号（1-indexed） */
    int line;
    /** 诊断位置列号（1-indexed） */
    int column;
    /** 建议修复描述列表，来源于M2 RuleConstraint.suggestedFixes或M5 FixAction */
    List<String> suggestedFixes;
    /** 规则文档URL，来源于M2 RuleSource.docUrl，指向华为开发者官网规范页面 */
    String ruleDocUrl;
}
