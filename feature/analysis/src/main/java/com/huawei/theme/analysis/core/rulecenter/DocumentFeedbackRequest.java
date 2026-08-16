package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;
import java.util.Set;

import com.huawei.theme.analysis.core.rulecenter.model.RuleCandidate;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DocumentFeedbackRequest {
    String documentId;
    String documentRevision;
    String releaseVersion;
    List<RuleCandidate> candidates;
    Set<String> carriedForwardCandidateIds;
    boolean releaseFailed;
}
