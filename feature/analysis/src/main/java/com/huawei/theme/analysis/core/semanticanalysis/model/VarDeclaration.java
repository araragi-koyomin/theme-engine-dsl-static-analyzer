package com.huawei.theme.analysis.core.semanticanalysis.model;

import lombok.Builder;
import lombok.Data;

import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.type.DslType;

@Data
@Builder
public class VarDeclaration {
    String name;
    DslType type;
    /**
     * 对应的expression的位置，如果外部变量则为null
     */
    ExpressionAstNode expression;
    boolean isConstAttr;
    /**
     * 是否是外部引入的全局变量
     */
    boolean isGlobal;
    /**
     * 对应的定义位置，如果是外部变量则为null
     */
    DslElementNode astNode;
}
