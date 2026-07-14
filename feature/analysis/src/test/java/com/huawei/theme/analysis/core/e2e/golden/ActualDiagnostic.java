package com.huawei.theme.analysis.core.e2e.golden;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ActualDiagnostic {
    private String severity;
    private int line;
    private int col;
    private String ruleId;
    private String message;
    private List<String> suggestedFixes;
}
