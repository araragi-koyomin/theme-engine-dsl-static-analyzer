package com.huawei.theme.analysis.core.rulecenter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.huawei.theme.analysis.core.rulecenter.model.CandidateStatus;
import com.huawei.theme.analysis.core.rulecenter.model.ConstraintVerification;
import com.huawei.theme.analysis.core.rulecenter.model.DocumentConversionFeedback;
import com.huawei.theme.analysis.core.rulecenter.model.ProposedKind;
import com.huawei.theme.analysis.core.rulecenter.model.RuleCandidate;
import com.huawei.theme.analysis.core.rulecenter.model.SkipReason;
import com.huawei.theme.analysis.core.rulecenter.model.TargetKind;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

public class RuleCenterValidationOrchestrator {
    private static final Pattern ATTRIBUTE_REFERENCE = Pattern.compile(
            "element\\.attrs\\[\\s*'([^']+)'\\s*]");

    private final CandidateExtractionService extractionService;
    private final StrictConditionAcceptor conditionAcceptor;
    private final ConstraintVerificationRunner verificationRunner;
    private final VerifiedConstraintExampleCatalog exampleCatalog;
    private final ConstraintRepairStrategy repairStrategy;
    private final DocumentFeedbackService feedbackService;
    private final CandidatePublicationRouter publicationRouter = new CandidatePublicationRouter();

    public RuleCenterValidationOrchestrator(
            CandidateExtractionService extractionService,
            StrictConditionAcceptor conditionAcceptor,
            ConstraintVerificationRunner verificationRunner,
            VerifiedConstraintExampleCatalog exampleCatalog,
            ConstraintRepairStrategy repairStrategy,
            DocumentFeedbackPublisher feedbackPublisher) {
        this.extractionService = Objects.requireNonNull(extractionService);
        this.conditionAcceptor = Objects.requireNonNull(conditionAcceptor);
        this.verificationRunner = Objects.requireNonNull(verificationRunner);
        this.exampleCatalog = Objects.requireNonNull(exampleCatalog);
        this.repairStrategy = Objects.requireNonNull(repairStrategy);
        this.feedbackService = new DocumentFeedbackService(
                Objects.requireNonNull(feedbackPublisher));
    }

    public RuleCenterValidationResult validate(RuleCenterValidationRequest request) {
        validateRequest(request);
        return validateBatch(RuleCenterBatchValidationRequest.builder()
                .documents(List.of(request.getDocument()))
                .rulesDirectory(request.getRulesDirectory())
                .functionsDirectory(request.getFunctionsDirectory())
                .outputDirectory(request.getOutputDirectory())
                .packageVersion(request.getPackageVersion())
                .createdAt(request.getCreatedAt())
                .minimumAnalyzerVersion(request.getMinimumAnalyzerVersion())
                .build());
    }

