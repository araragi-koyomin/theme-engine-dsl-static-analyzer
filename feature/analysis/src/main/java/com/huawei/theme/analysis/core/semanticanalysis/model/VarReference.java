package com.huawei.theme.analysis.core.semanticanalysis.model;

import lombok.Builder;
import lombok.Data;

import com.huawei.theme.analysis.core.shared.ast.DslAstNode;

@Data
@Builder
public class VarReference {
    String name;
    ReferenceKind kind;
    DslAstNode astNode;
}
