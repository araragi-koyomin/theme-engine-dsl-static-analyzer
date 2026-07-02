package com.huawei.theme.analysis.core.cli;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

@Data
@Builder
public class InspectionConfig {
    List<String> rootElementNames;
    List<String> enabledRuleIds;
    List<String> disabledRuleIds;
    Map<String, DiagnosticSeverity> severityOverrides;
}
