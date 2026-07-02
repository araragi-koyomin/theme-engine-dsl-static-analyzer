package com.huawei.theme.analysis.core.shared.ast;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 源码区间，由起始和结束的(行,列)构成。行1-based，列0-based（与全库位置约定一致）。
 *
 * <p>语义：start为闭区间，end为开区间（endLine/endColumn指向区间末尾之后的位置）。
 * 零宽区间（start==end）表示一个点位置。</p>
 *
 * <p>消费方：DslAstNode.getRange()/Diagnostic.getRange()返回本类，
 * IntelliJ插件层可据此处区间进行高亮/下划线定位。</p>
 */
@Getter
@AllArgsConstructor
public final class SourceRange {
    private final int startLine;
    private final int startColumn;
    private final int endLine;
    private final int endColumn;

    public static SourceRange point(int line, int column) {
        return new SourceRange(line, column, line, column);
    }

    public static SourceRange of(int startLine, int startColumn, int endLine, int endColumn) {
        return new SourceRange(startLine, startColumn, endLine, endColumn);
    }
}
