package com.huawei.theme.analysis.core.quickfix;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.huawei.theme.analysis.core.quickfix.generators.ClampValueGenerator;
import com.huawei.theme.analysis.core.quickfix.generators.ConstraintFixGenerator;
import com.huawei.theme.analysis.core.quickfix.generators.FixExpressionGenerator;
import com.huawei.theme.analysis.core.quickfix.generators.InsertAttrGenerator;
import com.huawei.theme.analysis.core.quickfix.generators.RemoveAttrGenerator;
import com.huawei.theme.analysis.core.quickfix.generators.ReplaceEnumGenerator;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;

public class FixActionRegistry {
    private FixActionRegistry() {}

    private static final Map<String, FixActionGenerator> generators = new HashMap<>();
    private static boolean initialized = false;
    private static FixActionGenerator fallbackGenerator;

    public static void init(RuleRepository ruleRepository) {
        if (initialized) {
            return;
        }
        initialized = true;
        register(new InsertAttrGenerator(ruleRepository));
        register(new ReplaceEnumGenerator(ruleRepository));
        register(new ClampValueGenerator());
        register(new FixExpressionGenerator());
        register(new RemoveAttrGenerator());
        setFallback(new ConstraintFixGenerator());
    }

    public static void register(FixActionGenerator generator) {
        generators.put(generator.getRuleId(), generator);
    }

    public static void setFallback(FixActionGenerator generator) {
        fallbackGenerator = generator;
    }

    public static Optional<FixActionGenerator> getGenerator(String ruleId) {
        FixActionGenerator exact = generators.get(ruleId);
        if (exact != null) {
            return Optional.of(exact);
        }
        return Optional.ofNullable(fallbackGenerator);
    }

    static void clear() {
        generators.clear();
        fallbackGenerator = null;
        initialized = false;
    }
}
