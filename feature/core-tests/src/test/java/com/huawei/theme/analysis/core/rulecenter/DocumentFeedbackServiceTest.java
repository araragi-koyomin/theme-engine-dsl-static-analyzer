package com.huawei.theme.analysis.core.rulecenter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonPrimitive;
import com.huawei.theme.analysis.core.rulecenter.model.AuthorAction;
import com.huawei.theme.analysis.core.rulecenter.model.CandidateStatus;
import com.huawei.theme.analysis.core.rulecenter.model.ConversionStatus;
import com.huawei.theme.analysis.core.rulecenter.model.DocumentConversionFeedback;
import com.huawei.theme.analysis.core.rulecenter.model.FeedbackOutcome;
import com.huawei.theme.analysis.core.rulecenter.model.FeedbackReasonCode;
import com.huawei.theme.analysis.core.rulecenter.model.ProposedKind;
import com.huawei.theme.analysis.core.rulecenter.model.RuleCandidate;
import com.huawei.theme.analysis.core.rulecenter.model.SkipReason;
import com.huawei.theme.analysis.core.rulecenter.model.TargetKind;
import com.huawei.theme.analysis.core.rulecenter.model.ValidationFailure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentFeedbackServiceTest {

    private final RecordingPublisher publisher = new RecordingPublisher();
    private final DocumentFeedbackService service = new DocumentFeedbackService(publisher);

    @Test
    void publishesLineLevelFeedbackForEveryOutcomeIncludingCarriedForwardRule() {
        RuleCandidate published = candidate(
                "published", ProposedKind.CONSTRAINT, CandidateStatus.PUBLISHED, null, null, 10);
        RuleCandidate description = candidate(
                "description", ProposedKind.DESCRIPTION, CandidateStatus.PUBLISHED, null, null, 20);
        RuleCandidate skipped = candidate(
                "skipped", ProposedKind.CONSTRAINT, CandidateStatus.SKIPPED,
                SkipReason.UNRESOLVED_TARGET, null, 30);
        RuleCandidate validationError = candidate(
                "error", ProposedKind.CONSTRAINT, CandidateStatus.VALIDATION_ERROR,
                null, ValidationFailure.POSITIVE_FIXTURE_MISSED, 40);

        DocumentConversionFeedback feedback = service.publish(request(
                List.of(published, description, skipped, validationError),
                Set.of("error"), false));

        assertEquals(ConversionStatus.PUBLISHED_WITH_ERRORS, feedback.getConversionStatus());
        assertEquals(1, feedback.getSummary().getPublished());
        assertEquals(1, feedback.getSummary().getDescriptionOnly());
        assertEquals(1, feedback.getSummary().getSkipped());
        assertEquals(1, feedback.getSummary().getValidationErrors());
        assertEquals(1, feedback.getSummary().getCarriedForward());
        assertEquals(4, feedback.getItems().size());
        assertEquals(List.of(10, 20, 30, 40), feedback.getItems().stream()
                .map(item -> item.getSourceEvidence().getLocation().getStartLine())
                .toList());

        DocumentConversionFeedback.FeedbackItem errorItem = feedback.getItems().get(3);
        assertEquals(FeedbackOutcome.VALIDATION_ERROR, errorItem.getOutcome());
        assertEquals(FeedbackReasonCode.POSITIVE_FIXTURE_MISSED, errorItem.getReasonCode());
        assertEquals(AuthorAction.REWORK_REQUIRED, errorItem.getAuthorAction());
        assertTrue(errorItem.isPreviousRuleRetained());
        assertEquals(List.of(feedback), publisher.published);
    }

    @Test
    void externalResourceSkipRequiresNoAuthorRewrite() {
        RuleCandidate skipped = candidate(
                "resource", ProposedKind.CONSTRAINT, CandidateStatus.SKIPPED,
                SkipReason.OUT_OF_STATIC_SCOPE, null, 51);

        DocumentConversionFeedback feedback = service.publish(request(
                List.of(skipped), Set.of(), false));

        assertEquals(ConversionStatus.PUBLISHED_WITH_SKIPS, feedback.getConversionStatus());
        DocumentConversionFeedback.FeedbackItem item = feedback.getItems().get(0);
        assertEquals(FeedbackReasonCode.OUT_OF_STATIC_SCOPE, item.getReasonCode());
        assertEquals(AuthorAction.NONE, item.getAuthorAction());
        assertFalse(item.isPreviousRuleRetained());
    }

    @Test
    void reportsNoApplicableChangeAndReleaseFailureSeparately() {
        DocumentConversionFeedback noChange = service.publish(request(List.of(), Set.of(), false));
        DocumentConversionFeedback failed = service.publish(request(List.of(), Set.of(), true));

        assertEquals(ConversionStatus.NO_APPLICABLE_CHANGE, noChange.getConversionStatus());
        assertEquals(ConversionStatus.RELEASE_FAILED, failed.getConversionStatus());
    }

    @Test
    void rejectsFeedbackItemWithoutValidSourceLine() {
        RuleCandidate candidate = candidate(
                "missing-line", ProposedKind.CONSTRAINT, CandidateStatus.SKIPPED,
                SkipReason.UNRESOLVED_TARGET, null, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.publish(request(List.of(candidate), Set.of(), false)));
        assertTrue(publisher.published.isEmpty());
    }

    private DocumentFeedbackRequest request(
            List<RuleCandidate> candidates,
            Set<String> carriedForward,
            boolean releaseFailed) {
        return DocumentFeedbackRequest.builder()
                .documentId("image")
                .documentRevision("r42")
                .releaseVersion("2026.07.20.1")
                .candidates(candidates)
                .carriedForwardCandidateIds(carriedForward)
                .releaseFailed(releaseFailed)
                .build();
    }

    private RuleCandidate candidate(
            String id,
            ProposedKind kind,
            CandidateStatus status,
            SkipReason skipReason,
            ValidationFailure failure,
            int startLine) {
        return RuleCandidate.builder()
                .candidateId(id)
                .documentId("image")
                .documentRevision("r42")
                .sourceEvidence(RuleCandidate.SourceEvidence.builder()
                        .sectionPath(List.of("参数说明", "src"))
                        .location(RuleCandidate.SourceLocation.builder()
                                .startLine(startLine)
                                .endLine(startLine + 1)
                                .build())
                        .excerpt("source excerpt")
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
                        .value(new JsonPrimitive("change"))
                        .build())
                .status(status)
                .skipReason(skipReason)
                .validationFailure(failure)
                .build();
    }

    private static final class RecordingPublisher implements DocumentFeedbackPublisher {
        private final List<DocumentConversionFeedback> published = new ArrayList<>();

        @Override
        public void publish(DocumentConversionFeedback feedback) {
            published.add(feedback);
        }
    }
}
