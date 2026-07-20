package com.huawei.theme.analysis.core.rulecenter;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConditionCapabilityRegistry {
    private static final String TRUE_COMPARISON = "'1'=='1'";
    private static final Pattern CHILDREN_TAG_COUNT = Pattern.compile(
            "element\\.children\\.(?:filter|where)\\(c\\s*->\\s*c\\.tagName\\s*==\\s*'[^']+'\\)"
                    + "\\.size\\(\\)\\s*(?:>=|<=|==|!=|>|<)\\s*\\d+");
    private static final Pattern CONTAINS_EXPRESSION = Pattern.compile(
            "containsExpression\\(\\s*element\\.attrs\\[\\s*'[^']+'\\s*]\\s*\\)");
    private static final Pattern FUNCTION_LIKE = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*\\s*\\(");

    public Set<ConditionCapability> registeredCapabilities() {
        return Set.of(
                ConditionCapability.BASE_GRAMMAR,
                ConditionCapability.CONTAINS_EXPRESSION,
                ConditionCapability.CHILDREN_TAG_COUNT);
    }

    public ConditionCapabilityAnalysis analyze(String condition) {
        if (condition == null || condition.isEmpty()) {
            return rejected();
        }

        EnumSet<ConditionCapability> capabilities = EnumSet.of(ConditionCapability.BASE_GRAMMAR);
        String normalized = replaceRegistered(
                condition,
                CHILDREN_TAG_COUNT,
                ConditionCapability.CHILDREN_TAG_COUNT,
                capabilities);
        normalized = replaceRegistered(
                normalized,
                CONTAINS_EXPRESSION,
                ConditionCapability.CONTAINS_EXPRESSION,
                capabilities);

        if (hasUnregisteredFunction(normalized)
                || normalized.contains("children.")
                || normalized.contains("containsExpression")) {
            return rejected();
        }
        return ConditionCapabilityAnalysis.builder()
                .extensionShapeSupported(true)
                .normalizedCondition(normalized)
                .capabilities(Set.copyOf(capabilities))
                .build();
    }

    private String replaceRegistered(
            String condition,
            Pattern pattern,
            ConditionCapability capability,
            Set<ConditionCapability> capabilities) {
        Matcher matcher = pattern.matcher(condition);
        if (!matcher.find()) {
            return condition;
        }
        capabilities.add(capability);
        return matcher.replaceAll(Matcher.quoteReplacement(TRUE_COMPARISON));
    }

    private boolean hasUnregisteredFunction(String condition) {
        Matcher matcher = FUNCTION_LIKE.matcher(condition);
        while (matcher.find()) {
            String token = matcher.group();
            String name = token.substring(0, token.indexOf('(')).trim();
            if (!"AND".equals(name) && !"OR".equals(name) && !"NOT".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private ConditionCapabilityAnalysis rejected() {
        return ConditionCapabilityAnalysis.builder()
                .extensionShapeSupported(false)
                .capabilities(Set.of())
                .rejection(ConditionCapabilityRejection.UNREGISTERED_EXTENSION)
                .build();
    }
}
