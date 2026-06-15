package com.huawei.theme.analysis.rule.loader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.huawei.theme.analysis.rule.model.AttrTypeSpec;
import com.huawei.theme.analysis.rule.model.DslElementRule;
import com.huawei.theme.analysis.rule.model.RuleSource;

public class JsonRuleLoader {

    private final Gson gson;

    public JsonRuleLoader() {
        this.gson = new GsonBuilder()
                .registerTypeAdapter(AttrTypeSpec.class, new AttrTypeSpecAdapter())
                .create();
    }

    public List<DslElementRule> loadElementRules(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Rule resource file not found: " + resourcePath);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            DslElementRule[] rules = gson.fromJson(root.getAsJsonArray("elementRules"), DslElementRule[].class);
            if (rules == null) {
                return Collections.emptyList();
            }
            return List.of(rules);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load rule resource: " + resourcePath, e);
        }
    }

    public List<RuleSource> loadRuleSources(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Rule resource file not found: " + resourcePath);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            RuleSource[] sources = gson.fromJson(root.getAsJsonArray("ruleSources"), RuleSource[].class);
            if (sources == null) {
                return Collections.emptyList();
            }
            return List.of(sources);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load rule resource: " + resourcePath, e);
        }
    }

    public Map<String, DslElementRule> buildElementRuleMap(String resourcePath) {
        List<DslElementRule> rules = loadElementRules(resourcePath);
        Map<String, DslElementRule> map = new LinkedHashMap<>();
        for (DslElementRule rule : rules) {
            map.put(rule.getElementName(), rule);
        }
        return map;
    }

    public Map<String, RuleSource> buildRuleSourceMap(String resourcePath) {
        List<RuleSource> sources = loadRuleSources(resourcePath);
        Map<String, RuleSource> map = new LinkedHashMap<>();
        for (RuleSource source : sources) {
            map.put(source.getRuleId(), source);
        }
        return map;
    }
}
