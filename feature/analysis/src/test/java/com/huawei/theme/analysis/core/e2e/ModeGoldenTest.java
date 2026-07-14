package com.huawei.theme.analysis.core.e2e;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies AC-2 and AC-3: mode-aware analysis dispatches the correct analyzer set.
 *
 * <p>Syntax-only fixtures must emit only SYN-* diagnostics (no SEM-*), and
 * semantic-only fixtures must emit only SEM-* diagnostics (no SYN-*). Each fixture
 * is also expected to produce at least one diagnostic so the mode actually exercised
 * the relevant analyzer.</p>
 */
class ModeGoldenTest {

    private static final Gson GSON = new Gson();
    private static final Path MODE_ROOT = Path.of("src/test/resources/fixtures/mode");

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

    static Stream<Arguments> modeFixtures() {
        return Stream.of(
                Arguments.of(MODE_ROOT.resolve("syntax_only_test.xml"), "--syntax-only", "SYN-", "SEM-"),
                Arguments.of(MODE_ROOT.resolve("semantic_only_test.xml"), "--semantic-only", "SEM-", "SYN-")
        );
    }

    @ParameterizedTest(name = "{0} [{1}]")
    @MethodSource("modeFixtures")
    void modeEmitsOnlyExpectedPrefixDiagnostics(Path fixture, String modeFlag,
                                                String allowedPrefix, String forbiddenPrefix) throws Exception {
        capturedOut.reset();
        int exitCode = CliMain.run(new String[]{modeFlag, "--format", "json", "--no-color", fixture.toString()});
        String json = capturedOut.toString(StandardCharsets.UTF_8);

        List<ActualDiagnostic> diags = Arrays.asList(extractDiagnostics(json));

        assertFalse(diags.isEmpty(),
                "Mode " + modeFlag + " produced no diagnostics for " + fixture
                        + "; expected at least one " + allowedPrefix + "* diagnostic.\n--- output ---\n" + json);

        List<String> forbidden = new ArrayList<>();
        for (ActualDiagnostic d : diags) {
            if (d.getRuleId() == null || !d.getRuleId().startsWith(allowedPrefix)) {
                forbidden.add(d.getRuleId());
            }
        }
        assertTrue(forbidden.isEmpty(),
                "Mode " + modeFlag + " must emit only " + allowedPrefix
                        + "* diagnostics for " + fixture + ", but found forbidden "
                        + forbiddenPrefix + "* ruleIds: " + forbidden
                        + "\n--- actual JSON output ---\n" + json);
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
