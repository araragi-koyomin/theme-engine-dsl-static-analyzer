package com.huawei.theme.analysis.core.rulecenter;

import com.huawei.theme.analysis.core.rulecenter.model.AuthorAction;
import com.huawei.theme.analysis.core.rulecenter.model.CandidateStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ConstraintRepairLoopOutcome {
    String candidateId;
    CandidateStatus status;
    AuthorAction authorAction;
    int repairAttempts;
    boolean immutableFieldsRejected;
    ConstraintRepairProposal finalProposal;
    ConstraintVerificationRunResult lastVerification;
}
