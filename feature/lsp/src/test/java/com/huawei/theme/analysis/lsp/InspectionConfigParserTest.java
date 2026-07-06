package com.huawei.theme.analysis.lsp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InspectionConfigParserTest {

    private final InspectionConfigParser parser = new InspectionConfigParser();

    @Test
    void nullReturnsNull() {
        assertNull(parser.parse(null));
    }

    @Test
    void emptyMapReturnsNull() {
        assertNull(parser.parse(new LinkedHashMap<>()));
    }

    @Test
    void parsesDisabledRuleIds() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("disabledRuleIds", List.of("SYN-003", "SEM-REQ-001"));
        InspectionConfig config = parser.parse(options);
        assertNotNull(config);
        assertEquals(List.of("SYN-003", "SEM-REQ-001"), config.getDisabledRuleIds());
    }

    @Test
    void parsesEnabledRuleIds() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("enabledRuleIds", List.of("SEM-TYPE-001"));
        InspectionConfig config = parser.parse(options);
        assertNotNull(config);
        assertEquals(List.of("SEM-TYPE-001"), config.getEnabledRuleIds());
    }

    @Test
    void parsesSeverityOverrides() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("severityOverrides", Map.of("SEM-REQ-001", "error", "SYN-003", "warning"));
        InspectionConfig config = parser.parse(options);
        assertNotNull(config);
        assertEquals(DiagnosticSeverity.ERROR, config.getSeverityOverrides().get("SEM-REQ-001"));
        assertEquals(DiagnosticSeverity.WARNING, config.getSeverityOverrides().get("SYN-003"));
    }

    @Test
    void parsesRootElementNames() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("rootElementNames", List.of("Lockscreen", "Widget"));
        InspectionConfig config = parser.parse(options);
        assertNotNull(config);
        assertEquals(List.of("Lockscreen", "Widget"), config.getRootElementNames());
    }

    @Test
    void enabledAndDisabledMutuallyExclusive() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("enabledRuleIds", List.of("SEM-TYPE-001"));
        options.put("disabledRuleIds", List.of("SYN-003"));
        assertNull(parser.parse(options));
    }

    @Test
    void fullConfigParsesAllFields() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("rootElementNames", List.of("Widget"));
        options.put("disabledRuleIds", List.of("SYN-003"));
        options.put("severityOverrides", Map.of("SEM-REQ-001", "info"));
        InspectionConfig config = parser.parse(options);
        assertNotNull(config);
        assertEquals(List.of("Widget"), config.getRootElementNames());
        assertEquals(List.of("SYN-003"), config.getDisabledRuleIds());
        assertEquals(DiagnosticSeverity.INFO, config.getSeverityOverrides().get("SEM-REQ-001"));
    }
}
