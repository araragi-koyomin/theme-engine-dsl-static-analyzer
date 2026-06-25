package com.huawei.theme.analysis.core.semanticanalysis.model;

import com.huawei.theme.analysis.core.shared.ast.DslAstNode;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VarReference {
    String name;
    ReferenceKind kind;
    DslAstNode astNode;
}
