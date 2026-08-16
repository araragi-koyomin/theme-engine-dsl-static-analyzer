package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class GitHubModelsHttpInferenceClient implements GitHubModelsInferenceClient {
    public static final String DEFAULT_ENDPOINT =
            "https://models.github.ai/inference/chat/completions";

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String token;
    private final Gson gson = new Gson();

    public GitHubModelsHttpInferenceClient(String token) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(),
                URI.create(DEFAULT_ENDPOINT), token);
    }

    public GitHubModelsHttpInferenceClient(HttpClient httpClient, URI endpoint, String token) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.endpoint = Objects.requireNonNull(endpoint);
        this.token = requireToken(token);
    }

    @Override
    public GitHubModelsInferenceResponse infer(GitHubModelsInferenceRequest request) {
        JsonObject body = new JsonObject();
        body.addProperty("model", request.getModel());
        body.addProperty("temperature", request.getTemperature());
        body.addProperty("seed", request.getSeed());
        body.addProperty("stream", false);
        JsonArray messages = new JsonArray();
        messages.add(message("system", request.getSystemPrompt()));
        messages.add(message("user", request.getUserPrompt()));
        body.add("messages", messages);
        body.add("response_format", request.getResponseFormat());

        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(90))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2026-03-10")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new CandidateExtractionException(
                        "GitHub Models inference failed with HTTP " + response.statusCode());
            }
            JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
            String actualModel = responseJson.has("model")
                    ? responseJson.get("model").getAsString() : request.getModel();
            String content = responseJson.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
            return GitHubModelsInferenceResponse.builder()
                    .actualModel(actualModel)
                    .content(content)
                    .build();
        } catch (IOException exception) {
            throw new CandidateExtractionException("GitHub Models request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CandidateExtractionException("GitHub Models request interrupted", exception);
        }
    }

    private JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private String requireToken(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("GitHub Models token must not be empty");
        }
        return value;
    }
}
