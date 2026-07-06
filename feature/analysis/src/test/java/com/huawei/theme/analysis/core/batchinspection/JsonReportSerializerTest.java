package com.huawei.theme.analysis.core.batchinspection;

import java.util.List;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.batchinspection.model.FileDiagnosticResult;
import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonReportSerializerTest {

    private JsonReportSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new JsonReportSerializer();
    }

    private Diagnostic createDiagnostic(DiagnosticSeverity severity, String filePath,
                                         int line, int column, String ruleId, String message) {
        return Diagnostic.builder()
                .severity(severity)
                .filePath(filePath)
                .line(line)
                .column(column)
                .ruleId(ruleId)
                .message(message)
                .suggestedFixes(List.of())
                .build();
    }

    private Diagnostic createDiagnosticWithFixes(DiagnosticSeverity severity, String filePath,
                                                  int line, int column, String ruleId, String message,
                                                  List<SuggestedFix> fixes) {
        return Diagnostic.builder()
                .severity(severity)
                .filePath(filePath)
                .line(line)
                .column(column)
                .ruleId(ruleId)
                .message(message)
                .suggestedFixes(fixes)
                .build();
    }

    @Test
    void singleFileResultUsesFileTopLevelKey() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "theme.xml", 15, 3, "SEM-REF-001", "引用未定义变量");
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("theme.xml")
                .diagnostics(List.of(diag))
                .fixActions(List.of())
                .build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(1)
                .skippedFiles(0)
                .errorCount(1)
                .warningCount(0)
                .infoCount(0)
                .fileResults(List.of(fileResult))
                .build();
        String json = serializer.serialize(result);
        assertTrue(json.contains("\"file\""));
        assertTrue(json.contains("\"diagnostics\""));
        assertFalse(json.contains("\"files\""));
    }

    @Test
    void multiFileResultUsesFilesTopLevelKey() {
        Diagnostic diag1 = createDiagnostic(DiagnosticSeverity.ERROR, "theme.xml", 15, 3, "SEM-REF-001", "error1");
        Diagnostic diag2 = createDiagnostic(DiagnosticSeverity.WARNING, "layout.xml", 5, 1, "SEM-SCOPE-001", "warning1");
        FileDiagnosticResult fileResult1 = FileDiagnosticResult.builder()
                .filePath("theme.xml")
                .diagnostics(List.of(diag1))
                .fixActions(List.of())
                .build();
        FileDiagnosticResult fileResult2 = FileDiagnosticResult.builder()
                .filePath("layout.xml")
                .diagnostics(List.of(diag2))
                .fixActions(List.of())
                .build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(2)
                .skippedFiles(1)
                .errorCount(1)
                .warningCount(1)
                .infoCount(0)
                .fileResults(List.of(fileResult1, fileResult2))
                .build();
        String json = serializer.serialize(result);
        assertTrue(json.contains("\"files\""));
        assertTrue(json.contains("\"totalFiles\""));
        assertTrue(json.contains("\"skippedFiles\""));
        assertFalse(json.contains("\n  \"file\":"));
    }

    @Test
    void severitySerializedAsLowercase() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "f.xml", 1, 0, "E1", "msg");
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("f.xml")
                .diagnostics(List.of(diag))
                .fixActions(List.of())
                .build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(1)
                .skippedFiles(0)
                .errorCount(1)
                .warningCount(0)
                .infoCount(0)
                .fileResults(List.of(fileResult))
                .build();
        String json = serializer.serialize(result);
        assertTrue(json.contains("\"severity\": \"error\""));
        assertFalse(json.contains("\"severity\": \"ERROR\""));
    }

    @Test
    void suggestedFixesSerializedAsTextArray() {
        List<SuggestedFix> fixes = List.of(
                SuggestedFix.builder().text("声明Var name=\"x\"").build(),
                SuggestedFix.builder().text("使用全局变量").build()
        );
        Diagnostic diag = createDiagnosticWithFixes(DiagnosticSeverity.ERROR, "f.xml", 1, 0, "E1", "msg", fixes);
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("f.xml")
                .diagnostics(List.of(diag))
                .fixActions(List.of())
                .build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(1)
                .skippedFiles(0)
                .errorCount(1)
                .warningCount(0)
                .infoCount(0)
                .fileResults(List.of(fileResult))
                .build();
        String json = serializer.serialize(result);
        assertTrue(json.contains("\"suggestedFixes\""));
        assertTrue(json.contains("声明Var name=\\\"x\\\""));
        assertTrue(json.contains("使用全局变量"));
    }

    @Test
    void emptySuggestedFixesSerializedAsEmptyArray() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.WARNING, "f.xml", 1, 0, "W1", "msg");
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("f.xml")
                .diagnostics(List.of(diag))
                .fixActions(List.of())
                .build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(1)
                .skippedFiles(0)
                .errorCount(0)
                .warningCount(1)
                .infoCount(0)
                .fileResults(List.of(fileResult))
                .build();
        String json = serializer.serialize(result);
        assertTrue(json.contains("\"suggestedFixes\": []"));
    }

    @Test
    void summaryUsesShortFieldNames() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "f.xml", 1, 0, "E1", "msg");
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("f.xml")
                .diagnostics(List.of(diag))
                .fixActions(List.of())
                .build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(1)
                .skippedFiles(0)
                .errorCount(2)
                .warningCount(3)
                .infoCount(4)
                .fileResults(List.of(fileResult))
                .build();
        String json = serializer.serialize(result);
        assertTrue(json.contains("\"errors\": 2"));
        assertTrue(json.contains("\"warnings\": 3"));
        assertTrue(json.contains("\"info\": 4"));
        assertFalse(json.contains("\"errorCount\""));
        assertFalse(json.contains("\"warningCount\""));
        assertFalse(json.contains("\"infoCount\""));
    }

    @Test
    void diagnosticFieldsPresent() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "path/to/theme.xml", 42, 7, "SEM-REF-001", "undefined var");
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("path/to/theme.xml")
                .diagnostics(List.of(diag))
                .fixActions(List.of())
                .build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(1)
                .skippedFiles(0)
                .errorCount(1)
                .warningCount(0)
                .infoCount(0)
                .fileResults(List.of(fileResult))
                .build();
        String json = serializer.serialize(result);
        assertTrue(json.contains("\"severity\": \"error\""));
        assertTrue(json.contains("\"line\": 42"));
        assertTrue(json.contains("\"col\": 7"));
        assertTrue(json.contains("\"ruleId\": \"SEM-REF-001\""));
        assertTrue(json.contains("\"message\": \"undefined var\""));
        assertFalse(json.contains("\"ruleDocUrl\""));
        assertFalse(json.contains("\"endLine\""));
        assertFalse(json.contains("\"endColumn\""));
        assertFalse(json.contains("\"astNode\""));
    }

    @Test
    void fileWithEmptyDiagnosticsInMultiFile() {
        FileDiagnosticResult emptyFile = FileDiagnosticResult.builder()
                .filePath("clean.xml")
                .diagnostics(List.of())
                .fixActions(List.of())
                .build();
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "dirty.xml", 1, 0, "E1", "err");
        FileDiagnosticResult dirtyFile = FileDiagnosticResult.builder()
                .filePath("dirty.xml")
                .diagnostics(List.of(diag))
                .fixActions(List.of())
                .build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(2)
                .skippedFiles(0)
                .errorCount(1)
                .warningCount(0)
                .infoCount(0)
                .fileResults(List.of(emptyFile, dirtyFile))
                .build();
        String json = serializer.serialize(result);
        assertTrue(json.contains("\"file\": \"clean.xml\""));
        assertTrue(json.contains("\"diagnostics\": []"));
        assertTrue(json.contains("\"file\": \"dirty.xml\""));
    }

    @Test
    void multiFileGlobalSummaryIncludesTotalFilesAndSkipped() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "a.xml", 1, 0, "E1", "err");
        FileDiagnosticResult fileResult1 = FileDiagnosticResult.builder()
                .filePath("a.xml")
                .diagnostics(List.of(diag))
                .fixActions(List.of())
                .build();
        FileDiagnosticResult fileResult2 = FileDiagnosticResult.builder()
                .filePath("b.xml")
                .diagnostics(List.of())
                .fixActions(List.of())
                .build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(5)
                .skippedFiles(3)
                .errorCount(1)
                .warningCount(0)
                .infoCount(0)
                .fileResults(List.of(fileResult1, fileResult2))
                .build();
        String json = serializer.serialize(result);
        assertTrue(json.contains("\"totalFiles\": 5"));
        assertTrue(json.contains("\"skippedFiles\": 3"));
    }
}
