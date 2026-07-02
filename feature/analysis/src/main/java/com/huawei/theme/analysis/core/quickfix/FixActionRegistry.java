package com.huawei.theme.analysis.core.quickfix;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FixActionRegistry {
    private FixActionRegistry() {}

    private static final Map<String, FixActionGenerator> generators = new HashMap<>();

    public static void register(FixActionGenerator generator) {
        generators.put(generator.getRuleId(), generator);
    }

    public static Optional<FixActionGenerator> getGenerator(String ruleId) {
        return Optional.ofNullable(generators.get(ruleId));
    }

    static void clear() {
        generators.clear();
    }
}
