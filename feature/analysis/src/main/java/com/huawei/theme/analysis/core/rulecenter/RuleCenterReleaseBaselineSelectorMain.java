package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class RuleCenterReleaseBaselineSelectorMain {
    private RuleCenterReleaseBaselineSelectorMain() {
    }

    public static void main(String[] args) throws IOException {
        Path tagsPath = Path.of(requiredEnvironment("RULE_CENTER_RELEASE_TAGS"));
        Path outputPath = Path.of(requiredEnvironment("RULE_CENTER_LATEST_TAG_OUTPUT"));
        String packageVersion = requiredEnvironment("RULE_CENTER_PACKAGE_VERSION");
        List<String> tags = Files.exists(tagsPath)
                ? Files.readAllLines(tagsPath, StandardCharsets.UTF_8)
                : List.of();
        String latestTag = ApprovedReleaseVersionSelector.selectLatestAndRequireNewer(
                tags, packageVersion);
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputPath, latestTag, StandardCharsets.UTF_8);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be configured");
        }
        return value;
    }
}
