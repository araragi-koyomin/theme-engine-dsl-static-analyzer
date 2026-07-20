package com.huawei.theme.analysis.core.rulecenter;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReleaseMetadata {
    String packageVersion;
    String createdAt;
    String contentSha256;
    String artifactSha256;
    String minimumAnalyzerVersion;
    String changeSummary;
}
