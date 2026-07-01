package com.huawei.theme.analysis.core.cli;

import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.shared.diagnostic.adapter.DiagnosticSeverityAdapter;

public class InspectionConfigLoader {

    private final Gson gson;

    public InspectionConfigLoader() {
        this.gson = new GsonBuilder()
                .registerTypeAdapter(DiagnosticSeverity.class, new DiagnosticSeverityAdapter())
                .create();
    }

    public InspectionConfig load(String configPath) {
        Path filePath = Path.of(configPath);
        if (!Files.exists(filePath)) {
            throw new ConfigLoadException("Config file not found: " + configPath);
        }
        if (!Files.isRegularFile(filePath)) {
            throw new ConfigLoadException("Config path is not a file: " + configPath);
        }

        InspectionConfig config;
        try (FileReader reader = new FileReader(filePath.toFile(), StandardCharsets.UTF_8)) {
            config = gson.fromJson(reader, InspectionConfig.class);
        } catch (JsonSyntaxException e) {
            throw new ConfigLoadException("JSON syntax error in config file: " + configPath, e);
        } catch (Exception e) {
            throw new ConfigLoadException("Failed to read config file: " + configPath, e);
        }
        if (config == null) {
            return InspectionConfig.builder().build();
        }
        validate(config);
        return config;
    }

    private void validate(InspectionConfig config) {
        if (config.getEnabledRuleIds() != null && !config.getEnabledRuleIds().isEmpty()
                && config.getDisabledRuleIds() != null && !config.getDisabledRuleIds().isEmpty()) {
            throw new ConfigValidationException("enabledRuleIds and disabledRuleIds cannot both be specified");
        }
        if (config.getSeverityOverrides() != null) {
            for (Map.Entry<String, DiagnosticSeverity> entry : config.getSeverityOverrides().entrySet()) {
                if (entry.getValue() == null) {
                    throw new ConfigValidationException("Invalid severity value for ruleId '" + entry.getKey() + "': null");
                }
            }
        }
    }

    public static class ConfigLoadException extends RuntimeException {
        public ConfigLoadException(String message) {
            super(message);
        }

        public ConfigLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ConfigValidationException extends RuntimeException {
        public ConfigValidationException(String message) {
            super(message);
        }
    }
}
