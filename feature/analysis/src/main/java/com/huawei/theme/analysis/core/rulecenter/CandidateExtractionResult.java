package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;

import com.huawei.theme.analysis.core.rulecenter.model.RuleCandidate;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CandidateExtractionResult {
    List<RuleCandidate> candidates;
    String requestedModel;
    String actualModel;
    String promptVersion;
    String promptSha256;
    String documentSha256;
    String rawResponseSha256;
}
