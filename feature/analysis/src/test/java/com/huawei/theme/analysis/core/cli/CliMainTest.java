package com.huawei.theme.analysis.core.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliMainTest {

    private static String tempFilePath;
    private static Path tempDir;

    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream capturedOut;
    private ByteArrayOutputStream capturedErr;

    @BeforeAll
    static void createTempFile() throws Exception {
        Path tempFile = Files.createTempFile("dsl-test", ".xml");
        Files.writeString(tempFile, "<Lockscreen/>", StandardCharsets.UTF_8);
        tempFilePath = tempFile.toString();
        tempDir = Files.createTempDirectory("dsl-test-cli");
    }

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        originalErr = System.err;
        capturedOut = new ByteArrayOutputStream();
        capturedErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));
        System.setErr(new PrintStream(capturedErr));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void runWithValidArgsReturnsZero() {
        int exitCode = CliMain.run(new String[]{tempFilePath});
        assertEquals(0, exitCode);
        assertTrue(capturedOut.toString().contains("Configuration:"));
        assertTrue(capturedOut.toString().contains("Target: " + tempFilePath));
    }

    @Test
    void runWithNoArgsReturnsTwoAndStderrHasError() {
        int exitCode = CliMain.run(new String[]{});
        assertEquals(2, exitCode);
        String stderr = capturedErr.toString();
        assertTrue(stderr.contains("Error: No target path provided"));
        assertTrue(stderr.contains("Usage: java -jar dsl-analyzer.jar"));
    }

    @Test
    void runWithRuleDirWithoutValueReturnsTwo() {
        int exitCode = CliMain.run(new String[]{"--rule-dir"});
        assertEquals(2, exitCode);
        String stderr = capturedErr.toString();
        assertTrue(stderr.contains("Error: --rule-dir requires a path value"));
        assertTrue(stderr.contains("Usage: java -jar dsl-analyzer.jar"));
    }

    @Test
    void runWithUnknownFlagReturnsTwo() {
        int exitCode = CliMain.run(new String[]{"--unknown-flag", tempFilePath});
        assertEquals(2, exitCode);
        String stderr = capturedErr.toString();
        assertTrue(stderr.contains("Error: Unknown option: --unknown-flag"));
        assertTrue(stderr.contains("Usage: java -jar dsl-analyzer.jar"));
    }

    @Test
    void runWithNoTypeCheckAndVerboseOutputContainsAllFields() {
        int exitCode = CliMain.run(new String[]{
                "--rule-dir", "/path/to/rules", "--no-type-check", "--verbose", tempFilePath
        });
        assertEquals(0, exitCode);
        String stdout = capturedOut.toString();
        assertTrue(stdout.contains("Target: " + tempFilePath));
        assertTrue(stdout.contains("Rule directory: /path/to/rules"));
        assertTrue(stdout.contains("Type check: disabled"));
        assertTrue(stdout.contains("Verbose: enabled"));
    }

    @Test
    void runWithNoFlagsOutputShowsDefaults() {
        int exitCode = CliMain.run(new String[]{tempFilePath});
        assertEquals(0, exitCode);
        String stdout = capturedOut.toString();
        assertTrue(stdout.contains("Rule directory: (built-in)"));
        assertTrue(stdout.contains("Type check: enabled"));
        assertTrue(stdout.contains("Verbose: disabled"));
    }

    @Test
    void runWithHelpFlagReturnsZeroAndStdoutHasUsage() {
        int exitCode = CliMain.run(new String[]{"--help"});
        assertEquals(0, exitCode);
        String stdout = capturedOut.toString();
        assertTrue(stdout.contains("Usage: java -jar dsl-analyzer.jar"));
        assertTrue(stdout.contains("--no-type-check"));
        assertTrue(stdout.contains("--help"));
        assertTrue(stdout.contains("--config"));
        assertEquals(0, capturedErr.toString().length());
    }

    @Test
    void runWithShortHelpFlagReturnsZero() {
        int exitCode = CliMain.run(new String[]{"-h"});
        assertEquals(0, exitCode);
        assertTrue(capturedOut.toString().contains("Usage: java -jar dsl-analyzer.jar"));
    }

    @Test
    void runWithMultiplePositionalArgsReturnsTwo() {
        int exitCode = CliMain.run(new String[]{tempFilePath, "layout.xml"});
        assertEquals(2, exitCode);
        String stderr = capturedErr.toString();
        assertTrue(stderr.contains("Error: Multiple target paths provided"));
    }

    @Test
    void runWithTypeCheckFlagReturnsTwo() {
        int exitCode = CliMain.run(new String[]{"--type-check", tempFilePath});
        assertEquals(2, exitCode);
        String stderr = capturedErr.toString();
        assertTrue(stderr.contains("Error: Unknown option: --type-check"));
    }

    @Test
    void runWithNonexistentPathReturnsTwo() {
        int exitCode = CliMain.run(new String[]{"/nonexistent/path/theme.xml"});
        assertEquals(2, exitCode);
        String stderr = capturedErr.toString();
        assertTrue(stderr.contains("Error: Path not found: /nonexistent/path/theme.xml"));
        assertTrue(stderr.contains("Usage: java -jar dsl-analyzer.jar"));
    }

    @Test
    void runWithNonFileNonDirectoryPathReturnsTwo() throws Exception {
        Path localTempDir = Files.createTempDirectory("dsl-test-dir");
        Path specialFile = localTempDir.resolve("special_pipe");
        Files.writeString(specialFile, "test", StandardCharsets.UTF_8);
        File f = specialFile.toFile();
        f.delete();
        f.createNewFile();
        int exitCode = CliMain.run(new String[]{specialFile.toString()});
        assertEquals(0, exitCode);
        Files.deleteIfExists(specialFile);
        Files.deleteIfExists(localTempDir);
    }

    @Test
    void runWithConfigPathReturnsZeroWhenConfigFileExists() throws Exception {
        Path configFile = Files.createTempFile(tempDir, "config", ".json");
        Files.writeString(configFile, "{}", StandardCharsets.UTF_8);

        int exitCode = CliMain.run(new String[]{"--config", configFile.toString(), tempFilePath});
        assertEquals(0, exitCode);
        String stdout = capturedOut.toString();
        assertTrue(stdout.contains("Config: " + configFile.toString()));
        assertTrue(stdout.contains("Target: " + tempFilePath));
    }

    @Test
    void runWithNonexistentConfigPathReturnsTwo() {
        int exitCode = CliMain.run(new String[]{"--config", "/nonexistent/config.json", tempFilePath});
        assertEquals(2, exitCode);
        assertTrue(capturedErr.toString().contains("Config file not found"));
    }

    @Test
    void runWithConfigPathNotFileReturnsTwo() throws Exception {
        Path dirPath = Files.createTempDirectory(tempDir, "not-a-file");
        int exitCode = CliMain.run(new String[]{"--config", dirPath.toString(), tempFilePath});
        assertEquals(2, exitCode);
        assertTrue(capturedErr.toString().contains("Config path is not a file"));
    }

    @Test
    void runWithInvalidConfigJsonReturnsTwo() throws Exception {
        Path configFile = Files.createTempFile(tempDir, "config", ".json");
        Files.writeString(configFile, "{ invalid json !!!", StandardCharsets.UTF_8);

        int exitCode = CliMain.run(new String[]{"--config", configFile.toString(), tempFilePath});
        assertEquals(2, exitCode);
        assertTrue(capturedErr.toString().contains("Config load error"));
    }

    @Test
    void runWithConfigAndRuleDirReturnsZero() throws Exception {
        Path configFile = Files.createTempFile(tempDir, "config", ".json");
        Files.writeString(configFile, "{}", StandardCharsets.UTF_8);

        int exitCode = CliMain.run(new String[]{
                "--config", configFile.toString(), "--rule-dir", "/path/to/rules", tempFilePath
        });
        assertEquals(0, exitCode);
        String stdout = capturedOut.toString();
        assertTrue(stdout.contains("Config: " + configFile.toString()));
        assertTrue(stdout.contains("Rule directory: /path/to/rules"));
    }

    @Test
    void runWithConfigMissingValueReturnsTwo() {
        int exitCode = CliMain.run(new String[]{"--config"});
        assertEquals(2, exitCode);
        assertTrue(capturedErr.toString().contains("--config requires a path value"));
    }
}
