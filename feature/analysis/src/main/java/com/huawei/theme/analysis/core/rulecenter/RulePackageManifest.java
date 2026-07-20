package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RulePackageManifest {
    int schemaVersion;
    String packageVersion;
    String channel;
    String createdAt;
    String contentSha256;
    String minimumAnalyzerVersion;
    List<SourceDocumentRevision> sourceDocumentRevisions;

    @Data
    @Builder
    public static class SourceDocumentRevision {
        String documentId;
        String revision;
        String sha256;
    }
}
