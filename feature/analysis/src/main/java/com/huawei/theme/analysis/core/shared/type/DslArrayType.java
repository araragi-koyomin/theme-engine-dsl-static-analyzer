package com.huawei.theme.analysis.core.shared.type;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
public class DslArrayType extends DslType {
    String baseType;

    @Override
    public String getName() {
        return "array";
    }
}
