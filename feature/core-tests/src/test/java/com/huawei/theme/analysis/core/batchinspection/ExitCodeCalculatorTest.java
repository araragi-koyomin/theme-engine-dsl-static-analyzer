package com.huawei.theme.analysis.core.batchinspection;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExitCodeCalculatorTest {

    @Test
    void computeReturnsZeroWhenNoErrors() {
        BatchInspectionResult result = BatchInspectionResult.builder()
                .errorCount(0)
                .warningCount(2)
                .infoCount(1)
                .totalFiles(3)
                .skippedFiles(0)
                .fileResults(java.util.List.of())
                .build();
        assertEquals(0, ExitCodeCalculator.compute(result));
    }

    @Test
    void computeReturnsOneWhenHasErrors() {
        BatchInspectionResult result = BatchInspectionResult.builder()
                .errorCount(3)
                .warningCount(0)
                .infoCount(0)
                .totalFiles(1)
                .skippedFiles(0)
                .fileResults(java.util.List.of())
                .build();
        assertEquals(1, ExitCodeCalculator.compute(result));
    }

    @Test
    void computeReturnsOneEvenWithSingleError() {
        BatchInspectionResult result = BatchInspectionResult.builder()
                .errorCount(1)
                .warningCount(5)
                .infoCount(10)
                .totalFiles(3)
                .skippedFiles(0)
                .fileResults(java.util.List.of())
                .build();
        assertEquals(1, ExitCodeCalculator.compute(result));
    }

    @Test
    void computeFromExceptionReturnsTwo() {
        assertEquals(2, ExitCodeCalculator.computeFromException(new RuntimeException("file not found")));
    }

    @Test
    void computeFromExceptionReturnsTwoForAnyThrowable() {
        assertEquals(2, ExitCodeCalculator.computeFromException(new Exception("any error")));
    }
}
