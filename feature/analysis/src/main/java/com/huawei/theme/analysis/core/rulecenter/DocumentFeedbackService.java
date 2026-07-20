package com.huawei.theme.analysis.core.rulecenter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.huawei.theme.analysis.core.rulecenter.model.AuthorAction;
import com.huawei.theme.analysis.core.rulecenter.model.CandidateStatus;
import com.huawei.theme.analysis.core.rulecenter.model.ConversionStatus;
import com.huawei.theme.analysis.core.rulecenter.model.DocumentConversionFeedback;
import com.huawei.theme.analysis.core.rulecenter.model.FeedbackOutcome;
import com.huawei.theme.analysis.core.rulecenter.model.FeedbackReasonCode;
import com.huawei.theme.analysis.core.rulecenter.model.ProposedKind;
import com.huawei.theme.analysis.core.rulecenter.model.RuleCandidate;
import com.huawei.theme.analysis.core.rulecenter.model.SkipReason;
import com.huawei.theme.analysis.core.rulecenter.model.ValidationFailure;

public class DocumentFeedbackService {
    private final DocumentFeedbackPublisher publisher;

    public DocumentFeedbackService(DocumentFeedbackPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher);
    }

    public DocumentConversionFeedback publish(DocumentFeedbackRequest request) {
        validateRequest(request);
        List<DocumentConversionFeedback.FeedbackItem> items = new ArrayList<>();
        int published = 0;
        int descriptionOnly = 0;
        int skipped = 0;
        int validationErrors = 0;
        int carriedForward = 0;

        for (RuleCandidate candidate : request.getCandidates()) {
            if (!belongsToDocument(candidate, request)) {
                continue;
            }
            validateEvidence(candidate);
            boolean retained = request.getCarriedForwardCandidateIds()
                    .contains(candidate.getCandidateId());
            DocumentConversionFeedback.FeedbackItem item;
            if (candidate.getStatus() == CandidateStatus.SKIPPED) {
                skipped++;
                item = skippedItem(candidate);
            } else if (candidate.getStatus() == CandidateStatus.VALIDATION_ERROR) {
                validationErrors++;
                if (retained) {
                    carriedForward++;
                }
                item = validationErrorItem(candidate, retained);
            } else if (candidate.getStatus() == CandidateStatus.PUBLISHED
                    || candidate.getStatus() == CandidateStatus.VERIFIED) {
                if (candidate.getProposedKind() == ProposedKind.DESCRIPTION) {
                    descriptionOnly++;
                    item = successfulItem(candidate, FeedbackOutcome.DESCRIPTION_ONLY);
                } else {
                    published++;
                    item = successfulItem(candidate, FeedbackOutcome.PUBLISHED);
                }
            } else {
                throw new IllegalArgumentException(
                        "candidate has no publishable terminal status: "
                                + candidate.getCandidateId());
            }
            items.add(item);
        }

        ConversionStatus conversionStatus = conversionStatus(
                request.isReleaseFailed(),
                published + descriptionOnly,
                skipped,
                validationErrors);
        DocumentConversionFeedback feedback = DocumentConversionFeedback.builder()
                .documentId(request.getDocumentId())
                .documentRevision(request.getDocumentRevision())
                .conversionStatus(conversionStatus)
                .releaseVersion(request.getReleaseVersion())
                .summary(DocumentConversionFeedback.FeedbackSummary.builder()
                        .published(published)
                        .descriptionOnly(descriptionOnly)
                        .skipped(skipped)
                        .validationErrors(validationErrors)
                        .carriedForward(carriedForward)
                        .build())
                .items(List.copyOf(items))
                .build();
        publisher.publish(feedback);
        return feedback;
    }

    private DocumentConversionFeedback.FeedbackItem skippedItem(RuleCandidate candidate) {
        SkipReason reason = Objects.requireNonNull(candidate.getSkipReason(), "skipReason");
        AuthorAction action = reason == SkipReason.OUT_OF_STATIC_SCOPE
                ? AuthorAction.NONE : AuthorAction.OPTIONAL_REWRITE;
        return DocumentConversionFeedback.FeedbackItem.builder()
                .sourceEvidence(candidate.getSourceEvidence())
                .outcome(FeedbackOutcome.SKIPPED)
                .reasonCode(FeedbackReasonCode.valueOf(reason.name()))
                .authorAction(action)
                .previousRuleRetained(false)
                .build();
    }

    private DocumentConversionFeedback.FeedbackItem validationErrorItem(
            RuleCandidate candidate,
            boolean retained) {
        ValidationFailure failure = Objects.requireNonNull(
                candidate.getValidationFailure(), "validationFailure");
        return DocumentConversionFeedback.FeedbackItem.builder()
                .sourceEvidence(candidate.getSourceEvidence())
                .outcome(FeedbackOutcome.VALIDATION_ERROR)
                .reasonCode(FeedbackReasonCode.valueOf(failure.name()))
                .authorAction(AuthorAction.REWORK_REQUIRED)
                .previousRuleRetained(retained)
                .build();
    }

    private DocumentConversionFeedback.FeedbackItem successfulItem(
            RuleCandidate candidate,
            FeedbackOutcome outcome) {
        return DocumentConversionFeedback.FeedbackItem.builder()
                .sourceEvidence(candidate.getSourceEvidence())
                .outcome(outcome)
                .authorAction(AuthorAction.NONE)
                .previousRuleRetained(false)
                .build();
    }

    private ConversionStatus conversionStatus(
            boolean releaseFailed,
            int successful,
            int skipped,
            int validationErrors) {
        if (releaseFailed) {
            return ConversionStatus.RELEASE_FAILED;
        }
        if (validationErrors > 0) {
            return ConversionStatus.PUBLISHED_WITH_ERRORS;
        }
        if (skipped > 0) {
            return ConversionStatus.PUBLISHED_WITH_SKIPS;
        }
        if (successful > 0) {
            return ConversionStatus.PUBLISHED;
        }
        return ConversionStatus.NO_APPLICABLE_CHANGE;
    }

    private boolean belongsToDocument(
            RuleCandidate candidate,
            DocumentFeedbackRequest request) {
        return Objects.equals(candidate.getDocumentId(), request.getDocumentId())
                && Objects.equals(candidate.getDocumentRevision(), request.getDocumentRevision());
    }

    private void validateEvidence(RuleCandidate candidate) {
        RuleCandidate.SourceEvidence evidence = Objects.requireNonNull(
                candidate.getSourceEvidence(), "sourceEvidence");
        RuleCandidate.SourceLocation location = Objects.requireNonNull(
                evidence.getLocation(), "sourceEvidence.location");
        if (location.getStartLine() < 1 || location.getEndLine() < location.getStartLine()) {
            throw new IllegalArgumentException("source evidence must include valid line range");
        }
        if (evidence.getExcerpt() == null || evidence.getExcerpt().isEmpty()) {
            throw new IllegalArgumentException("source evidence must include excerpt");
        }
    }

    private void validateRequest(DocumentFeedbackRequest request) {
        Objects.requireNonNull(request, "request");
        requireText(request.getDocumentId(), "documentId");
        requireText(request.getDocumentRevision(), "documentRevision");
        Objects.requireNonNull(request.getCandidates(), "candidates");
        Objects.requireNonNull(request.getCarriedForwardCandidateIds(), "carriedForwardCandidateIds");
    }

    private void requireText(String value, String field) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
    }
}
