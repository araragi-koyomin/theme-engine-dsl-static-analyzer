# ReportExporter 报告导出 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement M7 ReportExporter to convert BatchInspectionResult into JSON, Markdown, Terminal formats with exit code calculation.

**Architecture:** Single ReportExporterImpl class delegates Terminal output to existing TerminalFormatter, JSON to JsonReportSerializer utility, and Markdown via inline StringBuilder. ExitCodeCalculator is a separate static utility class. All classes in core/batchinspection package, no IDEA SDK dependency.

**Tech Stack:** Java 17, GSON 2.9.0 (already in project), Lombok, JUnit 5

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `core/batchinspection/ReportExporter.java` | Create | Interface with 4 methods: exportMarkdown, exportJson, exportTerminal, exportToFile |
| `core/batchinspection/ReportExporterImpl.java` | Create | Single implementation: delegates terminal to TerminalFormatter, json to JsonReportSerializer, markdown via StringBuilder |
| `core/batchinspection/JsonReportSerializer.java` | Create | GSON JsonObject builder utility; manual JSON construction for field name mismatch (errorCount→errors, filePath→file) |
| `core/batchinspection/ExitCodeCalculator.java` | Create | Static utility: compute(BatchInspectionResult)→0/1, computeFromException(Throwable)→2 |
| `core/batchinspection/ReportExporterImplTest.java` | Create | Tests for all 4 export methods + constructor null checks |
| `core/batchinspection/JsonReportSerializerTest.java` | Create | Tests for JSON serialization details (field names, severity, suggestedFixes, single/multi file) |
| `core/batchinspection/ExitCodeCalculatorTest.java` | Create | Tests for exit code computation |

Base path: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/` (source) and `feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/` (test).

---

### Task 1: ExitCodeCalculator

**Files:**
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/ExitCodeCalculatorTest.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/ExitCodeCalculator.java`

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.ExitCodeCalculatorTest"`
Expected: FAIL — ExitCodeCalculator class not found

- [ ] **Step 3: Write minimal implementation**

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.ExitCodeCalculatorTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/ExitCodeCalculator.java feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/ExitCodeCalculatorTest.java
git commit -m "feat(M7): ExitCodeCalculator 退出码计算工具类"
```

---

### Task 2: ReportExporter Interface

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/ReportExporter.java`

- [ ] **Step 1: Write the interface**

```java
package com.huawei.theme.analysis.core.batchinspection;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;

public interface ReportExporter {
    String exportMarkdown(BatchInspectionResult result);
    String exportJson(BatchInspectionResult result);
    String exportTerminal(BatchInspectionResult result);
    void exportToFile(BatchInspectionResult result, String format, String outputPath);
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :feature:analysis:compileJava`
Expected: PASS — interface compiles

- [ ] **Step 3: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/ReportExporter.java
git commit -m "feat(M7): ReportExporter 接口定义"
```

---

### Task 3: JsonReportSerializer

**Files:**
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/JsonReportSerializerTest.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/JsonReportSerializer.java`

- [ ] **Step 1: Write the failing test**

```java
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
        assertFalse(json.contains("\"file\":"));
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
        assertTrue(json.contains("\"severity\":\"error\""));
        assertFalse(json.contains("\"severity\":\"ERROR\""));
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
        assertTrue(json.contains("\"suggestedFixes\":[]"));
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
        assertTrue(json.contains("\"errors\":2"));
        assertTrue(json.contains("\"warnings\":3"));
        assertTrue(json.contains("\"info\":4"));
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
        assertTrue(json.contains("\"severity\":\"error\""));
        assertTrue(json.contains("\"line\":42"));
        assertTrue(json.contains("\"col\":7"));
        assertTrue(json.contains("\"ruleId\":\"SEM-REF-001\""));
        assertTrue(json.contains("\"message\":\"undefined var\""));
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
        assertTrue(json.contains("\"file\":\"clean.xml\""));
        assertTrue(json.contains("\"diagnostics\":[]"));
        assertTrue(json.contains("\"file\":\"dirty.xml\""));
    }

    @Test
    void multiFileGlobalSummaryIncludesTotalFilesAndSkipped() {
        Diagnostic diag = createDiagnostic(DiagnosticSeverity.ERROR, "a.xml", 1, 0, "E1", "err");
        FileDiagnosticResult fileResult = FileDiagnosticResult.builder()
                .filePath("a.xml")
                .diagnostics(List.of(diag))
                .fixActions(List.of())
                .build();
        BatchInspectionResult result = BatchInspectionResult.builder()
                .totalFiles(5)
                .skippedFiles(3)
                .errorCount(1)
                .warningCount(0)
                .infoCount(0)
                .fileResults(List.of(fileResult))
                .build();
        String json = serializer.serialize(result);
        assertTrue(json.contains("\"totalFiles\":5"));
        assertTrue(json.contains("\"skippedFiles\":3"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.JsonReportSerializerTest"`