    public RuleCenterValidationResult validateBatch(RuleCenterBatchValidationRequest request) {
        validateBatchRequest(request);
        List<CandidateExtractionResult> extractions = new ArrayList<>();
        List<RuleCandidate> candidates = new ArrayList<>();
        for (RuleDocumentRevision document : request.getDocuments()) {
            CandidateExtractionResult extraction = extractionService.extract(
                    CandidateExtractionRequest.builder()
                            .documentId(document.getDocumentId())
                            .documentRevision(document.getRevision())
                            .markdown(document.getMarkdown())
                            .examples(exampleCatalog.allVerifiedExamples())
                            .build());
            extractions.add(extraction);
            candidates.addAll(extraction.getCandidates());
        }
        List<ConstraintVerification> verifications = new ArrayList<>();
        Set<String> publishedRuleIds = new HashSet<>();
        Set<String> carriedForward = new HashSet<>();
        RulePackageChangeApplier applier = new RulePackageChangeApplier(
                request.getRulesDirectory(), request.getOutputDirectory().resolve("staged-rules"));
        Set<String> grandfatheredDuplicateRuleIds = new HashSet<>(
                applier.conflictingDuplicateRuleIds());

        for (RuleCandidate candidate : candidates) {
            if (candidate.getProposedKind() == ProposedKind.DESCRIPTION) {
                processDescription(candidate, applier);
            } else if (candidate.getProposedKind() == ProposedKind.CONSTRAINT) {
                processConstraint(candidate, applier, verifications,
                        publishedRuleIds, carriedForward);
            } else {
                candidate.setStatus(CandidateStatus.SKIPPED);
                candidate.setSkipReason(SkipReason.UNRESOLVED_TARGET);
            }
        }

        Map<String, SourceDocumentArtifact> sourceDocumentsByPath = new LinkedHashMap<>();
        for (SourceDocumentArtifact retained : request.getRetainedSourceDocuments()) {
            sourceDocumentsByPath.put(retained.getRelativePath(), retained);
        }
        for (RuleDocumentRevision document : request.getDocuments()) {
            SourceDocumentArtifact artifact = SourceDocumentArtifact.builder()
                    .documentId(document.getDocumentId())
                    .revision(document.getRevision())
                    .relativePath(document.getSourceMarkdownRelativePath())
                    .content(document.getMarkdown())
                    .build();
            sourceDocumentsByPath.put(artifact.getRelativePath(), artifact);
        }
        List<SourceDocumentArtifact> sourceDocuments = List.copyOf(
                sourceDocumentsByPath.values());
        grandfatheredDuplicateRuleIds.removeAll(publishedRuleIds);
        RulePackageAssemblyResult assembly = new RulePackageAssembler(conditionAcceptor).assemble(
                RulePackageAssemblyRequest.builder()
                        .packageDirectory(request.getOutputDirectory().resolve("rule-package"))
                        .rulesDirectory(request.getOutputDirectory().resolve("staged-rules"))
                        .functionsDirectory(request.getFunctionsDirectory())
                        .packageVersion(request.getPackageVersion())
                        .createdAt(request.getCreatedAt())
                        .minimumAnalyzerVersion(request.getMinimumAnalyzerVersion())
                        .sourceDocuments(sourceDocuments)
                        .candidates(candidates)
                        .verifications(verifications)
                        .publishedConstraintRuleIds(publishedRuleIds)
                        .carriedForwardCandidateIds(carriedForward)
                        .grandfatheredDuplicateRuleIds(grandfatheredDuplicateRuleIds)
                        .build());
        List<DocumentConversionFeedback> feedbackItems = request.getDocuments().stream()
                .map(document -> feedbackService.publish(
                        DocumentFeedbackRequest.builder()
                                .documentId(document.getDocumentId())
                                .documentRevision(document.getRevision())
                                .releaseVersion(request.getPackageVersion())
                                .candidates(candidates)
                                .carriedForwardCandidateIds(carriedForward)
                                .releaseFailed(assembly.getStatus() == ReleaseReportStatus.FAILED)
                                .build()))
                .toList();
        return RuleCenterValidationResult.builder()
                .assembly(assembly)
                .candidates(List.copyOf(candidates))
                .verifications(List.copyOf(verifications))
                .extraction(extractions.get(0))
                .extractions(List.copyOf(extractions))
                .feedback(feedbackItems.get(0))
                .feedbackItems(feedbackItems)
                .build();
    }

    private void processDescription(
            RuleCandidate candidate,
            RulePackageChangeApplier applier) {
        if (!applier.targetExists(candidate)) {
            candidate.setStatus(CandidateStatus.SKIPPED);
            candidate.setSkipReason(SkipReason.UNRESOLVED_TARGET);
            return;
        }
        applier.applyDescription(candidate);
        candidate.setStatus(CandidateStatus.PUBLISHED);
    }

