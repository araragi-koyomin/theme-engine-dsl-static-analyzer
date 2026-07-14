package com.huawei.theme.analysis.core.e2e.golden;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class MatchResult {
    private final boolean passed;
    private final List<String> diffs = new ArrayList<>();

    public MatchResult(boolean passed) {
        this.passed = passed;
    }

    public static MatchResult pass() {
        return new MatchResult(true);
    }

    public static MatchResult fail(String reason) {
        MatchResult r = new MatchResult(false);
        r.diffs.add(reason);
        return r;
    }

    public MatchResult addDiff(String diff) {
        this.diffs.add(diff);
        return this;
    }

    public String renderDiffs() {
        return String.join("\n", diffs);
    }
}
