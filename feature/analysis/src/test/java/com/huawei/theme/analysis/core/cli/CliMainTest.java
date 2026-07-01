package com.huawei.theme.analysis.core.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliMainTest {

    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream capturedOut;
    private ByteArrayOutputStream capturedErr;

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
        int exitCode = CliMain.run(new String[]{"theme.xml"});
        assertEquals(0, exitCode);
        assertTrue(capturedOut.toString().contains("Configuration:"));
        assertTrue(capturedOut.toString().contains("Target: theme.xml"));
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
        int exitCode = CliMain.run(new String[]{"--unknown-flag", "theme.xml"});
        assertEquals(2, exitCode);
        String stderr = capturedErr.toString();
        assertTrue(stderr.contains("Error: Unknown option: --unknown-flag"));
        assertTrue(stderr.contains("Usage: java -jar dsl-analyzer.jar"));
    }

    @Test
    void runWithNoTypeCheckAndVerboseOutputContainsAllFields() {
        int exitCode = CliMain.run(new String[]{
                "--rule-dir", "/path/to/rules", "--no-type-check", "--verbose", "theme.xml"
        });
        assertEquals(0, exitCode);
        String stdout = capturedOut.toString();
        assertTrue(stdout.contains("Target: theme.xml"));
        assertTrue(stdout.contains("Rule directory: /path/to/rules"));
        assertTrue(stdout.contains("Type check: disabled"));
        assertTrue(stdout.contains("Verbose: enabled"));
    }

    @Test
    void runWithNoFlagsOutputShowsDefaults() {
        int exitCode = CliMain.run(new String[]{"theme.xml"});
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
        int exitCode = CliMain.run(new String[]{"theme.xml", "layout.xml"});
        assertEquals(2, exitCode);
        String stderr = capturedErr.toString();
        assertTrue(stderr.contains("Error: Multiple target paths provided"));
    }

    @Test
    void runWithTypeCheckFlagReturnsTwo() {
        int exitCode = CliMain.run(new String[]{"--type-check", "theme.xml"});
        assertEquals(2, exitCode);
        String stderr = capturedErr.toString();
        assertTrue(stderr.contains("Error: Unknown option: --type-check"));
    }
}
