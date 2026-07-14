package com.huawei.theme.analysis.core.batchinspection;

import java.util.ArrayList;
import java.util.List;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.batchinspection.model.FileDiagnosticResult;
import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.shared.model.FixActionType;
import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalFormatterTest {

    private final TerminalFormatter colorFormatter = new TerminalFormatter(false);
    private final TerminalFormatter noColorFormatter = new TerminalFormatter(true);

    private Diagnostic createDiagnostic(DiagnosticSeverity severity, String filePath, int line, int column, String ruleId, String message) {
        return Diagnostic.builder()
                .severity(severity)
                .ruleId(ruleId)
                .message(message)
                .filePath(filePath)
                .line(line)
                .column(column)
                .build();
    }

    private Diagnostic createDiagnosticWithFixes(
            DiagnosticSeverity severity, String filePath, int line, int column,
            String ruleId, String message, List<SuggestedFix> fixes) {
        return Diagnostic.builder()
                .severity(severity)
                .ruleId(ruleId)
                .message(message)
                .filePath(filePath)
                .line(line)
                .column(column)
                .suggestedFixes(fixes)
                .build();
    }

    @Test
    void formatDiagnosticWithColorError() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "theme.xml", 15, 3, "SEM-REF-001", "引用未定义变量 #steps_value");
        String result = colorFormatter.formatDiagnostic(diag);
        assertTrue(result.contains("\u001B[31m"));
        assertTrue(result.contains("\u001B[0m"));
        assertTrue(result.contains("theme.xml:15:3: error: 引用未定义变量 #steps_value [SEM-REF-001]"));
    }

    @Test
    void formatDiagnosticWithColorWarning() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.WARNING, "theme.xml", 20, 5, "SEM-SCOPE-001", "scope not allowed");
        String result = colorFormatter.formatDiagnostic(diag);
        assertTrue(result.contains("\u001B[33m"));
        assertTrue(result.contains("\u001B[0m"));
        assertTrue(result.contains("warning"));
    }

    @Test
    void formatDiagnosticWithColorInfo() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.INFO, "theme.xml", 25, 1, "SEM-INFO-001", "info message");
        String result = colorFormatter.formatDiagnostic(diag);
        assertTrue(result.contains("\u001B[34m"));
        assertTrue(result.contains("\u001B[0m"));
        assertTrue(result.contains("info"));
    }

    @Test
    void formatDiagnosticNoColorMode() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "theme.xml", 15, 3, "SEM-REF-001", "引用未定义变量 #steps_value");
        String result = noColorFormatter.formatDiagnostic(diag);
        assertFalse(result.contains("\u001B["));
        assertEquals("theme.xml:15:3: error: 引用未定义变量 #steps_value [SEM-REF-001]", result);
    }

    @Test
    void formatDiagnosticNoColorWarning() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.WARNING, "layout.xml", 8, 2, "SEM-SCOPE-001", "scope warning");
        String result = noColorFormatter.formatDiagnostic(diag);
        assertFalse(result.contains("\u001B["));
        assertEquals("layout.xml:8:2: warning: scope warning [SEM-SCOPE-001]", result);
    }

    @Test
    void formatDiagnosticNoColorInfo() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.INFO, "config.xml", 100, 50, "SEM-INFO-001", "info note");
        String result = noColorFormatter.formatDiagnostic(diag);
        assertFalse(result.contains("\u001B["));
        assertEquals("config.xml:100:50: info: info note [SEM-INFO-001]", result);
    }

    @Test
    void formatSuggestedFixes() {
        List<SuggestedFix> fixes = List.of(
                SuggestedFix.builder().text("声明Var name=\"steps_value\"").build(),
                SuggestedFix.builder().text("使用全局变量替代").build()
        );
        String result = noColorFormatter.formatSuggestedFixes(fixes);
        assertTrue(result.contains("建议修复: 声明Var name=\"steps_value\""));
        assertTrue(result.contains("建议修复: 使用全局变量替代"));
    }

    @Test
    void formatSuggestedFixesEmptyList() {
        String result = noColorFormatter.formatSuggestedFixes(List.of());
        assertEquals("", result);
    }

    @Test
    void formatSuggestedFixesNull() {
        String result = noColorFormatter.formatSuggestedFixes(null);
        assertEquals("", result);
    }

    @Test
    void formatSuggestedFixesWithColor() {
        List<SuggestedFix> fixes = List.of(
                SuggestedFix.builder().text("修复方案A").build()
        );
        String result = colorFormatter.formatSuggestedFixes(fixes);
        assertTrue(result.contains("建议修复: 修复方案A"));
    }

    @Test
    void formatSuggestedFixesMultiple() {
        List<SuggestedFix> fixes = List.of(
                SuggestedFix.builder().text("方案1").build(),
                SuggestedFix.builder().text("方案2").build(),
                SuggestedFix.builder().text("方案3").build()
        );
        String result = noColorFormatter.formatSuggestedFixes(fixes);
        String[] lines = result.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[0].contains("方案1"));
        assertTrue(lines[1].contains("方案2"));
        assertTrue(lines[2].contains("方案3"));
    }

    @Test
    void formatSummary() {
        BatchInspectionResult batchResult = BatchInspectionResult.builder()
                .errorCount(3)
                .warningCount(1)
                .infoCount(2)
                .build();
        String result = noColorFormatter.formatSummary(batchResult);
        assertEquals("3 errors, 1 warnings, 2 info", result);
    }

    @Test
    void formatSummaryZeroCounts() {
        BatchInspectionResult batchResult = BatchInspectionResult.builder()
                .errorCount(0)
                .warningCount(0)
                .infoCount(0)
                .build();
        String result = noColorFormatter.formatSummary(batchResult);
        assertEquals("0 errors, 0 warnings, 0 info", result);
    }

    @Test
    void formatSummaryLargeCounts() {
        BatchInspectionResult batchResult = BatchInspectionResult.builder()
                .errorCount(100)
                .warningCount(50)
                .infoCount(25)
                .build();
        String result = noColorFormatter.formatSummary(batchResult);
        assertEquals("100 errors, 50 warnings, 25 info", result);
    }

    @Test
    void formatFileResultWithSingleDiagnostic() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "theme.xml", 15, 3, "SEM-REF-001", "引用未定义变量");
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("theme.xml")
                .diagnostics(List.of(diag))
                .fixActions(List.of())
                .build();
        String result = noColorFormatter.formatFileResult(fileResult);
        assertTrue(result.contains("theme.xml:15:3: error: 引用未定义变量 [SEM-REF-001]"));
        assertTrue(result.contains("1 errors, 0 warnings, 0 info"));
    }

    @Test
    void formatFileResultWithSuggestedFixes() {
        Diagnostic diag = createDiagnosticWithFixes(
                DiagnosticSeverity.ERROR, "theme.xml", 15, 3, "SEM-REF-001", "引用未定义变量",
                List.of(
                        SuggestedFix.builder().text("声明Var name=\"x\"").build(),
                        SuggestedFix.builder().text("使用全局变量 $x").build()
                )
        );
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("theme.xml")
                .diagnostics(List.of(diag))
                .fixActions(List.of())
                .build();
        String result = noColorFormatter.formatFileResult(fileResult);
        assertTrue(result.contains("theme.xml:15:3: error: 引用未定义变量 [SEM-REF-001]"));
        assertTrue(result.contains("建议修复: 声明Var name=\"x\""));
        assertTrue(result.contains("建议修复: 使用全局变量 $x"));
    }

    @Test
    void formatFileResultWithMixedSeveritySortedBySeverity() {
        Diagnostic infoDiag = createDiagnostic(DiagnosticSeverity.INFO, "theme.xml", 30, 1, "SEM-INFO-001", "info note");
        Diagnostic warningDiag = createDiagnostic(DiagnosticSeverity.WARNING, "theme.xml", 20, 5, "SEM-SCOPE-001", "scope warning");
        Diagnostic errorDiag = createDiagnostic(DiagnosticSeverity.ERROR, "theme.xml", 10, 3, "SEM-REF-001", "ref error");
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("theme.xml")
                .diagnostics(List.of(infoDiag, warningDiag, errorDiag))
                .fixActions(List.of())
                .build();
        String result = noColorFormatter.formatFileResult(fileResult);
        int errorPos = result.indexOf("error:");
        int warningPos = result.indexOf("warning:");
        int infoPos = result.indexOf("info:");
        assertTrue(errorPos < warningPos);
        assertTrue(warningPos < infoPos);
        assertTrue(result.contains("1 errors, 1 warnings, 1 info"));
    }

    @Test
    void formatFileResultWithDiagnosticsSortedByLine() {
        Diagnostic diag10 = createDiagnostic(DiagnosticSeverity.ERROR, "theme.xml", 10, 0, "E1", "error line 10");
        Diagnostic diag5 = createDiagnostic(DiagnosticSeverity.ERROR, "theme.xml", 5, 0, "E2", "error line 5");
        Diagnostic diag20 = createDiagnostic(DiagnosticSeverity.ERROR, "theme.xml", 20, 0, "E3", "error line 20");
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("theme.xml")
                .diagnostics(List.of(diag10, diag5, diag20))
                .fixActions(List.of())
                .build();
        String result = noColorFormatter.formatFileResult(fileResult);
        assertTrue(result.indexOf("line 5") < result.indexOf("line 10"));
        assertTrue(result.indexOf("line 10") < result.indexOf("line 20"));
    }

    @Test
    void formatFileResultWithDiagnosticsSortedByFilePath() {
        Diagnostic diagB = createDiagnostic(DiagnosticSeverity.ERROR, "b.xml", 5, 0, "E1", "error in b");
        Diagnostic diagA = createDiagnostic(DiagnosticSeverity.ERROR, "a.xml", 5, 0, "E2", "error in a");
        Diagnostic diagC = createDiagnostic(DiagnosticSeverity.ERROR, "c.xml", 5, 0, "E3", "error in c");
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("mixed")
                .diagnostics(List.of(diagB, diagA, diagC))
                .fixActions(List.of())
                .build();
        String result = noColorFormatter.formatFileResult(fileResult);
        assertTrue(result.indexOf("a.xml") < result.indexOf("b.xml"));
        assertTrue(result.indexOf("b.xml") < result.indexOf("c.xml"));
    }

    @Test
    void formatFullReport() {
        Diagnostic diag1 = createDiagnostic(DiagnosticSeverity.ERROR, "theme.xml", 15, 3, "SEM-REF-001", "引用未定义变量");
        Diagnostic diag2 = createDiagnostic(DiagnosticSeverity.WARNING, "layout.xml", 5, 1, "SEM-SCOPE-001", "scope not allowed");
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
        BatchInspectionResult batchResult = BatchInspectionResult.builder()
                .totalFiles(2)
                .errorCount(1)
                .warningCount(1)
                .infoCount(0)
                .fileResults(List.of(fileResult1, fileResult2))
                .build();
        String report = noColorFormatter.formatFullReport(batchResult);
        assertTrue(report.contains("theme.xml:15:3: error: 引用未定义变量 [SEM-REF-001]"));
        assertTrue(report.contains("layout.xml:5:1: warning: scope not allowed [SEM-SCOPE-001]"));
        assertTrue(report.contains("1 errors, 1 warnings, 0 info"));
    }

    @Test
    void formatFullReportSkipsFilesWithNoDiagnostics() {
        FileDiagnosticResult emptyFile = FileDiagnosticResult.builder()
                .filePath("clean.xml")
                .diagnostics(List.of())
                .fixActions(List.of())
                .build();
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "dirty.xml", 5, 0, "E1", "error");
        FileDiagnosticResult dirtyFile = FileDiagnosticResult.builder()
                .filePath("dirty.xml")
                .diagnostics(List.of(diag))
                .fixActions(List.of())
                .build();
        BatchInspectionResult batchResult = BatchInspectionResult.builder()
                .totalFiles(2)
                .errorCount(1)
                .warningCount(0)
                .infoCount(0)
                .fileResults(List.of(emptyFile, dirtyFile))
                .build();
        String report = noColorFormatter.formatFullReport(batchResult);
        assertFalse(report.contains("clean.xml"));
        assertTrue(report.contains("dirty.xml"));
    }

    @Test
    void formatFullReportSkipsFilesWithNullDiagnostics() {
        FileDiagnosticResult nullDiagFile = FileDiagnosticResult.builder()
                .filePath("null.xml")
                .diagnostics(null)
                .fixActions(List.of())
                .build();
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "has.xml", 5, 0, "E1", "error");
        FileDiagnosticResult hasDiagFile = FileDiagnosticResult.builder()
                .filePath("has.xml")
                .diagnostics(List.of(diag))
                .fixActions(List.of())
                .build();
        BatchInspectionResult batchResult = BatchInspectionResult.builder()
                .totalFiles(2)
                .errorCount(1)
                .warningCount(0)
                .infoCount(0)
                .fileResults(List.of(nullDiagFile, hasDiagFile))
                .build();
        String report = noColorFormatter.formatFullReport(batchResult);
        assertFalse(report.contains("null.xml"));
        assertTrue(report.contains("has.xml"));
    }

    @Test
    void formatFullReportWithMultipleFilesAndMixedSeverity() {
        List<FileDiagnosticResult> fileResults = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Diagnostic diag = createDiagnostic(
                    i % 2 == 0 ? DiagnosticSeverity.WARNING : DiagnosticSeverity.ERROR,
                    "file" + i + ".xml", i * 10, i, "RULE-" + i, "msg" + i
            );
            fileResults.add(FileDiagnosticResult.builder()
                    .filePath("file" + i + ".xml")
                    .diagnostics(List.of(diag))
                    .fixActions(List.of())
                    .build());
        }
        BatchInspectionResult batchResult = BatchInspectionResult.builder()
                .totalFiles(5)
                .errorCount(3)
                .warningCount(2)
                .infoCount(0)
                .fileResults(fileResults)
                .build();
        String report = noColorFormatter.formatFullReport(batchResult);
        assertTrue(report.contains("3 errors, 2 warnings, 0 info"));
        assertTrue(report.contains("file1.xml"));
        assertTrue(report.contains("file5.xml"));
    }

    @Test
    void formatFullReportAllClean() {
        List<FileDiagnosticResult> fileResults = List.of(
                FileDiagnosticResult.builder().filePath("a.xml").diagnostics(List.of()).fixActions(List.of()).build(),
                FileDiagnosticResult.builder().filePath("b.xml").diagnostics(List.of()).fixActions(List.of()).build()
        );
        BatchInspectionResult batchResult = BatchInspectionResult.builder()
                .totalFiles(2)
                .errorCount(0)
                .warningCount(0)
                .infoCount(0)
                .fileResults(fileResults)
                .build();
        String report = noColorFormatter.formatFullReport(batchResult);
        assertFalse(report.contains("a.xml"));
        assertFalse(report.contains("b.xml"));
        assertTrue(report.contains("0 errors, 0 warnings, 0 info"));
    }

    @Test
    void formatDiagnosticWithChineseMessage() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "中文文件名.xml", 1, 1, "SEM-REF-001", "属性值\"亮度\"不在允许范围内");
        String result = noColorFormatter.formatDiagnostic(diag);
        assertTrue(result.contains("中文文件名.xml"));
        assertTrue(result.contains("属性值\"亮度\"不在允许范围内"));
    }

    @Test
    void colorFormatterWrapsEachSeverityCorrectly() {
        Diagnostic errorDiag = createDiagnostic(DiagnosticSeverity.ERROR, "e.xml", 1, 0, "E", "msg");
        Diagnostic warningDiag = createDiagnostic(DiagnosticSeverity.WARNING, "w.xml", 1, 0, "W", "msg");
        Diagnostic infoDiag = createDiagnostic(DiagnosticSeverity.INFO, "i.xml", 1, 0, "I", "msg");

        String errorResult = colorFormatter.formatDiagnostic(errorDiag);
        assertTrue(errorResult.startsWith("\u001B[31m"));
        assertTrue(errorResult.endsWith("\u001B[0m"));

        String warningResult = colorFormatter.formatDiagnostic(warningDiag);
        assertTrue(warningResult.startsWith("\u001B[33m"));
        assertTrue(warningResult.endsWith("\u001B[0m"));

        String infoResult = colorFormatter.formatDiagnostic(infoDiag);
        assertTrue(infoResult.startsWith("\u001B[34m"));
        assertTrue(infoResult.endsWith("\u001B[0m"));
    }

    @Test
    void formatDiagnosticContainsAllComponents() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "path/to/theme.xml", 42, 7, "SEM-REF-001", "undefined var");
        String result = noColorFormatter.formatDiagnostic(diag);
        assertTrue(result.contains("path/to/theme.xml"));
        assertTrue(result.contains(":42:7:"));
        assertTrue(result.contains("error"));
        assertTrue(result.contains("undefined var"));
        assertTrue(result.contains("[SEM-REF-001]"));
    }

    @Test
    void formatDiagnosticEndLineEndColumnIgnoredInFormat() {
        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("E1")
                .message("msg")
                .filePath("f.xml")
                .line(1)
                .column(0)
                .endLine(3)
                .endColumn(10)
                .build();
        String result = noColorFormatter.formatDiagnostic(diag);
        assertTrue(result.contains("f.xml:1:0"));
        assertFalse(result.contains("3:10"));
    }
}
