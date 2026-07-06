package com.huawei.theme.analysis.core.batchinspection;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;

public interface ReportExporter {
    String exportMarkdown(BatchInspectionResult result);
    String exportJson(BatchInspectionResult result);
    String exportTerminal(BatchInspectionResult result);
    void exportToFile(BatchInspectionResult result, String format, String outputPath);
}