    private void processConstraint(
            RuleCandidate candidate,
            RulePackageChangeApplier applier,
            List<ConstraintVerification> verifications,
            Set<String> publishedRuleIds,
            Set<String> carriedForward) {
        JsonObject draft = requireDraft(candidate);
        String condition = requiredString(draft, "condition");
        boolean previousRuleExists = applier.hasRuleId(
                candidate, requiredString(draft, "ruleId"));
        ConditionAcceptance acceptance = conditionAcceptor.accept(condition);
        CandidateRoutingDecision preflight = publicationRouter.route(
                CandidateRoutingRequest.builder()
                        .candidateId(candidate.getCandidateId())
                        .proposedKind(ProposedKind.CONSTRAINT)
                        .targetResolved(applier.constraintTargetExists(candidate, condition))
                        .evidenceScope(requiredBoolean(draft, "staticTextOnly")
                                && applier.conditionUsesOnlyDeclaredLiteralValues(
                                        candidate, condition)
                                ? ConstraintEvidenceScope.DSL_TEXT_ONLY
                                : ConstraintEvidenceScope.EXTERNAL_RESOURCE)
                        .evidenceConflict(requiredBoolean(draft, "evidenceConflict"))
                        .conditionAcceptance(acceptance)
                        .build());
        if (preflight.getStatus() == CandidateStatus.SKIPPED) {
            candidate.setStatus(CandidateStatus.SKIPPED);
            candidate.setSkipReason(preflight.getSkipReason());
            return;
        }

        String existingRuleId = applier.existingRuleIdForCondition(candidate, condition);
        RuleConstraint constraint = constraint(draft, condition, existingRuleId);
        ConstraintVerificationRequest verificationRequest = ConstraintVerificationRequest.builder()
                .targetElement(candidate.getTarget().getElement())
                .constraint(constraint)
                .positiveFixturePath(candidate.getCandidateId() + "-positive.dsl")
                .positiveFixtureContent(requiredString(draft, "positiveFixture"))
                .negativeFixturePath(candidate.getCandidateId() + "-negative.dsl")
                .negativeFixtureContent(requiredString(draft, "negativeFixture"))
                .evidenceCandidateIds(List.of(candidate.getCandidateId()))
                .build();
        ConstraintRepairLoopOutcome outcome = new ConstraintRepairLoop(
                verificationRunner, exampleCatalog, repairStrategy).repair(
                        ConstraintRepairLoopRequest.builder()
                                .candidateId(candidate.getCandidateId())
                                .initialProposal(ConstraintRepairProposal.builder()
                                        .verificationRequest(verificationRequest)
                                        .sourceEvidenceFingerprint(sourceFingerprint(candidate))
                                        .targetFingerprint(targetFingerprint(candidate))
                                        .build())
                                .exampleQuery(exampleQuery(candidate, acceptance))
                                .build());
        if (outcome.getStatus() == CandidateStatus.VERIFIED) {
            RuleConstraint verified = outcome.getFinalProposal()
                    .getVerificationRequest().getConstraint();
            applier.applyConstraint(candidate, verified);
            verifications.add(outcome.getLastVerification().getVerification());
            publishedRuleIds.add(verified.getRuleId());
            candidate.setStatus(CandidateStatus.PUBLISHED);
            candidate.setValidationFailure(null);
        } else {
            candidate.setStatus(CandidateStatus.VALIDATION_ERROR);
            candidate.setValidationFailure(outcome.getLastVerification().getFailure());
            if (previousRuleExists) {
                carriedForward.add(candidate.getCandidateId());
            }
        }
    }

    private ConstraintExampleQuery exampleQuery(
            RuleCandidate candidate,
            ConditionAcceptance acceptance) {
        Set<String> attributes = new HashSet<>();
        Matcher matcher = ATTRIBUTE_REFERENCE.matcher(acceptance.getOriginalCondition());
        while (matcher.find()) {
            attributes.add(matcher.group(1));
        }
        return ConstraintExampleQuery.builder()
                .targetKind(candidate.getTarget().getKind())
                .targetElement(candidate.getTarget().getElement())
                .attributes(Set.copyOf(attributes))
                .relation(relation(acceptance.getOriginalCondition()))
                .requiredCapabilities(acceptance.getCapabilities())
                .build();
    }

    private ConstraintRelation relation(String condition) {
        if (condition.contains("children.")) {
            return ConstraintRelation.CHILD_COUNT;
        }
        if (condition.contains("containsExpression")) {
            return ConstraintRelation.EXPRESSION_PRESENCE;
        }
        if (condition.contains(" AND ") && condition.contains("!= null")) {
            return ConstraintRelation.MUTUAL_EXCLUSION;
        }
        return ConstraintRelation.ATTRIBUTE_VALUE;
    }

