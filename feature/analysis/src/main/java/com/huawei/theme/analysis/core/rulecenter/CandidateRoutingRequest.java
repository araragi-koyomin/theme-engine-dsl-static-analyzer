package com.huawei.theme.analysis.core.rulecenter;

import com.huawei.theme.analysis.core.rulecenter.model.ProposedKind;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CandidateRoutingRequest {
    String candidateId;
    ProposedKind proposedKind;
    boolean targetResolved;
    ConstraintEvidenceScope evidenceScope;
    boolean evidenceConflict;
    ConditionAcceptance conditionAcceptance;
    ConstraintVerificationRunResult verificationResult;
}
