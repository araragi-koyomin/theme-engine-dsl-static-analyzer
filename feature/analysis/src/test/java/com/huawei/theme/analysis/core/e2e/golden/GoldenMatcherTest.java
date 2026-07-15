package com.huawei.theme.analysis.core.e2e.golden;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoldenMatcherTest {

    private ActualDiagnostic diag(String ruleId, String severity, int line) {
        ActualDiagnostic d = new ActualDiagnostic();
        d.setRuleId(ruleId);
        d.setSeverity(severity);
        d.setLine(line);
        return d;
    }

    private ExpectedDiagnostic exp(String ruleId, String severity, int line) {
        return ExpectedDiagnostic.builder()
                .ruleId(ruleId).severity(severity).approxLine(line).lineTolerance(2).build();
    }

    @Test
    void match_allCorrect_passes() {
        GoldenExpectation ge = GoldenExpectation.builder()
                .expectedExitCode(1)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder().errors(2).warnings(0).info(0).build())
                .expectedDiagnostics(List.of(exp("SEM-ATTR-001", "error", 5), exp("SEM-ENUM-001", "error", 8)))
                .mustNotTrigger(List.of())
                .build();
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "error", 5), diag("SEM-ENUM-001", "error", 8));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertTrue(result.isPassed(), result.renderDiffs());
    }

    @Test
    void match_lineWithinTolerance_passes() {
        GoldenExpectation ge = GoldenExpectation.builder()
                .expectedExitCode(1)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder().errors(1).warnings(0).info(0).build())
                .expectedDiagnostics(List.of(exp("SEM-ATTR-001", "error", 5)))
                .mustNotTrigger(List.of())
                .build();
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "error", 7));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertTrue(result.isPassed(), result.renderDiffs());
    }

    @Test
    void match_lineBeyondTolerance_failsAsFalseNegative() {
        GoldenExpectation ge = GoldenExpectation.builder()
                .expectedExitCode(1)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder().errors(1).warnings(0).info(0).build())
                .expectedDiagnostics(List.of(exp("SEM-ATTR-001", "error", 5)))
                .mustNotTrigger(List.of())
                .build();
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "error", 20));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertFalse(result.isPassed());
        assertTrue(result.renderDiffs().contains("FN") || result.renderDiffs().contains("missing"));
    }

    @Test
    void match_unexpectedActual_failsAsFalsePositive() {
        GoldenExpectation ge = GoldenExpectation.builder()
                .expectedExitCode(0)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder().errors(0).warnings(0).info(0).build())
                .expectedDiagnostics(List.of())
                .mustNotTrigger(List.of())
                .build();
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "error", 5));

        MatchResult result = new GoldenMatcher().match(actuals, 0, ge);

        assertFalse(result.isPassed());
        assertTrue(result.renderDiffs().contains("FP") || result.renderDiffs().contains("unexpected"));
    }

    @Test
    void match_exitCodeMismatch_fails() {
        GoldenExpectation ge = GoldenExpectation.builder()
                .expectedExitCode(0)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder().errors(0).warnings(0).info(0).build())
                .expectedDiagnostics(List.of())
                .mustNotTrigger(List.of())
                .build();

        MatchResult result = new GoldenMatcher().match(List.of(), 1, ge);

        assertFalse(result.isPassed());
        assertTrue(result.renderDiffs().toLowerCase().contains("exit code"));
    }

    @Test
    void match_countMismatch_fails() {
        GoldenExpectation ge = GoldenExpectation.builder()
                .expectedExitCode(1)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder().errors(3).warnings(0).info(0).build())
                .expectedDiagnostics(List.of(exp("SEM-ATTR-001", "error", 5)))
                .mustNotTrigger(List.of())
                .build();
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "error", 5));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertFalse(result.isPassed());
        assertTrue(result.renderDiffs().toLowerCase().contains("error count"));
    }

    @Test
    void match_mustNotTriggerViolated_fails() {
        GoldenExpectation ge = GoldenExpectation.builder()
                .expectedExitCode(0)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder().errors(0).warnings(0).info(0).build())
                .expectedDiagnostics(List.of())
                .mustNotTrigger(List.of(MustNotTriggerEntry.builder().approxLine(3).reason("boundary").build()))
                .build();
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "warning", 4));

        MatchResult result = new GoldenMatcher().match(actuals, 0, ge);

        assertFalse(result.isPassed());
        assertTrue(result.renderDiffs().toLowerCase().contains("mustnottrigger"));
    }

    @Test
    void match_severityMismatch_failsAsFnAndFp() {
        GoldenExpectation ge = GoldenExpectation.builder()
                .expectedExitCode(1)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder().errors(1).warnings(1).info(0).build())
                .expectedDiagnostics(List.of(exp("SEM-ATTR-001", "error", 5)))
                .mustNotTrigger(List.of())
                .build();
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "warning", 5));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertFalse(result.isPassed());
    }

    @Test
    void match_multipleSameRuleIdAtDifferentLines_eachMatchedIndividually() {
        GoldenExpectation ge = GoldenExpectation.builder()
                .expectedExitCode(1)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder().errors(2).warnings(0).info(0).build())
                .expectedDiagnostics(List.of(exp("SEM-ENUM-001", "error", 8), exp("SEM-ENUM-001", "error", 11)))
                .mustNotTrigger(List.of())
                .build();
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ENUM-001", "error", 8), diag("SEM-ENUM-001", "error", 11));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertTrue(result.isPassed(), result.renderDiffs());
    }
}
