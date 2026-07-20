package com.huawei.theme.analysis.core.rulecenter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.huawei.theme.analysis.core.rulecenter.model.CandidateStatus;
import com.huawei.theme.analysis.core.rulecenter.model.RuleCandidate;

public class GitHubModelsCandidateExtractionService {
    private static final int SEED = 42;
    private static final double TEMPERATURE = 0.0;

    private final GitHubModelsInferenceClient inferenceClient;
    private final String model;
    private final String promptVersion;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final RuleCenterJsonCodec candidateCodec = new RuleCenterJsonCodec();

    public GitHubModelsCandidateExtractionService(
            GitHubModelsInferenceClient inferenceClient,
            String model,
            String promptVersion) {
        this.inferenceClient = Objects.requireNonNull(inferenceClient);
        this.model = requireText(model, "model");
        this.promptVersion = requireText(promptVersion, "promptVersion");
    }

    public CandidateExtractionResult extract(CandidateExtractionRequest request) {
        validateRequest(request);
        String systemPrompt = systemPrompt();
        String userPrompt = userPrompt(request);
        GitHubModelsInferenceResponse response = inferenceClient.infer(
                GitHubModelsInferenceRequest.builder()
                        .model(model)
                        .temperature(TEMPERATURE)
                        .seed(SEED)
                        .systemPrompt(systemPrompt)
                        .userPrompt(userPrompt)
                        .responseFormat(responseFormat())
                        .build());
        List<RuleCandidate> candidates = parseAndValidate(request, response.getContent());
        return CandidateExtractionResult.builder()
                .candidates(candidates)
                .requestedModel(model)
                .actualModel(response.getActualModel())
                .promptVersion(promptVersion)
                .promptSha256(sha256(systemPrompt + "\n" + userPrompt))
                .documentSha256(sha256(request.getMarkdown()))
                .rawResponseSha256(sha256(response.getContent()))
                .build();
    }

    private List<RuleCandidate> parseAndValidate(
            CandidateExtractionRequest request,
            String content) {
        try {
            CandidateEnvelope envelope = gson.fromJson(content, CandidateEnvelope.class);
            if (envelope == null || envelope.candidates == null) {
                throw new CandidateExtractionException("model response has no candidates array");
            }
            Set<String> ids = new HashSet<>();
            List<RuleCandidate> validated = new ArrayList<>();
            for (RuleCandidate candidate : envelope.candidates) {
                RuleCandidate strict = candidateCodec.readRuleCandidate(gson.toJson(candidate));
                validateCandidate(request, strict, ids);
                validated.add(strict);
            }
            return List.copyOf(validated);
        } catch (JsonParseException exception) {
            throw new CandidateExtractionException("invalid structured model response", exception);
        }
    }

    private void validateCandidate(
            CandidateExtractionRequest request,
            RuleCandidate candidate,
            Set<String> ids) {
        if (candidate.getStatus() != CandidateStatus.EXTRACTED) {
            throw new CandidateExtractionException("model candidates must remain EXTRACTED");
        }
        if (!request.getDocumentId().equals(candidate.getDocumentId())
                || !request.getDocumentRevision().equals(candidate.getDocumentRevision())) {
            throw new CandidateExtractionException("candidate document identity mismatch");
        }
        if (!ids.add(candidate.getCandidateId())) {
            throw new CandidateExtractionException("duplicate candidateId: "
                    + candidate.getCandidateId());
        }
        RuleCandidate.SourceEvidence evidence = candidate.getSourceEvidence();
        int startLine = evidence.getLocation().getStartLine();
        int endLine = evidence.getLocation().getEndLine();
        String[] lines = request.getMarkdown().split("\\R", -1);
        if (startLine < 1 || endLine < startLine || endLine > lines.length) {
            throw new CandidateExtractionException("source evidence line range is out of bounds");
        }
        StringBuilder referenced = new StringBuilder();
        for (int line = startLine; line <= endLine; line++) {
            if (!referenced.isEmpty()) {
                referenced.append('\n');
            }
            referenced.append(lines[line - 1]);
        }
        if (evidence.getExcerpt().isEmpty()
                || !referenced.toString().contains(evidence.getExcerpt())) {
            throw new CandidateExtractionException(
                    "source evidence is not present in referenced markdown lines");
        }
    }

    private String systemPrompt() {
        return "Prompt version: " + promptVersion + "\n"
                + "Extract candidate changes from the entire approved DSL Markdown document. "
                + "Every candidate must quote exact source evidence and exact 1-based line numbers. "
                + "Never infer file existence, resource format, size, duration, runtime behavior, "
                + "or engine capability from a path/string attribute. Description text may retain "
                + "such documentation, but it must not become a static constraint. "
                + "Return status extracted only. Do not publish, skip, validate, or repair candidates. "
                + "Examples demonstrate condition syntax only and never transfer business semantics.";
    }