    private RuleConstraint constraint(
            JsonObject draft,
            String condition,
            String existingRuleId) {
        DiagnosticSeverity severity;
        try {
            severity = DiagnosticSeverity.valueOf(
                    requiredString(draft, "severity").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported constraint severity", exception);
        }
        return RuleConstraint.builder()
                .ruleId(existingRuleId == null
                        ? requiredString(draft, "ruleId") : existingRuleId)
                .condition(condition)
                .message(requiredString(draft, "message"))
                .severity(severity)
                .build();
    }

    private JsonObject requireDraft(RuleCandidate candidate) {
        if (candidate.getProposedChange() == null
                || candidate.getProposedChange().getValue() == null
                || !candidate.getProposedChange().getValue().isJsonObject()) {
            throw new IllegalArgumentException("constraint proposal must be an object");
        }
        return candidate.getProposedChange().getValue().getAsJsonObject();
    }

    private String requiredString(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()
                || !object.get(field).getAsJsonPrimitive().isString()
                || object.get(field).getAsString().isEmpty()) {
            throw new IllegalArgumentException(field + " must be a non-empty string");
        }
        return object.get(field).getAsString();
    }

    private boolean requiredBoolean(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()
                || !object.get(field).getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return object.get(field).getAsBoolean();
    }

    private String sourceFingerprint(RuleCandidate candidate) {
        return sha256(candidate.getDocumentId() + "\n" + candidate.getDocumentRevision() + "\n"
                + candidate.getSourceEvidence().getLocation().getStartLine() + "\n"
                + candidate.getSourceEvidence().getLocation().getEndLine() + "\n"
                + candidate.getSourceEvidence().getExcerpt());
    }

    private String targetFingerprint(RuleCandidate candidate) {
        return sha256(candidate.getTarget().getKind() + "\n"
                + candidate.getTarget().getElement() + "\n"
                + candidate.getTarget().getAttribute());
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void validateRequest(RuleCenterValidationRequest request) {
        Objects.requireNonNull(request, "request");
        RuleDocumentRevision document = Objects.requireNonNull(request.getDocument(), "document");
        requireText(document.getDocumentId(), "documentId");
        requireText(document.getRevision(), "documentRevision");
        Objects.requireNonNull(document.getMarkdown(), "markdown");
        requireText(document.getSourceMarkdownRelativePath(), "sourceMarkdownRelativePath");
        Objects.requireNonNull(request.getRulesDirectory(), "rulesDirectory");
        Objects.requireNonNull(request.getFunctionsDirectory(), "functionsDirectory");
        Objects.requireNonNull(request.getOutputDirectory(), "outputDirectory");
        requireText(request.getPackageVersion(), "packageVersion");
        requireText(request.getCreatedAt(), "createdAt");
        requireText(request.getMinimumAnalyzerVersion(), "minimumAnalyzerVersion");
    }

    private void validateBatchRequest(RuleCenterBatchValidationRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.getDocuments() == null || request.getDocuments().isEmpty()) {
            throw new IllegalArgumentException("documents must not be empty");
        }
        Objects.requireNonNull(
                request.getRetainedSourceDocuments(), "retainedSourceDocuments");
        for (RuleDocumentRevision document : request.getDocuments()) {
            Objects.requireNonNull(document, "document");
            requireText(document.getDocumentId(), "documentId");
            requireText(document.getRevision(), "documentRevision");
            Objects.requireNonNull(document.getMarkdown(), "markdown");
            requireText(document.getSourceMarkdownRelativePath(), "sourceMarkdownRelativePath");
        }
        Objects.requireNonNull(request.getRulesDirectory(), "rulesDirectory");
        Objects.requireNonNull(request.getFunctionsDirectory(), "functionsDirectory");
        Objects.requireNonNull(request.getOutputDirectory(), "outputDirectory");
        requireText(request.getPackageVersion(), "packageVersion");
        requireText(request.getCreatedAt(), "createdAt");
        requireText(request.getMinimumAnalyzerVersion(), "minimumAnalyzerVersion");
    }

    private void requireText(String value, String field) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
    }
}
