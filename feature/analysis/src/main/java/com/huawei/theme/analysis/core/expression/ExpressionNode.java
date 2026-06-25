package com.huawei.theme.analysis.core.expression;

import lombok.Data;

import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;

@Data
public abstract class ExpressionNode implements ExpressionAstNode {
    String text;
    int line;
    int column;

    @Override
    public String getText() { return text; }

    @Override
    public int getLine() { return line; }

    @Override
    public int getColumn() { return column; }

    @Override
    public abstract ExpressionKind getKind();
}
