package com.huawei.theme.analysis.core.batchinspection.model;

import java.util.List;

import com.huawei.theme.analysis.core.batchinspection.model.FileDiagnosticResult;
import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchInspectionResultTest {
    @Test
    void builderCreatesResultWithAllFields() {
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(3)
                .skippedFiles(1)
                .errorCount(5)
                .warningCount(2)
                .infoCount(1)
                .fileResults(List.of())
                .build();
        assertEquals(3, result.getTotalFiles());
        assertEquals(1, result.getSkippedFiles());
        assertEquals(5, result.getErrorCount());
        assertEquals(2, result.getWarningCount());
        assertEquals(1, result.getInfoCount());
        assertEquals(0, result.getFileResults().size());
    }

    @Test
    void builderDefaultValues() {
        BatchInspectionResult result = BatchInspectionResult.builder().build();
        assertEquals(0, result.getTotalFiles());
        assertEquals(0, result.getSkippedFiles());
        assertEquals(0, result.getErrorCount());
        assertEquals(0, result.getWarningCount());
        assertEquals(0, result.getInfoCount());
    }

    @Test
    void builderWithFileResults() {
        Diagnostic diag1 = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("E1")
                .message("err")
                .filePath("a.xml")
                .line(1).column(0)
                .build();
        Diagnostic diag2 = Diagnostic.builder()
                .severity(DiagnosticSeverity.WARNING)
                .ruleId("W1")
                .message("warn")
                .filePath("b.xml")
                .line(2).column(5)
                .build();
        FileDiagnosticResult fr1 = FileDiagnosticResult.builder()
                .filePath("a.xml")
                .diagnostics(List.of(diag1))
                .fixActions(List.of())
                .build();
        FileDiagnosticResult fr2 = FileDiagnosticResult.builder()
                .filePath("b.xml")
                .diagnostics(List.of(diag2))
                .fixActions(List.of())
                .build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(2)
                .skippedFiles(0)
                .errorCount(1)
                .warningCount(1)
                .infoCount(0)
                .fileResults(List.of(fr1, fr2))
                .build();
        assertEquals(2, result.getFileResults().size());
        assertEquals("a.xml", result.getFileResults().get(0).getFilePath());
        assertEquals("b.xml", result.getFileResults().get(1).getFilePath());
        assertEquals(1, result.getFileResults().get(0).getDiagnostics().size());
        assertEquals(DiagnosticSeverity.ERROR, result.getFileResults().get(0).getDiagnostics().get(0).getSeverity());
        assertEquals(DiagnosticSeverity.WARNING, result.getFileResults().get(1).getDiagnostics().get(0).getSeverity());
    }

    @Test
    void builderWithNestedDiagnosticData() {
        Diagnostic error1 = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-REF-001")
                .message("undefined var #x")
                .filePath("theme.xml")
                .line(10).column(5)
                .build();
        Diagnostic error2 = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-REF-002")
                .message("undefined var #y")
                .filePath("theme.xml")
                .line(15).column(3)
                .build();
        Diagnostic warning1 = Diagnostic.builder()
                .severity(DiagnosticSeverity.WARNING)
                .ruleId("SEM-SCOPE-001")
                .message("element out of scope")
                .filePath("theme.xml")
                .line(20).column(1)
                .build();
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("theme.xml")
                .diagnostics(List.of(error1, error2, warning1))
                .fixActions(List.of())
                .build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(1)
                .skippedFiles(0)
                .errorCount(2)
                .warningCount(1)
                .infoCount(0)
                .fileResults(List.of(fileResult))
                .build();
        assertEquals(3, result.getFileResults().get(0).getDiagnostics().size());
        assertEquals(2, result.getErrorCount());
        assertEquals(1, result.getWarningCount());
    }

    @Test
    void builderWithSkippedOnlyResult() {
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(0)
                .skippedFiles(5)
                .errorCount(0)
                .warningCount(0)
                .infoCount(0)
                .fileResults(List.of())
                .build();
        assertEquals(5, result.getSkippedFiles());
        assertEquals(0, result.getTotalFiles());
        assertEquals(0, result.getFileResults().size());
    }
}
