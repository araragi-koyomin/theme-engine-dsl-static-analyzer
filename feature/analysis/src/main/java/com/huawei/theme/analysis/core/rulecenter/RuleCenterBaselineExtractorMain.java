package com.huawei.theme.analysis.core.rulecenter;

import java.nio.file.Path;
import java.util.Map;

public final class RuleCenterBaselineExtractorMain {
    private RuleCenterBaselineExtractorMain() {
    }

    public static void main(String[] args) {
        Map<String, String> env = System.getenv();
        Path result = new RulePackageZipExtractor().extractAndValidate(
                Path.of(required(env, "RULE_CENTER_BASELINE_ZIP")),
                Path.of(required(env, "RULE_CENTER_BASELINE_OUTPUT")));
        System.out.println("baseline_gate=passed");
        System.out.println("baseline_directory=" + result.toAbsolutePath().normalize());
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be configured");
        }
        return value;
    }
}
