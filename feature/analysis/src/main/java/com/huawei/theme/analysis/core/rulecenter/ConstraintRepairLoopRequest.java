package com.huawei.theme.analysis.core.rulecenter;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ConstraintRepairLoopRequest {
    String candidateId;
    ConstraintRepairProposal initialProposal;
    ConstraintExampleQuery exampleQuery;
}
