package com.huawei.theme.analysis.core.rulecenter;

import java.util.Set;

import com.huawei.theme.analysis.core.rulecenter.model.TargetKind;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ConstraintExampleQuery {
    TargetKind targetKind;
    String targetElement;
    Set<String> attributes;
    ConstraintRelation relation;
    Set<ConditionCapability> requiredCapabilities;
}
