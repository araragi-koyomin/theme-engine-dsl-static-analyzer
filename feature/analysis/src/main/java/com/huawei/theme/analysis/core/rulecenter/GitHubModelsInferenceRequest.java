package com.huawei.theme.analysis.core.rulecenter;

import com.google.gson.JsonObject;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GitHubModelsInferenceRequest {
    String model;
    double temperature;
    int seed;
    String systemPrompt;
    String userPrompt;
    JsonObject responseFormat;
}
