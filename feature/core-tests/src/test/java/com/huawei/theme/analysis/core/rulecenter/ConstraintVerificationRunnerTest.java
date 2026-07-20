package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulecenter.model.ValidationFailure;
import com.huawei.theme.analysis.core.rulecenter.model.VerificationStatus;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstraintVerificationRunnerTest {

    private static final String RULE_ID = "SEM-IMG-900";
    private final ConstraintVerificationRunner runner = new ConstraintVerificationRunner(
            new StrictConditionAcceptor(new ConditionCapabilityRegistry()));

    @Test
    void passesOnlyWhenProductionAnalyzerHitsPositiveAndMissesNegative() {
        ConstraintVerificationRunResult result = runner.verify(request(
                "<Image src=\"picture.png\" srcExp=\"#picture\"/>",
                "<Image src=\"picture.png\"/>"));

        assertTrue(result.isPassed());
        assertNull(result.getFailure());
        assertEquals(List.of(RULE_ID), result.getPositiveObservedRuleIds());
        assertEquals(List.of(), result.getNegativeObservedRuleIds());
        assertEquals(VerificationStatus.PASSED, result.getVerification().getStatus());
        assertTrue(result.getVerification().isParserAccepted());
        assertEquals(List.of("candidate-1"), result.getVerification().getEvidenceCandidateIds());
    }

    @Test
    void reportsPositiveFixtureMissInsteadOfTreatingItAsUnsupportedGrammar() {
        ConstraintVerificationRunResult result = runner.verify(request(
                "<Image src=\"picture.png\"/>",
                "<Image src=\"picture.png\"/>"));

        assertFalse(result.isPassed());
        assertEquals(ValidationFailure.POSITIVE_FIXTURE_MISSED, result.getFailure());
        assertEquals(List.of(), result.getPositiveObservedRuleIds());
    }

    @Test
    void reportsNegativeFixtureHit() {
        ConstraintVerificationRunResult result = runner.verify(request(
                "<Image src=\"picture.png\" srcExp=\"#picture\"/>",
                "<Image src=\"picture.png\" srcExp=\"#other\"/>"));

        assertFalse(result.isPassed());
        assertEquals(ValidationFailure.NEGATIVE_FIXTURE_HIT, result.getFailure());
        assertEquals(List.of(RULE_ID), result.getNegativeObservedRuleIds());
    }

    @Test
    void rejectsMalformedFixtureBeforeAnalyzerExecution() {
        ConstraintVerificationRunResult malformedPositive = runner.verify(request(
                "<Image src=\"picture.png\"",
                "<Image src=\"picture.png\"/>"));
        ConstraintVerificationRunResult malformedNegative = runner.verify(request(
                "<Image src=\"picture.png\" srcExp=\"#picture\"/>",
                "{\"Image\": {\"src\": \"picture.png\"}}"));

        assertEquals(ValidationFailure.FIXTURE_PARSE_ERROR, malformedPositive.getFailure());
        assertEquals(ValidationFailure.FIXTURE_PARSE_ERROR, malformedNegative.getFailure());
    }

    @Test
    void refusesConditionThatStrictAcceptorDidNotAccept() {
        ConstraintVerificationRequest request = request(
                "<Image src=\"picture.mp4\"/>",
                "<Image src=\"picture.png\"/>");
        request.getConstraint().setCondition("element.attrs['src'].endsWith('.mp4')");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> runner.verify(request));

        assertTrue(error.getMessage().contains("UNSUPPORTED_CONDITION_GRAMMAR"));
    }

    private ConstraintVerificationRequest request(String positive, String negative) {
        RuleConstraint constraint = RuleConstraint.builder()
                .ruleId(RULE_ID)
                .condition("element.attrs['src'] != null AND element.attrs['srcExp'] != null")
                .message("src and srcExp cannot coexist")
                .severity(DiagnosticSeverity.ERROR)
                .build();
        return ConstraintVerificationRequest.builder()
                .targetElement("Image")
                .constraint(constraint)
                .positiveFixturePath("fixtures/SEM-IMG-900/positive.xml")
                .positiveFixtureContent(positive)
                .negativeFixturePath("fixtures/SEM-IMG-900/negative.xml")
                .negativeFixtureContent(negative)
                .evidenceCandidateIds(List.of("candidate-1"))
                .build();
    }
}
