package com.huawei.theme.analysis.core.rulecenter;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.huawei.theme.analysis.core.rulecenter.model.ConstraintVerification;
import com.huawei.theme.analysis.core.rulecenter.model.RuleCandidate;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RulePackageAssemblyRequest {
    Path packageDirectory;
    Path rulesDirectory;
    Path functionsDirectory;
    String packageVersion;
    String createdAt;
    String minimumAnalyzerVersion;
    List<SourceDocumentArtifact> sourceDocuments;
    List<RuleCandidate> candidates;
    List<ConstraintVerification> verifications;
    Set<String> publishedConstraintRuleIds;
    Set<String> carriedForwardCandidateIds;
    @Builder.Default
    RulePackageInventory minimumInventory = RulePackageInventory.builder().build();
    @Builder.Default
    Set<String> grandfatheredDuplicateRuleIds = Set.of();
}
