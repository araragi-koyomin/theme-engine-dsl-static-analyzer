package com.huawei.theme.analysis.core.rulecenter;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GitHubReleaseEvaluation {
    boolean approved;
    GitHubReleaseRejection rejection;
    GitHubApprovedRelease release;
}
