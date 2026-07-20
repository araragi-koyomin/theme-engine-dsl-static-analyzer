package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;
import java.util.Map;

import com.huawei.theme.analysis.core.rulecenter.model.ConstraintVerification;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RulePackageReleaseReport {
    String packageVersion;
    String manifestContentSha256;
    ReleaseReportStatus status;
    Map<String, Integer> candidateCounts;
    Map<String, List<String>> candidatesByStatus;
    List<String> carriedForwardCandidateIds;
    List<ConstraintVerification> constraintVerifications;
    boolean jsonSchemaValid;
    boolean packageComplete;
    List<String> errors;
}
