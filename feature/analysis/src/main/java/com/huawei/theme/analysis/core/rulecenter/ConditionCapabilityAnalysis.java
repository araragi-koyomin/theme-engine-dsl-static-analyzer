package com.huawei.theme.analysis.core.rulecenter;

import java.util.Set;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ConditionCapabilityAnalysis {
    boolean extensionShapeSupported;
    String normalizedCondition;
    Set<ConditionCapability> capabilities;
    ConditionCapabilityRejection rejection;
}
