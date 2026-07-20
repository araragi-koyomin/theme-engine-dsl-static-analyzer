package com.huawei.theme.analysis.core.rulecenter;

import java.util.Objects;

import com.huawei.theme.analysis.core.rulecenter.model.CandidateStatus;
import com.huawei.theme.analysis.core.rulecenter.model.ProposedKind;
import com.huawei.theme.analysis.core.rulecenter.model.SkipReason;

public class CandidatePublicationRouter {

    public CandidateRoutingDecision route(CandidateRoutingRequest request) {
        validateRequest(request);
        if (request.getProposedKind() == ProposedKind.DESCRIPTION) {
            return decision(request, CandidateStatus.EXTRACTED, null);
        }
        if (!request.isTargetResolved()) {
            return decision(request, CandidateStatus.SKIPPED, SkipReason.UNRESOLVED_TARGET);
        }
        if (request.getEvidenceScope() != ConstraintEvidenceScope.DSL_TEXT_ONLY) {
            return decision(request, CandidateStatus.SKIPPED, SkipReason.OUT_OF_STATIC_SCOPE);
        }
        if (request.isEvidenceConflict()) {
            return decision(request, CandidateStatus.SKIPPED, SkipReason.EVIDENCE_CONFLICT);
        }
        ConditionAcceptance acceptance = Objects.requireNonNull(
                request.getConditionAcceptance(), "conditionAcceptance");
        if (!acceptance.isAccepted()) {
            return decision(
                    request,
                    CandidateStatus.SKIPPED,
                    SkipReason.UNSUPPORTED_CONDITION_GRAMMAR);
        }

        ConstraintVerificationRunResult verification = request.getVerificationResult();
        if (verification == null) {
            return decision(request, CandidateStatus.VALIDATING, null);
        }
        if (!verification.isPassed()) {
            if (verification.getFailure() == null) {
                throw new IllegalArgumentException("failed verification must include validationFailure");
            }
            return CandidateRoutingDecision.builder()
                    .candidateId(request.getCandidateId())
                    .status(CandidateStatus.VALIDATION_ERROR)
                    .validationFailure(verification.getFailure())
                    .build();
        }
        return decision(request, CandidateStatus.VERIFIED, null);
    }

    private CandidateRoutingDecision decision(
            CandidateRoutingRequest request,
            CandidateStatus status,
            SkipReason skipReason) {
        return CandidateRoutingDecision.builder()
                .candidateId(request.getCandidateId())
                .status(status)
                .skipReason(skipReason)
                .build();
    }

    private void validateRequest(CandidateRoutingRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.getCandidateId() == null || request.getCandidateId().isEmpty()) {
            throw new IllegalArgumentException("candidateId must not be empty");
        }
        Objects.requireNonNull(request.getProposedKind(), "proposedKind");
        if (request.getProposedKind() == ProposedKind.CONSTRAINT) {
            Objects.requireNonNull(request.getEvidenceScope(), "evidenceScope");
        }
    }
}
