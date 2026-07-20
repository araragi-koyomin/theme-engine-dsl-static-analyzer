package com.huawei.theme.analysis.core.rulecenter;

import java.util.Arrays;

public final class RulePackageArtifact {
    private final String packageVersion;
    private final String contentSha256;
    private final String artifactSha256;
    private final byte[] bytes;

    public RulePackageArtifact(
            String packageVersion,
            String contentSha256,
            String artifactSha256,
            byte[] bytes) {
        this.packageVersion = packageVersion;
        this.contentSha256 = contentSha256;
        this.artifactSha256 = artifactSha256;
        this.bytes = Arrays.copyOf(bytes, bytes.length);
    }

    public String getPackageVersion() {
        return packageVersion;
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public String getArtifactSha256() {
        return artifactSha256;
    }

    public byte[] getBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
