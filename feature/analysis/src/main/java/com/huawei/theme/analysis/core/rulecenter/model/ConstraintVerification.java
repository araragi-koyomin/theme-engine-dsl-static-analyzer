package com.huawei.theme.analysis.core.rulecenter.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConstraintVerification {
    String ruleId;
    String condition;
    boolean parserAccepted;
    String positiveFixture;
    String negativeFixture;
    List<String> positiveObservedRuleIds;
    List<String> negativeObservedRuleIds;
    List<String> evidenceCandidateIds;
    VerificationStatus status;
}
