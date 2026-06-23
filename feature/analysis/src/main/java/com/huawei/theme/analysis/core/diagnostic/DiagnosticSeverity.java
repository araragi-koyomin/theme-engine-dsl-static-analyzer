package com.huawei.theme.analysis.core.diagnostic;

/**
 * 诊断严重级别枚举，定义DSL分析结果的三种严重程度。
 *
 * <p>对应JSON规则库中的severity字段，通过DiagnosticSeverityAdapter实现字符串与枚举的映射。
 * JSON中存储为小写字符串("error"/"warning"/"info")，Java内存中为枚举常量。</p>
 */
public enum DiagnosticSeverity {
    /** 错误级别，必须修复，阻断CI/CD流水线（CLI退出码1） */
    ERROR,
    /** 警告级别，建议修复，不阻断流水线 */
    WARNING,
    /** 信息级别，仅供参考，如--verbose模式下的类型推断过程 */
    INFO
}
