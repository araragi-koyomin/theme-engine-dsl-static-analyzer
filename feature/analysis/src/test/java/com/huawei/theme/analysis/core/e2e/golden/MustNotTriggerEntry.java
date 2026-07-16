package com.huawei.theme.analysis.core.e2e.golden;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MustNotTriggerEntry {
    private int approxLine;
    private String reason;
}
