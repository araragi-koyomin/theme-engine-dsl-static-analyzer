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
public class ExpectedDiagnostic {
    private String ruleId;
    private String severity;
    private int approxLine;
    private int lineTolerance;
    private String description;
    private List<String> expectedFixes;
}
