package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulecenter.model.ValidationFailure;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubModelsConstraintRepairStrategyTest {

    @Test
    void changesOnlyConditionAndFixturesWhileKeepingEvidenceTargetAndRuleIdentity() {
        CapturingClient client = new CapturingClient();
        GitHubModelsConstraintRepairStrategy strategy =
                new GitHubModelsConstraintRepairStrategy(client, "openai/gpt-4.1", "repair-v1");
        ConstraintRepairProposal current = ConstraintRepairProposal.builder()
                .sourceEvidenceFingerprint("evidence")
                .targetFingerprint("target")
                .verificationRequest(ConstraintVerificationRequest.builder()
                        .targetElement("Image")
                        .constraint(RuleConstraint.builder()
                                .ruleId("SEM-IMG-901")
                                .condition("bad")
                                .message("message")
                                .severity(DiagnosticSeverity.ERROR)
                                .build())
                        .positiveFixturePath("positive.xml")
                        .positiveFixtureContent("bad-positive")
                        .negativeFixturePath("negative.xml")
                        .negativeFixtureContent("bad-negative")
                        .evidenceCandidateIds(List.of("candidate"))
                        .build())
                .build();

        ConstraintRepairProposal repaired = strategy.repair(ConstraintRepairContext.builder()
                .candidateId("candidate")
                .attempt(1)
                .currentProposal(current)
                .validationFailure(ValidationFailure.POSITIVE_FIXTURE_MISSED)
                .examples(List.of())
                .build());

        assertEquals("evidence", repaired.getSourceEvidenceFingerprint());
        assertEquals("target", repaired.getTargetFingerprint());
        assertEquals("SEM-IMG-901", repaired.getVerificationRequest()
                .getConstraint().getRuleId());
        assertEquals("Image", repaired.getVerificationRequest().getTargetElement());
        assertEquals(List.of("candidate"), repaired.getVerificationRequest()
                .getEvidenceCandidateIds());
        assertEquals(0.0, client.request.getTemperature());
        assertEquals(42, client.request.getSeed());
        assertTrue(client.request.getResponseFormat().toString()
                .contains("negativeFixture"));
    }

    private static final class CapturingClient implements GitHubModelsInferenceClient {
        private GitHubModelsInferenceRequest request;

        @Override
        public GitHubModelsInferenceResponse infer(GitHubModelsInferenceRequest request) {
            this.request = request;
            return GitHubModelsInferenceResponse.builder()
                    .actualModel(request.getModel())
                    .content("{\"condition\":\"element.attrs['src'] != null\","
                            + "\"positiveFixture\":\"<Image src='a'/>\","
                            + "\"negativeFixture\":\"<Image/>\"}")
                    .build();
        }
    }
}
