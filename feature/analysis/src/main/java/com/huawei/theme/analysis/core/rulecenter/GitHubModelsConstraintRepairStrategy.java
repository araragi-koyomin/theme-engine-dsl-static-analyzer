package com.huawei.theme.analysis.core.rulecenter;

import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;

public class GitHubModelsConstraintRepairStrategy implements ConstraintRepairStrategy {
    private final GitHubModelsInferenceClient inferenceClient;
    private final String model;
    private final String promptVersion;
    private final Gson gson = new Gson();

    public GitHubModelsConstraintRepairStrategy(
            GitHubModelsInferenceClient inferenceClient,
            String model,
            String promptVersion) {
        this.inferenceClient = Objects.requireNonNull(inferenceClient);
        this.model = requireText(model, "model");
        this.promptVersion = requireText(promptVersion, "promptVersion");
    }

    @Override
    public ConstraintRepairProposal repair(ConstraintRepairContext context) {
        ConstraintVerificationRequest current = context.getCurrentProposal()
                .getVerificationRequest();
        String systemPrompt = "Prompt version: " + promptVersion + "\n"
                + "Repair only the condition and two DSL-text fixtures. Preserve the rule id, "
                + "source evidence, target element, and evidence candidate ids. Never inspect or "
                + "infer files, URLs, media metadata, resource size, duration, or engine behavior.";
        String userPrompt = "attempt=" + context.getAttempt() + "\n"
                + "validationFailure=" + context.getValidationFailure() + "\n"
                + "current=" + gson.toJson(current) + "\n"
                + "verifiedSyntaxExamples=" + gson.toJson(context.getExamples());
        GitHubModelsInferenceResponse response = inferenceClient.infer(
                GitHubModelsInferenceRequest.builder()
                        .model(model)
                        .temperature(0.0)
                        .seed(42)
                        .systemPrompt(systemPrompt)
                        .userPrompt(userPrompt)
                        .responseFormat(responseFormat())
                        .build());
        JsonObject repaired = JsonParser.parseString(response.getContent()).getAsJsonObject();
        RuleConstraint prior = current.getConstraint();
        RuleConstraint constraint = RuleConstraint.builder()
                .ruleId(prior.getRuleId())
                .condition(requiredString(repaired, "condition"))
                .message(prior.getMessage())
                .severity(prior.getSeverity())
                .suggestedFixes(prior.getSuggestedFixes())
                .build();
        return ConstraintRepairProposal.builder()
                .sourceEvidenceFingerprint(context.getCurrentProposal()
                        .getSourceEvidenceFingerprint())
                .targetFingerprint(context.getCurrentProposal().getTargetFingerprint())
                .verificationRequest(ConstraintVerificationRequest.builder()
                        .targetElement(current.getTargetElement())
                        .constraint(constraint)
                        .positiveFixturePath(current.getPositiveFixturePath())
                        .positiveFixtureContent(requiredString(repaired, "positiveFixture"))
                        .negativeFixturePath(current.getNegativeFixturePath())
                        .negativeFixtureContent(requiredString(repaired, "negativeFixture"))
                        .evidenceCandidateIds(current.getEvidenceCandidateIds())
                        .build())
                .build();
    }

    private JsonObject responseFormat() {
        JsonObject string = new JsonObject();
        string.addProperty("type", "string");
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);
        JsonObject properties = new JsonObject();
        properties.add("condition", string.deepCopy());
        properties.add("positiveFixture", string.deepCopy());
        properties.add("negativeFixture", string.deepCopy());
        schema.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("condition");
        required.add("positiveFixture");
        required.add("negativeFixture");
        schema.add("required", required);
        JsonObject jsonSchema = new JsonObject();
        jsonSchema.addProperty("name", "dsl_constraint_repair");
        jsonSchema.addProperty("strict", true);
        jsonSchema.add("schema", schema);
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_schema");
        responseFormat.add("json_schema", jsonSchema);
        return responseFormat;
    }

    private String requiredString(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()
                || !object.get(field).getAsJsonPrimitive().isString()
                || object.get(field).getAsString().isEmpty()) {
            throw new CandidateExtractionException("repair response lacks " + field);
        }
        return object.get(field).getAsString();
    }

    private String requireText(String value, String field) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return value;
    }
}
