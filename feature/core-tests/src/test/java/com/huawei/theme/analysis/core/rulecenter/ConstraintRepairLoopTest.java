package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulecenter.model.AuthorAction;
import com.huawei.theme.analysis.core.rulecenter.model.CandidateStatus;
import com.huawei.theme.analysis.core.rulecenter.model.ConstraintVerification;
import com.huawei.theme.analysis.core.rulecenter.model.TargetKind;
import com.huawei.theme.analysis.core.rulecenter.model.VerificationStatus;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstraintRepairLoopTest {

    private static final String RULE_ID = "SEM-IMG-901";
    private static final String CONDITION =
            "element.attrs['src'] != null AND element.attrs['srcExp'] != null";
    private final StrictConditionAcceptor acceptor = new StrictConditionAcceptor(
            new ConditionCapabilityRegistry());
    private final ConstraintVerificationRunner verificationRunner =
            new ConstraintVerificationRunner(acceptor);

    @Test
    void firstRepairCanSucceedUsingVerifiedExamplesAndRealAnalyzer() {
        AtomicInteger calls = new AtomicInteger();
        ConstraintRepairStrategy strategy = context -> {
            calls.incrementAndGet();
            assertEquals(1, context.getAttempt());
            assertEquals(List.of("SEM-IMG-002"), context.getExamples().stream()
                    .map(VerifiedConstraintExample::getRuleId)
                    .toList());
            return proposal(validRequest(), "evidence-sha256", "Image.src/srcExp");
        };

        ConstraintRepairLoopOutcome outcome = loop(strategy, exampleCatalog()).repair(loopRequest());

        assertEquals(1, calls.get());
        assertEquals(1, outcome.getRepairAttempts());
        assertEquals(CandidateStatus.VERIFIED, outcome.getStatus());
        assertEquals(AuthorAction.NONE, outcome.getAuthorAction());
        assertTrue(outcome.getLastVerification().isPassed());
        assertNotNull(outcome.getLastVerification().getVerification());
    }

    @Test
    void twoFailedRepairsEndAsValidationErrorAndRequireAuthorRework() {
        AtomicInteger calls = new AtomicInteger();
        ConstraintRepairStrategy strategy = context -> {
            calls.incrementAndGet();
            return proposal(failingRequest(), "evidence-sha256", "Image.src/srcExp");
        };

        ConstraintRepairLoopOutcome outcome = loop(strategy, emptyCatalog()).repair(loopRequest());

        assertEquals(2, calls.get());
        assertEquals(2, outcome.getRepairAttempts());
        assertEquals(CandidateStatus.VALIDATION_ERROR, outcome.getStatus());
        assertEquals(AuthorAction.REWORK_REQUIRED, outcome.getAuthorAction());
        assertFalse(outcome.getLastVerification().isPassed());
    }

    @Test
    void neverCallsRepairStrategyAThirdTime() {
        AtomicInteger calls = new AtomicInteger();
        ConstraintRepairStrategy strategy = context -> {
            if (calls.incrementAndGet() > 2) {
                throw new AssertionError("third repair attempt must not happen");
            }
            return proposal(failingRequest(), "evidence-sha256", "Image.src/srcExp");
        };

        ConstraintRepairLoopOutcome outcome = loop(strategy, emptyCatalog()).repair(loopRequest());

        assertEquals(2, calls.get());
        assertEquals(2, outcome.getRepairAttempts());
    }

    @Test
    void rejectsRepairThatChangesEvidenceOrTarget() {
        ConstraintRepairStrategy targetChanging = context -> proposal(
                validRequest(), "evidence-sha256", "Video.src");
        ConstraintRepairStrategy evidenceChanging = context -> proposal(
                validRequest(), "different-evidence", "Image.src/srcExp");

        ConstraintRepairLoopOutcome changedTarget = loop(targetChanging, emptyCatalog())
                .repair(loopRequest());
        ConstraintRepairLoopOutcome changedEvidence = loop(evidenceChanging, emptyCatalog())
                .repair(loopRequest());

        assertTrue(changedTarget.isImmutableFieldsRejected());
        assertTrue(changedEvidence.isImmutableFieldsRejected());
        assertEquals(CandidateStatus.VALIDATION_ERROR, changedTarget.getStatus());
        assertEquals(AuthorAction.REWORK_REQUIRED, changedEvidence.getAuthorAction());
    }

    private ConstraintRepairLoop loop(
            ConstraintRepairStrategy strategy,
            VerifiedConstraintExampleCatalog catalog) {
        return new ConstraintRepairLoop(verificationRunner, catalog, strategy);
    }

    private ConstraintRepairLoopRequest loopRequest() {
        return ConstraintRepairLoopRequest.builder()
                .candidateId("candidate-1")
                .initialProposal(proposal(
                        failingRequest(), "evidence-sha256", "Image.src/srcExp"))
                .exampleQuery(ConstraintExampleQuery.builder()
                        .targetKind(TargetKind.ELEMENT_ATTRIBUTE)
                        .targetElement("Image")
                        .attributes(Set.of("src", "srcExp"))
                        .relation(ConstraintRelation.MUTUAL_EXCLUSION)
                        .requiredCapabilities(Set.of(ConditionCapability.BASE_GRAMMAR))
                        .build())
                .build();
    }

    private ConstraintRepairProposal proposal(
            ConstraintVerificationRequest request,
            String evidenceFingerprint,
            String targetFingerprint) {
        return ConstraintRepairProposal.builder()
                .verificationRequest(request)
                .sourceEvidenceFingerprint(evidenceFingerprint)
                .targetFingerprint(targetFingerprint)
                .build();
    }

    private ConstraintVerificationRequest failingRequest() {
        return verificationRequest(
                "<Image src=\"picture.png\"/>",
                "<Image src=\"picture.png\"/>");
    }

    private ConstraintVerificationRequest validRequest() {
        return verificationRequest(
                "<Image src=\"picture.png\" srcExp=\"#picture\"/>",
                "<Image src=\"picture.png\"/>");
    }

    private ConstraintVerificationRequest verificationRequest(String positive, String negative) {
        return ConstraintVerificationRequest.builder()
                .targetElement("Image")
                .constraint(RuleConstraint.builder()
                        .ruleId(RULE_ID)
                        .condition(CONDITION)
                        .message("src and srcExp cannot coexist")
                        .severity(DiagnosticSeverity.ERROR)
                        .build())
                .positiveFixturePath("fixtures/SEM-IMG-901/positive.xml")
                .positiveFixtureContent(positive)
                .negativeFixturePath("fixtures/SEM-IMG-901/negative.xml")
                .negativeFixtureContent(negative)
                .evidenceCandidateIds(List.of("candidate-1"))
                .build();
    }

    private VerifiedConstraintExampleCatalog emptyCatalog() {
        return new VerifiedConstraintExampleCatalog(List.of(), acceptor);
    }

    private VerifiedConstraintExampleCatalog exampleCatalog() {
        String exampleRuleId = "SEM-IMG-002";
        ConstraintVerification verification = ConstraintVerification.builder()
                .ruleId(exampleRuleId)
                .condition(CONDITION)
                .parserAccepted(true)
                .positiveFixture("fixtures/SEM-IMG-002/positive.xml")
                .negativeFixture("fixtures/SEM-IMG-002/negative.xml")
                .positiveObservedRuleIds(List.of(exampleRuleId))
                .negativeObservedRuleIds(List.of())
                .evidenceCandidateIds(List.of("old-candidate"))
                .status(VerificationStatus.PASSED)
                .build();
        VerifiedConstraintExample example = VerifiedConstraintExample.builder()
                .ruleId(exampleRuleId)
                .targetKind(TargetKind.ELEMENT_ATTRIBUTE)
                .targetElement("Image")
                .attributes(Set.of("src", "srcExp"))
                .relation(ConstraintRelation.MUTUAL_EXCLUSION)
                .evidenceScope(ConstraintEvidenceScope.DSL_TEXT_ONLY)
                .condition(CONDITION)
                .verification(verification)
                .build();
        return new VerifiedConstraintExampleCatalog(List.of(example), acceptor);
    }
}
