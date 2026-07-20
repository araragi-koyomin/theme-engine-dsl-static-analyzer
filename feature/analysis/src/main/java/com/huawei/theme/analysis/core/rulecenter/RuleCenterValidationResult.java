package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;

import com.huawei.theme.analysis.core.rulecenter.model.ConstraintVerification;
import com.huawei.theme.analysis.core.rulecenter.model.DocumentConversionFeedback;
import com.huawei.theme.analysis.core.rulecenter.model.RuleCandidate;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RuleCenterValidationResult {
    RulePackageAssemblyResult assembly;
    List<RuleCandidate> candidates;
    List<ConstraintVerification> verifications;
    CandidateExtractionResult extraction;
    List<CandidateExtractionResult> extractions;
    DocumentConversionFeedback feedback;
    List<DocumentConversionFeedback> feedbackItems;
}
