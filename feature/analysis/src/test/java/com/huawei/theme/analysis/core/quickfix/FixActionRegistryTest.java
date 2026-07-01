package com.huawei.theme.analysis.core.quickfix;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixActionRegistryTest {

    @AfterEach
    void clearRegistry() {
        FixActionRegistry.clear();
    }

    @Test
    void registerAndGetGenerator() {
        FixActionGenerator gen = stub("SEM-REF-001");
        FixActionRegistry.register(gen);
        Optional<FixActionGenerator> found = FixActionRegistry.getGenerator("SEM-REF-001");
        assertTrue(found.isPresent());
        assertSame(gen, found.get());
    }

    @Test
    void unregisteredRuleIdReturnsEmpty() {
        assertTrue(FixActionRegistry.getGenerator("UNKNOWN-999").isEmpty());
    }

    @Test
    void duplicateRegisterOverwrites() {
        FixActionGenerator first = stub("SEM-X-001");
        FixActionGenerator second = stub("SEM-X-001");
        FixActionRegistry.register(first);
        FixActionRegistry.register(second);
        assertSame(second, FixActionRegistry.getGenerator("SEM-X-001").get());
    }

    @Test
    void clearRemovesAllGenerators() {
        FixActionRegistry.register(stub("SEM-CLEAR-001"));
        FixActionRegistry.clear();
        assertTrue(FixActionRegistry.getGenerator("SEM-CLEAR-001").isEmpty());
    }

    private static FixActionGenerator stub(String ruleId) {
        return new FixActionGenerator() {
            @Override
            public String getRuleId() {
                return ruleId;
            }

            @Override
            public List<FixAction> generate(Diagnostic diagnostic) {
                return Collections.emptyList();
            }
        };
    }
}
