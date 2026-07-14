package com.huawei.theme.analysis.core.batchinspection.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BatchInspectionResult {
    int totalFiles;
    int skippedFiles;
    int errorCount;
    int warningCount;
    int infoCount;
    List<FileDiagnosticResult> fileResults;
    @Builder.Default
    boolean hasInternalErrors = false;
}
