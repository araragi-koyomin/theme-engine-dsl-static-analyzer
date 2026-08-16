package com.huawei.theme.analysis.core.rulecenter;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RuleDocumentRevision {
    String documentId;
    String revision;
    String markdown;
    String sourceMarkdownRelativePath;
}
