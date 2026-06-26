package com.huawei.theme.analysis.core.expression.model;

import lombok.Builder;
import lombok.Data;

import com.huawei.theme.analysis.core.shared.type.DslType;

@Data
@Builder
public class FunctionParam {
    String name;
    DslType type;
    boolean isVariadic;
}
