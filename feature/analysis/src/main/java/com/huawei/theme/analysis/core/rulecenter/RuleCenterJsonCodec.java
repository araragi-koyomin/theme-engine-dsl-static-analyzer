package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.huawei.theme.analysis.core.rulecenter.model.CandidateStatus;
import com.huawei.theme.analysis.core.rulecenter.model.ConstraintVerification;
import com.huawei.theme.analysis.core.rulecenter.model.DocumentConversionFeedback;
import com.huawei.theme.analysis.core.rulecenter.model.FeedbackOutcome;
import com.huawei.theme.analysis.core.rulecenter.model.RuleCandidate;
import com.huawei.theme.analysis.core.rulecenter.model.VerificationStatus;

public class RuleCenterJsonCodec {
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public RuleCandidate readRuleCandidate(String json) {
        RuleCandidate candidate = parse(json, RuleCandidate.class, "candidate");
        requireText(candidate.getCandidateId(), "candidateId");
        requireText(candidate.getDocumentId(), "documentId");
        requireText(candidate.getDocumentRevision(), "documentRevision");
        validateEvidence(candidate.getSourceEvidence());
        if (candidate.getTarget() == null || candidate.getTarget().getKind() == null) {
            throw invalid("target.kind");
        }
        if (candidate.getProposedKind() == null) {
            throw invalid("proposedKind");
        }
        if (candidate.getProposedChange() == null) {
            throw invalid("proposedChange");
        }
        requireText(candidate.getProposedChange().getField(), "proposedChange.field");
        if (candidate.getProposedChange().getValue() == null) {
            throw invalid("proposedChange.value");
        }
        if (candidate.getStatus() == null) {
            throw invalid("status");
        }
        validateCandidateOutcome(candidate);
        return candidate;
    }

    public ConstraintVerification readConstraintVerification(String json) {
        ConstraintVerification verification = parse(json, ConstraintVerification.class, "verification");
        requireText(verification.getRuleId(), "ruleId");
        requireText(verification.getCondition(), "condition");
        requireText(verification.getPositiveFixture(), "positiveFixture");
        requireText(verification.getNegativeFixture(), "negativeFixture");
        requireList(verification.getPositiveObservedRuleIds(), "positiveObservedRuleIds");
        requireList(verification.getNegativeObservedRuleIds(), "negativeObservedRuleIds");
        requireList(verification.getEvidenceCandidateIds(), "evidenceCandidateIds");
        if (verification.getStatus() != VerificationStatus.PASSED || !verification.isParserAccepted()) {
            throw invalid("status/parserAccepted");
        }
        if (!verification.getPositiveObservedRuleIds().contains(verification.getRuleId())) {
            throw invalid("positiveObservedRuleIds");
        }
        if (verification.getNegativeObservedRuleIds().contains(verification.getRuleId())) {
            throw invalid("negativeObservedRuleIds");
        }
        return verification;
    }

    public DocumentConversionFeedback readDocumentConversionFeedback(String json) {
        DocumentConversionFeedback feedback = parse(json, DocumentConversionFeedback.class, "feedback");
        requireText(feedback.getDocumentId(), "documentId");
        requireText(feedback.getDocumentRevision(), "documentRevision");
        if (feedback.getConversionStatus() == null) {
            throw invalid("conversionStatus");
        }
        if (feedback.getSummary() == null) {
            throw invalid("summary");
        }
        requireList(feedback.getItems(), "items");
        for (DocumentConversionFeedback.FeedbackItem item : feedback.getItems()) {
            if (item == null || item.getOutcome() == null || item.getAuthorAction() == null) {
                throw invalid("items.outcome/authorAction");
            }
            validateEvidence(item.getSourceEvidence());
            if ((item.getOutcome() == FeedbackOutcome.SKIPPED
                    || item.getOutcome() == FeedbackOutcome.VALIDATION_ERROR)
                    && item.getReasonCode() == null) {
                throw invalid("items.reasonCode");
            }
        }
        return feedback;
    }

    public String write(Object value) {
        if (value == null) {
            throw invalid("value");
        }
        return gson.toJson(value);
    }

    private <T> T parse(String json, Class<T> type, String label) {
        if (json == null || json.isEmpty()) {
            throw invalid(label);
        }
        try {
            T value = gson.fromJson(json, type);
            if (value == null) {
                throw invalid(label);
            }
            return value;
        } catch (JsonParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new JsonParseException("Invalid " + label, exception);
        }
    }

    private void validateEvidence(RuleCandidate.SourceEvidence evidence) {
        if (evidence == null) {
            throw invalid("sourceEvidence");
        }
        requireList(evidence.getSectionPath(), "sourceEvidence.sectionPath");
        requireText(evidence.getExcerpt(), "sourceEvidence.excerpt");
        if (evidence.getLocation() == null || evidence.getLocation().getStartLine() < 1) {
            throw invalid("sourceEvidence.location.startLine");
        }
        if (evidence.getLocation().getEndLine() < evidence.getLocation().getStartLine()) {
            throw invalid("sourceEvidence.location.endLine");
        }
    }

    private void validateCandidateOutcome(RuleCandidate candidate) {
        if (candidate.getStatus() == CandidateStatus.SKIPPED) {
            if (candidate.getSkipReason() == null) {
                throw invalid("skipReason");
            }
            if (candidate.getValidationFailure() != null) {
                throw invalid("validationFailure");
            }
            return;
        }
        if (candidate.getStatus() == CandidateStatus.VALIDATION_ERROR) {
            if (candidate.getValidationFailure() == null) {
                throw invalid("validationFailure");
            }
            if (candidate.getSkipReason() != null) {
                throw invalid("skipReason");
            }
            return;
        }
        if (candidate.getSkipReason() != null || candidate.getValidationFailure() != null) {
            throw invalid("skipReason/validationFailure");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isEmpty()) {
            throw invalid(field);
        }
    }

    private void requireList(List<?> value, String field) {
        if (value == null) {
            throw invalid(field);
        }
    }

    private JsonParseException invalid(String field) {
        return new JsonParseException("Invalid or missing " + field);
    }
}
