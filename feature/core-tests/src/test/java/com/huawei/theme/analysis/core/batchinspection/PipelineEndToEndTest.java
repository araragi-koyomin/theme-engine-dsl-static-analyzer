package com.huawei.theme.analysis.core.batchinspection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.batchinspection.model.FileDiagnosticResult;
import com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary;
import com.huawei.theme.analysis.core.fileidentification.DslFileIdentifier;
import com.huawei.theme.analysis.core.fileidentification.DslFileMatcher;
import com.huawei.theme.analysis.core.function.JsonFunctionSignatureLoader;
import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionRegistry;
import com.huawei.theme.analysis.core.quickfix.QuickFixProvider;
import com.huawei.theme.analysis.core.quickfix.QuickFixProviderImpl;
import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.AnalyzerRegistry;
import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.cli.PipelineMode;
import com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProvider;
import com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProviderImpl;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilder;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilderImpl;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;
import com.huawei.theme.analysis.core.syntaxanalysis.DslAstProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineEndToEndTest {

    private static RuleRepository ruleRepo;
    private static BatchInspectionRunner runner;
    private static ReportExporterImpl exporter;
    private static TerminalFormatter noColorFormatter;
    private static TerminalFormatter colorFormatter;
    private static JsonReportSerializer jsonSerializer;
    private static Path fixturesDir;
    private Path tempOutputDir;

    @BeforeAll
    static void setup() {
        String rulesDir = System.getProperty("user.dir") + "/src/main/resources/rules";
        FunctionSignatureLibrary functionLibrary = new JsonFunctionSignatureLoader().loadFromClasspath();
        ruleRepo = new JsonRuleLoader().loadFromDirectory(rulesDir, functionLibrary);
        DslFileMatcher fileMatcher = new DslFileIdentifier(ruleRepo);
        DslAstProvider astProvider = new AstBuilder(ruleRepo);
        DiagnosticProvider diagnosticProvider = new DiagnosticProviderImpl();
        SymbolTableBuilder symbolTableBuilder = new SymbolTableBuilderImpl();
        AnalyzerRegistry.init();
        FixActionRegistry.init(ruleRepo);
        QuickFixProvider quickFixProvider = new QuickFixProviderImpl();
        runner = new BatchInspectionRunnerImpl(
                fileMatcher, astProvider, diagnosticProvider,
                quickFixProvider, symbolTableBuilder, ruleRepo,
                InspectionConfig.builder().pipelineMode(PipelineMode.FULL).typeCheck(true).build());
        noColorFormatter = new TerminalFormatter(true);
        colorFormatter = new TerminalFormatter(false);
        jsonSerializer = new JsonReportSerializer();
        exporter = new ReportExporterImpl(noColorFormatter);
        fixturesDir = Paths.get(System.getProperty("user.dir") + "/src/test/resources/fixtures/e2e-pipeline");
    }

    @BeforeEach
    void createTempDir() throws IOException {
        tempOutputDir = Files.createTempDirectory("e2e-pipeline-test");
    }

    @AfterEach
    void cleanupTempDir() throws IOException {
        Files.walk(tempOutputDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
            try { Files.deleteIfExists(p); } catch (IOException e) { }
        });
    }

    @Test
    void directoryScanCoversAllDslFilesAndSkipsNonDsl() {
        BatchInspectionResult result = runner.runOnDirectory(fixturesDir.toString());
        assertTrue(result.getTotalFiles() >= 5,
                "Expected at least 5 DSL files, got " + result.getTotalFiles());
        assertTrue(result.getSkippedFiles() >= 1,
                "Expected at least 1 skipped non-DSL XML, got " + result.getSkippedFiles());
        assertFalse(result.getFileResults().stream()
                .anyMatch(fr -> fr.getFilePath().contains("data.json")),
                "JSON files should never appear in results");
    }

    @Test
    void exitCodeZeroForCleanFile() {
        // FIX004 C7: was theater — accepted exit 1 + errorCount<=2 for a "clean"
        // file, encoding false positives as OK. Canary: inject 1 false-positive
        // ERROR per file → original test took else branch, errorCount<=2 passed
        // + exitCode==1 passed = theater confirmed. Now: strict — clean file
        // must have ZERO errors and exit 0 (no false positives tolerated).
        Path cleanFile = fixturesDir.resolve("clean").resolve("lockscreen_valid.xml");
        BatchInspectionResult result = runner.runOnFile(cleanFile.toString());
        assertEquals(0, result.getErrorCount(),
                "clean file must produce ZERO errors (no false positives tolerated); got: "
                        + result.getFileResults().get(0).getDiagnostics());
        assertEquals(0, ExitCodeCalculator.compute(result),
                "clean file with 0 errors must produce exit code 0");
    }

    @Test
    void exitCodeOneForMultiErrorFile() {
        Path errorFile = fixturesDir.resolve("lockscreen_type_and_ref.xml");
        BatchInspectionResult result = runner.runOnFile(errorFile.toString());
        assertTrue(result.getErrorCount() > 0,
                "Multi-error file should have at least 1 error");
        assertEquals(1, ExitCodeCalculator.compute(result),
                "File with errors should produce exit code 1");
    }

    @Test
    void exitCodeTwoFromExceptionPath() {
        BatchInspectionException ex = assertThrows(BatchInspectionException.class,
                () -> runner.runOnFile("/nonexistent/path/fake.xml"));
        assertEquals(2, ExitCodeCalculator.computeFromException(ex));
    }

    @Test
    void allFormatsContainSameRuleIdsForDirectory() {
        // FIX004 b2 P1: was theater — guard `if (fileResults.isEmpty() ||
        // errorCount==0) return;` skipped all assertions when analyzer produced
        // 0 diagnostics, masking silent analyzer failure. Canary: mutate
        // DiagnosticProviderImpl to return empty → original test passed = theater
        // confirmed. Now: strict — directory must produce errors, then verify
        // formats contain matching rule IDs.
        BatchInspectionResult result = runner.runOnDirectory(fixturesDir.toString());
        assertFalse(result.getFileResults().isEmpty(),
                "directory scan should produce file results; got empty");
        assertTrue(result.getErrorCount() > 0,
                "fixtures directory should produce errors; got: " + result.getErrorCount());
        Set<String> ruleIdsFromJson = extractRuleIdsFromJson(exporter.exportJson(result));
        Set<String> ruleIdsFromTerminal = extractRuleIdsFromTerminal(noColorFormatter.formatFullReport(result));
        Set<String> ruleIdsFromMarkdown = extractRuleIdsFromMarkdown(exporter.exportMarkdown(result));
        assertTrue(ruleIdsFromJson.size() > 0, "JSON should contain rule IDs");
        assertTrue(ruleIdsFromTerminal.size() > 0, "Terminal should contain rule IDs");
        assertTrue(ruleIdsFromMarkdown.size() > 0, "Markdown should contain rule IDs");
        assertTrue(ruleIdsFromJson.equals(ruleIdsFromTerminal),
                "JSON and Terminal rule IDs differ: json=" + ruleIdsFromJson + " terminal=" + ruleIdsFromTerminal);
        assertTrue(ruleIdsFromJson.equals(ruleIdsFromMarkdown),
                "JSON and Markdown rule IDs differ: json=" + ruleIdsFromJson + " md=" + ruleIdsFromMarkdown);
    }

    @Test
    void allFormatsSummaryMatchesExitCode() {
        // FIX004 b2 P1: was theater — guard `if (exitCode == 1) {...}` meant
        // any other exit code (e.g., 0 from silent analyzer failure) caused
        // the test to pass vacuously with no assertions. Canary: mutate
        // DiagnosticProviderImpl to return empty → exitCode=0, original test
        // took else branch and passed = theater confirmed. Now: strict — file
        // must produce exit code 1, then verify all format summaries show error.
        Path errorFile = fixturesDir.resolve("lockscreen_type_and_ref.xml");
        BatchInspectionResult result = runner.runOnFile(errorFile.toString());
        int exitCode = ExitCodeCalculator.compute(result);
        assertEquals(1, exitCode,
                "lockscreen_type_and_ref.xml should produce exit code 1; got: " + exitCode
                        + " (errorCount=" + result.getErrorCount() + ")");
        String json = exporter.exportJson(result);
        assertTrue(json.contains("\"severity\": \"error\""),
                "Exit code 1 but JSON has no error severity");
        String terminal = noColorFormatter.formatFullReport(result);
        assertTrue(terminal.contains("error"),
                "Exit code 1 but Terminal has no error");
        String markdown = exporter.exportMarkdown(result);
        assertTrue(markdown.contains("Error"),
                "Exit code 1 but Markdown has no Error section");
    }

    @Test
    void jsonSingleFileFormatVsMultiFileFormat() {
        Path singleFile = fixturesDir.resolve("lockscreen_type_and_ref.xml");
        BatchInspectionResult singleResult = runner.runOnFile(singleFile.toString());
        String singleJson = exporter.exportJson(singleResult);
        assertTrue(singleJson.contains("\"file\""),
                "Single file JSON should use \"file\" top-level key");
        assertFalse(singleJson.contains("\"files\""),
                "Single file JSON should NOT use \"files\" top-level key");
        BatchInspectionResult dirResult = runner.runOnDirectory(fixturesDir.toString());
        String dirJson = exporter.exportJson(dirResult);
        assertTrue(dirJson.contains("\"files\""),
                "Directory JSON should use \"files\" top-level key");
    }

    @Test
    void functionCallTypeMismatchTriggered() {
        Path typeFile = fixturesDir.resolve("lockscreen_type_and_ref.xml");
        BatchInspectionResult result = runner.runOnFile(typeFile.toString());
        List<Diagnostic> diagnostics = result.getFileResults().get(0).getDiagnostics();
        Set<String> typeRuleIds = diagnostics.stream()
                .filter(d -> d.getRuleId().startsWith("SEM-TYPE"))
                .map(Diagnostic::getRuleId)
                .collect(Collectors.toSet());
        assertTrue(typeRuleIds.size() >= 1,
                "Expected at least one SEM-TYPE rule for sin('hello') or type mismatch, got: " + typeRuleIds);
    }

    @Test
    void functionCallInExpressionParsedCorrectly() {
        Path cleanFile = fixturesDir.resolve("clean").resolve("lockscreen_valid.xml");
        BatchInspectionResult result = runner.runOnFile(cleanFile.toString());
        List<Diagnostic> diagnostics = result.getFileResults().isEmpty()
                ? List.of() : result.getFileResults().get(0).getDiagnostics();
        boolean hasSinTypeError = diagnostics.stream()
                .anyMatch(d -> d.getRuleId().startsWith("SEM-TYPE")
                        && d.getMessage().contains("sin"));
        assertFalse(hasSinTypeError,
                "sin(0) with correct number argument should NOT trigger SEM-TYPE error");
    }

    @Test
    void ruleDslEvaluatorConstraintTriggered() {
        Path cmdFile = fixturesDir.resolve("charging_skin_cmd_nest.xml");
        BatchInspectionResult result = runner.runOnFile(cmdFile.toString());
        List<Diagnostic> diagnostics = result.getFileResults().get(0).getDiagnostics();
        boolean hasCmd001 = diagnostics.stream()
                .anyMatch(d -> d.getRuleId().equals("SEM-CMD-001"));
        assertTrue(hasCmd001,
                "Expected SEM-CMD-001 for VideoCommand play+sound mutual exclusion, got: "
                        + diagnostics.stream().map(Diagnostic::getRuleId).collect(Collectors.toSet()));
    }

    @Test
    void fixActionsProducedAcrossMultipleFiles() {
        BatchInspectionResult result = runner.runOnDirectory(fixturesDir.toString());
        int filesWithFixActions = 0;
        for (FileDiagnosticResult fr : result.getFileResults()) {
            if (fr.getFixActions() != null && fr.getFixActions().size() > 0) {
                filesWithFixActions++;
            }
        }
        assertTrue(filesWithFixActions >= 3,
                "Expected at least 3 files with fix actions, got " + filesWithFixActions);
    }

    @Test
    void severityCountsConsistentWithActualDiagnostics() {
        BatchInspectionResult result = runner.runOnDirectory(fixturesDir.toString());
        int computedErrors = 0;
        int computedWarnings = 0;
        int computedInfos = 0;
        for (FileDiagnosticResult fr : result.getFileResults()) {
            if (fr.getDiagnostics() != null) {
                computedErrors += (int) fr.getDiagnostics().stream()
                        .filter(d -> d.getSeverity() == DiagnosticSeverity.ERROR).count();
                computedWarnings += (int) fr.getDiagnostics().stream()
                        .filter(d -> d.getSeverity() == DiagnosticSeverity.WARNING).count();
                computedInfos += (int) fr.getDiagnostics().stream()
                        .filter(d -> d.getSeverity() == DiagnosticSeverity.INFO).count();
            }
        }
        assertEquals(computedErrors, result.getErrorCount(),
                "Error count mismatch: computed=" + computedErrors + " reported=" + result.getErrorCount());
        assertEquals(computedWarnings, result.getWarningCount(),
                "Warning count mismatch: computed=" + computedWarnings + " reported=" + result.getWarningCount());
        assertEquals(computedInfos, result.getInfoCount(),
                "Info count mismatch: computed=" + computedInfos + " reported=" + result.getInfoCount());
    }

    @Test
    void exportToFileJsonMatchesExportJson() throws IOException {
        Path errorFile = fixturesDir.resolve("lockscreen_type_and_ref.xml");
        BatchInspectionResult result = runner.runOnFile(errorFile.toString());
        String directJson = exporter.exportJson(result);
        Path outputPath = tempOutputDir.resolve("report.json");
        exporter.exportToFile(result, "json", outputPath.toString());
        String fileJson = Files.readString(outputPath, StandardCharsets.UTF_8);
        assertEquals(directJson, fileJson,
                "exportToFile json should match exportJson output");
    }

    @Test
    void exportToFileMarkdownMatchesExportMarkdown() throws IOException {
        Path errorFile = fixturesDir.resolve("lockscreen_type_and_ref.xml");
        BatchInspectionResult result = runner.runOnFile(errorFile.toString());
        String directMd = exporter.exportMarkdown(result);
        Path outputPath = tempOutputDir.resolve("report.md");
        exporter.exportToFile(result, "markdown", outputPath.toString());
        String fileMd = Files.readString(outputPath, StandardCharsets.UTF_8);
        assertEquals(directMd, fileMd,
                "exportToFile markdown should match exportMarkdown output");
    }

    @Test
    void eachErrorFileHasThreeOrMoreDiagnostics() {
        // FIX004 b2 P1: was theater — `if (diagnostics.isEmpty()) continue;`
        // caused the test to silently skip every file when analyzer produced 0
        // diagnostics, ending the loop with zero assertions executed. Canary:
        // DiagnosticProviderImpl.analyze → return empty → original test passed
        // = theater confirmed. Now: strict — count error files explicitly,
        // require >=1 error file, and fail any file whose diagnostics list is
        // unexpectedly empty (rather than silently skipping).
        BatchInspectionResult result = runner.runOnDirectory(fixturesDir.toString());
        int errorFileCount = 0;
        for (FileDiagnosticResult fr : result.getFileResults()) {
            String fileName = Path.of(fr.getFilePath()).getFileName().toString();
            if (fileName.contains("lockscreen_valid")) {
                continue;
            }
            errorFileCount++;
            List<Diagnostic> diags = fr.getDiagnostics() == null ? List.of() : fr.getDiagnostics();
            assertFalse(diags.isEmpty(),
                    "error file " + fileName + " should have diagnostics; got empty list");
            assertTrue(diags.size() >= 3,
                    fileName + " should have at least 3 diagnostics, got " + diags.size()
                            + " ruleIds: " + diags.stream().map(Diagnostic::getRuleId).collect(Collectors.toSet()));
        }
        assertTrue(errorFileCount >= 1,
                "expected at least 1 error file in fixtures directory; got " + errorFileCount
                        + " (totalFiles=" + result.getTotalFiles() + ")");
    }

    @Test
    void cleanFileHasMinimalOrZeroErrors() {
        // FIX004 C6: was theater — asserted errorCount<=2 on a "clean" file,
        // tolerating up to 2 false positives. Canary: inject 1 false-positive
        // ERROR per file → errorCount<=2 still passed = theater confirmed.
        // Now: strict — clean file must have ZERO errors.
        Path cleanFile = fixturesDir.resolve("clean").resolve("lockscreen_valid.xml");
        BatchInspectionResult result = runner.runOnFile(cleanFile.toString());
        assertEquals(0, result.getErrorCount(),
                "clean file must produce ZERO errors (no false positives tolerated); got: "
                        + result.getFileResults().get(0).getDiagnostics());
    }

    @Test
    void nondslSkippedAndNotInReport() {
        BatchInspectionResult dirResult = runner.runOnDirectory(fixturesDir.toString());
        String terminal = noColorFormatter.formatFullReport(dirResult);
        String markdown = exporter.exportMarkdown(dirResult);
        String json = exporter.exportJson(dirResult);
        assertFalse(terminal.contains("config.xml"),
                "Non-DSL config.xml should not appear in Terminal report");
        assertFalse(markdown.contains("config.xml"),
                "Non-DSL config.xml should not appear in Markdown report");
        assertFalse(json.contains("config.xml"),
                "Non-DSL config.xml should not appear in JSON report");
    }

    @Test
    void terminalColorAndNoColorConsistentContent() {
        // FIX004 b2 P1: was theater — guard `if (fileResults.isEmpty() ||
        // getDiagnostics().isEmpty()) return;` skipped all assertions when
        // analyzer produced 0 diagnostics, masking silent analyzer failure.
        // Canary: DiagnosticProviderImpl.analyze → return empty → original
        // test returned early and passed = theater confirmed. Now: strict —
        // file must produce a non-empty result with diagnostics, then verify
        // color stripped of ANSI equals no-color output.
        Path errorFile = fixturesDir.resolve("wallpaper_constraint_enum.xml");
        BatchInspectionResult result = runner.runOnFile(errorFile.toString());
        assertFalse(result.getFileResults().isEmpty(),
                "wallpaper_constraint_enum.xml should produce a file result; got empty");
        List<Diagnostic> diagnostics = result.getFileResults().get(0).getDiagnostics();
        assertFalse(diagnostics.isEmpty(),
                "wallpaper_constraint_enum.xml should produce diagnostics; got empty list");
        String noColor = noColorFormatter.formatFullReport(result);
        String color = colorFormatter.formatFullReport(result);
        String noColorStripped = color.replaceAll("\u001B\\[[0-9;]*m", "");
        assertEquals(noColor, noColorStripped,
                "Color output stripped of ANSI should equal no-color output");
    }

    @Test
    void directoryResultHasMoreErrorsThanWarningsOrInfo() {
        BatchInspectionResult result = runner.runOnDirectory(fixturesDir.toString());
        assertTrue(result.getErrorCount() > 0,
                "E2E directory should produce error-level diagnostics");
    }

    private Set<String> extractRuleIdsFromJson(String json) {
        Set<String> ruleIds = new HashSet<>();
        Pattern p = Pattern.compile("\"ruleId\":\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        while (m.find()) {
            ruleIds.add(m.group(1));
        }
        return ruleIds;
    }

    private Set<String> extractRuleIdsFromTerminal(String terminal) {
        Set<String> ruleIds = new HashSet<>();
        Pattern p = Pattern.compile("\\[([A-Z]+(?:-[A-Z0-9]+)+)\\]");
        Matcher m = p.matcher(terminal);
        while (m.find()) {
            ruleIds.add(m.group(1));
        }
        return ruleIds;
    }

    private Set<String> extractRuleIdsFromMarkdown(String markdown) {
        Set<String> ruleIds = new HashSet<>();
        Pattern p = Pattern.compile("\\*\\*([A-Z]+(?:-[A-Z0-9]+)+)\\*\\*");
        Matcher m = p.matcher(markdown);
        while (m.find()) {
            ruleIds.add(m.group(1));
        }
        return ruleIds;
    }
}
