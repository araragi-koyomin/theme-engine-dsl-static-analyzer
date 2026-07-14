package com.huawei.theme.analysis.core.e2e;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.huawei.theme.analysis.core.cli.CliMain;
import com.huawei.theme.analysis.core.e2e.golden.ActualDiagnostic;

/**
 * Dumps current CLI JSON output as a .expected.json draft for a fixture.
 * Usage: run as test to regenerate golden drafts, then human-review against ANSWER_KEY.md.
 */
public class GoldenDumper {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void dumpFixture(Path fixtureXml, Path outputGolden) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        int exitCode;
        try {
            exitCode = CliMain.run(new String[]{"--format", "json", "--no-color", fixtureXml.toString()});
        } finally {
            System.setOut(original);
        }
        String json = out.toString(StandardCharsets.UTF_8);
        ActualDiagnostic[] diags = extractDiagnostics(json);
        int errors = countSeverity(diags, "error");
        int warnings = countSeverity(diags, "warning");
        int infos = countSeverity(diags, "info");

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"fixture\": \"").append(fixtureXml.getFileName()).append("\",\n");
        sb.append("  \"expectedExitCode\": ").append(exitCode).append(",\n");
        sb.append("  \"expectedCounts\": { \"errors\": ").append(errors)
                .append(", \"warnings\": ").append(warnings)
                .append(", \"info\": ").append(infos).append(" },\n");
        sb.append("  \"expectedDiagnostics\": [\n");
        for (int i = 0; i < diags.length; i++) {
            ActualDiagnostic d = diags[i];
            sb.append("    { \"ruleId\": \"").append(d.getRuleId())
                    .append("\", \"severity\": \"").append(d.getSeverity())
                    .append("\", \"approxLine\": ").append(d.getLine())
                    .append(", \"lineTolerance\": 2 }");
            if (i < diags.length - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");
        sb.append("  \"mustNotTrigger\": []\n");
        sb.append("}\n");
        Files.writeString(outputGolden, sb.toString(), StandardCharsets.UTF_8);
    }

    private static ActualDiagnostic[] extractDiagnostics(String json) {
        JsonElement root = JsonParser.parseString(json);
        JsonElement diagsElement;
        if (root.isJsonObject() && root.getAsJsonObject().has("files")) {
            diagsElement = root.getAsJsonObject().getAsJsonArray("files").get(0).getAsJsonObject().get("diagnostics");
        } else {
            diagsElement = root.getAsJsonObject().get("diagnostics");
        }
        return GSON.fromJson(diagsElement, ActualDiagnostic[].class);
    }

    private static int countSeverity(ActualDiagnostic[] diags, String severity) {
        int c = 0;
        for (ActualDiagnostic d : diags) {
            if (severity.equals(d.getSeverity())) c++;
        }
        return c;
    }
}
