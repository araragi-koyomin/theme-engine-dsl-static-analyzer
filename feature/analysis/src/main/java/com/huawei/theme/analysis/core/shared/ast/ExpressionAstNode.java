package com.huawei.theme.analysis.core.shared.ast;

public interface ExpressionAstNode {
    String getText();
    int getLine();
    int getColumn();
    ExpressionKind getKind();
}
