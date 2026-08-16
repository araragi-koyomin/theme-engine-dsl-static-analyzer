package com.huawei.theme.analysis.core.rulecenter;

import java.util.Set;

import com.huawei.theme.analysis.core.rulecenter.model.ConstraintVerification;
import com.huawei.theme.analysis.core.rulecenter.model.TargetKind;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VerifiedConstraintExample {
    String ruleId;
    TargetKind targetKind;
    String targetElement;
    Set<String> attributes;
    ConstraintRelation relation;
    ConstraintEvidenceScope evidenceScope;
    String condition;
    ConstraintVerification verification;
}
