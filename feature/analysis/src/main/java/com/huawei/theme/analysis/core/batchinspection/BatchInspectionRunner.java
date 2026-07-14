package com.huawei.theme.analysis.core.batchinspection;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;

public interface BatchInspectionRunner {
    BatchInspectionResult runOnFile(String filePath);
    BatchInspectionResult runOnDirectory(String directoryPath);
}
