package com.huawei.theme.analysis.core.e2e;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.huawei.theme.analysis.core.cli.CliMain;
import com.huawei.theme.analysis.core.e2e.golden.ActualDiagnostic;
import com.huawei.theme.analysis.core.e2e.golden.GoldenExpectation;
import com.huawei.theme.analysis.core.e2e.golden.GoldenExpectationParser;
import com.huawei.theme.analysis.core.e2e.golden.GoldenMatcher;
import com.huawei.theme.analysis.core.e2e.golden.MatchResult;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GoldenDiagnosticMatchTest {

    private static final Path FIXTURES_ROOT = Path.of("src/test/resources/fixtures");
    private static final Path DSL_ROOT = Path.of("src/test/resources/dsl");
    private static final Gson GSON = new Gson();

    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream capturedOut;

    @BeforeEach
    void captureStdout() {
        originalOut = System.out;
        originalErr = System.err;
        capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
        System.setErr(originalErr);
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
    void cliOutput_matchesGoldenExpectation(Path goldenFile, Path fixtureXml) throws Exception {
        GoldenExpectationParser parser = new GoldenExpectationParser();
        GoldenExpectation expectation = parser.parse(goldenFile);
        GoldenMatcher matcher = new GoldenMatcher();

        capturedOut.reset();
        int exitCode = CliMain.run(new String[]{"--format", "json", "--no-color", fixtureXml.toString()});
        String json = capturedOut.toString(StandardCharsets.UTF_8);

        ActualDiagnostic[] diags = extractDiagnostics(json);
        List<ActualDiagnostic> diagList = Arrays.asList(diags);

        MatchResult result = matcher.match(diagList, exitCode, expectation);

        assertTrue(result.isPassed(),
                "Golden mismatch for " + fixtureXml + ":\n" + result.renderDiffs()
                        + "\n--- Actual JSON output ---\n" + json);
    }

    private static ActualDiagnostic[] extractDiagnostics(String json) {
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
