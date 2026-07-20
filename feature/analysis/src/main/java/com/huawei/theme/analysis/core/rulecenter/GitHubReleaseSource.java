package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;

public interface GitHubReleaseSource {
    List<GitHubReleaseDescriptor> listReleases();

    byte[] download(String packageDownloadUrl);
}