Expected: FAIL — JsonReportSerializer class not found

- [ ] **Step 3: Write minimal implementation**

```java
package com.huawei.theme.analysis.core.batchinspection;

import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.batchinspection.model.FileDiagnosticResult;
import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.shared.diagnostic.adapter.DiagnosticSeverityAdapter;

public class JsonReportSerializer {

    private final com.google.gson.Gson gson;

    public JsonReportSerializer() {
        this.gson = new GsonBuilder()
                .registerTypeAdapter(DiagnosticSeverity.class, new DiagnosticSeverityAdapter())
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
    }

    public String serialize(BatchInspectionResult result) {
        if (result.getFileResults().size() == 1) {
            return gson.toJson(buildSingleFileJson(result));
        }
        return gson.toJson(buildMultiFileJson(result));
    }

    private JsonObject buildSingleFileJson(BatchInspectionResult result) {
        JsonObject root = new JsonObject();
        FileDiagnosticResult fileResult = result.getFileResults().get(0);
        root.addProperty("file", fileResult.getFilePath());
        root.add("diagnostics", buildDiagnosticsArray(fileResult.getDiagnostics()));
        root.add("summary", buildFileSummary(result.getErrorCount(), result.getWarningCount(), result.getInfoCount()));
        return root;
    }

    private JsonObject buildMultiFileJson(BatchInspectionResult result) {
        JsonObject root = new JsonObject();
        JsonArray filesArray = new JsonArray();
        for (FileDiagnosticResult fileResult : result.getFileResults()) {
            JsonObject fileObj = new JsonObject();
            fileObj.addProperty("file", fileResult.getFilePath());
            fileObj.add("diagnostics", buildDiagnosticsArray(fileResult.getDiagnostics()));
            int fileErrors = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.ERROR);
            int fileWarnings = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.WARNING);
            int fileInfos = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.INFO);
            fileObj.add("summary", buildFileSummary(fileErrors, fileWarnings, fileInfos));
            filesArray.add(fileObj);
        }
        root.add("files", filesArray);
        JsonObject globalSummary = buildFileSummary(result.getErrorCount(), result.getWarningCount(), result.getInfoCount());
        globalSummary.addProperty("totalFiles", result.getTotalFiles());
        globalSummary.addProperty("skippedFiles", result.getSkippedFiles());
        root.add("summary", globalSummary);
        return root;
    }

    private JsonArray buildDiagnosticsArray(List<Diagnostic> diagnostics) {
        JsonArray array = new JsonArray();
        if (diagnostics == null) {
            return array;
        }
        for (Diagnostic diag : diagnostics) {
            JsonObject diagObj = new JsonObject();
            diagObj.addProperty("severity", diag.getSeverity().name().toLowerCase());
            diagObj.addProperty("line", diag.getLine());
            diagObj.addProperty("col", diag.getColumn());
            diagObj.addProperty("ruleId", diag.getRuleId());
            diagObj.addProperty("message", diag.getMessage());
            JsonArray fixesArray = new JsonArray();
            if (diag.getSuggestedFixes() != null) {
                for (SuggestedFix fix : diag.getSuggestedFixes()) {
                    fixesArray.add(fix.getText());
                }
            }
            diagObj.add("suggestedFixes", fixesArray);
            array.add(diagObj);
        }
        return array;
    }

    private JsonObject buildFileSummary(int errors, int warnings, int infos) {
        JsonObject summary = new JsonObject();
        summary.addProperty("errors", errors);
        summary.addProperty("warnings", warnings);
        summary.addProperty("info", infos);
        return summary;
    }

    private int countBySeverity(List<Diagnostic> diagnostics, DiagnosticSeverity severity) {
        if (diagnostics == null) {
            return 0;
        }
        return (int) diagnostics.stream()
                .filter(d -> d.getSeverity() == severity)
                .count();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.JsonReportSerializerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/JsonReportSerializer.java feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/JsonReportSerializerTest.java
git commit -m "feat(M7): JsonReportSerializer JSON报告序列化工具类"
```

---

### Task 4: ReportExporterImpl — Terminal and Markdown export

**Files:**
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/ReportExporterImplTest.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/ReportExporterImpl.java`

- [ ] **Step 1: Write the failing test for exportTerminal**

```java
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
        assertTrue(md.contains("3 files, 1 skipped, 1 error, 1 warning, 1 info"));
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.ReportExporterImplTest"`
Expected: FAIL — ReportExporterImpl class not found

