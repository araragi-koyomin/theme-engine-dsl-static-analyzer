package com.huawei.theme.analysis.core.e2e.golden;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class GoldenExpectationParserTest {

    @TempDir
    Path tempDir;

    private Path writeGolden(String content) throws Exception {
        Path p = tempDir.resolve("sample.expected.json");
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void parse_validGolden_populatesAllFields() throws Exception {
        String json = """
                {
                  "fixture": "complex/deep_nesting_violations.xml",
                  "expectedExitCode": 1,
                  "expectedCounts": { "errors": 2, "warnings": 1, "info": 0 },
                  "expectedDiagnostics": [
                    { "ruleId": "SEM-ATTR-001", "severity": "error", "approxLine": 5, "lineTolerance": 2, "description": "alpha=300" },
                    { "ruleId": "SEM-ENUM-001", "severity": "error", "approxLine": 8, "lineTolerance": 2 }
                  ],
                  "mustNotTrigger": [
                    { "approxLine": 3, "reason": "boundary valid" }
                  ]
                }
                """;
        Path goldenFile = writeGolden(json);
        GoldenExpectationParser parser = new GoldenExpectationParser();

        GoldenExpectation exp = parser.parse(goldenFile);

        assertEquals("complex/deep_nesting_violations.xml", exp.getFixture());
        assertEquals(1, exp.getExpectedExitCode());
        assertEquals(2, exp.getExpectedCounts().getErrors());
        assertEquals(1, exp.getExpectedCounts().getWarnings());
        assertEquals(0, exp.getExpectedCounts().getInfo());
        assertEquals(2, exp.getExpectedDiagnostics().size());
        assertEquals("SEM-ATTR-001", exp.getExpectedDiagnostics().get(0).getRuleId());
        assertEquals("error", exp.getExpectedDiagnostics().get(0).getSeverity());
        assertEquals(5, exp.getExpectedDiagnostics().get(0).getApproxLine());
        assertEquals(2, exp.getExpectedDiagnostics().get(0).getLineTolerance());
        assertEquals(1, exp.getMustNotTrigger().size());
        assertEquals(3, exp.getMustNotTrigger().get(0).getApproxLine());
    }

    @Test
    void parse_cleanFixture_emptyDiagnostics() throws Exception {
        String json = """
                {
                  "fixture": "clean/lockscreen_valid.xml",
                  "expectedExitCode": 0,
                  "expectedCounts": { "errors": 0, "warnings": 0, "info": 0 },
                  "expectedDiagnostics": [],
                  "mustNotTrigger": []
                }
                """;
        Path goldenFile = writeGolden(json);
        GoldenExpectationParser parser = new GoldenExpectationParser();

        GoldenExpectation exp = parser.parse(goldenFile);

        assertEquals(0, exp.getExpectedExitCode());
        assertTrue(exp.getExpectedDiagnostics().isEmpty());
        assertTrue(exp.getMustNotTrigger().isEmpty());
    }

    @Test
    void parse_missingMustNotTrigger_defaultsToEmptyList() throws Exception {
        String json = """
                {
                  "fixture": "x.xml",
                  "expectedExitCode": 1,
                  "expectedCounts": { "errors": 1, "warnings": 0, "info": 0 },
                  "expectedDiagnostics": [
                    { "ruleId": "SEM-REF-001", "severity": "error", "approxLine": 5, "lineTolerance": 2 }
                  ]
                }
                """;
        Path goldenFile = writeGolden(json);
        GoldenExpectationParser parser = new GoldenExpectationParser();

        GoldenExpectation exp = parser.parse(goldenFile);

        assertNotNull(exp.getMustNotTrigger());
        assertTrue(exp.getMustNotTrigger().isEmpty());
    }

    @Test
    void parse_missingLineTolerance_defaultsToTwo() throws Exception {
        String json = """
                {
                  "fixture": "x.xml",
                  "expectedExitCode": 1,
                  "expectedCounts": { "errors": 1, "warnings": 0, "info": 0 },
                  "expectedDiagnostics": [
                    { "ruleId": "SEM-REF-001", "severity": "error", "approxLine": 5 }
                  ]
                }
                """;
        Path goldenFile = writeGolden(json);
        GoldenExpectationParser parser = new GoldenExpectationParser();

        GoldenExpectation exp = parser.parse(goldenFile);

        assertEquals(2, exp.getExpectedDiagnostics().get(0).getLineTolerance());
    }

    @Test
    void parse_expectedFixes_populatesField() throws Exception {
        String json = """
                {
                  "fixture": "x.xml",
                  "expectedExitCode": 1,
                  "expectedCounts": { "errors": 1, "warnings": 0, "info": 0 },
                  "expectedDiagnostics": [
                    { "ruleId": "SEM-ATTR-001", "severity": "error", "approxLine": 5, "lineTolerance": 2,
                      "expectedFixes": ["replace alpha=300 with alpha=255", "remove attribute"] }
                  ]
                }
                """;
        Path goldenFile = writeGolden(json);
        GoldenExpectationParser parser = new GoldenExpectationParser();

        GoldenExpectation exp = parser.parse(goldenFile);

        List<String> fixes = exp.getExpectedDiagnostics().get(0).getExpectedFixes();
        assertNotNull(fixes);
        assertEquals(2, fixes.size());
        assertEquals("replace alpha=300 with alpha=255", fixes.get(0));
        assertEquals("remove attribute", fixes.get(1));
    }

    @Test
    void parse_missingExpectedFixes_staysNull() throws Exception {
        String json = """
                {
                  "fixture": "x.xml",
                  "expectedExitCode": 1,
                  "expectedCounts": { "errors": 1, "warnings": 0, "info": 0 },
                  "expectedDiagnostics": [
                    { "ruleId": "SEM-REF-001", "severity": "error", "approxLine": 5, "lineTolerance": 2 }
                  ]
                }
                """;
        Path goldenFile = writeGolden(json);
        GoldenExpectationParser parser = new GoldenExpectationParser();

        GoldenExpectation exp = parser.parse(goldenFile);

        assertNull(exp.getExpectedDiagnostics().get(0).getExpectedFixes());
    }
}
