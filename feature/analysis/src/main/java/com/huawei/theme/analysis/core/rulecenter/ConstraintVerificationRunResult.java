package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;

import com.huawei.theme.analysis.core.rulecenter.model.ConstraintVerification;
import com.huawei.theme.analysis.core.rulecenter.model.ValidationFailure;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ConstraintVerificationRunResult {
    boolean passed;
    ValidationFailure failure;
    ConstraintVerification verification;
    List<String> positiveObservedRuleIds;
    List<String> negativeObservedRuleIds;
}
