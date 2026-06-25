package com.huawei.theme.analysis.core.semanticanalysis.model;

import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.type.DslType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VarDeclaration {
    String name;
    DslType type;
    String expression;
    boolean isConstAttr;
    DslElementNode astNode;
}
