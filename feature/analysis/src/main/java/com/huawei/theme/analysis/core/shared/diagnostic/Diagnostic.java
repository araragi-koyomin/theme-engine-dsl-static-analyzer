package com.huawei.theme.analysis.core.shared.diagnostic;

import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Diagnostic {
    DiagnosticSeverity severity;
    String ruleId;
    String message;
    String filePath;
    int line;
    int column;
    @Builder.Default List<String> suggestedFixes = Collections.emptyList();
    String ruleDocUrl;
}
