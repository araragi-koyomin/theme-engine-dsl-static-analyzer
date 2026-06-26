package com.huawei.theme.analysis.core.expression.model;

import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Data;

import com.huawei.theme.analysis.core.shared.type.DslType;

@Data
@Builder
public class FunctionSignature {
    String name;
    @Builder.Default List<FunctionParam> params = Collections.emptyList();
    DslType returnType;
    String expressionKind;
}
