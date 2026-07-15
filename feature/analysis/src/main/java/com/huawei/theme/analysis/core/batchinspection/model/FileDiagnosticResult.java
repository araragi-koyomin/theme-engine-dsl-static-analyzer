package com.huawei.theme.analysis.core.batchinspection.model;

import java.util.List;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileDiagnosticResult {
    String filePath;
    List<Diagnostic> diagnostics;
    List<FixAction> fixActions;
    @Builder.Default
    boolean hasInternalError = false;
}
