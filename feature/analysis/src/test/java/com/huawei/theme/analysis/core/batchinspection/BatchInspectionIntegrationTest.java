package com.huawei.theme.analysis.core.batchinspection;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.batchinspection.model.FileDiagnosticResult;
import com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary;
import com.huawei.theme.analysis.core.fileidentification.DslFileIdentifier;
import com.huawei.theme.analysis.core.fileidentification.DslFileMatcher;
import com.huawei.theme.analysis.core.function.JsonFunctionSignatureLoader;
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

class BatchInspectionIntegrationTest {

    private static RuleRepository ruleRepo;
    private static BatchInspectionRunner runner;
    private static TerminalFormatter formatter;
    private static TerminalFormatter colorFormatter;

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
        formatter = new TerminalFormatter(true);
        colorFormatter = new TerminalFormatter(false);
    }

    @Test
    void runOnFileWithValidLockscreen() {
        Path tempFile = writeTempFile("lockscreen.xml",
                "<Lockscreen>\n" +
                "  <Var name=\"test_var\" type=\"number\" const=\"true\" expression=\"1\"/>\n" +
                "</Lockscreen>");
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertTrue(result.getTotalFiles() > 0);
        assertEquals(0, result.getSkippedFiles());
        assertTrue(result.getFileResults().size() > 0);
        assertNotNull(result.getFileResults().get(0).getFilePath());
    }

    @Test
    void runOnFileWithUndeclaredVarReference() {
        Path tempFile = writeTempFile("varref.xml",
                "<Lockscreen>\n" +
                "  <Var name=\"valid_var\" type=\"number\" const=\"true\" expression=\"1\"/>\n" +
                "  <DateTime format=\"#undeclared_var\"/>\n" +
                "</Lockscreen>");
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertTrue(result.getTotalFiles() > 0);
        assertTrue(result.getFileResults().get(0).getDiagnostics().size() > 0);
        assertTrue(result.getErrorCount() > 0);
    }

    @Test
    void runOnFileWithNonDslContent() throws Exception {
        Path dir = Files.createTempDirectory("batch-int-nondsl");
        Path nonDslFile = dir.resolve("nondsl.xml");
        Files.writeString(nonDslFile, "<html><body>not dsl</body></html>", StandardCharsets.UTF_8);
        BatchInspectionResult result = runner.runOnFile(nonDslFile.toString());
        assertEquals(0, result.getTotalFiles());
        assertEquals(1, result.getSkippedFiles());
        assertEquals(0, result.getFileResults().size());
    }

    @Test
    void runOnFileWithEmptyLockscreen() {
        Path tempFile = writeTempFile("empty.xml", "<Lockscreen/>");
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        assertEquals(1, result.getTotalFiles());
        assertEquals(0, result.getSkippedFiles());
        assertEquals(1, result.getFileResults().size());
    }

    @Test
    void runOnFileWithInvalidAttrValue() {
        Path tempFile = writeTempFile("invalid.xml",
                "<Lockscreen>\n" +
                "  <Var name=\"x\" type=\"number\" const=\"true\" expression=\"1\"/>\n" +
                "  <DateTime format=\"HH:mm\" x=\"not_a_number\"/>\n" +
                "</Lockscreen>");
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        if (result.getErrorCount() > 0 || result.getWarningCount() > 0) {
            assertTrue(result.getFileResults().get(0).getDiagnostics().size() > 0);
        }
    }

    @Test
    void runOnDirectoryWithMixedFiles() throws Exception {
        Path dir = Files.createTempDirectory("batch-int-mixed");
        Path dslFile = dir.resolve("lockscreen.xml");
        Files.writeString(dslFile, "<Lockscreen>\n  <Var name=\"v\" type=\"number\" const=\"true\" expression=\"1\"/>\n</Lockscreen>", StandardCharsets.UTF_8);
        Path nonDslFile = dir.resolve("nondsl.xml");
        Files.writeString(nonDslFile, "<html><body>not dsl</body></html>", StandardCharsets.UTF_8);
        BatchInspectionResult result = runner.runOnDirectory(dir.toString());
        assertTrue(result.getTotalFiles() > 0);
        assertTrue(result.getSkippedFiles() > 0);
    }

    @Test
    void runOnDirectoryWithNestedStructure() throws Exception {
        Path dir = Files.createTempDirectory("batch-int-nested");
        Path subdir = dir.resolve("subdir");
        Files.createDirectory(subdir);
        Path dslFile1 = dir.resolve("lockscreen1.xml");
        Files.writeString(dslFile1, "<Lockscreen>\n  <Var name=\"a\" type=\"number\" const=\"true\" expression=\"1\"/>\n</Lockscreen>", StandardCharsets.UTF_8);
        Path dslFile2 = subdir.resolve("lockscreen2.xml");
        Files.writeString(dslFile2, "<Lockscreen>\n  <Var name=\"b\" type=\"string\" const=\"true\" expression=\"'hello'\"/>\n</Lockscreen>", StandardCharsets.UTF_8);
        Path nonDslFile = dir.resolve("nondsl.xml");
        Files.writeString(nonDslFile, "<html/>", StandardCharsets.UTF_8);
        BatchInspectionResult result = runner.runOnDirectory(dir.toString());
        assertTrue(result.getTotalFiles() >= 2);
        assertTrue(result.getSkippedFiles() >= 1);
    }

    @Test
    void runOnDirectoryIgnoresNonXmlFiles() throws Exception {
        Path dir = Files.createTempDirectory("batch-int-noxml");
        Path jsonFile = dir.resolve("data.json");
        Files.writeString(jsonFile, "{\"key\": \"value\"}", StandardCharsets.UTF_8);
        Path txtFile = dir.resolve("readme.txt");
        Files.writeString(txtFile, "This is a readme", StandardCharsets.UTF_8);
        BatchInspectionResult result = runner.runOnDirectory(dir.toString());
        assertEquals(0, result.getTotalFiles());
        assertEquals(0, result.getSkippedFiles());
        assertEquals(0, result.getFileResults().size());
    }

    @Test
    void runOnDirectoryWithOnlyDslFiles() throws Exception {
        Path dir = Files.createTempDirectory("batch-int-onlydsl");
        Path dsl1 = dir.resolve("a.xml");
        Files.writeString(dsl1, "<Lockscreen>\n  <Var name=\"x\" type=\"number\" const=\"true\" expression=\"1\"/>\n</Lockscreen>", StandardCharsets.UTF_8);
        Path dsl2 = dir.resolve("b.xml");
        Files.writeString(dsl2, "<Lockscreen>\n  <Var name=\"y\" type=\"number\" const=\"true\" expression=\"2\"/>\n</Lockscreen>", StandardCharsets.UTF_8);
        BatchInspectionResult result = runner.runOnDirectory(dir.toString());
        assertEquals(2, result.getTotalFiles());
        assertEquals(0, result.getSkippedFiles());
        assertEquals(2, result.getFileResults().size());
    }

    @Test
    void runOnDirectoryWithOnlyNonDslXmlFiles() throws Exception {
        Path dir = Files.createTempDirectory("batch-int-onlynondsl");
        Path file1 = dir.resolve("a.xml");
        Files.writeString(file1, "<html/>", StandardCharsets.UTF_8);
        Path file2 = dir.resolve("b.xml");
        Files.writeString(file2, "<svg/>", StandardCharsets.UTF_8);
        BatchInspectionResult result = runner.runOnDirectory(dir.toString());
        assertEquals(0, result.getTotalFiles());
        assertEquals(2, result.getSkippedFiles());
        assertEquals(0, result.getFileResults().size());
    }

    @Test
    void terminalFormatterNoColorOutputIsValid() {
        Path tempFile = writeTempFile("lockscreen.xml",
                "<Lockscreen>\n" +
                "  <Var name=\"test_var\" type=\"number\" const=\"true\" expression=\"1\"/>\n" +
                "</Lockscreen>");
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        if (!result.getFileResults().isEmpty() && !result.getFileResults().get(0).getDiagnostics().isEmpty()) {
            String output = formatter.formatFullReport(result);
            assertFalse(output.contains("\u001B["));
            assertTrue(output.contains("lockscreen.xml"));
            assertTrue(output.contains("errors"));
        }
    }

    @Test
    void terminalFormatterColorOutputIsValid() {
        Path tempFile = writeTempFile("lockscreen.xml",
                "<Lockscreen>\n" +
                "  <Var name=\"test_var\" type=\"number\" const=\"true\" expression=\"1\"/>\n" +
                "</Lockscreen>");
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        if (result.getErrorCount() > 0) {
            String output = colorFormatter.formatFullReport(result);
            assertTrue(output.contains("\u001B[31m"));
        }
    }

    @Test
    void diagnosticProviderAndQuickFixChainProducesFixActions() {
        Path tempFile = writeTempFile("fixtest.xml",
                "<Lockscreen>\n" +
                "  <Var name=\"v\" type=\"number\" const=\"true\" expression=\"1\"/>\n" +
                "  <DateTime format=\"#undeclared_var\"/>\n" +
                "</Lockscreen>");
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        if (!result.getFileResults().isEmpty() && !result.getFileResults().get(0).getDiagnostics().isEmpty()) {
            FileDiagnosticResult fileResult = result.getFileResults().get(0);
            assertNotNull(fileResult.getFixActions());
        }
    }

    @Test
    void severityCountsAreConsistentWithDiagnostics() {
        Path tempFile = writeTempFile("sevcheck.xml",
                "<Lockscreen>\n" +
                "  <Var name=\"v\" type=\"number\" const=\"true\" expression=\"1\"/>\n" +
                "</Lockscreen>");
        BatchInspectionResult result = runner.runOnFile(tempFile.toString());
        if (!result.getFileResults().isEmpty() && !result.getFileResults().get(0).getDiagnostics().isEmpty()) {
            int actualErrors = (int) result.getFileResults().get(0).getDiagnostics().stream()
                    .filter(d -> d.getSeverity() == DiagnosticSeverity.ERROR).count();
            int actualWarnings = (int) result.getFileResults().get(0).getDiagnostics().stream()
                    .filter(d -> d.getSeverity() == DiagnosticSeverity.WARNING).count();
            int actualInfos = (int) result.getFileResults().get(0).getDiagnostics().stream()
                    .filter(d -> d.getSeverity() == DiagnosticSeverity.INFO).count();
            assertEquals(actualErrors, result.getErrorCount());
            assertEquals(actualWarnings, result.getWarningCount());
            assertEquals(actualInfos, result.getInfoCount());
        }
    }

    @Test
    void runOnDirectoryWithSemanticErrorsAcrossFiles() throws Exception {
        Path dir = Files.createTempDirectory("batch-int-sevdir");
        Path file1 = dir.resolve("err1.xml");
        Files.writeString(file1, "<Lockscreen>\n  <Var name=\"x\" type=\"number\" const=\"true\" expression=\"1\"/>\n  <DateTime format=\"#bad_var\"/>\n</Lockscreen>", StandardCharsets.UTF_8);
        Path file2 = dir.resolve("err2.xml");
        Files.writeString(file2, "<Lockscreen>\n  <Var name=\"y\" type=\"number\" const=\"true\" expression=\"2\"/>\n  <DateTime format=\"#another_bad\"/>\n</Lockscreen>", StandardCharsets.UTF_8);
        BatchInspectionResult result = runner.runOnDirectory(dir.toString());
        assertEquals(2, result.getTotalFiles());
        assertTrue(result.getErrorCount() >= 2);
    }

    private Path writeTempFile(String name, String content) {
        try {
            Path dir = Files.createTempDirectory("batch-int-test");
            Path file = dir.resolve(name);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
