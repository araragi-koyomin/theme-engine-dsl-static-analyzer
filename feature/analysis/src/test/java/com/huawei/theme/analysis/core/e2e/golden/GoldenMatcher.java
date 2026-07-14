package com.huawei.theme.analysis.core.e2e.golden;

import java.util.ArrayList;
import java.util.List;

public class GoldenMatcher {

    private static final int MUST_NOT_TRIGGER_TOLERANCE = 2;

    public MatchResult match(List<ActualDiagnostic> actuals, int actualExitCode, GoldenExpectation expectation) {
        MatchResult result = new MatchResult(false);

        checkExitCode(actualExitCode, expectation, result);
        checkCounts(actuals, expectation, result);
        checkExpectedDiagnostics(actuals, expectation, result);
        checkMustNotTrigger(actuals, expectation, result);

        if (!result.getDiffs().isEmpty()) {
            return result;
        }
        return MatchResult.pass();
    }

    private void checkExitCode(int actual, GoldenExpectation exp, MatchResult result) {
        if (actual != exp.getExpectedExitCode()) {
            result.addDiff(String.format("exit code: expected %d got %d", exp.getExpectedExitCode(), actual));
        }
    }

    private void checkCounts(List<ActualDiagnostic> actuals, GoldenExpectation exp, MatchResult result) {
        int actualErrors = countBySeverity(actuals, "error");
        int actualWarnings = countBySeverity(actuals, "warning");
        int actualInfos = countBySeverity(actuals, "info");
        GoldenExpectation.ExpectedCounts expected = exp.getExpectedCounts();
        if (actualErrors != expected.getErrors()) {
            result.addDiff(String.format("error count: expected %d got %d", expected.getErrors(), actualErrors));
        }
        if (actualWarnings != expected.getWarnings()) {
            result.addDiff(String.format("warning count: expected %d got %d", expected.getWarnings(), actualWarnings));
        }
        if (actualInfos != expected.getInfo()) {
            result.addDiff(String.format("info count: expected %d got %d", expected.getInfo(), actualInfos));
        }
    }

    private void checkExpectedDiagnostics(List<ActualDiagnostic> actuals, GoldenExpectation exp, MatchResult result) {
        List<ActualDiagnostic> remaining = new ArrayList<>(actuals);
        for (ExpectedDiagnostic ed : exp.getExpectedDiagnostics()) {
            ActualDiagnostic matched = findAndRemove(remaining, ed);
            if (matched == null) {
                result.addDiff(String.format("FN: missing %s/%s near line %d (tolerance %d)",
                        ed.getRuleId(), ed.getSeverity(), ed.getApproxLine(), ed.getLineTolerance()));
            }
        }
        for (ActualDiagnostic leftover : remaining) {
            result.addDiff(String.format("FP: unexpected %s/%s at line %d col %d: %s",
                    leftover.getRuleId(), leftover.getSeverity(), leftover.getLine(), leftover.getCol(),
                    leftover.getMessage()));
        }
    }

    private ActualDiagnostic findAndRemove(List<ActualDiagnostic> pool, ExpectedDiagnostic ed) {
        for (int i = 0; i < pool.size(); i++) {
            ActualDiagnostic d = pool.get(i);
            if (matches(d, ed)) {
                pool.remove(i);
                return d;
            }
        }
        return null;
    }

    private boolean matches(ActualDiagnostic d, ExpectedDiagnostic ed) {
        if (!d.getRuleId().equals(ed.getRuleId())) {
            return false;
        }
        if (!d.getSeverity().equals(ed.getSeverity())) {
            return false;
        }
        return Math.abs(d.getLine() - ed.getApproxLine()) <= ed.getLineTolerance();
    }

    private void checkMustNotTrigger(List<ActualDiagnostic> actuals, GoldenExpectation exp, MatchResult result) {
        for (MustNotTriggerEntry entry : exp.getMustNotTrigger()) {
            for (ActualDiagnostic d : actuals) {
                if (Math.abs(d.getLine() - entry.getApproxLine()) <= MUST_NOT_TRIGGER_TOLERANCE) {
                    result.addDiff(String.format("mustNotTrigger violated at line %d: %s (reason: %s)",
                            d.getLine(), d.getRuleId(), entry.getReason()));
                    break;
                }
            }
        }
    }

    private int countBySeverity(List<ActualDiagnostic> actuals, String severity) {
        return (int) actuals.stream().filter(d -> severity.equals(d.getSeverity())).count();
    }
}
