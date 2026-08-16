package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;

import com.huawei.theme.analysis.core.rulecenter.model.ValidationFailure;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ConstraintRepairContext {
    String candidateId;
    int attempt;
    ConstraintRepairProposal currentProposal;
    ValidationFailure validationFailure;
    List<VerifiedConstraintExample> examples;
}
