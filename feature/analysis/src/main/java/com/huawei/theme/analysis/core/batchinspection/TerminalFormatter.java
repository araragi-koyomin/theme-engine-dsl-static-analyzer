package com.huawei.theme.analysis.core.batchinspection;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.batchinspection.model.FileDiagnosticResult;
import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

public class TerminalFormatter {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";

    private final boolean noColor;

    public TerminalFormatter(boolean noColor) {
        this.noColor = noColor;
    }

    public String formatDiagnostic(Diagnostic diagnostic) {
        String severityLabel = severityLabel(diagnostic.getSeverity());
        String colorPrefix = colorForSeverity(diagnostic.getSeverity());
        String base = diagnostic.getFilePath() + ":" + diagnostic.getLine() + ":" + diagnostic.getColumn()
                + ": " + severityLabel + ": " + diagnostic.getMessage() + " [" + diagnostic.getRuleId() + "]";
        if (noColor) {
            return base;
        }
        return colorPrefix + base + ANSI_RESET;
    }

    public String formatSuggestedFixes(List<SuggestedFix> fixes) {
        if (fixes == null || fixes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (SuggestedFix fix : fixes) {
            sb.append("  建议修复: ").append(fix.getText()).append("\n");
        }
        return sb.toString();
    }

    public String formatSummary(BatchInspectionResult result) {
        return result.getErrorCount() + " errors, " + result.getWarningCount() + " warnings, " + result.getInfoCount() + " info";
    }

    public String formatFileResult(FileDiagnosticResult result) {
        StringBuilder sb = new StringBuilder();
        List<Diagnostic> sorted = sortDiagnostics(result.getDiagnostics());
        for (Diagnostic d : sorted) {
            sb.append(formatDiagnostic(d)).append("\n");
            if (d.getSuggestedFixes() != null && !d.getSuggestedFixes().isEmpty()) {
                sb.append(formatSuggestedFixes(d.getSuggestedFixes()));
            }
        }
        sb.append(fileSummary(result)).append("\n");
        return sb.toString();
    }

    public String formatFullReport(BatchInspectionResult result) {
        StringBuilder sb = new StringBuilder();
        for (FileDiagnosticResult fileResult : result.getFileResults()) {
            if (fileResult.getDiagnostics() != null && !fileResult.getDiagnostics().isEmpty()) {
                sb.append(formatFileResult(fileResult));
            }
        }
        sb.append("\n").append(formatSummary(result)).append("\n");
        return sb.toString();
    }

    private String severityLabel(DiagnosticSeverity severity) {
        switch (severity) {
            case ERROR: return "error";
            case WARNING: return "warning";
            case INFO: return "info";
            default: return "unknown";
        }
    }

    private String colorForSeverity(DiagnosticSeverity severity) {
        if (noColor) {
            return "";
        }
        switch (severity) {
            case ERROR: return ANSI_RED;
            case WARNING: return ANSI_YELLOW;
            case INFO: return ANSI_BLUE;
            default: return "";
        }
    }

    private List<Diagnostic> sortDiagnostics(List<Diagnostic> diagnostics) {
        if (diagnostics == null) {
            return List.of();
        }
        return diagnostics.stream()
                .sorted(Comparator.comparing(Diagnostic::getSeverity, severityOrder())
                        .thenComparing(Diagnostic::getFilePath)
                        .thenComparing(Diagnostic::getLine))
                .collect(Collectors.toList());
    }

    private Comparator<DiagnosticSeverity> severityOrder() {
        return Comparator.comparingInt(s -> {
            switch (s) {
                case ERROR: return 0;
                case WARNING: return 1;
                case INFO: return 2;
                default: return 3;
            }
        });
    }

    private String fileSummary(FileDiagnosticResult result) {
        int errors = countBySeverity(result.getDiagnostics(), DiagnosticSeverity.ERROR);
        int warnings = countBySeverity(result.getDiagnostics(), DiagnosticSeverity.WARNING);
        int infos = countBySeverity(result.getDiagnostics(), DiagnosticSeverity.INFO);
        return errors + " errors, " + warnings + " warnings, " + infos + " info";
    }

    private int countBySeverity(List<Diagnostic> diagnostics, DiagnosticSeverity severity) {
        if (diagnostics == null) {
            return 0;
        }
        return (int) diagnostics.stream()
                .filter(d -> d.getSeverity() == severity)
                .count();
    }
}
