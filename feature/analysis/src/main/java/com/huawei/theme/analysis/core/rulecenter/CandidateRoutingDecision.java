package com.huawei.theme.analysis.core.rulecenter;

import com.huawei.theme.analysis.core.rulecenter.model.CandidateStatus;
import com.huawei.theme.analysis.core.rulecenter.model.SkipReason;
import com.huawei.theme.analysis.core.rulecenter.model.ValidationFailure;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CandidateRoutingDecision {
    String candidateId;
    CandidateStatus status;
    SkipReason skipReason;
    ValidationFailure validationFailure;
}
