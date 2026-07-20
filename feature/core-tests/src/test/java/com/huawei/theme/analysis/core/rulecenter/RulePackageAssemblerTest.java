package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.huawei.theme.analysis.core.rulecenter.model.CandidateStatus;
import com.huawei.theme.analysis.core.rulecenter.model.ConstraintVerification;
import com.huawei.theme.analysis.core.rulecenter.model.ProposedKind;
import com.huawei.theme.analysis.core.rulecenter.model.RuleCandidate;
import com.huawei.theme.analysis.core.rulecenter.model.SkipReason;
import com.huawei.theme.analysis.core.rulecenter.model.TargetKind;
import com.huawei.theme.analysis.core.rulecenter.model.ValidationFailure;
import com.huawei.theme.analysis.core.rulecenter.model.VerificationStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulePackageAssemblerTest {

    @TempDir
    Path tempDir;

    private Path rulesDirectory;
    private Path functionsDirectory;
    private final RulePackageAssembler assembler = new RulePackageAssembler(
            new StrictConditionAcceptor(new ConditionCapabilityRegistry()));

    @BeforeEach
    void createBaselineSnapshot() throws IOException {
        rulesDirectory = tempDir.resolve("input/rules");
        functionsDirectory = tempDir.resolve("input/functions");
        write(rulesDirectory.resolve("elements/view/Image.json"), elementJson("[]"));
        write(rulesDirectory.resolve("global_vars.json"), "[]");
        write(rulesDirectory.resolve("rule_sources.json"), "[]");
        write(functionsDirectory.resolve("dsl_functions.json"), "{\"functions\":[]}");
    }

    @Test
    void assemblesCompleteSnapshotWithAlignedSourceMarkdownAndDeterministicManifest()
            throws IOException {
        RulePackageAssemblyResult result = assembler.assemble(requestBuilder()
                .sourceDocuments(List.of(SourceDocumentArtifact.builder()
                        .documentId("image")
                        .revision("r42")
                        .relativePath("elements/view/Image/image.md")
                        .content("# Image specification")
                        .build()))
                .build());

        Path output = result.getPackageDirectory();
        assertEquals(ReleaseReportStatus.PASSED, result.getStatus());
        assertTrue(Files.isRegularFile(output.resolve("rules/elements/view/Image.json")));
        assertTrue(Files.isRegularFile(output.resolve("rules/global_vars.json")));
        assertTrue(Files.isRegularFile(output.resolve("rules/rule_sources.json")));
        assertTrue(Files.isRegularFile(output.resolve("functions/dsl_functions.json")));
        assertEquals(
                "# Image specification",
                Files.readString(output.resolve("source-markdown/elements/view/Image/image.md")));
        assertTrue(Files.isRegularFile(output.resolve("verification/release-report.json")));

        JsonObject manifest = json(output.resolve("manifest.json"));
        JsonObject report = json(output.resolve("verification/release-report.json"));
        assertEquals("approved", manifest.get("channel").getAsString());
        assertFalse(manifest.get("contentSha256").getAsString().isEmpty());
        assertEquals(
                manifest.get("contentSha256").getAsString(),
                report.get("manifestContentSha256").getAsString());
        assertEquals("passed", report.get("status").getAsString());
    }

    @Test
    void exclusionsRemainVisibleButDoNotFailSafeSnapshot() throws IOException {
        RuleCandidate skipped = candidate(
                "candidate-skip", CandidateStatus.SKIPPED,
                SkipReason.OUT_OF_STATIC_SCOPE, null);
        RuleCandidate validationError = candidate(
                "candidate-error", CandidateStatus.VALIDATION_ERROR,
                null, ValidationFailure.POSITIVE_FIXTURE_MISSED);

        RulePackageAssemblyResult result = assembler.assemble(requestBuilder()
                .candidates(List.of(skipped, validationError))
                .carriedForwardCandidateIds(Set.of("candidate-carried"))
                .build());

        JsonObject report = json(result.getPackageDirectory()
                .resolve("verification/release-report.json"));
        assertEquals(ReleaseReportStatus.PASSED_WITH_EXCLUSIONS, result.getStatus());
        assertEquals("passed-with-exclusions", report.get("status").getAsString());
        assertTrue(report.toString().contains("candidate-skip"));
        assertTrue(report.toString().contains("candidate-error"));
        assertTrue(report.toString().contains("candidate-carried"));
    }

    @Test
    void missingRequiredRuleCategoryMakesReportFailed() throws IOException {
        Files.delete(rulesDirectory.resolve("rule_sources.json"));

        RulePackageAssemblyResult result = assembler.assemble(requestBuilder().build());

        assertEquals(ReleaseReportStatus.FAILED, result.getStatus());
        assertTrue(result.getErrors().stream()
                .anyMatch(error -> error.contains("rules/rule_sources.json")));
    }

    @Test
    void malformedJsonMakesReportFailed() throws IOException {
        write(functionsDirectory.resolve("dsl_functions.json"), "{not-json}");

        RulePackageAssemblyResult result = assembler.assemble(requestBuilder().build());

        assertEquals(ReleaseReportStatus.FAILED, result.getStatus());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("invalid JSON")));
    }

    @Test
    void unsupportedNewConditionCannotEnterFinalPackageWithForgedVerification()
            throws IOException {
        String condition = "element.attrs['src'].endsWith('.mp4')";
        write(rulesDirectory.resolve("elements/view/Image.json"), elementJson("[{"
                + "\"ruleId\":\"SEM-IMG-BAD\","
                + "\"condition\":\"" + condition + "\","
                + "\"message\":\"bad\",\"severity\":\"error\",\"suggestedFixes\":[]}]"));
        ConstraintVerification forged = ConstraintVerification.builder()
                .ruleId("SEM-IMG-BAD")
                .condition(condition)
                .parserAccepted(true)
                .positiveFixture("positive.xml")
                .negativeFixture("negative.xml")
                .positiveObservedRuleIds(List.of("SEM-IMG-BAD"))
                .negativeObservedRuleIds(List.of())
                .evidenceCandidateIds(List.of("candidate-bad"))
                .status(VerificationStatus.PASSED)
                .build();

        RulePackageAssemblyResult result = assembler.assemble(requestBuilder()
                .publishedConstraintRuleIds(Set.of("SEM-IMG-BAD"))
                .verifications(List.of(forged))
                .build());

        assertEquals(ReleaseReportStatus.FAILED, result.getStatus());
        assertTrue(result.getErrors().stream()
                .anyMatch(error -> error.contains("SEM-IMG-BAD")
                        && error.contains("condition")));
    }

    @Test
    void baselineDuplicateDebtIsExplicitlyGrandfatheredButStillFailsWithoutDeclaration()
            throws IOException {
        String first = "[{\"ruleId\":\"SEM-LEGACY-001\","
                + "\"condition\":\"element.attrs['a'] != null\","
                + "\"message\":\"first\",\"severity\":\"error\",\"suggestedFixes\":[]}]";
        String second = first.replace("first", "second");
        write(rulesDirectory.resolve("elements/view/Image.json"), elementJson(first));
        write(rulesDirectory.resolve("elements/view/Other.json"),
                elementJson(second).replace("\"Image\"", "\"Other\""));

        RulePackageAssemblyResult rejected = assembler.assemble(requestBuilder().build());
        RulePackageAssemblyResult grandfathered = assembler.assemble(requestBuilder()
                .grandfatheredDuplicateRuleIds(Set.of("SEM-LEGACY-001"))
                .build());

        assertEquals(ReleaseReportStatus.FAILED, rejected.getStatus());
        assertEquals(ReleaseReportStatus.PASSED, grandfathered.getStatus());
    }

    private RulePackageAssemblyRequest.RulePackageAssemblyRequestBuilder requestBuilder() {
        return RulePackageAssemblyRequest.builder()
                .packageDirectory(tempDir.resolve("output-" + System.nanoTime()))
                .rulesDirectory(rulesDirectory)
                .functionsDirectory(functionsDirectory)
                .packageVersion("2026.07.20.1")
                .createdAt("2026-07-20T10:00:00Z")
                .minimumAnalyzerVersion("1.0.0")
                .sourceDocuments(List.of())
                .candidates(List.of())
                .verifications(List.of())
                .publishedConstraintRuleIds(Set.of())
                .carriedForwardCandidateIds(Set.of());
    }

    private RuleCandidate candidate(
            String id,
            CandidateStatus status,
            SkipReason skipReason,
            ValidationFailure validationFailure) {
        return RuleCandidate.builder()
                .candidateId(id)
                .documentId("image")
                .documentRevision("r42")
                .sourceEvidence(RuleCandidate.SourceEvidence.builder()
                        .sectionPath(List.of("参数说明"))
                        .location(RuleCandidate.SourceLocation.builder()
                                .startLine(10)
                                .endLine(11)
                                .build())
                        .excerpt("source text")
                        .build())
                .target(RuleCandidate.CandidateTarget.builder()
                        .kind(TargetKind.ELEMENT_ATTRIBUTE)
                        .element("Image")
                        .attribute("src")
                        .build())
                .proposedKind(ProposedKind.CONSTRAINT)
                .proposedChange(RuleCandidate.ProposedChange.builder()
                        .field("constraints[]")
                        .value(new JsonPrimitive("candidate"))
                        .build())
                .status(status)
                .skipReason(skipReason)
                .validationFailure(validationFailure)
                .build();
    }

    private String elementJson(String constraints) {
        return "{\"element\":\"Image\",\"category\":\"view\","
                + "\"requiredAttrs\":[],\"optionalAttrs\":[],\"attrTypes\":{},"
                + "\"allowedParents\":[],\"scope\":{},\"deviceSupport\":{},"
                + "\"constraints\":" + constraints + "}";
    }

    private JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
