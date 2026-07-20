package com.huawei.theme.analysis.core.batchinspection;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchInspectionRealScenarioTest {

    private static RuleRepository ruleRepo;
    private static BatchInspectionRunner runner;
    private static TerminalFormatter noColorFormatter;
    private static TerminalFormatter colorFormatter;
    private static Path fixturesDir;

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
        fixturesDir = Paths.get(System.getProperty("user.dir") + "/src/test/resources/fixtures/batch-inspection");
    }

    @Test
    void directoryScanFindsExpectedXmlFiles() {
        BatchInspectionResult result = runner.runOnDirectory(fixturesDir.toString());
        assertTrue(result.getTotalFiles() >= 5,
                "Expected at least 5 DSL files (Lockscreen+Widget+Wallpaper+ChargingSkin+nested Lockscreen+ChargingSkin), got " + result.getTotalFiles());
        assertTrue(result.getSkippedFiles() >= 1,
                "Expected at least 1 skipped file (nondsl_config), got " + result.getSkippedFiles());
        assertTrue(result.getTotalFiles() + result.getSkippedFiles() >= 6,
                "Expected at least 6 XML files total (5 DSL + 1 nondsl), got totalFiles=" + result.getTotalFiles() + " skipped=" + result.getSkippedFiles());
    }

    @Test
    void multiErrorLockscreenProducesDiverseRuleIds() {
        Path multiErrorFile = fixturesDir.resolve("lockscreen_multi_error.xml");
        BatchInspectionResult result = runner.runOnFile(multiErrorFile.toString());
        assertTrue(result.getTotalFiles() > 0);
        assertTrue(result.getFileResults().size() > 0);
        List<Diagnostic> diagnostics = result.getFileResults().get(0).getDiagnostics();
        assertTrue(diagnostics.size() > 0, "multi_error file should have diagnostics");
        Set<String> ruleIds = diagnostics.stream().map(Diagnostic::getRuleId).collect(Collectors.toSet());
        assertTrue(ruleIds.size() >= 5,
                "Expected at least 5 different rule IDs, got: " + ruleIds);
    }

    @Test
    void multiErrorLockscreenTriggersSemRef001() {
        Path multiErrorFile = fixturesDir.resolve("lockscreen_multi_error.xml");
        BatchInspectionResult result = runner.runOnFile(multiErrorFile.toString());
        List<Diagnostic> diagnostics = result.getFileResults().get(0).getDiagnostics();
        boolean hasRef001 = diagnostics.stream().anyMatch(d -> d.getRuleId().equals("SEM-REF-001"));
        assertTrue(hasRef001, "Expected SEM-REF-001 for #undefined_var in Image.x");
    }

    @Test
    void multiErrorLockscreenTriggersSemRef003() {
        Path multiErrorFile = fixturesDir.resolve("lockscreen_multi_error.xml");
        BatchInspectionResult result = runner.runOnFile(multiErrorFile.toString());
        List<Diagnostic> diagnostics = result.getFileResults().get(0).getDiagnostics();
        boolean hasRef003 = diagnostics.stream().anyMatch(d -> d.getRuleId().equals("SEM-REF-003"));
        assertTrue(hasRef003, "Expected SEM-REF-003 for duplicate Var name=dup_var");
    }

    @Test
    void multiErrorLockscreenTriggersConstraintErrors() {
        Path multiErrorFile = fixturesDir.resolve("lockscreen_multi_error.xml");
        BatchInspectionResult result = runner.runOnFile(multiErrorFile.toString());
        List<Diagnostic> diagnostics = result.getFileResults().get(0).getDiagnostics();
        Set<String> constraintRuleIds = Set.of("SEM-ATTR-001", "SEM-PERSIST-001", "SEM-IMG-002",
                "SEM-IMG-SRC", "SEM-TRIG-001", "SEM-TRIG-002", "SEM-IMG-003");
        boolean hasConstraint = diagnostics.stream().anyMatch(d -> constraintRuleIds.contains(d.getRuleId()));
        assertTrue(hasConstraint,
                "Expected at least one constraint-based rule ID. Got: " +
                diagnostics.stream().map(Diagnostic::getRuleId).collect(Collectors.toSet()));
    }

    @Test
    void multiErrorLockscreenTriggersNestingViolation() {
        Path multiErrorFile = fixturesDir.resolve("lockscreen_multi_error.xml");
        BatchInspectionResult result = runner.runOnFile(multiErrorFile.toString());
        List<Diagnostic> diagnostics = result.getFileResults().get(0).getDiagnostics();
        boolean hasNestingViolation = diagnostics.stream().anyMatch(d ->
                d.getRuleId().equals("SEM-NEST-001") || d.getRuleId().equals("SEM-SCOPE-001"));
        assertTrue(hasNestingViolation,
                "Expected SEM-NEST-001 or SEM-SCOPE-001 for Layer outside MultiLayer");
    }

    @Test
    void widgetMissingRequiredAttrs() {
        Path widgetFile = fixturesDir.resolve("widget_missing_required.xml");
        BatchInspectionResult result = runner.runOnFile(widgetFile.toString());
        if (result.getTotalFiles() > 0 && !result.getFileResults().isEmpty()) {
            List<Diagnostic> diagnostics = result.getFileResults().get(0).getDiagnostics();
            boolean hasReq001 = diagnostics.stream().anyMatch(d -> d.getRuleId().equals("SEM-REQ-001"));
            assertTrue(hasReq001,
                    "Expected SEM-REQ-001 for Widget missing screenWidth/screenHeight");
        }
    }

    @Test
    void wallpaperInvalidEnumAndSrcConflict() {
        Path wallpaperFile = fixturesDir.resolve("wallpaper_invalid_enum.xml");
        BatchInspectionResult result = runner.runOnFile(wallpaperFile.toString());
        assertTrue(result.getTotalFiles() > 0);
        assertTrue(result.getFileResults().size() > 0);
        List<Diagnostic> diagnostics = result.getFileResults().get(0).getDiagnostics();
        assertTrue(diagnostics.size() > 0);
        Set<String> ruleIds = diagnostics.stream().map(Diagnostic::getRuleId).collect(Collectors.toSet());
        assertTrue(ruleIds.contains("SEM-ENUM-001") || ruleIds.contains("SEM-IMG-002") || ruleIds.contains("SEM-ATTR-001"),
                "Expected SEM-ENUM-001, SEM-IMG-002, or SEM-ATTR-001 in wallpaper file. Got: " + ruleIds);
    }

    @Test
    void nestedVarRefTriggersVariableAndElementRefErrors() {
        Path varRefFile = fixturesDir.resolve("nested").resolve("lockscreen_var_ref.xml");
        BatchInspectionResult result = runner.runOnFile(varRefFile.toString());
        assertTrue(result.getTotalFiles() > 0);
        List<Diagnostic> diagnostics = result.getFileResults().get(0).getDiagnostics();
        Set<String> refRuleIds = Set.of("SEM-REF-001", "SEM-REF-002", "SEM-REF-003");
        boolean hasRef = diagnostics.stream().anyMatch(d -> refRuleIds.contains(d.getRuleId()));
        assertTrue(hasRef,
                "Expected at least one SEM-REF rule. Got: " +
                diagnostics.stream().map(Diagnostic::getRuleId).collect(Collectors.toSet()));
    }

    @Test
    void chargingSkinTriggersConstraintAndCommandErrors() {
        Path cmdFile = fixturesDir.resolve("nested").resolve("charging_skin_cmd.xml");
        BatchInspectionResult result = runner.runOnFile(cmdFile.toString());
        assertTrue(result.getTotalFiles() > 0);
        List<Diagnostic> diagnostics = result.getFileResults().get(0).getDiagnostics();
        assertTrue(diagnostics.size() > 0, "charging_skin_cmd should produce diagnostics");
        Set<String> ruleIds = diagnostics.stream().map(Diagnostic::getRuleId).collect(Collectors.toSet());
        assertTrue(ruleIds.contains("SEM-CMD-001") || ruleIds.contains("SEM-ATTR-001") || ruleIds.contains("SEM-REQ-001"),
                "Expected SEM-CMD-001, SEM-ATTR-001, or SEM-REQ-001. Got: " + ruleIds);
    }

    @Test
    void nondslConfigIsSkipped() {
        Path nondslFile = fixturesDir.resolve("nested").resolve("nondsl_config.xml");
        BatchInspectionResult result = runner.runOnFile(nondslFile.toString());
        assertEquals(0, result.getTotalFiles());
        assertEquals(1, result.getSkippedFiles());
    }

    @Test
    void cleanLockscreenHasMinimalErrors() {
        // FIX004 C8: was theater — asserted errorCount<=2 on a "clean" file,
        // tolerating up to 2 false positives. Canary: inject 1 false-positive
        // ERROR per file → errorCount<=2 still passed = theater confirmed.
        // Now: strict — clean file must have ZERO errors.
        Path cleanFile = fixturesDir.resolve("clean").resolve("lockscreen_valid.xml");
        BatchInspectionResult result = runner.runOnFile(cleanFile.toString());
        assertEquals(1, result.getTotalFiles());
        assertEquals(0, result.getSkippedFiles());
        assertEquals(0, result.getErrorCount(),
                "clean lockscreen must produce ZERO errors (no false positives tolerated); got: "
                        + result.getFileResults().get(0).getDiagnostics());
    }

    @Test
    void severityCountsAreConsistentAcrossDirectory() {
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
    void fixActionsAreProducedForConstraintErrors() {
        Path multiErrorFile = fixturesDir.resolve("lockscreen_multi_error.xml");
        BatchInspectionResult result = runner.runOnFile(multiErrorFile.toString());
        List<FixAction> fixActions = result.getFileResults().get(0).getFixActions();
        assertNotNull(fixActions);
        assertTrue(fixActions.size() > 0,
                "Expected fix actions for constraint errors (SEM-ATTR-001, SEM-TRIG-001, etc.)");
    }

    @Test
    void noColorFormatterProducesValidReport() {
        BatchInspectionResult result = runner.runOnDirectory(fixturesDir.toString());
        if (result.getErrorCount() > 0) {
            String report = noColorFormatter.formatFullReport(result);
            assertFalse(report.contains("\u001B["));
            assertTrue(report.contains("errors"));
            assertTrue(report.contains("lockscreen_multi_error"));
        }
    }

    @Test
    void colorFormatterProducesAnsiReport() {
        BatchInspectionResult result = runner.runOnDirectory(fixturesDir.toString());
        if (result.getErrorCount() > 0) {
            String report = colorFormatter.formatFullReport(result);
            assertTrue(report.contains("\u001B[31m"),
                    "Expected ANSI red color codes in color formatter output");
        }
    }

    @Test
    void fullReportSkipsFilesWithNoDiagnostics() {
        BatchInspectionResult result = runner.runOnDirectory(fixturesDir.toString());
        String report = noColorFormatter.formatFullReport(result);
        for (FileDiagnosticResult fr : result.getFileResults()) {
            if (fr.getDiagnostics() == null || fr.getDiagnostics().isEmpty()) {
                String fileName = Path.of(fr.getFilePath()).getFileName().toString();
                assertFalse(report.contains(fileName),
                        "File with no diagnostics (" + fileName + ") should not appear in formatted report");
            }
        }
    }

    @Test
    void fullReportContainsAllErrorFiles() {
        BatchInspectionResult result = runner.runOnDirectory(fixturesDir.toString());
        String report = noColorFormatter.formatFullReport(result);
        assertTrue(report.contains("lockscreen_multi_error"));
    }

    @Test
    void notXmlFilesAreNeverScanned() {
        BatchInspectionResult result = runner.runOnDirectory(fixturesDir.toString());
        boolean jsonFileFound = result.getFileResults().stream()
                .anyMatch(fr -> fr.getFilePath().contains("data.json"));
        assertFalse(jsonFileFound, "JSON files should never be scanned by directory walker");
    }

    @Test
    void directoryResultHasMoreErrorsThanWarnings() {
        BatchInspectionResult result = runner.runOnDirectory(fixturesDir.toString());
        assertTrue(result.getErrorCount() > result.getWarningCount(),
                "Real scenario: errors (" + result.getErrorCount() +
                ") should exceed warnings (" + result.getWarningCount() + ")");
    }

    @Test
    void eachFileResultPreservesOriginalFilePath() {
        BatchInspectionResult result = runner.runOnDirectory(fixturesDir.toString());
        for (FileDiagnosticResult fr : result.getFileResults()) {
            assertTrue(fr.getFilePath().contains("batch-inspection"),
                    "File path should contain fixture directory name: " + fr.getFilePath());
        }
    }
}
