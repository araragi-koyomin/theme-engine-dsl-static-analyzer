package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;

import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ConstraintVerificationRequest {
    String targetElement;
    RuleConstraint constraint;
    String positiveFixturePath;
    String positiveFixtureContent;
    String negativeFixturePath;
    String negativeFixtureContent;
    List<String> evidenceCandidateIds;
}
