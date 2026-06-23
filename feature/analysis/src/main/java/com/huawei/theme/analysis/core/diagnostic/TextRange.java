package com.huawei.theme.analysis.core.diagnostic;

import lombok.Builder;
import lombok.Data;

/**
 * 文本范围描述，用于精确定位诊断问题在DSL文件中的位置。
 *
 * <p>与Diagnostic的line+column粗定位互补，TextRange提供起止行列的精确范围，
 * 用于M5 FixAction定位替换目标、PSI Adapter映射Annotation范围。</p>
 */
@Data
@Builder
public class TextRange {
    /** 起始行号（1-indexed） */
    int startLine;
    /** 起始列号（1-indexed） */
    int startColumn;
    /** 结束行号（1-indexed） */
    int endLine;
    /** 结束列号（1-indexed） */
    int endColumn;
}
