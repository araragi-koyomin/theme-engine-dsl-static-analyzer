package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;
import java.util.Set;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ConditionAcceptance {
    boolean accepted;
    ConditionAcceptanceStatus status;
    String originalCondition;
    String normalizedCondition;
    Set<ConditionCapability> capabilities;
    List<String> syntaxErrors;
}
