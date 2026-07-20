package com.huawei.theme.analysis.core.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliMainE2ETest {

    private static final String RESOURCES_DSL_DIR = "src/test/resources/dsl";
    private static final String RESOURCES_FIXTURES_DIR = "src/test/resources/fixtures";

    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream capturedOut;
    private ByteArrayOutputStream capturedErr;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        originalOut = System.out;
        originalErr = System.err;
        capturedOut = new ByteArrayOutputStream();
        capturedErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));
        System.setErr(new PrintStream(capturedErr));
        tempDir = Files.createTempDirectory("cli-e2e-test");
    }

    @AfterEach
    void tearDown() throws Exception {
        System.setOut(originalOut);
        System.setErr(originalErr);
        Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
    }

    private String stdout() { return capturedOut.toString(); }
    private String stderr() { return capturedErr.toString(); }

    private Path copyResourceToTemp(String resourcePath, String fileName) throws Exception {
        Path src = Path.of(RESOURCES_FIXTURES_DIR, resourcePath);
        String content = Files.readString(src, StandardCharsets.UTF_8);
        Path dest = tempDir.resolve(fileName);
        Files.writeString(dest, content, StandardCharsets.UTF_8);
        return dest;
    }

    private Path copyDslResourceToTemp(String fileName) throws Exception {
        Path src = Path.of(RESOURCES_DSL_DIR, fileName);
        String content = Files.readString(src, StandardCharsets.UTF_8);
        Path dest = tempDir.resolve(fileName);
        Files.writeString(dest, content, StandardCharsets.UTF_8);
        return dest;
    }

    @Test
    void versionFlag_returnsZero_printsVersion() {
        int exitCode = CliMain.run(new String[]{"--version"});
        assertEquals(0, exitCode);
        assertTrue(stdout().contains("dsl-analyzer 0.1.0"));
        assertEquals(0, stderr().length());
    }

    @Test
    void helpFlag_returnsZero_printsUsage() {
        int exitCode = CliMain.run(new String[]{"--help"});
        assertEquals(0, exitCode);
        assertTrue(stdout().contains("Usage:"));
        assertTrue(stdout().contains("--syntax-only"));
        assertTrue(stdout().contains("--format"));
        assertTrue(stdout().contains("--version"));
    }

    @Test
    void helpShortFlag_returnsZero_printsUsage() {
        int exitCode = CliMain.run(new String[]{"-h"});
        assertEquals(0, exitCode);
        assertTrue(stdout().contains("Usage:"));
    }

    @Test
    void noTargetPath_returnsTwo_printsError() {
        int exitCode = CliMain.run(new String[]{});
        assertEquals(2, exitCode);
        assertTrue(stderr().contains("No target path provided"));
        assertTrue(stderr().contains("Usage:"));
    }

    @Test
    void nonexistentPath_returnsTwo_printsPathNotFound() {
        int exitCode = CliMain.run(new String[]{"/nonexistent/theme.xml"});
        assertEquals(2, exitCode);
        assertTrue(stderr().contains("Path not found"));
    }

    @Test
    void verboseQuietMutualExclusion_returnsTwo() {
        int exitCode = CliMain.run(new String[]{"--verbose", "--quiet", "some.xml"});
        assertEquals(2, exitCode);
        assertTrue(stderr().contains("mutually exclusive"));
    }

    @Test
    void syntaxOnlySemanticOnlyMutualExclusion_returnsTwo() {
        int exitCode = CliMain.run(new String[]{"--syntax-only", "--semantic-only", "some.xml"});
        assertEquals(2, exitCode);
        assertTrue(stderr().contains("mutually exclusive"));
    }

    @Test
    void unknownFlag_returnsTwo_printsUnknownOption() {
        int exitCode = CliMain.run(new String[]{"--unknown-flag", "some.xml"});
        assertEquals(2, exitCode);
        assertTrue(stderr().contains("Unknown option: --unknown-flag"));
    }

    @Test
    void formatMissingValue_returnsTwo() {
        int exitCode = CliMain.run(new String[]{"--format"});
        assertEquals(2, exitCode);
        assertTrue(stderr().contains("--format requires a value"));
    }

    @Test
    void outputMissingValue_returnsTwo() {
        int exitCode = CliMain.run(new String[]{"--output"});
        assertEquals(2, exitCode);
        assertTrue(stderr().contains("--output requires a path value"));
    }

    @Test
    void ruleDirNotExists_returnsTwo_printsRuleDirectoryNotFound() throws Exception {
        Path targetFile = copyDslResourceToTemp("valid_lockscreen.xml");
        int exitCode = CliMain.run(new String[]{"--rule-dir", "/nonexistent/rules", targetFile.toString()});
        assertEquals(2, exitCode);
        assertTrue(stderr().toLowerCase().contains("rule directory"));
    }

    @Test
    void malformedRuleDirJson_returnsTwo_printsRuleLoadError() throws Exception {
        Path badRuleDir = Files.createTempDirectory(tempDir, "bad-rules");
        Path elementsDir = badRuleDir.resolve("elements");
        Files.createDirectories(elementsDir);
        Files.writeString(elementsDir.resolve("Bad.json"), "{ invalid json !!!", StandardCharsets.UTF_8);
        Path targetFile = copyDslResourceToTemp("valid_lockscreen.xml");
        int exitCode = CliMain.run(new String[]{"--rule-dir", badRuleDir.toString(), targetFile.toString()});
        assertEquals(2, exitCode);
        assertTrue(stderr().contains("Rule load error"));
    }

    @Test
    void emptyRuleDir_fallsBackToBuiltIn_returnsZeroOrOne() throws Exception {
        Path emptyDir = Files.createTempDirectory(tempDir, "empty-rules");
        Path targetFile = copyDslResourceToTemp("valid_lockscreen.xml");
        int exitCode = CliMain.run(new String[]{"--rule-dir", emptyDir.toString(), targetFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        assertTrue(stderr().contains("falling back"));
    }

    @Test
    void singleFile_terminalFormat_hasDiagnosticOutput() throws Exception {
        Path targetFile = copyDslResourceToTemp("valid_lockscreen.xml");
        int exitCode = CliMain.run(new String[]{"--no-color", targetFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        assertTrue(stdout().length() > 0);
        assertTrue(stdout().contains("errors") || stdout().contains("warnings") || stdout().contains("info"));
    }

    @Test
    void singleFileWithErrors_returnsOne_terminalFormatShowsErrors() throws Exception {
        // FIX004 C9: was theater — asserted exitCode==0||1 + stdout.length()>0.
        // Name says "returnsOne"+"ShowsErrors" but accepted exit 0 (analyzer
        // failed to detect) and only checked stdout non-empty (even "0 errors"
        // header passes). Canary: DiagnosticProviderImpl → return empty →
        // exit 0 + non-empty stdout = original test passed = theater confirmed.
        // Now: strict — exitCode must be 1 (errors detected) AND stdout must
        // contain a SEM-* ruleId (proves errors are actually shown).
        Path targetFile = copyResourceToTemp("batch-inspection/widget_missing_required.xml", "widget_missing_required.xml");
        int exitCode = CliMain.run(new String[]{"--no-color", targetFile.toString()});
        assertEquals(1, exitCode,
                "file with missing required attrs must return exit 1; got " + exitCode
                        + ", stdout=" + stdout());
        assertTrue(stdout().contains("SEM-"),
                "terminal output must show SEM-* diagnostic ruleIds; got: " + stdout());
    }

    @Test
    void singleFile_jsonFormat_producesOutput() throws Exception {
        Path targetFile = copyDslResourceToTemp("valid_lockscreen.xml");
        Path outputFile = tempDir.resolve("report.json");
        int exitCode = CliMain.run(new String[]{"--format", "json", "--no-color",
                "--output", outputFile.toString(), targetFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        assertTrue(Files.exists(outputFile));
        String content = Files.readString(outputFile, StandardCharsets.UTF_8);
        assertTrue(content.length() > 0);
        assertTrue(content.trim().startsWith("{") || content.trim().startsWith("["));
    }

    @Test
    void singleFileWithErrors_jsonFormat_producesOutput() throws Exception {
        // FIX004 C10: was theater — name "WithErrors" but accepted exit 0
        // (analyzer failed) and only checked JSON starts with {/[, which is
        // true even for the empty-diagnostics report. Canary: DiagnosticProviderImpl
        // → return empty → exit 0 + valid JSON = original test passed = theater.
        // Now: strict — exitCode must be 1 AND JSON must contain error severity
        // + at least one SEM-* ruleId.
        Path targetFile = copyResourceToTemp("batch-inspection/widget_missing_required.xml", "widget_missing_required.xml");
        Path outputFile = tempDir.resolve("report.json");
        int exitCode = CliMain.run(new String[]{"--format", "json", "--no-color",
                "--output", outputFile.toString(), targetFile.toString()});
        assertEquals(1, exitCode,
                "file with errors must return exit 1; got " + exitCode);
        assertTrue(Files.exists(outputFile));
        String content = Files.readString(outputFile, StandardCharsets.UTF_8);
        assertTrue(content.length() > 0);
        assertTrue(content.trim().startsWith("{") || content.trim().startsWith("["));
        assertTrue(content.contains("\"severity\": \"error\""),
                "JSON report for file-with-errors must contain error severity; got: " + content);
        assertTrue(content.contains("\"ruleId\": \"SEM-"),
                "JSON report must contain SEM-* ruleId; got: " + content);
    }

    @Test
    void singleFile_markdownFormat_hasMarkdownStructure() throws Exception {
        Path targetFile = copyDslResourceToTemp("valid_lockscreen.xml");
        int exitCode = CliMain.run(new String[]{"--format", "markdown", "--no-color", targetFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        assertTrue(stdout().contains("# DSL 诊断报告"));
        assertTrue(stdout().contains("## 汇总"));
    }

    @Test
    void singleFileWithErrors_markdownFormat_producesReport() throws Exception {
        // FIX004 C11: was theater — name "WithErrors" but accepted exit 0 and
        // only checked markdown headers exist (headers are present even with
        // 0 diagnostics). Canary: DiagnosticProviderImpl → return empty →
        // exit 0 + markdown headers = original test passed = theater.
        // Now: strict — exitCode must be 1 AND markdown must contain a SEM-*
        // ruleId (proves errors are actually reported in the body).
        Path targetFile = copyResourceToTemp("batch-inspection/widget_missing_required.xml", "widget_missing_required.xml");
        int exitCode = CliMain.run(new String[]{"--format", "markdown", "--no-color", targetFile.toString()});
        assertEquals(1, exitCode,
                "file with errors must return exit 1; got " + exitCode);
        assertTrue(stdout().contains("# DSL 诊断报告"));
        assertTrue(stdout().contains("## 汇总"));
        assertTrue(stdout().contains("SEM-"),
                "markdown report body must contain SEM-* ruleId; got: " + stdout());
    }

    @Test
    void outputToFile_json_createsFile() throws Exception {
        Path targetFile = copyDslResourceToTemp("valid_lockscreen.xml");
        Path outputFile = tempDir.resolve("report.json");
        int exitCode = CliMain.run(new String[]{"--format", "json", "--no-color",
                "--output", outputFile.toString(), targetFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        assertTrue(Files.exists(outputFile));
        String fileContent = Files.readString(outputFile, StandardCharsets.UTF_8);
        assertTrue(fileContent.length() > 0);
        assertTrue(fileContent.trim().startsWith("{") || fileContent.trim().startsWith("["));
    }

    @Test
    void outputToFile_markdown_createsFile() throws Exception {
        Path targetFile = copyDslResourceToTemp("valid_lockscreen.xml");
        Path outputFile = tempDir.resolve("report.md");
        int exitCode = CliMain.run(new String[]{"--format", "markdown", "--no-color",
                "--output", outputFile.toString(), targetFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        assertTrue(Files.exists(outputFile));
        String fileContent = Files.readString(outputFile, StandardCharsets.UTF_8);
        assertTrue(fileContent.contains("# DSL 诊断报告"));
    }

    @Test
    void syntaxOnlyMode_producesOutput_skipsSemanticDiags() throws Exception {
        // FIX004 C12: was theater — name "skipsSemanticDiags" but NEVER verified
        // output contains no SEM-* (audit: "从未验证输出中无 SEM-*"). Original
        // asserted exitCode==0||1 + stdout.length()>0 — passes even if semantic
        // diags leak through. Canary: DiagnosticProviderImpl → return empty →
        // original test passed = theater confirmed (weak assertion). To verify
        // the FIXED assertion is real: mutate DiagnosticProviderImpl to remove
        // the SYNTAX_ONLY guard (semantic runs in syntax-only mode) → fixed
        // test must FAIL (proves the SEM-* absence check catches the bug).
        Path targetFile = copyResourceToTemp("batch-inspection/widget_missing_required.xml", "widget_missing_required.xml");
        int exitCode = CliMain.run(new String[]{"--syntax-only", "--no-color", targetFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1,
                "syntax-only mode exit code should be 0 or 1; got " + exitCode);
        assertTrue(stdout().length() > 0,
                "syntax-only mode must produce some output");
        // The key assertion the original test was missing: no SEM-* diagnostics
        // may appear in syntax-only output (widget_missing_required has SEM-REQ-001
        // + SEM-TRIG-002 in full mode; these MUST be absent in syntax-only mode).
        assertFalse(stdout().contains("SEM-"),
                "syntax-only mode must NOT produce semantic (SEM-*) diagnostics; got: " + stdout());
    }

    @Test
    void semanticOnlyMode_producesOutput() throws Exception {
        Path targetFile = copyResourceToTemp("batch-inspection/widget_missing_required.xml", "widget_missing_required.xml");
        int exitCode = CliMain.run(new String[]{"--semantic-only", "--no-color", targetFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        assertTrue(stdout().length() > 0);
    }

    @Test
    void noTypeCheck_producesOutput() throws Exception {
        Path targetFile = copyResourceToTemp("batch-inspection/widget_missing_required.xml", "widget_missing_required.xml");
        int exitCode = CliMain.run(new String[]{"--no-type-check", "--no-color", targetFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        assertTrue(stdout().length() > 0);
    }

    @Test
    void noColor_terminalFormat_noAnsiEscapeCodes() throws Exception {
        Path targetFile = copyResourceToTemp("batch-inspection/widget_missing_required.xml", "widget_missing_required.xml");
        int exitCode = CliMain.run(new String[]{"--no-color", targetFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        assertFalse(stdout().contains("\u001B["));
    }

    @Test
    void withColor_terminalFormat_hasAnsiEscapeCodes() throws Exception {
        Path targetFile = copyResourceToTemp("batch-inspection/widget_missing_required.xml", "widget_missing_required.xml");
        int exitCode = CliMain.run(new String[]{targetFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        if (stdout().contains("error:") || stdout().contains("warning:") || stdout().contains("info:")) {
            assertTrue(stdout().contains("\u001B["));
        }
    }

    @Test
    void directoryScan_mixedDslAndNonDsl_skipsNonDsl() throws Exception {
        Path lockscreenFile = copyDslResourceToTemp("valid_lockscreen.xml");
        String nonDslContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<configuration>\n" +
                "  <property name=\"host\" value=\"localhost\"/>\n" +
                "</configuration>";
        Path nonDslFile = tempDir.resolve("config.xml");
        Files.writeString(nonDslFile, nonDslContent, StandardCharsets.UTF_8);

        int exitCode = CliMain.run(new String[]{"--no-color", tempDir.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        assertTrue(stdout().length() > 0);
        assertTrue(stdout().contains("skipped") || stdout().contains("1 errors") || stdout().contains("0 errors"));
    }

    @Test
    void directoryScan_jsonFormat_multiFileStructure() throws Exception {
        Path subDir = tempDir.resolve("themes");
        Files.createDirectories(subDir);
        String lockscreenContent = Files.readString(Path.of(RESOURCES_DSL_DIR, "valid_lockscreen.xml"), StandardCharsets.UTF_8);
        Files.writeString(subDir.resolve("lockscreen.xml"), lockscreenContent, StandardCharsets.UTF_8);
        String widgetContent = Files.readString(Path.of(RESOURCES_DSL_DIR, "valid_widget.xml"), StandardCharsets.UTF_8);
        Files.writeString(subDir.resolve("widget.xml"), widgetContent, StandardCharsets.UTF_8);

        int exitCode = CliMain.run(new String[]{"--format", "json", "--no-color", subDir.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        assertTrue(stdout().contains("\"files\""));
        assertTrue(stdout().contains("\"totalFiles\""));
        assertTrue(stdout().contains("\"skippedFiles\""));
    }

    @Test
    void multiErrorFile_producesOutput() throws Exception {
        Path targetFile = copyResourceToTemp("batch-inspection/lockscreen_multi_error.xml", "lockscreen_multi_error.xml");
        int exitCode = CliMain.run(new String[]{"--no-color", targetFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        assertTrue(stdout().length() > 0);
    }

    @Test
    void configFlag_validConfig_runsPipeline() throws Exception {
        Path targetFile = copyResourceToTemp("batch-inspection/widget_missing_required.xml", "widget_missing_required.xml");
        String configJson = "{\"severityOverrides\": {\"SEM-REQ-001\": \"warning\"}}";
        Path configFile = tempDir.resolve("inspection.json");
        Files.writeString(configFile, configJson, StandardCharsets.UTF_8);

        int exitCode = CliMain.run(new String[]{"--no-color", "--config", configFile.toString(), targetFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        assertTrue(stdout().length() > 0);
    }

    @Test
    void configFlag_nonexistentFile_returnsTwo() throws Exception {
        Path targetFile = copyDslResourceToTemp("valid_lockscreen.xml");
        int exitCode = CliMain.run(new String[]{"--config", "/nonexistent/config.json", targetFile.toString()});
        assertEquals(2, exitCode);
        assertTrue(stderr().contains("Config file not found"));
    }

    @Test
    void fullPipeline_validFile_exitCodeZeroOrOne() throws Exception {
        Path targetFile = copyDslResourceToTemp("valid_lockscreen.xml");
        int exitCode = CliMain.run(new String[]{targetFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        assertTrue(stdout().length() > 0);
    }

    @Test
    void fullPipeline_allOptionsCombined() throws Exception {
        Path targetFile = copyResourceToTemp("batch-inspection/widget_missing_required.xml", "widget_missing_required.xml");
        Path outputFile = tempDir.resolve("report.json");
        int exitCode = CliMain.run(new String[]{
                "--format", "json", "--no-color",
                "--output", outputFile.toString(),
                "--no-type-check", targetFile.toString()
        });
        assertTrue(exitCode == 0 || exitCode == 1);
        assertTrue(Files.exists(outputFile));
    }

    @Test
    void multipleTargetPaths_returnsTwo_printsMultiplePathsError() throws Exception {
        Path file1 = copyDslResourceToTemp("valid_lockscreen.xml");
        Path file2 = copyDslResourceToTemp("valid_widget.xml");
        int exitCode = CliMain.run(new String[]{file1.toString(), file2.toString()});
        assertEquals(2, exitCode);
        assertTrue(stderr().contains("Multiple target paths"));
    }

    @Test
    void quietMode_jsonOutput_containsOnlyErrorSeverity() throws Exception {
        Path targetFile = copyResourceToTemp("batch-inspection/wallpaper_invalid_enum.xml", "wallpaper_invalid_enum.xml");
        int exitCode = CliMain.run(new String[]{"--quiet", "--format", "json", "--no-color", targetFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        String json = stdout();
        assertFalse(json.contains("\"severity\": \"warning\""));
        assertFalse(json.contains("\"severity\": \"info\""));
    }

    @Test
    void verboseMode_output_containsVerboseLines() throws Exception {
        Path targetFile = copyDslResourceToTemp("valid_lockscreen.xml");
        int exitCode = CliMain.run(new String[]{"--verbose", "--no-color", targetFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        String out = stdout();
        assertTrue(out.contains("[verbose]"));
        assertTrue(out.contains("AST:"));
        assertFalse(out.contains("AST: 0 elements, 0 attributes, 0 expressions"));
        assertTrue(out.contains("Symbols:"));
        assertTrue(out.contains("Diagnostics:"));
        assertTrue(out.contains("Type inference:"));
    }

    @Test
    void verboseMode_typeInference_showsTraces() throws Exception {
        Path targetFile = copyResourceToTemp("complex/type_inference_edge_cases.xml", "type_inference_edge_cases.xml");
        int exitCode = CliMain.run(new String[]{"--verbose", "--no-color", targetFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        String out = stdout();
        assertTrue(out.contains("[verbose]"));
        assertFalse(out.contains("Type inference: (none)"));
    }

    @Test
    void jsonOutput_suggestedFixes_nonEmpty() throws Exception {
        Path targetFile = copyResourceToTemp("batch-inspection/wallpaper_invalid_enum.xml", "wallpaper_invalid_enum.xml");
        Path outputFile = tempDir.resolve("report.json");
        int exitCode = CliMain.run(new String[]{"--format", "json", "--no-color",
                "--output", outputFile.toString(), targetFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        String content = Files.readString(outputFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("\"suggestedFixes\""));
        assertTrue(content.replaceAll("\\s", "").contains("[\""));
    }
}
