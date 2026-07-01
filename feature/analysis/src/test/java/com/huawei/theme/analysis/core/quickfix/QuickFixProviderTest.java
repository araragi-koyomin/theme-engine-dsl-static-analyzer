package com.huawei.theme.analysis.core.quickfix;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickFixProviderTest {

    @AfterEach
    void clearRegistry() {
        FixActionRegistry.clear();
    }

    @Test
    void batchOverloadAggregatesInOrder() {
        QuickFixProvider provider = diagnostic -> List.of(
                FixAction.builder()
                        .fixType("t-" + diagnostic.getRuleId())
                        .description(diagnostic.getMessage())
                        .build()
        );
        List<FixAction> all = provider.getFixActions(List.of(diag("R1", "m1"), diag("R2", "m2")));
        assertEquals(2, all.size());
        assertEquals("t-R1", all.get(0).getFixType());
        assertEquals("t-R2", all.get(1).getFixType());
        assertEquals("m1", all.get(0).getDescription());
        assertEquals("m2", all.get(1).getDescription());
    }

    @Test
    void batchOverloadEmptyOrNullInputReturnsEmpty() {
        QuickFixProvider provider = diagnostic -> Collections.emptyList();
        assertTrue(provider.getFixActions(Collections.emptyList()).isEmpty());
        assertTrue(provider.getFixActions((List<Diagnostic>) null).isEmpty());
    }

    @Test
    void implDelegatesToRegisteredGenerator() {
        FixActionGenerator gen = new FixActionGenerator() {
            @Override
            public String getRuleId() {
                return "SEM-REF-001";
            }

            @Override
            public List<FixAction> generate(Diagnostic diagnostic) {
                return List.of(FixAction.builder()
                        .fixType("insert_attr")
                        .description("声明Var name=" + diagnostic.getMessage())
                        .build());
            }
        };
        FixActionRegistry.register(gen);
        QuickFixProvider provider = new QuickFixProviderImpl();
        List<FixAction> actions = provider.getFixActions(diag("SEM-REF-001", "steps_value"));
        assertEquals(1, actions.size());
        assertEquals("insert_attr", actions.get(0).getFixType());
        assertEquals("声明Var name=steps_value", actions.get(0).getDescription());
    }

    @Test
    void implReturnsEmptyWhenNoGeneratorRegistered() {
        QuickFixProvider provider = new QuickFixProviderImpl();
        assertTrue(provider.getFixActions(diag("UNREGISTERED-001", "x")).isEmpty());
    }

    @Test
    void implReturnsEmptyForNullDiagnostic() {
        QuickFixProvider provider = new QuickFixProviderImpl();
        assertTrue(provider.getFixActions((Diagnostic) null).isEmpty());
    }

    @Test
    void implBatchSkipsDiagnosticsWithoutGenerator() {
        FixActionRegistry.register(generator("R1", "fix-R1"));
        FixActionRegistry.register(generator("R2", "fix-R2"));
        QuickFixProvider provider = new QuickFixProviderImpl();
        List<FixAction> all = provider.getFixActions(List.of(
                diag("R1", "m1"),
                diag("UNKNOWN", "m2"),
                diag("R2", "m3")
        ));
        assertEquals(2, all.size());
        assertEquals("fix-R1", all.get(0).getFixType());
        assertEquals("fix-R2", all.get(1).getFixType());
    }

    private static FixActionGenerator generator(String ruleId, String fixType) {
        return new FixActionGenerator() {
            @Override
            public String getRuleId() {
                return ruleId;
            }

            @Override
            public List<FixAction> generate(Diagnostic diagnostic) {
                return List.of(FixAction.builder().fixType(fixType).build());
            }
        };
    }

    private static Diagnostic diag(String ruleId, String message) {
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(ruleId)
                .message(message)
                .filePath("test.xml")
                .line(1)
                .column(1)
                .build();
    }
}