- [ ] **Step 3: Write minimal implementation (exportTerminal + exportMarkdown)**

```java
package com.huawei.theme.analysis.core.batchinspection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.batchinspection.model.FileDiagnosticResult;
import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

public class ReportExporterImpl implements ReportExporter {

    private final TerminalFormatter terminalFormatter;
    private final JsonReportSerializer jsonSerializer;

    public ReportExporterImpl(TerminalFormatter terminalFormatter) {
        this.terminalFormatter = Objects.requireNonNull(terminalFormatter, "terminalFormatter must not be null");
        this.jsonSerializer = new JsonReportSerializer();
    }

    @Override
    public String exportTerminal(BatchInspectionResult result) {
        Objects.requireNonNull(result, "result must not be null");
        return terminalFormatter.formatFullReport(result);
    }

    @Override
    public String exportJson(BatchInspectionResult result) {
        Objects.requireNonNull(result, "result must not be null");
        return jsonSerializer.serialize(result);
    }

    @Override
    public String exportMarkdown(BatchInspectionResult result) {
        Objects.requireNonNull(result, "result must not be null");
        StringBuilder sb = new StringBuilder();
        sb.append("# DSL 诊断报告\n\n");

        Map<DiagnosticSeverity, List<Diagnostic>> bySeverity = groupBySeverity(result);
        appendSeveritySection(sb, DiagnosticSeverity.ERROR, "Error", bySeverity);
        appendSeveritySection(sb, DiagnosticSeverity.WARNING, "Warning", bySeverity);
        appendSeveritySection(sb, DiagnosticSeverity.INFO, "Info", bySeverity);

        sb.append("---\n\n");
        sb.append("## 汇总\n\n");
        appendSummaryTable(sb, result);
        appendTotalLine(sb, result);
        return sb.toString();
    }

    @Override
    public void exportToFile(BatchInspectionResult result, String format, String outputPath) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        String content;
        switch (format.toLowerCase()) {
            case "json":
                content = exportJson(result);
                break;
            case "markdown":
                content = exportMarkdown(result);
                break;
            case "md":
                content = exportMarkdown(result);
                break;
            case "terminal":
                content = exportTerminal(result);
                break;
            default:
                throw new BatchInspectionException("Unsupported format: " + format);
        }
        try {
            Files.writeString(Path.of(outputPath), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BatchInspectionException("Failed to write report to: " + outputPath, e);
        }
    }

    private Map<DiagnosticSeverity, List<Diagnostic>> groupBySeverity(BatchInspectionResult result) {
        Map<DiagnosticSeverity, List<Diagnostic>> map = new HashMap<>();
        map.put(DiagnosticSeverity.ERROR, new ArrayList<>());
        map.put(DiagnosticSeverity.WARNING, new ArrayList<>());
        map.put(DiagnosticSeverity.INFO, new ArrayList<>());
        for (FileDiagnosticResult fileResult : result.getFileResults()) {
            if (fileResult.getDiagnostics() != null) {
                for (Diagnostic diag : fileResult.getDiagnostics()) {
                    map.getOrDefault(diag.getSeverity(), new ArrayList<>()).add(diag);
                }
            }
        }
        return map;
    }

    private void appendSeveritySection(StringBuilder sb, DiagnosticSeverity severity,
                                        String label, Map<DiagnosticSeverity, List<Diagnostic>> bySeverity) {
        sb.append("## ").append(label).append(" 级别问题\n\n");
        List<Diagnostic> diagnostics = bySeverity.getOrDefault(severity, List.of());
        if (diagnostics.isEmpty()) {
            sb.append("无 ").append(label).append(" 级别问题\n\n");
            return;
        }
        Map<String, List<Diagnostic>> byFile = diagnostics.stream()
                .collect(Collectors.groupingBy(Diagnostic::getFilePath));
        List<String> sortedFiles = byFile.keySet().stream().sorted().collect(Collectors.toList());
        for (String filePath : sortedFiles) {
            sb.append("### ").append(filePath).append("\n\n");
            List<Diagnostic> fileDiags = byFile.get(filePath).stream()
                    .sorted(Comparator.comparingInt(Diagnostic::getLine))
                    .collect(Collectors.toList());
            for (Diagnostic diag : fileDiags) {
                sb.append("- **").append(diag.getRuleId()).append("** (line ")
                        .append(diag.getLine()).append(", col ").append(diag.getColumn())
                        .append("): ").append(diag.getMessage()).append("\n");
                if (diag.getSuggestedFixes() != null && !diag.getSuggestedFixes().isEmpty()) {
                    for (SuggestedFix fix : diag.getSuggestedFixes()) {
                        sb.append("  - 建议修复: ").append(fix.getText()).append("\n");
                    }
                }
            }
            sb.append("\n");
        }
    }

    private void appendSummaryTable(StringBuilder sb, BatchInspectionResult result) {
        sb.append("| 文件 | Error | Warning | Info |\n");
        sb.append("|------|-------|---------|------|\n");
        for (FileDiagnosticResult fileResult : result.getFileResults()) {
            int errors = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.ERROR);
            int warnings = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.WARNING);
            int infos = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.INFO);
            sb.append("| ").append(fileResult.getFilePath())
                    .append(" | ").append(errors)
                    .append(" | ").append(warnings)
                    .append(" | ").append(infos).append(" |\n");
        }
    }

    private void appendTotalLine(StringBuilder sb, BatchInspectionResult result) {
        sb.append("\n**总计**: ").append(result.getTotalFiles()).append(" files, ")
                .append(result.getSkippedFiles()).append(" skipped, ")
                .append(result.getErrorCount()).append(" errors, ")
                .append(result.getWarningCount()).append(" warnings, ")
                .append(result.getInfoCount()).append(" info\n");
    }

    private int countBySeverity(List<Diagnostic> diagnostics, DiagnosticSeverity severity) {
        if (diagnostics == null) {
            return 0;
        }
        return (int) diagnostics.stream()
                .filter(d -> d.getSeverity() == severity)
                .count();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.ReportExporterImplTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/ReportExporterImpl.java feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/ReportExporterImplTest.java
git commit -m "feat(M7): ReportExporterImpl 终端和Markdown导出实现"
```

