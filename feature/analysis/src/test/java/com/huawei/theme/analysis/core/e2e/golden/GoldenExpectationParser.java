package com.huawei.theme.analysis.core.e2e.golden;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class GoldenExpectationParser {

    private final Gson gson;

    public GoldenExpectationParser() {
        this.gson = new GsonBuilder().create();
    }

    public GoldenExpectation parse(Path goldenFile) {
        try (Reader reader = new InputStreamReader(Files.newInputStream(goldenFile), StandardCharsets.UTF_8)) {
            GoldenExpectation exp = gson.fromJson(reader, GoldenExpectation.class);
            if (exp == null) {
                throw new IllegalStateException("Empty or invalid golden file: " + goldenFile);
            }
            applyDefaults(exp);
            return exp;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read golden file: " + goldenFile, e);
        }
    }

    private void applyDefaults(GoldenExpectation exp) {
        if (exp.getExpectedDiagnostics() == null) {
            exp.setExpectedDiagnostics(java.util.Collections.emptyList());
        }
        for (ExpectedDiagnostic d : exp.getExpectedDiagnostics()) {
            if (d.getLineTolerance() == 0) {
                d.setLineTolerance(2);
            }
        }
        if (exp.getMustNotTrigger() == null) {
            exp.setMustNotTrigger(java.util.Collections.emptyList());
        }
        if (exp.getExpectedCounts() == null) {
            exp.setExpectedCounts(GoldenExpectation.ExpectedCounts.builder().build());
        }
    }
}
