package com.huawei.theme.analysis.core.rulecenter;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ConstraintRepairProposal {
    ConstraintVerificationRequest verificationRequest;
    String sourceEvidenceFingerprint;
    String targetFingerprint;
}
