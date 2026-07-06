package com.huawei.theme.analysis.core.batchinspection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.batchinspection.model.FileDiagnosticResult;
import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

public class ReportExporterImpl implements ReportExporter {

    private final TerminalFormatter terminalFormatter;
    private final JsonReportSerializer jsonSerializer;

    public ReportExporterImpl(TerminalFormatter terminalFormatter) {
        this.terminalFormatter = Objects.requireNonNull(terminalFormatter, "terminalFormatter must not be null");
        this.jsonSerializer = new JsonReportSerializer();
    }

    @Override
    public String exportTerminal(BatchInspectionResult result) {
        Objects.requireNonNull(result, "result must not be null");
        return terminalFormatter.formatFullReport(result);
    }

    @Override
    public String exportJson(BatchInspectionResult result) {
        Objects.requireNonNull(result, "result must not be null");
        return jsonSerializer.serialize(result);
    }

    @Override
    public String exportMarkdown(BatchInspectionResult result) {
        Objects.requireNonNull(result, "result must not be null");
        StringBuilder sb = new StringBuilder();
        sb.append("# DSL 诊断报告\n\n");

        Map<DiagnosticSeverity, List<Diagnostic>> bySeverity = groupBySeverity(result);
        appendSeveritySection(sb, DiagnosticSeverity.ERROR, "Error", bySeverity);
        appendSeveritySection(sb, DiagnosticSeverity.WARNING, "Warning", bySeverity);
        appendSeveritySection(sb, DiagnosticSeverity.INFO, "Info", bySeverity);

        sb.append("---\n\n");
        sb.append("## 汇总\n\n");
        appendSummaryTable(sb, result);
        appendTotalLine(sb, result);
        return sb.toString();
    }

    @Override
    public void exportToFile(BatchInspectionResult result, String format, String outputPath) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        String content;
        switch (format.toLowerCase()) {
            case "json":
                content = exportJson(result);
                break;
            case "markdown":
                content = exportMarkdown(result);
                break;
            case "md":
                content = exportMarkdown(result);
                break;
            case "terminal":
                content = exportTerminal(result);
                break;
            default:
                throw new BatchInspectionException("Unsupported format: " + format);
        }
        try {
            Files.writeString(Path.of(outputPath), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BatchInspectionException("Failed to write report to: " + outputPath, e);
        }
    }

    private Map<DiagnosticSeverity, List<Diagnostic>> groupBySeverity(BatchInspectionResult result) {
        Map<DiagnosticSeverity, List<Diagnostic>> map = new HashMap<>();
        map.put(DiagnosticSeverity.ERROR, new ArrayList<>());
        map.put(DiagnosticSeverity.WARNING, new ArrayList<>());
        map.put(DiagnosticSeverity.INFO, new ArrayList<>());
        for (FileDiagnosticResult fileResult : result.getFileResults()) {
            if (fileResult.getDiagnostics() != null) {
                for (Diagnostic diag : fileResult.getDiagnostics()) {
                    map.getOrDefault(diag.getSeverity(), new ArrayList<>()).add(diag);
                }
            }
        }
        return map;
    }

    private void appendSeveritySection(StringBuilder sb, DiagnosticSeverity severity,
                                        String label, Map<DiagnosticSeverity, List<Diagnostic>> bySeverity) {
        sb.append("## ").append(label).append(" 级别问题\n\n");
        List<Diagnostic> diagnostics = bySeverity.getOrDefault(severity, List.of());
        if (diagnostics.isEmpty()) {
            sb.append("无 ").append(label).append(" 级别问题\n\n");
            return;
        }
        Map<String, List<Diagnostic>> byFile = diagnostics.stream()
                .collect(Collectors.groupingBy(Diagnostic::getFilePath));
        List<String> sortedFiles = byFile.keySet().stream().sorted().collect(Collectors.toList());
        for (String filePath : sortedFiles) {
            sb.append("### ").append(filePath).append("\n\n");
            List<Diagnostic> fileDiags = byFile.get(filePath).stream()
                    .sorted(Comparator.comparingInt(Diagnostic::getLine))
                    .collect(Collectors.toList());
            for (Diagnostic diag : fileDiags) {
                sb.append("- **").append(diag.getRuleId()).append("** (line ")
                        .append(diag.getLine()).append(", col ").append(diag.getColumn())
                        .append("): ").append(diag.getMessage()).append("\n");
                if (diag.getSuggestedFixes() != null && !diag.getSuggestedFixes().isEmpty()) {
                    for (SuggestedFix fix : diag.getSuggestedFixes()) {
                        sb.append("  - 建议修复: ").append(fix.getText()).append("\n");
                    }
                }
            }
            sb.append("\n");
        }
    }

    private void appendSummaryTable(StringBuilder sb, BatchInspectionResult result) {
        sb.append("| 文件 | Error | Warning | Info |\n");
        sb.append("|------|-------|---------|------|\n");
        for (FileDiagnosticResult fileResult : result.getFileResults()) {
            int errors = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.ERROR);
            int warnings = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.WARNING);
            int infos = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.INFO);
            sb.append("| ").append(fileResult.getFilePath())
                    .append(" | ").append(errors)
                    .append(" | ").append(warnings)
                    .append(" | ").append(infos).append(" |\n");
        }
    }

    private void appendTotalLine(StringBuilder sb, BatchInspectionResult result) {
        sb.append("\n**总计**: ").append(result.getTotalFiles()).append(" files, ")
                .append(result.getSkippedFiles()).append(" skipped, ")
                .append(result.getErrorCount()).append(" errors, ")
                .append(result.getWarningCount()).append(" warnings, ")
                .append(result.getInfoCount()).append(" info\n");
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
