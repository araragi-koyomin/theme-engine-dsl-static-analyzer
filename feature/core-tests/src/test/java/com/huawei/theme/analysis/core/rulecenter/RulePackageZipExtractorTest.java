package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulePackageZipExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsOnlyAfterThePreviousApprovedPackagePassesDigestGate() throws IOException {
        Path packageDirectory = passingPackage();
        Path release = tempDir.resolve("release");
        Path zip = new RulePackagePublicationPreparer().prepare(packageDirectory, release)
                .getRulePackageZip();

        Path extracted = new RulePackageZipExtractor().extractAndValidate(
                zip, tempDir.resolve("baseline"));

        assertTrue(Files.isRegularFile(extracted.resolve("manifest.json")));
        assertTrue(Files.isRegularFile(extracted.resolve("rules/elements/view/Image.json")));
    }

    @Test
    void rejectsZipSlipWithoutWritingOutsideOrPublishingPartialBaseline() throws IOException {
        Path zip = tempDir.resolve("malicious.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("../escaped.txt"));
            output.write("bad".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        Path baseline = tempDir.resolve("rejected-baseline");

        assertThrows(IllegalArgumentException.class,
                () -> new RulePackageZipExtractor().extractAndValidate(zip, baseline));
        assertFalse(Files.exists(tempDir.resolve("escaped.txt")));
        assertFalse(Files.exists(baseline));
    }

    private Path passingPackage() throws IOException {
        Path rules = tempDir.resolve("input/rules");
        Path functions = tempDir.resolve("input/functions");
        write(rules.resolve("elements/view/Image.json"),
                "{\"element\":\"Image\",\"constraints\":[]}");
        write(rules.resolve("global_vars.json"), "[]");
        write(rules.resolve("rule_sources.json"), "[]");
        write(functions.resolve("dsl_functions.json"), "{\"functions\":[]}");
        return new RulePackageAssembler(new StrictConditionAcceptor(
                new ConditionCapabilityRegistry())).assemble(
                        RulePackageAssemblyRequest.builder()
                                .packageDirectory(tempDir.resolve("package"))
                                .rulesDirectory(rules)
                                .functionsDirectory(functions)
                                .packageVersion("2026.07.20.8")
                                .createdAt("2026-07-20T10:00:00Z")
                                .minimumAnalyzerVersion("1.0.0")
                                .sourceDocuments(List.of())
                                .candidates(List.of())
                                .verifications(List.of())
                                .publishedConstraintRuleIds(Set.of())
                                .carriedForwardCandidateIds(Set.of())
                                .build()).getPackageDirectory();
    }

    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
