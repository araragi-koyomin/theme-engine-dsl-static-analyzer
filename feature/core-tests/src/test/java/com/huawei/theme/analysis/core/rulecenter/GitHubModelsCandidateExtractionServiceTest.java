package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.huawei.theme.analysis.core.rulecenter.model.CandidateStatus;
import com.huawei.theme.analysis.core.rulecenter.model.RuleCandidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubModelsCandidateExtractionServiceTest {

    @Test
    void sendsDeterministicJsonSchemaRequestAndReturnsOnlyExtractedCandidates() {
        CapturingClient client = new CapturingClient(response(candidateJson(
                "extracted", 3, 3, "src is the image path", "image path")));
        GitHubModelsCandidateExtractionService service = service(client);

        CandidateExtractionResult result = service.extract(request());

        assertEquals(1, result.getCandidates().size());
        assertEquals(CandidateStatus.EXTRACTED, result.getCandidates().get(0).getStatus());
        assertEquals("openai/gpt-4.1", client.request.getModel());
        assertEquals(0.0, client.request.getTemperature());
        assertEquals(42, client.request.getSeed());
        assertEquals("json_schema", client.request.getResponseFormat().get("type").getAsString());
        assertTrue(client.request.getResponseFormat().toString().contains("sourceEvidence"));
        assertTrue(client.request.getResponseFormat().toString().contains("staticTextOnly"));
        assertTrue(client.request.getResponseFormat().toString().contains("positiveFixture"));
        String schema = client.request.getResponseFormat().toString();
        assertTrue(schema.contains("elementAttribute"));
        assertTrue(schema.contains("\"element\""));
        assertFalse(schema.contains("functionSignature"));
        assertFalse(schema.contains("globalVariable"));
        assertFalse(schema.contains("ruleSource"));
        assertFalse(schema.contains("parentChildRelation"));
        assertTrue(client.request.getSystemPrompt().contains("must prove"));
        assertTrue(client.request.getUserPrompt().contains("1: # Image"));
        assertTrue(client.request.getUserPrompt().contains("3: src is the image path"));
        assertEquals("md-to-rule-v1", result.getPromptVersion());
        assertEquals(64, result.getPromptSha256().length());
        assertEquals(64, result.getDocumentSha256().length());
        assertEquals(64, result.getRawResponseSha256().length());
        assertEquals("openai/gpt-4.1-2025-04-14", result.getActualModel());
    }

    @Test
    void rejectsMissingEvidenceButDeterministicallyRelocatesUniqueExactExcerpt() {
        CapturingClient missingEvidence = new CapturingClient(response(candidateJson(
                "extracted", 3, 3, "", "image path")));
        CapturingClient outOfBounds = new CapturingClient(response(candidateJson(
                "extracted", 30, 31, "src is the image path", "image path")));

        assertThrows(CandidateExtractionException.class,
                () -> service(missingEvidence).extract(request()));
        CandidateExtractionResult relocated = service(outOfBounds).extract(request());
        assertEquals(3, relocated.getCandidates().get(0)
                .getSourceEvidence().getLocation().getStartLine());
    }

    @Test
    void rejectsModelAttemptToBypassGateWithPublishedStatus() {
        CapturingClient client = new CapturingClient(response(candidateJson(
                "published", 3, 3, "src is the image path", "image path")));

        CandidateExtractionException error = assertThrows(
                CandidateExtractionException.class,
                () -> service(client).extract(request()));

        assertTrue(error.getMessage().contains("EXTRACTED"));
    }

    @Test
    void rejectsClaimWhoseQuotedEvidenceDoesNotExistInReferencedMarkdownLines() {
        CapturingClient client = new CapturingClient(response(candidateJson(
                "extracted", 3, 3,
                "transparent videos must be mp4 and shorter than 30 seconds",
                "mp4 only")));

        CandidateExtractionException error = assertThrows(
                CandidateExtractionException.class,
                () -> service(client).extract(request()));

        assertTrue(error.getMessage().contains("evidence"));
    }

    @Test
    void recoversExactSourceLineWhenModelDropsInlineMarkdownBackticks() {
        String markdown = "# Image\n\n`src` 与 `srcExp` 不能同时设置。";
        String candidate = candidateJson(
                "extracted", 1, 1, "src 与 srcExp 不能同时设置。", "description");
        CandidateExtractionRequest request = CandidateExtractionRequest.builder()
                .documentId("image")
                .documentRevision("r42")
                .markdown(markdown)
                .examples(List.of())
                .build();

        RuleCandidate recovered = service(new CapturingClient(response(candidate)))
                .extract(request).getCandidates().get(0);

        assertEquals(3, recovered.getSourceEvidence().getLocation().getStartLine());
        assertEquals("`src` 与 `srcExp` 不能同时设置。",
                recovered.getSourceEvidence().getExcerpt());
    }

    @Test
    void rejectsDuplicateCandidateIdsAndDocumentIdentityMismatch() {
        String duplicate = response(candidateJson(
                "extracted", 3, 3, "src is the image path", "image path"),
                candidateJson("extracted", 3, 3, "src is the image path", "image path"));
        String wrongDocument = response(candidateJson(
                "extracted", 3, 3, "src is the image path", "image path")
                .replace("\"documentId\":\"image\"", "\"documentId\":\"video\""));

        assertThrows(CandidateExtractionException.class,
                () -> service(new CapturingClient(duplicate)).extract(request()));
        assertThrows(CandidateExtractionException.class,
                () -> service(new CapturingClient(wrongDocument)).extract(request()));
    }

    @Test
    void rejectsTargetKindsThatTheP1PackageApplierCannotApply() {
        String unsupportedTarget = response(candidateJson(
                "extracted", 3, 3, "src is the image path", "image path")
                .replace("elementAttribute", "functionSignature"));

        CandidateExtractionException error = assertThrows(
                CandidateExtractionException.class,
                () -> service(new CapturingClient(unsupportedTarget)).extract(request()));

        assertTrue(error.getMessage().contains("target kind"));
    }

    private GitHubModelsCandidateExtractionService service(CapturingClient client) {
        return new GitHubModelsCandidateExtractionService(
                client, "openai/gpt-4.1", "md-to-rule-v1");
    }

    private CandidateExtractionRequest request() {
        return CandidateExtractionRequest.builder()
                .documentId("image")
                .documentRevision("r42")
                .markdown("# Image\n\n"
                        + "src is the image path\n\n"
                        + "The document may omit standard sections.")
                .examples(List.of())
                .build();
    }

    private String response(String... candidates) {
        return "{\"candidates\":[" + String.join(",", candidates) + "]}";
    }

    private String candidateJson(
            String status,
            int startLine,
            int endLine,
            String excerpt,
            String value) {
        return "{"
                + "\"candidateId\":\"candidate-1\","
                + "\"documentId\":\"image\",\"documentRevision\":\"r42\","
                + "\"sourceEvidence\":{\"sectionPath\":[\"参数说明\",\"src\"],"
                + "\"location\":{\"startLine\":" + startLine
                + ",\"endLine\":" + endLine + "},"
                + "\"excerpt\":\"" + excerpt + "\"},"
                + "\"target\":{\"kind\":\"elementAttribute\","
                + "\"element\":\"Image\",\"attribute\":\"src\"},"
                + "\"proposedKind\":\"description\","
                + "\"proposedChange\":{\"field\":\"attrTypes.src.description\","
                + "\"value\":\"" + value + "\"},"
                + "\"status\":\"" + status + "\","
                + "\"skipReason\":null,\"validationFailure\":null}";
    }

    private static final class CapturingClient implements GitHubModelsInferenceClient {
        private final String content;
        private GitHubModelsInferenceRequest request;

        private CapturingClient(String content) {
            this.content = content;
        }

        @Override
        public GitHubModelsInferenceResponse infer(GitHubModelsInferenceRequest request) {
            this.request = request;
            JsonObject parsed = JsonParser.parseString(content).getAsJsonObject();
            assertFalse(parsed.getAsJsonArray("candidates").isEmpty());
            return GitHubModelsInferenceResponse.builder()
                    .actualModel("openai/gpt-4.1-2025-04-14")
                    .content(content)
                    .build();
        }
    }
}
