package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.huawei.theme.analysis.core.rulecenter.model.RuleCandidate;
import com.huawei.theme.analysis.core.rulecenter.model.TargetKind;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;

final class RulePackageChangeApplier {
    private static final Pattern STRING_COMPARISON = Pattern.compile(
            "element\\.attrs\\[\\s*'([^']+)'\\s*]\\s*(?:==|!=)\\s*'([^']*)'");
    private static final Pattern STRING_SET_COMPARISON = Pattern.compile(
            "element\\.attrs\\[\\s*'([^']+)'\\s*]\\s*(?:NOT\\s+)?IN\\s*\\[([^]]*)]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_VALUE = Pattern.compile("'([^']*)'");
    private final Path rulesDirectory;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    RulePackageChangeApplier(Path sourceRulesDirectory, Path rulesDirectory) {
        this.rulesDirectory = Objects.requireNonNull(rulesDirectory);
        copyDirectory(Objects.requireNonNull(sourceRulesDirectory), rulesDirectory);
    }

    boolean targetExists(RuleCandidate candidate) {
        ResolvedElement resolved = resolve(candidate);
        if (resolved == null) {
            return false;
        }
        if (candidate.getTarget().getKind() == TargetKind.ELEMENT_ATTRIBUTE) {
            String attribute = candidate.getTarget().getAttribute();
            return attribute != null
                    && resolved.json.has("attrTypes")
                    && resolved.json.get("attrTypes").isJsonObject()
                    && resolved.json.getAsJsonObject("attrTypes").has(attribute);
        }
        return candidate.getTarget().getKind() == TargetKind.ELEMENT;
    }

    boolean constraintTargetExists(RuleCandidate candidate, String condition) {
        ResolvedElement resolved = resolve(candidate);
        if (resolved == null || !resolved.json.has("attrTypes")
                || !resolved.json.get("attrTypes").isJsonObject()) {
            return false;
        }
        Matcher matcher = Pattern.compile(
                "element\\.attrs\\[\\s*'([^']+)'\\s*]").matcher(condition);
        boolean foundAttribute = false;
        JsonObject attrTypes = resolved.json.getAsJsonObject("attrTypes");
        while (matcher.find()) {
            foundAttribute = true;
            if (!attrTypes.has(matcher.group(1))) {
                return false;
            }
        }
        return foundAttribute || candidate.getTarget().getKind() == TargetKind.ELEMENT;
    }

    boolean conditionUsesOnlyDeclaredLiteralValues(
            RuleCandidate candidate,
            String condition) {
        ResolvedElement resolved = resolve(candidate);
        if (resolved == null || !resolved.json.has("attrTypes")
                || !resolved.json.get("attrTypes").isJsonObject()) {
            return false;
        }
        Matcher comparison = STRING_COMPARISON.matcher(condition);
        while (comparison.find()) {
            if (!isDeclaredLiteral(resolved.json, comparison.group(1), comparison.group(2))) {
                return false;
            }
        }
        Matcher setComparison = STRING_SET_COMPARISON.matcher(condition);
        while (setComparison.find()) {
            Set<String> declared = declaredValues(resolved.json, setComparison.group(1));
            Matcher values = QUOTED_VALUE.matcher(setComparison.group(2));
            while (values.find()) {
                if (!declared.contains(values.group(1))
                        && !isDeclaredLiteral(
                                resolved.json, setComparison.group(1), values.group(1))) {
                    return false;
                }
            }
        }
        return true;
    }

    String existingRuleIdForCondition(RuleCandidate candidate, String condition) {
        ResolvedElement resolved = resolve(candidate);
        if (resolved == null || !resolved.json.has("constraints")
                || !resolved.json.get("constraints").isJsonArray()) {
            return null;
        }
        String normalized = normalizeCondition(condition);
        for (var item : resolved.json.getAsJsonArray("constraints")) {
            if (item.isJsonObject() && item.getAsJsonObject().has("ruleId")
                    && item.getAsJsonObject().has("condition")
                    && normalized.equals(normalizeCondition(
                            item.getAsJsonObject().get("condition").getAsString()))) {
                return item.getAsJsonObject().get("ruleId").getAsString();
            }
        }
        return null;
    }

    boolean hasRuleId(RuleCandidate candidate, String ruleId) {
        ResolvedElement resolved = resolve(candidate);
        if (resolved == null || !resolved.json.has("constraints")
                || !resolved.json.get("constraints").isJsonArray()) {
            return false;
        }
        for (var item : resolved.json.getAsJsonArray("constraints")) {
            if (item.isJsonObject() && item.getAsJsonObject().has("ruleId")
                    && ruleId.equals(item.getAsJsonObject().get("ruleId").getAsString())) {
                return true;
            }
        }
        return false;
    }

    Set<String> conflictingDuplicateRuleIds() {
        Path elements = rulesDirectory.resolve("elements");
        if (!Files.isDirectory(elements)) {
            return Set.of();
        }
        Map<String, JsonObject> firstByRuleId = new HashMap<>();
        Set<String> conflicts = new HashSet<>();
        try (var paths = Files.walk(elements)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".json")).toList()) {
                JsonObject element = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (!element.has("constraints") || !element.get("constraints").isJsonArray()) {
                    continue;
                }
                for (var item : element.getAsJsonArray("constraints")) {
                    if (!item.isJsonObject() || !item.getAsJsonObject().has("ruleId")) {
                        continue;
                    }
                    JsonObject constraint = item.getAsJsonObject();
                    String ruleId = constraint.get("ruleId").getAsString();
                    JsonObject first = firstByRuleId.putIfAbsent(ruleId, constraint);
                    if (first != null && !first.equals(constraint)) {
                        conflicts.add(ruleId);
                    }
                }
            }
            return Set.copyOf(conflicts);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect baseline rule ids", exception);
        }
    }

    void applyDescription(RuleCandidate candidate) {
        ResolvedElement resolved = requireResolved(candidate);
        if (!candidate.getProposedChange().getValue().isJsonPrimitive()
                || !candidate.getProposedChange().getValue().getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("description value must be a string");
        }
        String description = candidate.getProposedChange().getValue().getAsString();
        if (candidate.getTarget().getKind() == TargetKind.ELEMENT) {
            resolved.json.addProperty("description", description);
        } else if (candidate.getTarget().getKind() == TargetKind.ELEMENT_ATTRIBUTE) {
            resolved.json.getAsJsonObject("attrTypes")
                    .getAsJsonObject(candidate.getTarget().getAttribute())
                    .addProperty("description", description);
        } else {
            throw new IllegalArgumentException("unsupported description target");
        }
        write(resolved);
    }

    void applyConstraint(RuleCandidate candidate, RuleConstraint constraint) {
        ResolvedElement resolved = resolve(candidate);
        if (resolved == null || !constraintTargetExists(candidate, constraint.getCondition())) {
            throw new IllegalArgumentException("constraint target cannot be resolved");
        }
        JsonArray constraints = resolved.json.has("constraints")
                ? resolved.json.getAsJsonArray("constraints") : new JsonArray();
        if (!resolved.json.has("constraints")) {
            resolved.json.add("constraints", constraints);
        }
        int existingIndex = -1;
        for (int index = 0; index < constraints.size(); index++) {
            var item = constraints.get(index);
            if (item.isJsonObject()
                    && item.getAsJsonObject().has("ruleId")
                    && constraint.getRuleId().equals(
                            item.getAsJsonObject().get("ruleId").getAsString())) {
                existingIndex = index;
                break;
            }
        }
        JsonObject json = new JsonObject();
        json.addProperty("ruleId", constraint.getRuleId());
        json.addProperty("condition", constraint.getCondition());
        json.addProperty("message", constraint.getMessage());
        json.addProperty("severity", constraint.getSeverity().name().toLowerCase());
        json.add("suggestedFixes", gson.toJsonTree(constraint.getSuggestedFixes()));
        if (existingIndex >= 0) {
            constraints.set(existingIndex, json);
        } else {
            constraints.add(json);
        }
        write(resolved);
    }

    private ResolvedElement requireResolved(RuleCandidate candidate) {
        ResolvedElement resolved = resolve(candidate);
        if (resolved == null || !targetExists(candidate)) {
            throw new IllegalArgumentException("candidate target cannot be resolved");
        }
        return resolved;
    }

    private ResolvedElement resolve(RuleCandidate candidate) {
        if (candidate == null || candidate.getTarget() == null
                || candidate.getTarget().getElement() == null) {
            return null;
        }
        Path elements = rulesDirectory.resolve("elements");
        if (!Files.isDirectory(elements)) {
            return null;
        }
        try (var paths = Files.walk(elements)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".json")).toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (json.has("element") && candidate.getTarget().getElement()
                        .equals(json.get("element").getAsString())) {
                    return new ResolvedElement(path, json);
                }
            }
            return null;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to resolve rule target", exception);
        }
    }

    private Set<String> declaredValues(JsonObject element, String attribute) {
        JsonObject attrTypes = element.getAsJsonObject("attrTypes");
        if (!attrTypes.has(attribute) || !attrTypes.get(attribute).isJsonObject()) {
            return Set.of();
        }
        JsonObject attrType = attrTypes.getAsJsonObject(attribute);
        if (!attrType.has("enumValues") || !attrType.get("enumValues").isJsonArray()) {
            return Set.of();
        }
        Set<String> values = new HashSet<>();
        for (var value : attrType.getAsJsonArray("enumValues")) {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                values.add(value.getAsString());
            }
        }
        return values;
    }

    private boolean isDeclaredLiteral(JsonObject element, String attribute, String literal) {
        if (declaredValues(element, attribute).contains(literal)) {
            return true;
        }
        JsonObject attrTypes = element.getAsJsonObject("attrTypes");
        if (!attrTypes.has(attribute) || !attrTypes.get(attribute).isJsonObject()) {
            return false;
        }
        JsonObject attrType = attrTypes.getAsJsonObject(attribute);
        if (!attrType.has("type") || !attrType.get("type").isJsonPrimitive()) {
            return false;
        }
        String type = attrType.get("type").getAsString();
        if ("boolean".equalsIgnoreCase(type)) {
            return "true".equalsIgnoreCase(literal) || "false".equalsIgnoreCase(literal);
        }
        if ("number".equalsIgnoreCase(type) || "integer".equalsIgnoreCase(type)) {
            try {
                Double.parseDouble(literal);
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private String normalizeCondition(String condition) {
        return condition == null ? "" : condition.replaceAll("\\s+", "").trim();
    }

    private void write(ResolvedElement resolved) {
        try {
            Files.writeString(resolved.path, gson.toJson(resolved.json), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write generated rule", exception);
        }
    }

    private void copyDirectory(Path source, Path target) {
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("rulesDirectory must exist: " + source);
        }
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to stage baseline rules", exception);
        }
    }

    private record ResolvedElement(Path path, JsonObject json) {
    }
}