    private String userPrompt(CandidateExtractionRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("documentId: ").append(request.getDocumentId()).append('\n');
        prompt.append("documentRevision: ").append(request.getDocumentRevision()).append('\n');
        prompt.append("Verified syntax examples:\n");
        prompt.append(gson.toJson(request.getExamples())).append('\n');
        prompt.append("Full line-numbered Markdown:\n");
        String[] lines = request.getMarkdown().split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            prompt.append(index + 1).append(": ").append(lines[index]).append('\n');
        }
        return prompt.toString();
    }

    private JsonObject responseFormat() {
        JsonObject string = type("string");
        JsonObject nullableString = new JsonObject();
        JsonArray nullableTypes = new JsonArray();
        nullableTypes.add("string");
        nullableTypes.add("null");
        nullableString.add("type", nullableTypes);

        JsonObject location = objectSchema();
        location.add("properties", properties(
                "startLine", type("integer"),
                "endLine", type("integer")));
        location.add("required", strings("startLine", "endLine"));

        JsonObject evidence = objectSchema();
        JsonObject sectionPath = type("array");
        sectionPath.add("items", string.deepCopy());
        evidence.add("properties", properties(
                "sectionPath", sectionPath,
                "location", location,
                "excerpt", string.deepCopy()));
        evidence.add("required", strings("sectionPath", "location", "excerpt"));

        JsonObject target = objectSchema();
        target.add("properties", properties(
                "kind", enumSchema("element", "elementAttribute", "parentChildRelation",
                        "globalVariable", "functionSignature", "ruleSource"),
                "element", nullableString.deepCopy(),
                "attribute", nullableString.deepCopy()));
        target.add("required", strings("kind", "element", "attribute"));

        JsonObject proposedChange = objectSchema();
        JsonObject value = new JsonObject();
        JsonArray valueTypes = new JsonArray();
        valueTypes.add("string");
        valueTypes.add("object");
        value.add("type", valueTypes);
        proposedChange.add("properties", properties(
                "field", string.deepCopy(), "value", value));
        proposedChange.add("required", strings("field", "value"));

        JsonObject candidate = objectSchema();
        candidate.add("properties", properties(
                "candidateId", string.deepCopy(),
                "documentId", string.deepCopy(),
                "documentRevision", string.deepCopy(),
                "sourceEvidence", evidence,
                "target", target,
                "proposedKind", enumSchema("description", "constraint", "skipped"),
                "proposedChange", proposedChange,
                "status", enumSchema("extracted"),
                "skipReason", type("null"),
                "validationFailure", type("null")));
        candidate.add("required", strings(
                "candidateId", "documentId", "documentRevision", "sourceEvidence",
                "target", "proposedKind", "proposedChange", "status",
                "skipReason", "validationFailure"));

        JsonObject schema = objectSchema();
        JsonObject candidates = type("array");
        candidates.add("items", candidate);
        schema.add("properties", properties("candidates", candidates));
        schema.add("required", strings("candidates"));

        JsonObject jsonSchema = new JsonObject();
        jsonSchema.addProperty("name", "dsl_rule_candidates");
        jsonSchema.addProperty("strict", true);
        jsonSchema.add("schema", schema);
        JsonObject format = new JsonObject();
        format.addProperty("type", "json_schema");
        format.add("json_schema", jsonSchema);
        return format;
    }

    private JsonObject type(String type) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", type);
        return schema;
    }

    private JsonObject objectSchema() {
        JsonObject schema = type("object");
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    private JsonObject enumSchema(String... values) {
        JsonObject schema = type("string");
        schema.add("enum", strings(values));
        return schema;
    }

    private JsonObject properties(Object... entries) {
        JsonObject properties = new JsonObject();
        for (int index = 0; index < entries.length; index += 2) {
            properties.add((String) entries[index], (JsonObject) entries[index + 1]);
        }
        return properties;
    }

    private JsonArray strings(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void validateRequest(CandidateExtractionRequest request) {
        Objects.requireNonNull(request, "request");
        requireText(request.getDocumentId(), "documentId");
        requireText(request.getDocumentRevision(), "documentRevision");
        Objects.requireNonNull(request.getMarkdown(), "markdown");
        Objects.requireNonNull(request.getExamples(), "examples");
    }

    private String requireText(String value, String field) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return value;
    }

    private static final class CandidateEnvelope {
        private List<RuleCandidate> candidates;
    }
}
