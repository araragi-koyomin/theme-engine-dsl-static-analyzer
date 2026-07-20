package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulecenter.model.CandidateStatus;
import com.huawei.theme.analysis.core.rulecenter.model.ProposedKind;
import com.huawei.theme.analysis.core.rulecenter.model.SkipReason;
import com.huawei.theme.analysis.core.rulecenter.model.ValidationFailure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CandidatePublicationRouterTest {

    private final StrictConditionAcceptor acceptor = new StrictConditionAcceptor(
            new ConditionCapabilityRegistry());
    private final CandidatePublicationRouter router = new CandidatePublicationRouter();

    @Test
    void directlySkipsOutOfStaticScopeWithoutTryingVerification() {
        CandidateRoutingDecision decision = router.route(requestBuilder()
                .evidenceScope(ConstraintEvidenceScope.EXTERNAL_RESOURCE)
                .build());

        assertSkipped(decision, SkipReason.OUT_OF_STATIC_SCOPE);
    }

    @Test
    void directlySkipsUnresolvedTargetUnsupportedGrammarAndEvidenceConflict() {
        CandidateRoutingDecision unresolved = router.route(requestBuilder()
                .targetResolved(false)
                .build());
        CandidateRoutingDecision unsupported = router.route(requestBuilder()
                .conditionAcceptance(acceptor.accept("element.attrs['src'].endsWith('.mp4')"))
                .build());
        CandidateRoutingDecision conflict = router.route(requestBuilder()
                .evidenceConflict(true)
                .build());

        assertSkipped(unresolved, SkipReason.UNRESOLVED_TARGET);
        assertSkipped(unsupported, SkipReason.UNSUPPORTED_CONDITION_GRAMMAR);
        assertSkipped(conflict, SkipReason.EVIDENCE_CONFLICT);
    }

    @Test
    void fixtureFailureIsValidationErrorAndNeverSkipped() {
        CandidateRoutingDecision decision = router.route(requestBuilder()
                .verificationResult(ConstraintVerificationRunResult.builder()
                        .passed(false)
                        .failure(ValidationFailure.POSITIVE_FIXTURE_MISSED)
                        .positiveObservedRuleIds(List.of())
                        .negativeObservedRuleIds(List.of())
                        .build())
                .build());

        assertEquals(CandidateStatus.VALIDATION_ERROR, decision.getStatus());
        assertEquals(ValidationFailure.POSITIVE_FIXTURE_MISSED, decision.getValidationFailure());
        assertNull(decision.getSkipReason());
    }

    @Test
    void acceptedConstraintMovesFromValidatingToVerified() {
        CandidateRoutingDecision awaitingFixture = router.route(requestBuilder().build());
        CandidateRoutingDecision verified = router.route(requestBuilder()
                .verificationResult(passedVerification())
                .build());

        assertEquals(CandidateStatus.VALIDATING, awaitingFixture.getStatus());
        assertEquals(CandidateStatus.VERIFIED, verified.getStatus());
        assertNull(verified.getSkipReason());
        assertNull(verified.getValidationFailure());
    }

    @Test
    void descriptionCandidateRemainsExtractedWithoutConditionVerification() {
        CandidateRoutingDecision decision = router.route(requestBuilder()
                .proposedKind(ProposedKind.DESCRIPTION)
                .conditionAcceptance(null)
                .verificationResult(null)
                .build());

        assertEquals(CandidateStatus.EXTRACTED, decision.getStatus());
        assertNull(decision.getSkipReason());
        assertNull(decision.getValidationFailure());
    }

    private CandidateRoutingRequest.CandidateRoutingRequestBuilder requestBuilder() {
        return CandidateRoutingRequest.builder()
                .candidateId("candidate-1")
                .proposedKind(ProposedKind.CONSTRAINT)
                .targetResolved(true)
                .evidenceScope(ConstraintEvidenceScope.DSL_TEXT_ONLY)
                .evidenceConflict(false)
                .conditionAcceptance(ConditionAcceptance.builder()
                        .accepted(true)
                        .status(ConditionAcceptanceStatus.ACCEPTED)
                        .originalCondition("element.attrs['src'] != null")
                        .normalizedCondition("element.attrs['src'] != null")
                        .capabilities(Set.of(ConditionCapability.BASE_GRAMMAR))
                        .syntaxErrors(List.of())
                        .build());
    }

    private ConstraintVerificationRunResult passedVerification() {
        return ConstraintVerificationRunResult.builder()
                .passed(true)
                .positiveObservedRuleIds(List.of("SEM-IMG-900"))
                .negativeObservedRuleIds(List.of())
                .build();
    }

    private void assertSkipped(CandidateRoutingDecision decision, SkipReason reason) {
        assertEquals(CandidateStatus.SKIPPED, decision.getStatus());
        assertEquals(reason, decision.getSkipReason());
        assertNull(decision.getValidationFailure());
    }
}
