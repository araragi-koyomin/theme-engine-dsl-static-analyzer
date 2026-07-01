package com.huawei.theme.analysis.core.cli;

import java.util.List;
import java.util.Map;

import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

public class CliOutputFormatter {

    private CliOutputFormatter() {}

    public static String formatError(String message) {
        return "Error: " + message;
    }

    public static String formatInfo(String message) {
        return message;
    }

    public static String formatVerbose(String message) {
        return message;
    }

    public static String formatConfig(CliConfig config, InspectionConfig inspectionConfig) {
        String ruleDirDisplay = config.getRuleDir() != null ? config.getRuleDir() : "(built-in)";
        String configPathDisplay = config.getConfigPath() != null ? config.getConfigPath() : "(none)";
        StringBuilder sb = new StringBuilder();
        sb.append("Configuration:\n");
        sb.append("  Target: ").append(config.getTargetPath()).append("\n");
        sb.append("  Rule directory: ").append(ruleDirDisplay).append("\n");
        sb.append("  Type check: ").append(config.isTypeCheck() ? "enabled" : "disabled").append("\n");
        sb.append("  Verbose: ").append(config.isVerbose() ? "enabled" : "disabled").append("\n");
        sb.append("  Config: ").append(configPathDisplay);
        if (inspectionConfig != null) {
            sb.append("\n");
            if (inspectionConfig.getRootElementNames() != null && !inspectionConfig.getRootElementNames().isEmpty()) {
                sb.append("  Root element override: ").append(inspectionConfig.getRootElementNames());
            }
            if (inspectionConfig.getEnabledRuleIds() != null && !inspectionConfig.getEnabledRuleIds().isEmpty()) {
                sb.append("\n  Enabled rule IDs: ").append(inspectionConfig.getEnabledRuleIds());
            }
            if (inspectionConfig.getDisabledRuleIds() != null && !inspectionConfig.getDisabledRuleIds().isEmpty()) {
                sb.append("\n  Disabled rule IDs: ").append(inspectionConfig.getDisabledRuleIds());
            }
            if (inspectionConfig.getSeverityOverrides() != null && !inspectionConfig.getSeverityOverrides().isEmpty()) {
                sb.append("\n  Severity overrides: ").append(formatSeverityOverrides(inspectionConfig.getSeverityOverrides()));
            }
        }
        return sb.toString();
    }

    private static String formatSeverityOverrides(Map<String, DiagnosticSeverity> overrides) {
        List<String> entries = overrides.entrySet().stream()
                .map(e -> e.getKey() + "->" + e.getValue().name().toLowerCase())
                .sorted()
                .toList();
        return entries.toString();
    }
}
