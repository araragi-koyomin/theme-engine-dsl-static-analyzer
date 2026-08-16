package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;
import java.util.Objects;

import com.huawei.theme.analysis.core.rulecenter.model.AuthorAction;
import com.huawei.theme.analysis.core.rulecenter.model.CandidateStatus;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;

public class ConstraintRepairLoop {
    private static final int MAX_REPAIR_ATTEMPTS = 2;

    private final ConstraintVerificationRunner verificationRunner;
    private final VerifiedConstraintExampleCatalog exampleCatalog;
    private final ConstraintRepairStrategy repairStrategy;

    public ConstraintRepairLoop(
            ConstraintVerificationRunner verificationRunner,
            VerifiedConstraintExampleCatalog exampleCatalog,
            ConstraintRepairStrategy repairStrategy) {
        this.verificationRunner = Objects.requireNonNull(verificationRunner);
        this.exampleCatalog = Objects.requireNonNull(exampleCatalog);
        this.repairStrategy = Objects.requireNonNull(repairStrategy);
    }

    public ConstraintRepairLoopOutcome repair(ConstraintRepairLoopRequest request) {
        validateRequest(request);
        ConstraintRepairProposal original = request.getInitialProposal();
        ConstraintRepairProposal current = original;
        ConstraintVerificationRunResult lastVerification = verificationRunner.verify(
                current.getVerificationRequest());
        if (lastVerification.isPassed()) {
            return verified(request, current, lastVerification, 0);
        }

        List<VerifiedConstraintExample> examples = exampleCatalog.findSimilar(
                request.getExampleQuery());
        for (int attempt = 1; attempt <= MAX_REPAIR_ATTEMPTS; attempt++) {
            ConstraintRepairProposal repaired = Objects.requireNonNull(
                    repairStrategy.repair(ConstraintRepairContext.builder()
                            .candidateId(request.getCandidateId())
                            .attempt(attempt)
                            .currentProposal(current)
                            .validationFailure(lastVerification.getFailure())
                            .examples(examples)
                            .build()),
                    "repair proposal");
            if (!preservesImmutableFields(original, repaired)) {
                return failed(request, repaired, lastVerification, attempt, true);
            }
            current = repaired;
            try {
                lastVerification = verificationRunner.verify(current.getVerificationRequest());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (lastVerification.isPassed()) {
                return verified(request, current, lastVerification, attempt);
            }
        }
        return failed(
                request,
                current,
                lastVerification,
                MAX_REPAIR_ATTEMPTS,
                false);
    }

    private boolean preservesImmutableFields(
            ConstraintRepairProposal original,
            ConstraintRepairProposal repaired) {
        if (repaired.getVerificationRequest() == null) {
            return false;
        }
        ConstraintVerificationRequest initialRequest = original.getVerificationRequest();
        ConstraintVerificationRequest repairedRequest = repaired.getVerificationRequest();
        RuleConstraint initialConstraint = initialRequest.getConstraint();
        RuleConstraint repairedConstraint = repairedRequest.getConstraint();
        return Objects.equals(
                        original.getSourceEvidenceFingerprint(),
                        repaired.getSourceEvidenceFingerprint())
                && Objects.equals(original.getTargetFingerprint(), repaired.getTargetFingerprint())
                && Objects.equals(initialRequest.getTargetElement(), repairedRequest.getTargetElement())
                && Objects.equals(
                        initialRequest.getEvidenceCandidateIds(),
                        repairedRequest.getEvidenceCandidateIds())
                && repairedConstraint != null
                && Objects.equals(initialConstraint.getRuleId(), repairedConstraint.getRuleId());
    }

    private ConstraintRepairLoopOutcome verified(
            ConstraintRepairLoopRequest request,
            ConstraintRepairProposal proposal,
            ConstraintVerificationRunResult verification,
            int attempts) {
        return ConstraintRepairLoopOutcome.builder()
                .candidateId(request.getCandidateId())
                .status(CandidateStatus.VERIFIED)
                .authorAction(AuthorAction.NONE)
                .repairAttempts(attempts)
                .finalProposal(proposal)
                .lastVerification(verification)
                .build();
    }

    private ConstraintRepairLoopOutcome failed(
            ConstraintRepairLoopRequest request,
            ConstraintRepairProposal proposal,
            ConstraintVerificationRunResult verification,
            int attempts,
            boolean immutableFieldsRejected) {
        return ConstraintRepairLoopOutcome.builder()
                .candidateId(request.getCandidateId())
                .status(CandidateStatus.VALIDATION_ERROR)
                .authorAction(AuthorAction.REWORK_REQUIRED)
                .repairAttempts(attempts)
                .immutableFieldsRejected(immutableFieldsRejected)
                .finalProposal(proposal)
                .lastVerification(verification)
                .build();
    }

    private void validateRequest(ConstraintRepairLoopRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.getCandidateId() == null || request.getCandidateId().isEmpty()) {
            throw new IllegalArgumentException("candidateId must not be empty");
        }
        ConstraintRepairProposal proposal = Objects.requireNonNull(
                request.getInitialProposal(), "initialProposal");
        Objects.requireNonNull(proposal.getVerificationRequest(), "verificationRequest");
        Objects.requireNonNull(proposal.getSourceEvidenceFingerprint(), "sourceEvidenceFingerprint");
        Objects.requireNonNull(proposal.getTargetFingerprint(), "targetFingerprint");
        Objects.requireNonNull(request.getExampleQuery(), "exampleQuery");
    }
}
