package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.huawei.theme.analysis.core.rulecenter.model.CandidateStatus;
import com.huawei.theme.analysis.core.rulecenter.model.ConversionStatus;
import com.huawei.theme.analysis.core.rulecenter.model.DocumentConversionFeedback;
import com.huawei.theme.analysis.core.rulecenter.model.ProposedKind;
import com.huawei.theme.analysis.core.rulecenter.model.RuleCandidate;
import com.huawei.theme.analysis.core.rulecenter.model.SkipReason;
import com.huawei.theme.analysis.core.rulecenter.model.TargetKind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleCenterValidationOrchestratorTest {

    @TempDir
    Path tempDir;

    private Path rulesDirectory;
    private Path functionsDirectory;
    private final List<DocumentConversionFeedback> feedback = new ArrayList<>();
    private final StrictConditionAcceptor acceptor = new StrictConditionAcceptor(
            new ConditionCapabilityRegistry());

    @BeforeEach
    void baseline() throws IOException {
        rulesDirectory = tempDir.resolve("baseline/rules");
        functionsDirectory = tempDir.resolve("baseline/functions");
        write(rulesDirectory.resolve("elements/view/Image.json"), "{"
                + "\"element\":\"Image\",\"category\":\"view\","
                + "\"description\":\"old\",\"requiredAttrs\":[],"
                + "\"optionalAttrs\":[\"src\",\"srcExp\"],"
                + "\"attrTypes\":{\"src\":{\"type\":\"string\","
                + "\"enumValues\":[],\"aliases\":[],\"supportsExpression\":false,"
                + "\"description\":\"old src\"},"
                + "\"srcExp\":{\"type\":\"string\",\"enumValues\":[],\"aliases\":[],"
                + "\"supportsExpression\":true,\"description\":\"old srcExp\"}},"
                + "\"allowedParents\":[],"
                + "\"scope\":{},\"deviceSupport\":{},\"constraints\":[]}");
        write(rulesDirectory.resolve("global_vars.json"), "[]");
        write(rulesDirectory.resolve("rule_sources.json"), "[]");
        write(functionsDirectory.resolve("dsl_functions.json"), "{\"functions\":[]}");
    }

    @Test
    void verifiedConstraintAndDescriptionReachCompletePackageAndFeedback() throws IOException {
        RuleCandidate constraint = candidate("constraint", ProposedKind.CONSTRAINT, draft(
                "SEM-IMG-901",
                "element.attrs['src'] != null AND element.attrs['srcExp'] != null",
                true,
                "<Image src=\"a\" srcExp=\"#b\"/>",
                "<Image src=\"a\"/>"));
        RuleCandidate description = candidate(
                "description", ProposedKind.DESCRIPTION,
                new JsonPrimitive("src is the image path"));
        RuleCenterValidationOrchestrator orchestrator = orchestrator(
                request -> extraction(constraint, description), context -> context.getCurrentProposal());

        RuleCenterValidationResult result = orchestrator.validate(request());

        assertEquals(ReleaseReportStatus.PASSED, result.getAssembly().getStatus());
        assertEquals(List.of(CandidateStatus.PUBLISHED, CandidateStatus.PUBLISHED),
                result.getCandidates().stream().map(RuleCandidate::getStatus).toList());
        assertEquals(1, result.getVerifications().size());
        Path imageRule = result.getAssembly().getPackageDirectory()
                .resolve("rules/elements/view/Image.json");
        JsonObject image = JsonParser.parseString(Files.readString(imageRule)).getAsJsonObject();
        assertEquals("src is the image path", image.getAsJsonObject("attrTypes")
                .getAsJsonObject("src").get("description").getAsString());
        assertEquals("SEM-IMG-901", image.getAsJsonArray("constraints")
                .get(0).getAsJsonObject().get("ruleId").getAsString());
        assertEquals(ConversionStatus.PUBLISHED, feedback.get(0).getConversionStatus());
        assertEquals(1, feedback.get(0).getSummary().getPublished());
        assertEquals(1, feedback.get(0).getSummary().getDescriptionOnly());
    }

    @Test
    void unsupportedAndExternalResourceCandidatesAreExcludedWithoutChangingRules()
            throws IOException {
        RuleCandidate unsupported = candidate("unsupported", ProposedKind.CONSTRAINT, draft(
                "SEM-IMG-902",
                "element.attrs['src'].endsWith('.mp4')",
                true,
                "<Image src=\"a.mp4\"/>",
                "<Image src=\"a.png\"/>"));
        RuleCandidate external = candidate("external", ProposedKind.CONSTRAINT, draft(
                "SEM-IMG-903",
                "element.attrs['src'] != null",
                false,
                "<Image src=\"large.mp4\"/>",
                "<Image/>"));
        RuleCenterValidationOrchestrator orchestrator = orchestrator(
                request -> extraction(unsupported, external), context -> context.getCurrentProposal());

        RuleCenterValidationResult result = orchestrator.validate(request());

        assertEquals(ReleaseReportStatus.PASSED_WITH_EXCLUSIONS,
                result.getAssembly().getStatus());
        assertEquals(List.of(SkipReason.UNSUPPORTED_CONDITION_GRAMMAR,
                        SkipReason.OUT_OF_STATIC_SCOPE),
                result.getCandidates().stream().map(RuleCandidate::getSkipReason).toList());
        assertTrue(result.getVerifications().isEmpty());
        JsonObject image = JsonParser.parseString(Files.readString(
                result.getAssembly().getPackageDirectory()
                        .resolve("rules/elements/view/Image.json"))).getAsJsonObject();
        assertTrue(image.getAsJsonArray("constraints").isEmpty());
        assertEquals(ConversionStatus.PUBLISHED_WITH_SKIPS,
                feedback.get(0).getConversionStatus());
        assertFalse(feedback.get(0).getItems().get(1).getAuthorAction()
                .name().contains("REWORK"));
    }

    @Test
    void modelCannotTurnAnOrdinaryStringAttributeIntoAnUndeclaredValueConstraint()
            throws IOException {
        RuleCandidate invented = candidate("invented", ProposedKind.CONSTRAINT, draft(
                "SEM-IMG-904",
                "element.attrs['src'] == 'video.mp4'",
                true,
                "<Image src=\"video.mp4\"/>",
                "<Image src=\"image.png\"/>"));
        RuleCenterValidationOrchestrator orchestrator = orchestrator(
                request -> extraction(invented), context -> context.getCurrentProposal());

        RuleCenterValidationResult result = orchestrator.validate(request());

        assertEquals(SkipReason.OUT_OF_STATIC_SCOPE,
                result.getCandidates().get(0).getSkipReason());
        JsonObject image = JsonParser.parseString(Files.readString(
                result.getAssembly().getPackageDirectory()
                        .resolve("rules/elements/view/Image.json"))).getAsJsonObject();
        assertTrue(image.getAsJsonArray("constraints").isEmpty());
    }

    @Test
    void verifiedRevisionReplacesExistingRuleIdInsteadOfCreatingDuplicate() throws IOException {
        Path imagePath = rulesDirectory.resolve("elements/view/Image.json");
        JsonObject baseline = JsonParser.parseString(Files.readString(imagePath)).getAsJsonObject();
        JsonObject old = new JsonObject();
        old.addProperty("ruleId", "SEM-IMG-901");
        old.addProperty("condition", "element.attrs['src'] == null");
        old.addProperty("message", "old message");
        old.addProperty("severity", "warning");
        old.add("suggestedFixes", new com.google.gson.JsonArray());
        baseline.getAsJsonArray("constraints").add(old);
        write(imagePath, baseline.toString());
        RuleCandidate revision = candidate("constraint", ProposedKind.CONSTRAINT, draft(
                "SEM-IMG-901",
                "element.attrs['src'] != null AND element.attrs['srcExp'] != null",
                true,
                "<Image src=\"a\" srcExp=\"#b\"/>",
                "<Image src=\"a\"/>"));

        RuleCenterValidationResult result = orchestrator(
                request -> extraction(revision), context -> context.getCurrentProposal())
                .validate(request());

        JsonObject image = JsonParser.parseString(Files.readString(
                result.getAssembly().getPackageDirectory()
                        .resolve("rules/elements/view/Image.json"))).getAsJsonObject();
        assertEquals(1, image.getAsJsonArray("constraints").size());
        assertEquals("src and srcExp cannot coexist", image.getAsJsonArray("constraints").get(0)
                .getAsJsonObject().get("message").getAsString());
    }

    @Test
    void multipleChangedDocumentsAreAppliedAndReportedInOneCompletePackage()
            throws IOException {
        CandidateExtractionService extraction = request -> {
            RuleCandidate description = candidate(
                    "description-" + request.getDocumentId(),
                    ProposedKind.DESCRIPTION,
                    new JsonPrimitive("description from " + request.getDocumentId()));
            description.setDocumentId(request.getDocumentId());
            description.setDocumentRevision(request.getDocumentRevision());
            return extraction(description);
        };
        RuleCenterValidationOrchestrator orchestrator = orchestrator(
                extraction, context -> context.getCurrentProposal());

        RuleCenterValidationResult result = orchestrator.validateBatch(
                RuleCenterBatchValidationRequest.builder()
                        .documents(List.of(
                                RuleDocumentRevision.builder()
                                        .documentId("image-a").revision("r1")
                                        .markdown("# A").sourceMarkdownRelativePath("a.md").build(),
                                RuleDocumentRevision.builder()
                                        .documentId("image-b").revision("r2")
                                        .markdown("# B").sourceMarkdownRelativePath("b.md").build()))
                        .retainedSourceDocuments(List.of(SourceDocumentArtifact.builder()
                                .documentId("old").revision("r0")
                                .relativePath("old.md").content("# Old").build()))
                        .rulesDirectory(rulesDirectory)
                        .functionsDirectory(functionsDirectory)
                        .outputDirectory(tempDir.resolve("batch-output"))
                        .packageVersion("2026.07.20.2")
                        .createdAt("2026-07-20T10:00:00Z")
                        .minimumAnalyzerVersion("1.0.0")
                        .build());

        assertEquals(2, result.getExtractions().size());
        assertEquals(2, result.getFeedbackItems().size());
        assertTrue(Files.isRegularFile(result.getAssembly().getPackageDirectory()
                .resolve("source-markdown/a.md")));
        assertTrue(Files.isRegularFile(result.getAssembly().getPackageDirectory()
                .resolve("source-markdown/b.md")));
        assertTrue(Files.isRegularFile(result.getAssembly().getPackageDirectory()
                .resolve("source-markdown/old.md")));
    }

    @Test
    void duplicateCandidateIdsAcrossDocumentsAbortTheBatchBeforeApplication() {
        CandidateExtractionService extraction = request -> {
            RuleCandidate duplicate = candidate(
                    "shared-id", ProposedKind.DESCRIPTION,
                    new JsonPrimitive("src is the image path"));
            duplicate.setDocumentId(request.getDocumentId());
            duplicate.setDocumentRevision(request.getDocumentRevision());
            return extraction(duplicate);
        };

        CandidateExtractionException error = assertThrows(
                CandidateExtractionException.class,
                () -> orchestrator(extraction, context -> context.getCurrentProposal())
                        .validateBatch(RuleCenterBatchValidationRequest.builder()
                                .documents(List.of(
                                        RuleDocumentRevision.builder()
                                                .documentId("image-a").revision("r1")
                                                .markdown("# A\n\nsrc is the image path")
                                                .sourceMarkdownRelativePath("a.md").build(),
                                        RuleDocumentRevision.builder()
                                                .documentId("image-b").revision("r2")
                                                .markdown("# B\n\nsrc is the image path")
                                                .sourceMarkdownRelativePath("b.md").build()))
                                .rulesDirectory(rulesDirectory)
                                .functionsDirectory(functionsDirectory)
                                .outputDirectory(tempDir.resolve("duplicate-output"))
                                .packageVersion("2026.07.20.3")
                                .createdAt("2026-07-20T10:00:00Z")
                                .minimumAnalyzerVersion("1.0.0")
                                .build()));

        assertTrue(error.getMessage().contains("shared-id"));
        assertFalse(Files.exists(tempDir.resolve("duplicate-output/staged-rules")));
    }

    @Test
    void fixtureFailureEntersRepairLoopAndPublishesOnlyAfterRealReverification()
            throws IOException {
        RuleCandidate candidate = candidate("repair", ProposedKind.CONSTRAINT, draft(
                "SEM-IMG-905",
                "element.attrs['src'] != null AND element.attrs['srcExp'] != null",
                true,
                "<Image src=\"a\"/>",
                "<Image src=\"a\" srcExp=\"#b\"/>"));
        AtomicInteger attempts = new AtomicInteger();
        ConstraintRepairStrategy repair = context -> {
            attempts.incrementAndGet();
            ConstraintRepairProposal current = context.getCurrentProposal();
            ConstraintVerificationRequest verification = current.getVerificationRequest();
            return ConstraintRepairProposal.builder()
                    .sourceEvidenceFingerprint(current.getSourceEvidenceFingerprint())
                    .targetFingerprint(current.getTargetFingerprint())
                    .verificationRequest(ConstraintVerificationRequest.builder()
                            .targetElement(verification.getTargetElement())
                            .constraint(verification.getConstraint())
                            .positiveFixturePath(verification.getPositiveFixturePath())
                            .positiveFixtureContent(
                                    "<Image src=\"a\" srcExp=\"#b\"/>")
                            .negativeFixturePath(verification.getNegativeFixturePath())
                            .negativeFixtureContent("<Image src=\"a\"/>")
                            .evidenceCandidateIds(verification.getEvidenceCandidateIds())
                            .build())
                    .build();
        };

        RuleCenterValidationResult result = orchestrator(
                request -> extraction(candidate), repair).validate(request());

        assertEquals(1, attempts.get());
        assertEquals(CandidateStatus.PUBLISHED, result.getCandidates().get(0).getStatus());
        assertEquals(1, result.getVerifications().size());
    }

    @Test
    void lyingStaticTextFlagCannotConvertExternalFileDurationOrExistenceSemantics()
            throws IOException {
        RuleCandidate external = candidate("external-lie", ProposedKind.CONSTRAINT, draft(
                "SEM-IMG-906",
                "element.attrs['src'] != null",
                true,
                "<Image src=\"video.mp4\"/>",
                "<Image/>"));
        external.getSourceEvidence().setExcerpt("视频文件必须存在且时长不能超过 30 秒");
        external.getProposedChange().getValue().getAsJsonObject()
                .addProperty("message", external.getSourceEvidence().getExcerpt());

        RuleCenterValidationResult result = orchestrator(
                request -> extraction(external), context -> context.getCurrentProposal())
                .validate(request());

        assertEquals(CandidateStatus.SKIPPED, result.getCandidates().get(0).getStatus());
        assertEquals(SkipReason.OUT_OF_STATIC_SCOPE,
                result.getCandidates().get(0).getSkipReason());
        assertTrue(result.getVerifications().isEmpty());
    }

    @Test
    void unrelatedDescriptiveEvidenceCannotAuthorizeInventedConstraintOrDescription()
            throws IOException {
        RuleCandidate inventedConstraint = candidate(
                "invented-constraint", ProposedKind.CONSTRAINT, draft(
                        "SEM-IMG-907",
                        "element.attrs['src'] != null",
                        true,
                        "<Image src=\"a\"/>",
                        "<Image/>"));
        inventedConstraint.getSourceEvidence().setExcerpt("src is the image path");
        inventedConstraint.getProposedChange().getValue().getAsJsonObject()
                .addProperty("message", "src is the image path");
        RuleCandidate inventedDescription = candidate(
                "invented-description", ProposedKind.DESCRIPTION,
                new JsonPrimitive("src must be an existing MP4 file"));

        RuleCenterValidationResult result = orchestrator(
                request -> extraction(inventedConstraint, inventedDescription),
                context -> context.getCurrentProposal()).validate(request());

        assertEquals(List.of(SkipReason.EVIDENCE_CONFLICT, SkipReason.EVIDENCE_CONFLICT),
                result.getCandidates().stream().map(RuleCandidate::getSkipReason).toList());
        JsonObject image = JsonParser.parseString(Files.readString(
                result.getAssembly().getPackageDirectory()
                        .resolve("rules/elements/view/Image.json"))).getAsJsonObject();
        assertEquals("old src", image.getAsJsonObject("attrTypes")
                .getAsJsonObject("src").get("description").getAsString());
        assertTrue(image.getAsJsonArray("constraints").isEmpty());
    }

    private RuleCenterValidationOrchestrator orchestrator(
            CandidateExtractionService extractionService,
            ConstraintRepairStrategy repairStrategy) {
        return new RuleCenterValidationOrchestrator(
                extractionService,
                acceptor,
                new ConstraintVerificationRunner(acceptor),
                new VerifiedConstraintExampleCatalog(List.of(), acceptor),
                repairStrategy,
                feedback::add);
    }

    private RuleCenterValidationRequest request() {
        return RuleCenterValidationRequest.builder()
                .document(RuleDocumentRevision.builder()
                        .documentId("image")
                        .revision("r42")
                        .markdown("# Image\n\nsrc and srcExp cannot coexist\n\nsrc is the image path")
                        .sourceMarkdownRelativePath("elements/view/Image/image.md")
                        .build())
                .rulesDirectory(rulesDirectory)
                .functionsDirectory(functionsDirectory)
                .outputDirectory(tempDir.resolve("output-" + System.nanoTime()))
                .packageVersion("2026.07.20.1")
                .createdAt("2026-07-20T10:00:00Z")
                .minimumAnalyzerVersion("1.0.0")
                .build();
    }

    private CandidateExtractionResult extraction(RuleCandidate... candidates) {
        return CandidateExtractionResult.builder()
                .candidates(List.of(candidates))
                .requestedModel("test")
                .actualModel("test")
                .promptVersion("test")
                .promptSha256("a".repeat(64))
                .documentSha256("b".repeat(64))
                .rawResponseSha256("c".repeat(64))
                .build();
    }

    private RuleCandidate candidate(String id, ProposedKind kind, com.google.gson.JsonElement value) {
        int line = id.equals("description") ? 5 : 3;
        String excerpt = id.equals("description")
                ? "src is the image path" : "src and srcExp cannot coexist";
        if (kind == ProposedKind.CONSTRAINT && value.isJsonObject()) {
            value.getAsJsonObject().addProperty("message", excerpt);
        }
        return RuleCandidate.builder()
                .candidateId(id)
                .documentId("image")
                .documentRevision("r42")
                .sourceEvidence(RuleCandidate.SourceEvidence.builder()
                        .sectionPath(List.of("参数说明"))
                        .location(RuleCandidate.SourceLocation.builder()
                                .startLine(line).endLine(line).build())
                        .excerpt(excerpt)
                        .build())
                .target(RuleCandidate.CandidateTarget.builder()
                        .kind(TargetKind.ELEMENT_ATTRIBUTE)
                        .element("Image")
                        .attribute("src")
                        .build())
                .proposedKind(kind)
                .proposedChange(RuleCandidate.ProposedChange.builder()
                        .field(kind == ProposedKind.DESCRIPTION
                                ? "attrTypes.src.description" : "constraints[]")
                        .value(value)
                        .build())
                .status(CandidateStatus.EXTRACTED)
                .build();
    }

    private JsonObject draft(
            String ruleId,
            String condition,
            boolean staticTextOnly,
            String positiveFixture,
            String negativeFixture) {
        JsonObject draft = new JsonObject();
        draft.addProperty("ruleId", ruleId);
        draft.addProperty("condition", condition);
        draft.addProperty("message", "generated message");
        draft.addProperty("severity", "error");
        draft.addProperty("staticTextOnly", staticTextOnly);
        draft.addProperty("evidenceConflict", false);
        draft.addProperty("positiveFixture", positiveFixture);
        draft.addProperty("negativeFixture", negativeFixture);
        return draft;
    }

    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
