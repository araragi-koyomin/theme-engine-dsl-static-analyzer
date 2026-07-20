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
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliMainIntegrationTest {

    private Path tempFile;
    private Path tempDir;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream capturedOut;
    private ByteArrayOutputStream capturedErr;

    @BeforeEach
    void setUp() throws Exception {
        originalOut = System.out;
        originalErr = System.err;
        capturedOut = new ByteArrayOutputStream();
        capturedErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));
        System.setErr(new PrintStream(capturedErr));

        String dslContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Lockscreen xmlns:sys=\"http://www.huawei.com/system\">\n" +
                "  <Var name=\"testVar\" expression=\"1+2\"/>\n" +
                "  <Image src=\"@testVar\"/>\n" +
                "</Lockscreen>";
        tempDir = Files.createTempDirectory("cli-integration-test");
        tempFile = tempDir.resolve("test_theme.xml");
        Files.writeString(tempFile, dslContent, StandardCharsets.UTF_8);
    }

    @AfterEach
    void tearDown() throws Exception {
        System.setOut(originalOut);
        System.setErr(originalErr);
        Files.deleteIfExists(tempFile);
        Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
    }

    @Test
    void runWithValidDslFileReturnsZeroOrOne() {
        int exitCode = CliMain.run(new String[]{tempFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        String stdout = capturedOut.toString();
        assertTrue(stdout.length() > 0);
    }

    @Test
    void runWithVersionReturnsZero() {
        int exitCode = CliMain.run(new String[]{"--version"});
        assertEquals(0, exitCode);
        assertTrue(capturedOut.toString().contains("dsl-analyzer"));
    }

    @Test
    void runWithVerboseAndQuietMutualExclusionReturnsTwo() {
        int exitCode = CliMain.run(new String[]{"--verbose", "--quiet", tempFile.toString()});
        assertEquals(2, exitCode);
        assertTrue(capturedErr.toString().contains("mutually exclusive"));
    }

    @Test
    void runWithSyntaxOnlyAndSemanticOnlyMutualExclusionReturnsTwo() {
        int exitCode = CliMain.run(new String[]{"--syntax-only", "--semantic-only", tempFile.toString()});
        assertEquals(2, exitCode);
        assertTrue(capturedErr.toString().contains("mutually exclusive"));
    }

    @Test
    void runWithNonexistentRuleDirReturnsTwo() {
        int exitCode = CliMain.run(new String[]{"--rule-dir", "/nonexistent/rules", tempFile.toString()});
        assertEquals(2, exitCode);
        assertTrue(capturedErr.toString().toLowerCase().contains("rule directory"));
    }

    @Test
    void runWithFormatJsonOutputContainsJson() {
        int exitCode = CliMain.run(new String[]{"--format", "json", "--no-color", tempFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        String stdout = capturedOut.toString();
        assertTrue(stdout.contains("{"));
    }

    @Test
    void runWithEmptyRuleDirFallsBackToBuiltIn() throws Exception {
        Path emptyDir = Files.createTempDirectory(tempDir, "empty-rules");
        int exitCode = CliMain.run(new String[]{"--rule-dir", emptyDir.toString(), tempFile.toString()});
        assertTrue(exitCode == 0 || exitCode == 1);
        assertTrue(capturedErr.toString().contains("falling back") || capturedOut.toString().length() > 0);
        Files.deleteIfExists(emptyDir);
    }

    @Test
    void runWithMalformedRuleJsonReturnsTwo() throws Exception {
        Path badRuleDir = Files.createTempDirectory(tempDir, "bad-rules");
        Path elementsDir = badRuleDir.resolve("elements");
        Files.createDirectories(elementsDir);
        Path badFile = elementsDir.resolve("Bad.json");
        Files.writeString(badFile, "{ invalid json !!!", StandardCharsets.UTF_8);
        int exitCode = CliMain.run(new String[]{"--rule-dir", badRuleDir.toString(), tempFile.toString()});
        assertEquals(2, exitCode);
        assertTrue(capturedErr.toString().contains("Rule load error"));
        Files.deleteIfExists(badFile);
        Files.deleteIfExists(elementsDir);
        Files.deleteIfExists(badRuleDir);
    }
}
