package com.huawei.theme.analysis.core.e2e.golden;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoldenExpectation {
    private String fixture;
    private int expectedExitCode;
    private ExpectedCounts expectedCounts;
    private List<ExpectedDiagnostic> expectedDiagnostics;
    private List<MustNotTriggerEntry> mustNotTrigger;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExpectedCounts {
        private int errors;
        private int warnings;
        private int info;
    }
}
