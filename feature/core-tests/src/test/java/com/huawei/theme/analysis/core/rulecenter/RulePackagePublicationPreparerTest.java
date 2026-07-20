package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulePackagePublicationPreparerTest {

    @TempDir
    Path tempDir;

    private Path packageDirectory;

    @BeforeEach
    void assemblePassingPackage() throws IOException {
        Path rules = tempDir.resolve("input/rules");
        Path functions = tempDir.resolve("input/functions");
        write(rules.resolve("elements/view/Image.json"),
                "{\"element\":\"Image\",\"constraints\":[]}");
        write(rules.resolve("global_vars.json"), "[]");
        write(rules.resolve("rule_sources.json"), "[]");
        write(functions.resolve("dsl_functions.json"), "{\"functions\":[]}");
        StrictConditionAcceptor acceptor = new StrictConditionAcceptor(
                new ConditionCapabilityRegistry());
        packageDirectory = new RulePackageAssembler(acceptor).assemble(
                RulePackageAssemblyRequest.builder()
                        .packageDirectory(tempDir.resolve("package"))
                        .rulesDirectory(rules)
                        .functionsDirectory(functions)
                        .packageVersion("2026.07.20.7")
                        .createdAt("2026-07-20T10:00:00Z")
                        .minimumAnalyzerVersion("1.0.0")
                        .sourceDocuments(List.of())
                        .candidates(List.of())
                        .verifications(List.of())
                        .publishedConstraintRuleIds(Set.of())
                        .carriedForwardCandidateIds(Set.of())
                        .build()).getPackageDirectory();
    }

    @Test
    void preparesThreeFixedImmutableAssetsAndReleaseMetadata() throws IOException {
        ReleasePublicationResult result = new RulePackagePublicationPreparer()
                .prepare(packageDirectory, tempDir.resolve("release"));

        assertEquals("2026.07.20.7", result.getPackageVersion());
        assertEquals("rules-v2026.07.20.7", result.getTagName());
        assertEquals(List.of("manifest.json", "release-report.json", "rule-package.zip"),
                result.getAssets().stream().map(asset -> asset.getFileName().toString())
                        .sorted().toList());
        assertTrue(result.getReleaseNotes().contains("2026.07.20.7"));
        try (ZipFile zip = new ZipFile(result.getRulePackageZip().toFile())) {
            assertTrue(zip.getEntry("manifest.json") != null);
            assertTrue(zip.getEntry("rules/elements/view/Image.json") != null);
            assertTrue(zip.getEntry("functions/dsl_functions.json") != null);
            assertTrue(zip.getEntry("source-markdown/") != null);
            assertTrue(zip.getEntry("verification/release-report.json") != null);
        }
        ReleasePublicationResult repeated = new RulePackagePublicationPreparer()
                .prepare(packageDirectory, tempDir.resolve("release-repeated"));
        assertArrayEquals(Files.readAllBytes(result.getRulePackageZip()),
                Files.readAllBytes(repeated.getRulePackageZip()));
    }

    @Test
    void failedReportTripsGateBeforeAnyReleaseAssetIsCreated() throws IOException {
        Path reportPath = packageDirectory.resolve("verification/release-report.json");
        JsonObject report = JsonParser.parseString(Files.readString(reportPath)).getAsJsonObject();
        report.addProperty("status", "failed");
        report.addProperty("manifestContentSha256", "");
        Files.writeString(reportPath, report.toString());
        String failedDigest = RulePackageDigest.compute(packageDirectory);
        report.addProperty("manifestContentSha256", failedDigest);
        Files.writeString(reportPath, report.toString());
        Path manifestPath = packageDirectory.resolve("manifest.json");
        JsonObject manifest = JsonParser.parseString(
                Files.readString(manifestPath)).getAsJsonObject();
        manifest.addProperty("contentSha256", failedDigest);
        Files.writeString(manifestPath, manifest.toString());
        Path output = tempDir.resolve("rejected-release");

        assertThrows(IllegalArgumentException.class,
                () -> new RulePackagePublicationPreparer().prepare(packageDirectory, output));
        assertFalse(Files.exists(output.resolve("rule-package.zip")));
        assertFalse(Files.exists(output.resolve("manifest.json")));
        assertFalse(Files.exists(output.resolve("release-report.json")));
    }

    @Test
    void digestMismatchTripsGate() throws IOException {
        Files.writeString(packageDirectory.resolve("rules/global_vars.json"), "[{}]");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new RulePackagePublicationPreparer().prepare(
                        packageDirectory, tempDir.resolve("digest-failure")));

        assertTrue(error.getMessage().contains("digest"));
    }

    @Test
    void manifestInventoryTripsGateEvenWhenAnAttackerRecomputesBothContentDigests()
            throws IOException {
        Files.delete(packageDirectory.resolve("rules/elements/view/Image.json"));
        String recomputed = RulePackageDigest.compute(packageDirectory);
        JsonObject manifest = JsonParser.parseString(Files.readString(
                packageDirectory.resolve("manifest.json"))).getAsJsonObject();
        manifest.addProperty("contentSha256", recomputed);
        Files.writeString(packageDirectory.resolve("manifest.json"), manifest.toString());
        JsonObject report = JsonParser.parseString(Files.readString(
                packageDirectory.resolve("verification/release-report.json"))).getAsJsonObject();
        report.addProperty("manifestContentSha256", recomputed);
        Files.writeString(packageDirectory.resolve("verification/release-report.json"),
                report.toString());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new RulePackagePublicationPreparer().prepare(
                        packageDirectory, tempDir.resolve("inventory-failure")));

        assertTrue(error.getMessage().contains("inventory"));
    }

    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
