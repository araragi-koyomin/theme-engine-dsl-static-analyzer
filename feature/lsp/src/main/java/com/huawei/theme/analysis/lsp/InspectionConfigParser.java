package com.huawei.theme.analysis.lsp;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.shared.diagnostic.adapter.DiagnosticSeverityAdapter;

/**
 * Parses LSP initialization options / workspace configuration settings into
 * an {@link InspectionConfig}.
 *
 * <p>LSP4J delivers {@code initializationOptions} / {@code settings} as raw
 * JSON (typically a {@link JsonElement} or {@code Map}); this parser reuses
 * core's {@link DiagnosticSeverityAdapter} so severity-override values
 * ("error"/"warning"/"info") map identically to the CLI
 * {@code InspectionConfigLoader}. Returns {@code null} on null/empty input or
 * validation failure so callers can fall back to the bare rule repository.</p>
 *
 * <p>The expected JSON shape matches {@link InspectionConfig} directly:
 * <pre>{@code
 * {
 *   "rootElementNames": ["Lockscreen", "Widget"],
 *   "enabledRuleIds": ["SEM-TYPE-001"],
 *   "disabledRuleIds": ["SYN-003"],
 *   "severityOverrides": { "SEM-REQ-001": "error" }
 * }
 * }</pre>
 */
final class InspectionConfigParser {

    private static final Logger LOG = Logger.getLogger(InspectionConfigParser.class.getName());

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(DiagnosticSeverity.class, new DiagnosticSeverityAdapter())
            .create();

    InspectionConfig parse(Object options) {
        if (options == null) {
            return null;
        }
        JsonElement tree;
        try {
            tree = gson.toJsonTree(options);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to serialize inspection config options", e);
            return null;
        }
        if (tree.isJsonNull() || (tree.isJsonObject() && tree.getAsJsonObject().size() == 0)) {
            return null;
        }
        InspectionConfig config;
        try {
            config = gson.fromJson(tree, InspectionConfig.class);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to parse inspection config: " + e.getMessage(), e);
            return null;
        }
        if (config == null) {
            return null;
        }
        if (!isValid(config)) {
            return null;
        }
        return config;
    }

    private static boolean isValid(InspectionConfig config) {
        List<String> enabled = config.getEnabledRuleIds();
        List<String> disabled = config.getDisabledRuleIds();
        if (enabled != null && !enabled.isEmpty()
                && disabled != null && !disabled.isEmpty()) {
            LOG.warning("Invalid inspection config: enabledRuleIds and disabledRuleIds"
                    + " cannot both be specified");
            return false;
        }
        Map<String, DiagnosticSeverity> overrides = config.getSeverityOverrides();
        if (overrides != null) {
            for (Map.Entry<String, DiagnosticSeverity> entry : overrides.entrySet()) {
                if (entry.getValue() == null) {
                    LOG.warning("Invalid inspection config: null severity for ruleId "
                            + entry.getKey());
                    return false;
                }
            }
        }
        return true;
    }
}
