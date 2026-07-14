package com.huawei.theme.analysis.core.e2e.golden;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoldenMatcherExpectedFixesTest {

    private ActualDiagnostic diag(String ruleId, String severity, int line, List<String> fixes) {
        ActualDiagnostic d = new ActualDiagnostic();
        d.setRuleId(ruleId);
        d.setSeverity(severity);
        d.setLine(line);
        d.setSuggestedFixes(fixes);
        return d;
    }

    private ExpectedDiagnostic exp(String ruleId, String severity, int line, List<String> expectedFixes) {
        return ExpectedDiagnostic.builder()
                .ruleId(ruleId).severity(severity).approxLine(line).lineTolerance(2)
                .expectedFixes(expectedFixes).build();
    }

    private GoldenExpectation expectation(ExpectedDiagnostic... diags) {
        return GoldenExpectation.builder()
                .expectedExitCode(1)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder()
                        .errors(diags.length).warnings(0).info(0).build())
                .expectedDiagnostics(List.of(diags))
                .mustNotTrigger(List.of())
                .build();
    }

    @Test
    void fixes_orderIndependent_passes() {
        ExpectedDiagnostic ed = exp("SEM-ATTR-001", "error", 5, List.of("fix1", "fix2"));
        GoldenExpectation ge = expectation(ed);
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "error", 5, List.of("fix2", "fix1")));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertTrue(result.isPassed(), result.renderDiffs());
    }

    @Test
    void fixes_actualHasExtra_failsAsFalsePositive() {
        ExpectedDiagnostic ed = exp("SEM-ATTR-001", "error", 5, List.of("fix1"));
        GoldenExpectation ge = expectation(ed);
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "error", 5, List.of("fix1", "fix2")));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertFalse(result.isPassed());
        assertTrue(result.renderDiffs().contains("FP"));
    }

    @Test
    void fixes_actualMissing_failsAsFalseNegative() {
        ExpectedDiagnostic ed = exp("SEM-ATTR-001", "error", 5, List.of("fix1", "fix2"));
        GoldenExpectation ge = expectation(ed);
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "error", 5, List.of("fix1")));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertFalse(result.isPassed());
        assertTrue(result.renderDiffs().contains("FN"));
    }

    @Test
    void fixes_contentMismatch_fails() {
        ExpectedDiagnostic ed = exp("SEM-ATTR-001", "error", 5, List.of("fix1"));
        GoldenExpectation ge = expectation(ed);
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "error", 5, List.of("wrong")));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertFalse(result.isPassed());
    }

    @Test
    void fixes_nullExpectedFixes_skipsCheck() {
        ExpectedDiagnostic ed = exp("SEM-ATTR-001", "error", 5, null);
        GoldenExpectation ge = expectation(ed);
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "error", 5, List.of("anything")));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertTrue(result.isPassed(), result.renderDiffs());
    }

    @Test
    void fixes_emptyBoth_passes() {
        ExpectedDiagnostic ed = exp("SEM-ATTR-001", "error", 5, List.of());
        GoldenExpectation ge = expectation(ed);
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "error", 5, List.of()));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertTrue(result.isPassed(), result.renderDiffs());
    }

    @Test
    void fixes_emptyExpectedButActualHasFix_failsAsFalsePositive() {
        ExpectedDiagnostic ed = exp("SEM-ATTR-001", "error", 5, List.of());
        GoldenExpectation ge = expectation(ed);
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "error", 5, List.of("unexpected")));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertFalse(result.isPassed());
        assertTrue(result.renderDiffs().contains("FP"));
    }
}
