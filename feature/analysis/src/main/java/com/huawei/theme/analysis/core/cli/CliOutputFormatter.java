package com.huawei.theme.analysis.core.cli;

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

    public static String formatConfig(CliConfig config) {
        String ruleDirDisplay = config.getRuleDir() != null ? config.getRuleDir() : "(built-in)";
        StringBuilder sb = new StringBuilder();
        sb.append("Configuration:\n");
        sb.append("  Target: ").append(config.getTargetPath()).append("\n");
        sb.append("  Rule directory: ").append(ruleDirDisplay).append("\n");
        sb.append("  Type check: ").append(config.isTypeCheck() ? "enabled" : "disabled").append("\n");
        sb.append("  Verbose: ").append(config.isVerbose() ? "enabled" : "disabled");
        return sb.toString();
    }
}
