package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CandidateExtractionRequest {
    String documentId;
    String documentRevision;
    String markdown;
    List<VerifiedConstraintExample> examples;
}