---

### Task 5: ReportExporterImpl — JSON export and exportToFile tests

**Files:**
- Modify: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/ReportExporterImplTest.java` — add JSON and exportToFile tests

- [ ] **Step 1: Add JSON export test to ReportExporterImplTest**

Append the following test methods to `ReportExporterImplTest.java`:

```java
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
        assertTrue(json.contains("\"severity\":\"error\""));
        assertTrue(json.contains("\"ruleId\":\"SEM-REF-001\""));
        assertTrue(json.contains("\"errors\":1"));
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
        assertTrue(json.contains("\"totalFiles\":2"));
        assertTrue(json.contains("\"skippedFiles\":1"));
        assertFalse(json.contains("\"file\":"));
    }
```

- [ ] **Step 2: Add exportToFile tests to ReportExporterImplTest**

Append the following test methods:

```java
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
        assertTrue(content.contains("\"severity\":\"error\""));
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
```

- [ ] **Step 3: Run tests to verify all pass**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.ReportExporterImplTest"`
Expected: PASS (all 12+ test methods)

- [ ] **Step 4: Commit**

```bash
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/ReportExporterImplTest.java
git commit -m "feat(M7): ReportExporterImpl JSON导出和文件导出测试"
```

---

### Task 6: Full test suite verification

**Files:**
- No new files — run all tests to verify everything works together

- [ ] **Step 1: Run full batchinspection module tests**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.*"`
Expected: PASS — all existing + new tests pass

- [ ] **Step 2: Run full project tests**

Run: `./gradlew :feature:analysis:test`
Expected: PASS — no regressions

- [ ] **Step 3: Verify no com.intellij imports in core**

Run: `grep -r "com.intellij" feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/` (use Grep tool)
Expected: No matches found — core layer isolation verified

---

## Self-Review

**Spec coverage check:**
- ReportExporter interface (4 methods) → Task 2 ✓
- ReportExporterImpl (delegates TerminalFormatter + JsonReportSerializer + inline Markdown) → Task 4 ✓
- JsonReportSerializer (GSON JsonObject building) → Task 3 ✓
- ExitCodeCalculator (0/1/2) → Task 1 ✓
- JSON format (single/multi file structure, field names, severity lowercase) → Task 3 + Task 5 ✓
- Markdown format (severity groups, file aggregation, summary table) → Task 4 ✓
- Terminal format (delegate to TerminalFormatter) → Task 4 ✓
- exportToFile (format routing + file write + exceptions) → Task 5 ✓
- Testing (3 formats each 1 test, exit code, JSON details) → Tasks 1,3,4,5 ✓

**Placeholder scan:** No TBD/TODO/implement-later patterns. All code blocks contain complete implementation code. ✓

**Type consistency check:**
- `ReportExporter` interface methods match `ReportExporterImpl` signatures ✓
- `TerminalFormatter` constructor matches test setup (boolean noColor) ✓
- `JsonReportSerializer` serialize method matches `ReportExporterImpl.exportJson` delegation ✓
- `BatchInspectionResult.builder()` fields match model (totalFiles/skippedFiles/errorCount/warningCount/infoCount/fileResults) ✓
- `FileDiagnosticResult.builder()` fields match model (filePath/diagnostics/fixActions) ✓
