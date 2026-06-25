package com.huawei.theme.analysis.core.expression.model;

import com.huawei.theme.analysis.core.shared.type.DslType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FunctionParam {
    String name;
    DslType type;
    boolean isVariadic;
}
