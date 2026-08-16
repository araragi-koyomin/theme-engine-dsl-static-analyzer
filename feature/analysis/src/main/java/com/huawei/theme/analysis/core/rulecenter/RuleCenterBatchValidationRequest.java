package com.huawei.theme.analysis.core.rulecenter;

import java.nio.file.Path;
import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RuleCenterBatchValidationRequest {
    List<RuleDocumentRevision> documents;
    @Builder.Default
    List<SourceDocumentArtifact> retainedSourceDocuments = List.of();
    Path rulesDirectory;
    Path functionsDirectory;
    Path outputDirectory;
    String packageVersion;
    String createdAt;
    String minimumAnalyzerVersion;
}
