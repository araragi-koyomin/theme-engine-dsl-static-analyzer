package com.huawei.theme.analysis.core.shared.ast;

/**
 * 表达式AST节点接口，携带源码区间。
 *
 * <p>line/column为区间起始(1-based行/0-based列)，endLine/endColumn为开区间末尾。
 * getRange()返回封装后的SourceRange。零宽区间(start==end)表示点位置。</p>
 */
public interface ExpressionAstNode {
    String getText();

    int getLine();

    int getColumn();

    int getEndLine();

    int getEndColumn();

    ExpressionKind getKind();

    /**
     * 返回该节点的源码区间。start闭end开，行1-based列0-based。
     */
    default SourceRange getRange() {
        return new SourceRange(getLine(), getColumn(), getEndLine(), getEndColumn());
    }
}
