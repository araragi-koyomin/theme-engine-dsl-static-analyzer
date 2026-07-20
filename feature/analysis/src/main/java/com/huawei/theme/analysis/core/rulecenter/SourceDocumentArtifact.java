package com.huawei.theme.analysis.core.rulecenter;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SourceDocumentArtifact {
    String documentId;
    String revision;
    String relativePath;
    String content;
}
