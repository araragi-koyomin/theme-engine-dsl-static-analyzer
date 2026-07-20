package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.huawei.theme.analysis.core.rulecenter.model.CandidateStatus;
import com.huawei.theme.analysis.core.rulecenter.model.ConstraintVerification;
import com.huawei.theme.analysis.core.rulecenter.model.RuleCandidate;
import com.huawei.theme.analysis.core.rulecenter.model.VerificationStatus;
import com.huawei.theme.analysis.core.function.JsonFunctionSignatureLoader;
import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;

public class RulePackageAssembler {
    private static final List<String> REQUIRED_PATHS = List.of(
            "rules/elements",
            "rules/global_vars.json",
            "rules/rule_sources.json",
            "functions/dsl_functions.json");

    private final StrictConditionAcceptor conditionAcceptor;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public RulePackageAssembler(StrictConditionAcceptor conditionAcceptor) {
        this.conditionAcceptor = Objects.requireNonNull(conditionAcceptor);
    }

    public RulePackageAssemblyResult assemble(RulePackageAssemblyRequest request) {
        validateRequest(request);
        try {
            Path packageDirectory = request.getPackageDirectory();
            Files.createDirectories(packageDirectory);
            copyDirectory(request.getRulesDirectory(), packageDirectory.resolve("rules"));
            copyDirectory(request.getFunctionsDirectory(), packageDirectory.resolve("functions"));
            writeSourceDocuments(packageDirectory, request.getSourceDocuments());
            Files.createDirectories(packageDirectory.resolve("verification"));

            List<String> errors = new ArrayList<>();
            RulePackageInventory inventory = RulePackageInventory.fromPackage(packageDirectory);
            boolean complete = validateCompleteness(
                    packageDirectory, request.getMinimumInventory(), inventory, errors);
            boolean jsonValid = validateJsonAndSchema(packageDirectory, errors);
            boolean productionLoadable = validateWithProductionLoaders(
                    packageDirectory, inventory, errors);
            jsonValid = jsonValid && productionLoadable;
            if (jsonValid) {
                validatePublishedConstraints(packageDirectory, request, errors);
            }
            ReleaseReportStatus status = determineStatus(request, errors);
            RulePackageReleaseReport report = buildReport(
                    request, status, jsonValid, complete, errors);
            Path reportPath = packageDirectory.resolve("verification/release-report.json");
            writeJson(reportPath, report);

            String contentSha256 = RulePackageDigest.compute(packageDirectory);
            report.setManifestContentSha256(contentSha256);
            writeJson(reportPath, report);
            writeJson(packageDirectory.resolve("manifest.json"),
                    buildManifest(request, contentSha256, inventory));

            return RulePackageAssemblyResult.builder()
                    .packageDirectory(packageDirectory)
                    .status(status)
                    .contentSha256(contentSha256)
                    .errors(List.copyOf(errors))
                    .build();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to assemble rule package", exception);
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            return;
        }
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void writeSourceDocuments(
            Path packageDirectory,
            List<SourceDocumentArtifact> sourceDocuments) throws IOException {
        Path sourceRoot = packageDirectory.resolve("source-markdown").toAbsolutePath().normalize();
        Files.createDirectories(sourceRoot);
        for (SourceDocumentArtifact document : sourceDocuments.stream()
                .sorted(Comparator.comparing(SourceDocumentArtifact::getRelativePath))
                .toList()) {
            Path target = sourceRoot.resolve(document.getRelativePath()).normalize();
            if (!target.startsWith(sourceRoot)) {
                throw new IllegalArgumentException("source markdown path escapes package");
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, document.getContent(), StandardCharsets.UTF_8);
        }
    }

    private boolean validateCompleteness(
            Path packageDirectory,
            RulePackageInventory minimumInventory,
            RulePackageInventory actualInventory,
            List<String> errors) {
        boolean complete = true;
        for (String requiredPath : REQUIRED_PATHS) {
            if (!Files.exists(packageDirectory.resolve(requiredPath))) {
                errors.add("missing required package path: " + requiredPath);
                complete = false;
            }
        }
        boolean hasElementRule = actualInventory.getRuleFiles().stream()
                .anyMatch(path -> path.startsWith("rules/elements/")
                        && path.endsWith(".json"));
        if (!hasElementRule) {
            errors.add("package contains no element rule JSON files");
            complete = false;
        }
        for (String missing : minimumInventory.missingFrom(actualInventory)) {
            errors.add("baseline inventory file is missing: " + missing);
            complete = false;
        }
        return complete;
    }

    private boolean validateWithProductionLoaders(
            Path packageDirectory,
            RulePackageInventory inventory,
            List<String> errors) {
        try {
            JsonRuleLoader ruleLoader = new JsonRuleLoader();
            int loadedElements = ruleLoader.loadElementRules(
                    packageDirectory.resolve("rules").toString()).size();
            long elementFiles = inventory.getRuleFiles().stream()
                    .filter(path -> path.startsWith("rules/elements/")
                            || path.startsWith("rules/commands/"))
                    .filter(path -> path.endsWith(".json"))
                    .count();
            if (loadedElements != elementFiles) {
                errors.add("production rule loader did not load every element rule file");
                return false;
            }
            ruleLoader.loadGlobalVars(packageDirectory.resolve("rules").toString());
            ruleLoader.loadRuleSources(packageDirectory.resolve("rules").toString());
            JsonObject functions = JsonParser.parseString(Files.readString(
                    packageDirectory.resolve("functions/dsl_functions.json")))
                    .getAsJsonObject();
            int declaredFunctions = functions.getAsJsonArray("functions").size();
            int loadedFunctions = new JsonFunctionSignatureLoader().loadFromDirectory(
                    packageDirectory.resolve("functions").toString())
                    .getAllSignatures().size();
            if (declaredFunctions != loadedFunctions) {
                errors.add("production function loader did not load every declared signature");
                return false;
            }
            return true;
        } catch (RuntimeException | IOException exception) {
            errors.add("production rule loader rejected package: "
                    + exception.getClass().getSimpleName());
            return false;
        }
    }

    private boolean validateJsonAndSchema(Path packageDirectory, List<String> errors)
            throws IOException {
        boolean valid = true;
        List<Path> jsonFiles = new ArrayList<>();
        collectJsonFiles(packageDirectory.resolve("rules"), jsonFiles);
        collectJsonFiles(packageDirectory.resolve("functions"), jsonFiles);
        for (Path jsonFile : jsonFiles) {
            try {
                JsonElement root = JsonParser.parseString(Files.readString(jsonFile));
                if (!matchesExistingSchema(packageDirectory, jsonFile, root)) {
                    errors.add("incompatible JSON schema: "
                            + relative(packageDirectory, jsonFile));
                    valid = false;
                }
            } catch (JsonParseException | IllegalStateException exception) {
                errors.add("invalid JSON: " + relative(packageDirectory, jsonFile));
                valid = false;
            }
        }
        return valid;
    }

    private void collectJsonFiles(Path root, List<Path> jsonFiles) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(jsonFiles::add);
        }
    }

    private boolean matchesExistingSchema(
            Path packageDirectory,
            Path jsonFile,
            JsonElement root) {
        String path = relative(packageDirectory, jsonFile);
        if ("rules/global_vars.json".equals(path)
                || "rules/rule_sources.json".equals(path)) {
            return root.isJsonArray();
        }
        if ("functions/dsl_functions.json".equals(path)) {
            return validFunctionFile(root);
        }
        if (path.startsWith("rules/elements/")) {
            return root.isJsonObject()
                    && hasString(root.getAsJsonObject(), "element")
                    && (!root.getAsJsonObject().has("constraints")
                            || root.getAsJsonObject().get("constraints").isJsonArray());
        }
        return true;
    }

    private boolean validFunctionFile(JsonElement root) {
        if (!root.isJsonObject() || !root.getAsJsonObject().has("functions")
                || !root.getAsJsonObject().get("functions").isJsonArray()) {
            return false;
        }
        for (JsonElement item : root.getAsJsonObject().getAsJsonArray("functions")) {
            if (!item.isJsonObject()) {
                return false;
            }
            JsonObject function = item.getAsJsonObject();
            if (!hasTextString(function, "name") || !hasTextString(function, "returnType")
                    || !hasTextString(function, "expressionKind")
                    || !function.has("params") || !function.get("params").isJsonArray()) {
                return false;
            }
            for (JsonElement parameterItem : function.getAsJsonArray("params")) {
                if (!parameterItem.isJsonObject()) {
                    return false;
                }
                JsonObject parameter = parameterItem.getAsJsonObject();
                if (!hasTextString(parameter, "name") || !hasTextString(parameter, "type")
                        || !parameter.has("isVariadic")
                        || !parameter.get("isVariadic").isJsonPrimitive()
                        || !parameter.get("isVariadic").getAsJsonPrimitive().isBoolean()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void validatePublishedConstraints(
            Path packageDirectory,
            RulePackageAssemblyRequest request,
            List<String> errors) throws IOException {
        Map<String, JsonObject> constraints = readConstraints(
                packageDirectory, errors, request.getGrandfatheredDuplicateRuleIds());
        Map<String, ConstraintVerification> verifications = new HashMap<>();
        for (ConstraintVerification verification : request.getVerifications()) {
            if (verification != null && verification.getRuleId() != null) {
                verifications.put(verification.getRuleId(), verification);
            }
        }
        for (String ruleId : request.getPublishedConstraintRuleIds()) {
            JsonObject constraint = constraints.get(ruleId);
            if (constraint == null) {
                errors.add("published constraint missing from rules: " + ruleId);
                continue;
            }
            String condition = hasString(constraint, "condition")
                    ? constraint.get("condition").getAsString()
                    : null;
            ConstraintVerification verification = verifications.get(ruleId);
            if (!isPassingVerification(ruleId, condition, verification)) {
                errors.add("published constraint lacks matching verification: " + ruleId);
                continue;
            }
            ConditionAcceptance acceptance = conditionAcceptor.accept(condition);
            if (!acceptance.isAccepted()) {
                errors.add("published constraint condition is unsupported: " + ruleId);
            }
        }
    }

    private Map<String, JsonObject> readConstraints(
            Path packageDirectory,
            List<String> errors,
            Set<String> grandfatheredDuplicateRuleIds) throws IOException {
        Map<String, JsonObject> constraints = new LinkedHashMap<>();
        Set<String> duplicates = new HashSet<>();
        Path elements = packageDirectory.resolve("rules/elements");
        if (!Files.isDirectory(elements)) {
            return constraints;
        }
        try (var paths = Files.walk(elements)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .toList()) {
                JsonObject element = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                JsonArray items = element.has("constraints")
                        ? element.getAsJsonArray("constraints") : new JsonArray();
                for (JsonElement item : items) {
                    if (!item.isJsonObject() || !hasString(item.getAsJsonObject(), "ruleId")) {
                        continue;
                    }
                    JsonObject constraint = item.getAsJsonObject();
                    String ruleId = constraint.get("ruleId").getAsString();
                    JsonObject existing = constraints.putIfAbsent(ruleId, constraint);
                    if (existing != null && !existing.equals(constraint)) {
                        duplicates.add(ruleId);
                    }
                }
            }
        }
        for (String duplicate : duplicates) {
            if (!grandfatheredDuplicateRuleIds.contains(duplicate)) {
                errors.add("duplicate constraint ruleId: " + duplicate);
            }
        }
        return constraints;
    }

    private boolean isPassingVerification(
            String ruleId,
            String condition,
            ConstraintVerification verification) {
        return verification != null
                && verification.getStatus() == VerificationStatus.PASSED
                && verification.isParserAccepted()
                && Objects.equals(ruleId, verification.getRuleId())
                && Objects.equals(condition, verification.getCondition())
                && verification.getPositiveObservedRuleIds() != null
                && verification.getPositiveObservedRuleIds().contains(ruleId)
                && verification.getNegativeObservedRuleIds() != null
                && !verification.getNegativeObservedRuleIds().contains(ruleId);
    }

    private ReleaseReportStatus determineStatus(
            RulePackageAssemblyRequest request,
            List<String> errors) {
        if (!errors.isEmpty()) {
            return ReleaseReportStatus.FAILED;
        }
        boolean exclusions = !request.getCarriedForwardCandidateIds().isEmpty()
                || request.getCandidates().stream().anyMatch(candidate ->
                        candidate.getStatus() == CandidateStatus.SKIPPED
                                || candidate.getStatus() == CandidateStatus.VALIDATION_ERROR);
        return exclusions
                ? ReleaseReportStatus.PASSED_WITH_EXCLUSIONS
                : ReleaseReportStatus.PASSED;
    }

    private RulePackageReleaseReport buildReport(
            RulePackageAssemblyRequest request,
            ReleaseReportStatus status,
            boolean jsonValid,
            boolean complete,
            List<String> errors) {
        Map<CandidateStatus, Integer> rawCounts = new EnumMap<>(CandidateStatus.class);
        Map<String, List<String>> byStatus = new LinkedHashMap<>();
        List<RuleCandidate> orderedCandidates = request.getCandidates().stream()
                .sorted(Comparator.comparing(
                        candidate -> candidate.getCandidateId() == null
                                ? "" : candidate.getCandidateId()))
                .toList();
        for (RuleCandidate candidate : orderedCandidates) {
            rawCounts.merge(candidate.getStatus(), 1, Integer::sum);
            byStatus.computeIfAbsent(statusName(candidate.getStatus()), ignored -> new ArrayList<>())
                    .add(candidate.getCandidateId());
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<CandidateStatus, Integer> entry : rawCounts.entrySet()) {
            counts.put(statusName(entry.getKey()), entry.getValue());
        }
        return RulePackageReleaseReport.builder()
                .packageVersion(request.getPackageVersion())
                .manifestContentSha256("")
                .status(status)
                .candidateCounts(counts)
                .candidatesByStatus(byStatus)
                .carriedForwardCandidateIds(request.getCarriedForwardCandidateIds().stream()
                        .sorted().toList())
                .constraintVerifications(request.getVerifications().stream()
                        .sorted(Comparator.comparing(verification -> verification == null
                                || verification.getRuleId() == null
                                        ? "" : verification.getRuleId()))
                        .toList())
                .jsonSchemaValid(jsonValid)
                .packageComplete(complete)
                .errors(List.copyOf(errors))
                .build();
    }

    private RulePackageManifest buildManifest(
            RulePackageAssemblyRequest request,
            String contentSha256,
            RulePackageInventory inventory) {
        List<RulePackageManifest.SourceDocumentRevision> revisions =
                request.getSourceDocuments().stream()
                        .map(document -> RulePackageManifest.SourceDocumentRevision.builder()
                                .documentId(document.getDocumentId())
                                .revision(document.getRevision())
                                .sha256(sha256(document.getContent()))
                                .build())
                        .sorted(Comparator.comparing(
                                RulePackageManifest.SourceDocumentRevision::getDocumentId)
                                .thenComparing(
                                        RulePackageManifest.SourceDocumentRevision::getRevision))
                        .toList();
        return RulePackageManifest.builder()
                .schemaVersion(1)
                .packageVersion(request.getPackageVersion())
                .channel("approved")
                .createdAt(request.getCreatedAt())
                .contentSha256(contentSha256)
                .minimumAnalyzerVersion(request.getMinimumAnalyzerVersion())
                .inventory(inventory)
                .sourceDocumentRevisions(revisions)
                .build();
    }

    private void writeJson(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, gson.toJson(value), StandardCharsets.UTF_8);
    }

    private boolean hasString(JsonObject object, String field) {
        return object.has(field) && object.get(field).isJsonPrimitive()
                && object.get(field).getAsJsonPrimitive().isString();
    }

    private boolean hasTextString(JsonObject object, String field) {
        return hasString(object, field) && !object.get(field).getAsString().trim().isEmpty();
    }

    private String relative(Path packageDirectory, Path file) {
        return packageDirectory.relativize(file).toString().replace('\\', '/');
    }

    private String statusName(CandidateStatus status) {
        return status.name().toLowerCase().replace('_', '-');
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void validateRequest(RulePackageAssemblyRequest request) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.getPackageDirectory(), "packageDirectory");
        Objects.requireNonNull(request.getRulesDirectory(), "rulesDirectory");
        Objects.requireNonNull(request.getFunctionsDirectory(), "functionsDirectory");
        requireText(request.getPackageVersion(), "packageVersion");
        requireText(request.getCreatedAt(), "createdAt");
        Objects.requireNonNull(request.getSourceDocuments(), "sourceDocuments");
        Objects.requireNonNull(request.getCandidates(), "candidates");
        Objects.requireNonNull(request.getVerifications(), "verifications");
        Objects.requireNonNull(request.getPublishedConstraintRuleIds(), "publishedConstraintRuleIds");
        Objects.requireNonNull(request.getCarriedForwardCandidateIds(), "carriedForwardCandidateIds");
        Objects.requireNonNull(request.getMinimumInventory(), "minimumInventory");
        Objects.requireNonNull(
                request.getGrandfatheredDuplicateRuleIds(), "grandfatheredDuplicateRuleIds");
    }

    private void requireText(String value, String field) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
    }
}
