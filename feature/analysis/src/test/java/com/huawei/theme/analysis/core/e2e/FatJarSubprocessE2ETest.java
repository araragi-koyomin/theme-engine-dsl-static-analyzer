package com.huawei.theme.analysis.core.e2e;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.huawei.theme.analysis.core.e2e.golden.ActualDiagnostic;
import com.huawei.theme.analysis.core.e2e.golden.GoldenExpectation;
import com.huawei.theme.analysis.core.e2e.golden.GoldenExpectationParser;
import com.huawei.theme.analysis.core.e2e.golden.GoldenMatcher;
import com.huawei.theme.analysis.core.e2e.golden.MatchResult;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FatJarSubprocessE2ETest {

    private static final Path FIXTURES_ROOT = Path.of("src/test/resources/fixtures");
    private static final Path DSL_ROOT = Path.of("src/test/resources/dsl");
    private static final Gson GSON = new Gson();
    private static String fatJarPath;

    @BeforeAll
    static void requireFatJar() {
        fatJarPath = System.getProperty("fatJar.path");
    }

    static Stream<Arguments> goldenFixtures() throws Exception {
        List<Arguments> args = new ArrayList<>();
        for (Path root : new Path[]{FIXTURES_ROOT, DSL_ROOT}) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".expected.json"))
                        .forEach(p -> args.add(Arguments.of(p, Path.of(p.toString().replace(".expected.json", ".xml")))));
            }
        }
        return args.stream();
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("goldenFixtures")
    void fatJarOutput_matchesGoldenExpectation(Path goldenFile, Path fixtureXml) throws Exception {
        Assumptions.assumeTrue(fatJarPath != null,
                "Fat jar E2E skipped: set -DfatJar.path (run via ./gradlew e2e)");
        Assumptions.assumeTrue(Files.exists(Path.of(fatJarPath)),
                "Fat jar not found at " + fatJarPath + " (run ./gradlew buildFatJar first)");
        GoldenExpectationParser parser = new GoldenExpectationParser();
        GoldenExpectation expectation = parser.parse(goldenFile);
        GoldenMatcher matcher = new GoldenMatcher(true);

        Path stdoutFile = Files.createTempFile("dsl-e2e-stdout", ".json");
        Path stderrFile = Files.createTempFile("dsl-e2e-stderr", ".log");
        ProcessBuilder pb = new ProcessBuilder(
                "java", "-Dfile.encoding=UTF-8", "-jar", fatJarPath,
                "--format", "json", "--no-color", fixtureXml.toString());
        pb.redirectOutput(stdoutFile.toFile());
        pb.redirectError(stderrFile.toFile());
        Process proc = pb.start();
        boolean finished = proc.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            String partialStdout = Files.exists(stdoutFile) ? Files.readString(stdoutFile, StandardCharsets.UTF_8) : "";
            throw new IllegalStateException("CLI subprocess timed out for " + fixtureXml
                    + "\n--- partial stdout ---\n" + partialStdout);
        }
        int exitCode = proc.exitValue();
        String stdout = Files.readString(stdoutFile, StandardCharsets.UTF_8);
        String stderr = Files.readString(stderrFile, StandardCharsets.UTF_8);
        Files.deleteIfExists(stdoutFile);
        Files.deleteIfExists(stderrFile);

        ActualDiagnostic[] diags = extractDiagnostics(stdout);
        List<ActualDiagnostic> diagList = Arrays.asList(diags);

        MatchResult result = matcher.match(diagList, exitCode, expectation);

        assertTrue(result.isPassed(),
                "Fat-jar golden mismatch for " + fixtureXml + ":\n" + result.renderDiffs()
                        + "\n--- stdout ---\n" + stdout
                        + "\n--- stderr ---\n" + stderr);
    }

    private ActualDiagnostic[] extractDiagnostics(String json) {
        JsonElement root = JsonParser.parseString(json);
        JsonElement diagsElement = null;
        if (root.isJsonObject()) {
            if (root.getAsJsonObject().has("files")) {
                JsonArray filesArray = root.getAsJsonObject().getAsJsonArray("files");
                if (!filesArray.isEmpty()) {
                    diagsElement = filesArray.get(0).getAsJsonObject().get("diagnostics");
                }
            } else if (root.getAsJsonObject().has("diagnostics")) {
                diagsElement = root.getAsJsonObject().get("diagnostics");
            }
        }
        if (diagsElement == null || diagsElement.isJsonNull()) {
            return new ActualDiagnostic[0];
        }
        return GSON.fromJson(diagsElement, ActualDiagnostic[].class);
    }
}
