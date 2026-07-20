package com.huawei.theme.analysis.core.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectionConfigLoaderTest {

    private Path tempDir;
    private InspectionConfigLoader loader;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("config-loader-test");
        loader = new InspectionConfigLoader();
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
    }

    private Path writeConfigFile(String content) throws Exception {
        Path file = Files.createTempFile(tempDir, "config", ".json");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void loadValidConfigWithAllFields() throws Exception {
        String json = """
                {
                  "rootElementNames": ["Lockscreen", "Wallpaper"],
                  "enabledRuleIds": ["SYN-001", "SYN-002", "SEM-REF-001"],
                  "disabledRuleIds": null,
                  "severityOverrides": {
                    "SYN-003": "warning",
                    "SEM-CMD-001": "info"
                  }
                }
                """;
        Path file = writeConfigFile(json);
        InspectionConfig config = loader.load(file.toString());

        assertNotNull(config.getRootElementNames());
        assertEquals(2, config.getRootElementNames().size());
        assertTrue(config.getRootElementNames().contains("Lockscreen"));
        assertTrue(config.getRootElementNames().contains("Wallpaper"));

        assertNotNull(config.getEnabledRuleIds());
        assertEquals(3, config.getEnabledRuleIds().size());
        assertTrue(config.getEnabledRuleIds().contains("SYN-001"));

        assertNull(config.getDisabledRuleIds());

        assertNotNull(config.getSeverityOverrides());
        assertEquals(2, config.getSeverityOverrides().size());
    }

    @Test
    void loadConfigWithOnlyRootElementNames() throws Exception {
        String json = """
                {
                  "rootElementNames": ["Lockscreen", "Wallpaper"]
                }
                """;
        Path file = writeConfigFile(json);
        InspectionConfig config = loader.load(file.toString());

        assertNotNull(config.getRootElementNames());
        assertEquals(2, config.getRootElementNames().size());
        assertNull(config.getEnabledRuleIds());
        assertNull(config.getDisabledRuleIds());
        assertNull(config.getSeverityOverrides());
    }

    @Test
    void loadConfigWithOnlyEnabledRuleIds() throws Exception {
        String json = """
                {
                  "enabledRuleIds": ["SYN-001", "SYN-002"]
                }
                """;
        Path file = writeConfigFile(json);
        InspectionConfig config = loader.load(file.toString());

        assertNull(config.getRootElementNames());
        assertNotNull(config.getEnabledRuleIds());
        assertEquals(2, config.getEnabledRuleIds().size());
        assertNull(config.getDisabledRuleIds());
        assertNull(config.getSeverityOverrides());
    }

    @Test
    void loadConfigWithOnlyDisabledRuleIds() throws Exception {
        String json = """
                {
                  "disabledRuleIds": ["SYN-005"]
                }
                """;
        Path file = writeConfigFile(json);
        InspectionConfig config = loader.load(file.toString());

        assertNull(config.getRootElementNames());
        assertNull(config.getEnabledRuleIds());
        assertNotNull(config.getDisabledRuleIds());
        assertEquals(1, config.getDisabledRuleIds().size());
        assertTrue(config.getDisabledRuleIds().contains("SYN-005"));
        assertNull(config.getSeverityOverrides());
    }

    @Test
    void loadConfigWithOnlySeverityOverrides() throws Exception {
        String json = """
                {
                  "severityOverrides": {
                    "SYN-003": "warning"
                  }
                }
                """;
        Path file = writeConfigFile(json);
        InspectionConfig config = loader.load(file.toString());

        assertNull(config.getRootElementNames());
        assertNull(config.getEnabledRuleIds());
        assertNull(config.getDisabledRuleIds());
        assertNotNull(config.getSeverityOverrides());
        assertEquals(1, config.getSeverityOverrides().size());
    }

    @Test
    void loadConfigWithEmptyFileReturnsAllNullFields() throws Exception {
        String json = "{}";
        Path file = writeConfigFile(json);
        InspectionConfig config = loader.load(file.toString());

        assertNull(config.getRootElementNames());
        assertNull(config.getEnabledRuleIds());
        assertNull(config.getDisabledRuleIds());
        assertNull(config.getSeverityOverrides());
    }

    @Test
    void loadConfigThrowsWhenFileNotFound() {
        assertThrows(
                InspectionConfigLoader.ConfigLoadException.class,
                () -> loader.load("/nonexistent/path/config.json")
        );
    }

    @Test
    void loadConfigThrowsWhenJsonSyntaxError() throws Exception {
        String json = "{ invalid json !!!";
        Path file = writeConfigFile(json);
        InspectionConfigLoader.ConfigLoadException ex = assertThrows(
                InspectionConfigLoader.ConfigLoadException.class,
                () -> loader.load(file.toString())
        );
        assertTrue(ex.getMessage().contains("JSON syntax error"));
    }

    @Test
    void loadConfigThrowsWhenEnabledAndDisabledBothSpecified() throws Exception {
        String json = """
                {
                  "enabledRuleIds": ["SYN-001"],
                  "disabledRuleIds": ["SYN-005"]
                }
                """;
        Path file = writeConfigFile(json);
        InspectionConfigLoader.ConfigValidationException ex = assertThrows(
                InspectionConfigLoader.ConfigValidationException.class,
                () -> loader.load(file.toString())
        );
        assertEquals("enabledRuleIds and disabledRuleIds cannot both be specified", ex.getMessage());
    }

    @Test
    void loadConfigThrowsWhenInvalidSeverityValue() throws Exception {
        String json = """
                {
                  "severityOverrides": {
                    "SYN-003": "critical"
                  }
                }
                """;
        Path file = writeConfigFile(json);
        assertThrows(
                InspectionConfigLoader.ConfigLoadException.class,
                () -> loader.load(file.toString())
        );
    }
}
