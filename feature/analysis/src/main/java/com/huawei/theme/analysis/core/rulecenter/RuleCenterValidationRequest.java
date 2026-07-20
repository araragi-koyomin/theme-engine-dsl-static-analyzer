package com.huawei.theme.analysis.core.rulecenter;

import java.nio.file.Path;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RuleCenterValidationRequest {
    RuleDocumentRevision document;
    Path rulesDirectory;
    Path functionsDirectory;
    Path outputDirectory;
    String packageVersion;
    String createdAt;
    String minimumAnalyzerVersion;
}
