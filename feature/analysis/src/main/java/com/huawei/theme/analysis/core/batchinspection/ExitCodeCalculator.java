package com.huawei.theme.analysis.core.batchinspection;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;

public final class ExitCodeCalculator {

    private ExitCodeCalculator() {
    }

    public static int compute(BatchInspectionResult result) {
        if (result.getErrorCount() > 0) {
            return 1;
        }
        return 0;
    }

    public static int computeFromException(Throwable e) {
        return 2;
    }
}
