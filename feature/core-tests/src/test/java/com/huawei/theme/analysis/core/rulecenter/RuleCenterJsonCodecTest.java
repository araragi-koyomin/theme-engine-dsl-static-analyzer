package com.huawei.theme.analysis.core.rulecenter;

import com.google.gson.JsonParseException;
import com.huawei.theme.analysis.core.rulecenter.model.CandidateStatus;
import com.huawei.theme.analysis.core.rulecenter.model.ConstraintVerification;
import com.huawei.theme.analysis.core.rulecenter.model.ConversionStatus;
import com.huawei.theme.analysis.core.rulecenter.model.DocumentConversionFeedback;
import com.huawei.theme.analysis.core.rulecenter.model.RuleCandidate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleCenterJsonCodecTest {

    private final RuleCenterJsonCodec codec = new RuleCenterJsonCodec();

    @Test
    void deserializesPublishedSkippedAndValidationErrorCandidatesWithExactEvidence() {
        RuleCandidate published = codec.readRuleCandidate(candidateJson("published", null, null));
        RuleCandidate skipped = codec.readRuleCandidate(candidateJson(
                "skipped", "OUT_OF_STATIC_SCOPE", null));
        RuleCandidate validationError = codec.readRuleCandidate(candidateJson(
                "validation-error", null, "POSITIVE_FIXTURE_MISSED"));

        assertEquals(CandidateStatus.PUBLISHED, published.getStatus());
        assertEquals(51, published.getSourceEvidence().getLocation().getStartLine());
        assertEquals("透明视频必须使用 mp4", published.getSourceEvidence().getExcerpt());
        assertEquals("OUT_OF_STATIC_SCOPE", skipped.getSkipReason().name());
        assertEquals(CandidateStatus.VALIDATION_ERROR, validationError.getStatus());
        assertEquals("POSITIVE_FIXTURE_MISSED", validationError.getValidationFailure().name());
    }

    @Test
    void rejectsCandidateWithoutEvidenceOrWithIllegalStatus() {
        String missingEvidence = candidateJson("published", null, null)
                .replace("\"sourceEvidence\": {", "\"ignoredEvidence\": {");
        String illegalStatus = candidateJson("silently-accepted", null, null);

        JsonParseException missing = assertThrows(
                JsonParseException.class, () -> codec.readRuleCandidate(missingEvidence));
        JsonParseException illegal = assertThrows(
                JsonParseException.class, () -> codec.readRuleCandidate(illegalStatus));

        assertTrue(missing.getMessage().contains("sourceEvidence"));
        assertTrue(illegal.getMessage().contains("status"));
    }

    @Test
    void rejectsSkippedCandidateWithoutReasonAndValidationErrorWithoutFailure() {
        JsonParseException skipped = assertThrows(
                JsonParseException.class,
                () -> codec.readRuleCandidate(candidateJson("skipped", null, null)));
        JsonParseException validation = assertThrows(
                JsonParseException.class,
                () -> codec.readRuleCandidate(candidateJson("validation-error", null, null)));

        assertTrue(skipped.getMessage().contains("skipReason"));
        assertTrue(validation.getMessage().contains("validationFailure"));
    }

    @Test
    void roundTripsPassedVerificationAndRejectsFalsePositiveNegativeFixture() {
        String passedJson = """
                {
                  "ruleId": "SEM-IMG-002",
                  "condition": "element.attrs['src'] != null AND element.attrs['srcExp'] != null",
                  "parserAccepted": true,
                  "positiveFixture": "fixtures/SEM-IMG-002/positive.xml",
                  "negativeFixture": "fixtures/SEM-IMG-002/negative.xml",
                  "positiveObservedRuleIds": ["SEM-IMG-002"],
                  "negativeObservedRuleIds": [],
                  "evidenceCandidateIds": ["cand-img-src-001"],
                  "status": "passed"
                }
                """;

        ConstraintVerification verification = codec.readConstraintVerification(passedJson);
        ConstraintVerification roundTripped = codec.readConstraintVerification(codec.write(verification));
        String falsePositiveJson = passedJson.replace(
                "\"negativeObservedRuleIds\": []",
                "\"negativeObservedRuleIds\": [\"SEM-IMG-002\"]");

        assertEquals("SEM-IMG-002", roundTripped.getRuleId());
        assertTrue(roundTripped.isParserAccepted());
        JsonParseException falsePositive = assertThrows(
                JsonParseException.class,
                () -> codec.readConstraintVerification(falsePositiveJson));
        assertTrue(falsePositive.getMessage().contains("negativeObservedRuleIds"));
    }

    @Test
    void readsActionableDocumentFeedbackAndRejectsItemWithoutSourceLines() {
        String feedbackJson = """
                {
                  "documentId": "image",
                  "documentRevision": "r42",
                  "conversionStatus": "PUBLISHED_WITH_SKIPS",
                  "releaseVersion": "2026.07.20.1",
                  "summary": {"published": 1, "descriptionOnly": 1, "skipped": 1},
                  "items": [{
                    "sourceEvidence": {
                      "sectionPath": ["参数说明", "src"],
                      "location": {"startLine": 51, "endLine": 53},
                      "excerpt": "视频不得超过 30 秒"
                    },
                    "outcome": "SKIPPED",
                    "reasonCode": "OUT_OF_STATIC_SCOPE",
                    "authorAction": "NONE"
                  }]
                }
                """;

        DocumentConversionFeedback feedback = codec.readDocumentConversionFeedback(feedbackJson);
        String missingLines = feedbackJson.replace(
                "\"location\": {\"startLine\": 51, \"endLine\": 53},",
                "\"location\": {},");

        assertEquals(ConversionStatus.PUBLISHED_WITH_SKIPS, feedback.getConversionStatus());
        assertEquals(1, feedback.getSummary().getSkipped());
        assertEquals("OUT_OF_STATIC_SCOPE", feedback.getItems().get(0).getReasonCode().name());
        JsonParseException missing = assertThrows(
                JsonParseException.class,
                () -> codec.readDocumentConversionFeedback(missingLines));
        assertTrue(missing.getMessage().contains("startLine"));
    }

    private String candidateJson(String status, String skipReason, String validationFailure) {
        String skip = skipReason == null ? "null" : "\"" + skipReason + "\"";
        String failure = validationFailure == null ? "null" : "\"" + validationFailure + "\"";
        return """
                {
                  "candidateId": "cand-img-src-001",
                  "documentId": "image",
                  "documentRevision": "r42",
                  "sourceEvidence": {
                    "sectionPath": ["参数说明", "src"],
                    "location": {"startLine": 51, "endLine": 53},
                    "excerpt": "透明视频必须使用 mp4"
                  },
                  "target": {
                    "kind": "elementAttribute",
                    "element": "Image",
                    "attribute": "src"
                  },
                  "proposedKind": "description",
                  "proposedChange": {
                    "field": "attrTypes.src.description",
                    "value": "透明视频说明"
                  },
                  "status": "%s",
                  "skipReason": %s,
                  "validationFailure": %s
                }
                """.formatted(status, skip, failure);
    }
}
