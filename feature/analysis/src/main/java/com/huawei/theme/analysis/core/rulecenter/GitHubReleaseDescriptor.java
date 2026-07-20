package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GitHubReleaseDescriptor {
    String tagName;
    boolean draft;
    boolean prerelease;
    String publishedAt;
    String body;
    List<GitHubReleaseAsset> assets;
}
