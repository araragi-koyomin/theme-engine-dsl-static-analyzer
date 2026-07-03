package com.huawei.theme.analysis.core.shared.ast;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AST节点基类，携带文本与源码区间。
 *
 * <p>line/column为区间起始(1-based行/0-based列)，endLine/endColumn为开区间末尾。
 * getRange()返回封装后的SourceRange。零宽区间(start==end)表示点位置。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class DslAstNode {
    String text;
    int line;
    int column;
    int endLine;
    int endColumn;

    /**
     * 返回该节点的源码区间。start闭end开，行1-based列0-based。
     */
    public SourceRange getRange() {
        return new SourceRange(line, column, endLine, endColumn);
    }
}
