package com.huawei.theme.analysis.core.expression.model;

import java.util.List;

import com.huawei.theme.analysis.core.shared.type.DslType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FunctionSignature {
    String name;
    List<FunctionParam> params;
    DslType returnType;
    String expressionKind;
}
