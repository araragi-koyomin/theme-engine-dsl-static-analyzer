package com.huawei.theme.analysis.core.batchinspection;

import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.huawei.theme.analysis.core.batchinspection.model.BatchInspectionResult;
import com.huawei.theme.analysis.core.batchinspection.model.FileDiagnosticResult;
import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.shared.diagnostic.adapter.DiagnosticSeverityAdapter;

public class JsonReportSerializer {

    private final com.google.gson.Gson gson;

    public JsonReportSerializer() {
        this.gson = new GsonBuilder()
                .registerTypeAdapter(DiagnosticSeverity.class, new DiagnosticSeverityAdapter())
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
    }

    public String serialize(BatchInspectionResult result) {
        if (result.getFileResults().size() == 1) {
            return gson.toJson(buildSingleFileJson(result));
        }
        return gson.toJson(buildMultiFileJson(result));
    }

    private JsonObject buildSingleFileJson(BatchInspectionResult result) {
        JsonObject root = new JsonObject();
        FileDiagnosticResult fileResult = result.getFileResults().get(0);
        root.addProperty("file", fileResult.getFilePath());
        root.add("diagnostics", buildDiagnosticsArray(fileResult.getDiagnostics()));
        root.add("summary", buildFileSummary(result.getErrorCount(), result.getWarningCount(), result.getInfoCount()));
        return root;
    }

    private JsonObject buildMultiFileJson(BatchInspectionResult result) {
        JsonObject root = new JsonObject();
        JsonArray filesArray = new JsonArray();
        for (FileDiagnosticResult fileResult : result.getFileResults()) {
            JsonObject fileObj = new JsonObject();
            fileObj.addProperty("file", fileResult.getFilePath());
            fileObj.add("diagnostics", buildDiagnosticsArray(fileResult.getDiagnostics()));
            int fileErrors = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.ERROR);
            int fileWarnings = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.WARNING);
            int fileInfos = countBySeverity(fileResult.getDiagnostics(), DiagnosticSeverity.INFO);
            fileObj.add("summary", buildFileSummary(fileErrors, fileWarnings, fileInfos));
            filesArray.add(fileObj);
        }
        root.add("files", filesArray);
        JsonObject globalSummary = buildFileSummary(result.getErrorCount(), result.getWarningCount(), result.getInfoCount());
        globalSummary.addProperty("totalFiles", result.getTotalFiles());
        globalSummary.addProperty("skippedFiles", result.getSkippedFiles());
        root.add("summary", globalSummary);
        return root;
    }

    private JsonArray buildDiagnosticsArray(List<Diagnostic> diagnostics) {
        JsonArray array = new JsonArray();
        if (diagnostics == null) {
            return array;
        }
        for (Diagnostic diag : diagnostics) {
            JsonObject diagObj = new JsonObject();
            diagObj.addProperty("severity", diag.getSeverity().name().toLowerCase());
            diagObj.addProperty("line", diag.getLine());
            diagObj.addProperty("col", diag.getColumn());
            diagObj.addProperty("ruleId", diag.getRuleId());
            diagObj.addProperty("message", diag.getMessage());
            JsonArray fixesArray = new JsonArray();
            if (diag.getSuggestedFixes() != null) {
                for (SuggestedFix fix : diag.getSuggestedFixes()) {
                    fixesArray.add(fix.getText());
                }
            }
            diagObj.add("suggestedFixes", fixesArray);
            array.add(diagObj);
        }
        return array;
    }

    private JsonObject buildFileSummary(int errors, int warnings, int infos) {
        JsonObject summary = new JsonObject();
        summary.addProperty("errors", errors);
        summary.addProperty("warnings", warnings);
        summary.addProperty("info", infos);
        return summary;
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
