package com.huawei.theme.analysis.core.rulecenter;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GitHubReleaseAsset {
    String name;
    String downloadUrl;
    String state;
    String digest;
    String content;
}
