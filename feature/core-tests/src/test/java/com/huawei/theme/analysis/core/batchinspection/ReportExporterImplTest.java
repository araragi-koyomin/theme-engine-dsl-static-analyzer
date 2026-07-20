package com.huawei.theme.analysis.core.batchinspection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.batchinspection.model.FileDiagnosticResult;
import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportExporterImplTest {

    private TerminalFormatter noColorFormatter;
    private ReportExporterImpl exporter;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        noColorFormatter = new TerminalFormatter(true);
        exporter = new ReportExporterImpl(noColorFormatter);
        tempDir = Files.createTempDirectory("report-exporter-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
            try { Files.deleteIfExists(p); } catch (IOException e) { }
        });
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
    void constructorRejectsNullTerminalFormatter() {
        assertThrows(NullPointerException.class, () -> new ReportExporterImpl(null));
    }

    @Test
    void exportTerminalDelegatesToFormatter() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "theme.xml", 15, 3, "SEM-REF-001", "引用未定义变量");
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("theme.xml")
                .diagnostics(List.of(diag))
                .fixActions(List.of())
                .build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(1).skippedFiles(0).errorCount(1).warningCount(0).infoCount(0)
                .fileResults(List.of(fileResult))
                .build();
        String exported = exporter.exportTerminal(result);
        String expected = noColorFormatter.formatFullReport(result);
        assertEquals(expected, exported);
    }

    @Test
    void exportMarkdownSingleErrorFile() {
        Diagnostic diag = createDiagnosticWithFixes(
                DiagnosticSeverity.ERROR, "theme.xml", 15, 3, "SEM-REF-001", "引用未定义变量 #steps_value",
                List.of(SuggestedFix.builder().text("声明Var name=\"steps_value\"").build())
        );
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("theme.xml")
                .diagnostics(List.of(diag))
                .fixActions(List.of())
                .build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(1).skippedFiles(0).errorCount(1).warningCount(0).infoCount(0)
                .fileResults(List.of(fileResult))
                .build();
        String md = exporter.exportMarkdown(result);
        assertTrue(md.contains("# DSL 诊断报告"));
        assertTrue(md.contains("## Error 级别问题"));
        assertTrue(md.contains("### theme.xml"));
        assertTrue(md.contains("**SEM-REF-001** (line 15, col 3): 引用未定义变量 #steps_value"));
        assertTrue(md.contains("建议修复: 声明Var name=\"steps_value\""));
        assertTrue(md.contains("## Warning 级别问题"));
        assertTrue(md.contains("无 Warning 级别问题"));
        assertTrue(md.contains("## Info 级别问题"));
        assertTrue(md.contains("无 Info 级别问题"));
        assertTrue(md.contains("## 汇总"));
        assertTrue(md.contains("| theme.xml | 1 | 0 | 0 |"));
        assertTrue(md.contains("1 files, 0 skipped, 1 errors, 0 warnings, 0 info"));
    }

    @Test
    void exportMarkdownMultiFileMixedSeverity() {
        Diagnostic errDiag = createDiagnostic(DiagnosticSeverity.ERROR, "a.xml", 10, 0, "E1", "error msg");
        Diagnostic warnDiag = createDiagnostic(DiagnosticSeverity.WARNING, "b.xml", 5, 1, "W1", "warning msg");
        Diagnostic infoDiag = createDiagnostic(DiagnosticSeverity.INFO, "c.xml", 1, 0, "I1", "info msg");
        FileDiagnosticResult fileA = FileDiagnosticResult.builder()
                .filePath("a.xml").diagnostics(List.of(errDiag)).fixActions(List.of()).build();
        FileDiagnosticResult fileB = FileDiagnosticResult.builder()
                .filePath("b.xml").diagnostics(List.of(warnDiag)).fixActions(List.of()).build();
        FileDiagnosticResult fileC = FileDiagnosticResult.builder()
                .filePath("c.xml").diagnostics(List.of(infoDiag)).fixActions(List.of()).build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(3).skippedFiles(1).errorCount(1).warningCount(1).infoCount(1)
                .fileResults(List.of(fileA, fileB, fileC))
                .build();
        String md = exporter.exportMarkdown(result);
        assertTrue(md.contains("### a.xml"));
        assertTrue(md.contains("### b.xml"));
        assertTrue(md.contains("### c.xml"));
        assertTrue(md.contains("3 files, 1 skipped, 1 errors, 1 warnings, 1 info"));
    }

    @Test
    void exportMarkdownAllClean() {
        FileDiagnosticResult cleanFile = FileDiagnosticResult.builder()
                .filePath("clean.xml").diagnostics(List.of()).fixActions(List.of()).build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(1).skippedFiles(0).errorCount(0).warningCount(0).infoCount(0)
                .fileResults(List.of(cleanFile))
                .build();
        String md = exporter.exportMarkdown(result);
        assertTrue(md.contains("无 Error 级别问题"));
        assertTrue(md.contains("无 Warning 级别问题"));
        assertTrue(md.contains("无 Info 级别问题"));
        assertTrue(md.contains("0 errors, 0 warnings, 0 info"));
    }

    @Test
    void exportJsonSingleFile() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "theme.xml", 15, 3, "SEM-REF-001", "引用未定义变量");
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("theme.xml").diagnostics(List.of(diag)).fixActions(List.of()).build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(1).skippedFiles(0).errorCount(1).warningCount(0).infoCount(0)
                .fileResults(List.of(fileResult))
                .build();
        String json = exporter.exportJson(result);
        assertTrue(json.contains("\"file\""));
        assertTrue(json.contains("\"theme.xml\""));
        assertTrue(json.contains("\"severity\": \"error\""));
        assertTrue(json.contains("\"ruleId\": \"SEM-REF-001\""));
        assertTrue(json.contains("\"errors\": 1"));
        assertFalse(json.contains("\"files\""));
    }

    @Test
    void exportJsonMultiFile() {
        Diagnostic diag1 = createDiagnostic(DiagnosticSeverity.ERROR, "a.xml", 1, 0, "E1", "err");
        Diagnostic diag2 = createDiagnostic(DiagnosticSeverity.WARNING, "b.xml", 2, 0, "W1", "warn");
        FileDiagnosticResult fileA = FileDiagnosticResult.builder()
                .filePath("a.xml").diagnostics(List.of(diag1)).fixActions(List.of()).build();
        FileDiagnosticResult fileB = FileDiagnosticResult.builder()
                .filePath("b.xml").diagnostics(List.of(diag2)).fixActions(List.of()).build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(2).skippedFiles(1).errorCount(1).warningCount(1).infoCount(0)
                .fileResults(List.of(fileA, fileB))
                .build();
        String json = exporter.exportJson(result);
        assertTrue(json.contains("\"files\""));
        assertTrue(json.contains("\"totalFiles\": 2"));
        assertTrue(json.contains("\"skippedFiles\": 1"));
        assertFalse(json.contains("\n  \"file\":"));
    }

    @Test
    void exportToFileWritesJsonFormat() throws IOException {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "f.xml", 1, 0, "E1", "err");
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("f.xml").diagnostics(List.of(diag)).fixActions(List.of()).build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(1).skippedFiles(0).errorCount(1).warningCount(0).infoCount(0)
                .fileResults(List.of(fileResult))
                .build();
        Path output = tempDir.resolve("report.json");
        exporter.exportToFile(result, "json", output.toString());
        String content = Files.readString(output, StandardCharsets.UTF_8);
        assertTrue(content.contains("\"severity\": \"error\""));
    }

    @Test
    void exportToFileWritesMarkdownFormat() throws IOException {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "f.xml", 1, 0, "E1", "err");
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("f.xml").diagnostics(List.of(diag)).fixActions(List.of()).build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(1).skippedFiles(0).errorCount(1).warningCount(0).infoCount(0)
                .fileResults(List.of(fileResult))
                .build();
        Path output = tempDir.resolve("report.md");
        exporter.exportToFile(result, "markdown", output.toString());
        String content = Files.readString(output, StandardCharsets.UTF_8);
        assertTrue(content.contains("# DSL 诊断报告"));
    }

    @Test
    void exportToFileWritesMdAlias() throws IOException {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "f.xml", 1, 0, "E1", "err");
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("f.xml").diagnostics(List.of(diag)).fixActions(List.of()).build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(1).skippedFiles(0).errorCount(1).warningCount(0).infoCount(0)
                .fileResults(List.of(fileResult))
                .build();
        Path output = tempDir.resolve("report2.md");
        exporter.exportToFile(result, "md", output.toString());
        String content = Files.readString(output, StandardCharsets.UTF_8);
        assertTrue(content.contains("# DSL 诊断报告"));
    }

    @Test
    void exportToFileWritesTerminalFormat() throws IOException {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "f.xml", 1, 0, "E1", "err");
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("f.xml").diagnostics(List.of(diag)).fixActions(List.of()).build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(1).skippedFiles(0).errorCount(1).warningCount(0).infoCount(0)
                .fileResults(List.of(fileResult))
                .build();
        Path output = tempDir.resolve("report.txt");
        exporter.exportToFile(result, "terminal", output.toString());
        String content = Files.readString(output, StandardCharsets.UTF_8);
        assertTrue(content.contains("f.xml:1:0: error: err [E1]"));
    }

    @Test
    void exportToFileThrowsOnUnsupportedFormat() {
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(0).skippedFiles(0).errorCount(0).warningCount(0).infoCount(0)
                .fileResults(List.of())
                .build();
        assertThrows(BatchInspectionException.class,
                () -> exporter.exportToFile(result, "xml", tempDir.resolve("out.xml").toString()));
    }

    @Test
    void exportToFileThrowsOnIoError() {
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(0).skippedFiles(0).errorCount(0).warningCount(0).infoCount(0)
                .fileResults(List.of())
                .build();
        String invalidPath = "/nonexistent/impossible/path/report.txt";
        assertThrows(BatchInspectionException.class,
                () -> exporter.exportToFile(result, "json", invalidPath));
    }
}
