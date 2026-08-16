package com.huawei.theme.analysis.core.rulecenter;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GitHubModelsInferenceResponse {
    String actualModel;
    String content;
}
