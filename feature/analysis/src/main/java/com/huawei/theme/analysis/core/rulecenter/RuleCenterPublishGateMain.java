package com.huawei.theme.analysis.core.rulecenter;

import java.nio.file.Path;
import java.util.Map;

public final class RuleCenterPublishGateMain {
    private RuleCenterPublishGateMain() {
    }

    public static void main(String[] args) {
        Map<String, String> env = System.getenv();
        Path packageDirectory = Path.of(required(env, "RULE_CENTER_PACKAGE_DIRECTORY"));
        Path releaseDirectory = Path.of(required(env, "RULE_CENTER_RELEASE_OUTPUT"));
        ReleasePublicationResult result = new RulePackagePublicationPreparer()
                .prepare(packageDirectory, releaseDirectory);
        System.out.println("release_gate=passed");
        System.out.println("package_version=" + result.getPackageVersion());
        System.out.println("tag=" + result.getTagName());
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be configured");
        }
        return value;
    }
}
