package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.huawei.theme.analysis.core.rulecenter.model.DocumentConversionFeedback;

public final class RuleCenterWorkflowMain {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting().disableHtmlEscaping().create();

    private RuleCenterWorkflowMain() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> env = System.getenv();
        Path repository = Path.of("").toAbsolutePath().normalize();
        Path output = resolve(repository, required(env, "RULE_CENTER_OUTPUT"));
        Files.createDirectories(output);
        try {
            RuleCenterValidationResult result = run(repository, output, env);
            writeArtifacts(output, result);
            if (result.getAssembly().getStatus() == ReleaseReportStatus.FAILED) {
                throw new IllegalStateException("rule package validation failed");
            }
        } catch (RuntimeException | IOException exception) {
            writeFailureArtifacts(output, exception);
            throw exception;
        }
    }

    private static RuleCenterValidationResult run(
            Path repository,
            Path output,
            Map<String, String> env) throws IOException {
        Path documentList = resolve(repository, required(env, "RULE_CENTER_DOCUMENT_LIST"));
        Path docsRoot = repository.resolve("rule-center/docs").normalize();
        List<RuleDocumentRevision> documents = new ArrayList<>();
        for (String line : Files.readAllLines(documentList, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            Path documentPath = resolve(repository, line.trim());
            if (!documentPath.startsWith(docsRoot) || !Files.isRegularFile(documentPath)
                    || !documentPath.getFileName().toString().endsWith(".md")) {
                throw new IllegalArgumentException(
                        "document must be a Markdown file under rule-center/docs: " + line);
            }
            String markdown = Files.readString(documentPath);
            Path relative = docsRoot.relativize(documentPath);
            String relativeText = relative.toString().replace('\\', '/');
            documents.add(RuleDocumentRevision.builder()
                    .documentId(withoutExtension(relativeText))
                    .revision(env.getOrDefault("GITHUB_SHA", sha256(markdown).substring(0, 12)))
                    .markdown(markdown)
                    .sourceMarkdownRelativePath(relativeText)
                    .build());
        }
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("document list contains no Markdown files");
        }

        Path rules = resolve(repository, valueOrDefault(env,
                "RULE_CENTER_BASELINE_RULES",
                "feature/analysis/src/main/resources/rules"));
        Path functions = resolve(repository, valueOrDefault(env,
                "RULE_CENTER_BASELINE_FUNCTIONS",
                "feature/analysis/src/main/resources/functions"));
        List<SourceDocumentArtifact> retainedSourceDocuments = retainedSourceDocuments(
                repository, env);
        StrictConditionAcceptor acceptor = new StrictConditionAcceptor(
                new ConditionCapabilityRegistry());
        ConstraintVerificationRunner verificationRunner = new ConstraintVerificationRunner(acceptor);
        VerifiedConstraintExampleCatalog examples = new VerifiedConstraintExampleCatalog(
                BuiltInVerifiedConstraintExamples.load(rules, verificationRunner), acceptor);
        String token = required(env, "GITHUB_TOKEN");
        String model = valueOrDefault(env, "RULE_CENTER_MODEL", "openai/gpt-4.1");
        String promptVersion = valueOrDefault(env,
                "RULE_CENTER_PROMPT_VERSION", "md-to-rule-v1");
        GitHubModelsInferenceClient inferenceClient = new GitHubModelsHttpInferenceClient(token);
        RuleCenterValidationOrchestrator orchestrator = new RuleCenterValidationOrchestrator(
                new GitHubModelsCandidateExtractionService(
                        inferenceClient, model, promptVersion),
                acceptor,
                verificationRunner,
                examples,
                new GitHubModelsConstraintRepairStrategy(
                        inferenceClient, model, promptVersion + "-repair"),
                ignored -> { });
        Path workDirectory = output.resolve("work").normalize();
        resetWorkDirectory(output, workDirectory);
        return orchestrator.validateBatch(RuleCenterBatchValidationRequest.builder()
                .documents(documents)
                .retainedSourceDocuments(retainedSourceDocuments)
                .rulesDirectory(rules)
                .functionsDirectory(functions)
                .outputDirectory(workDirectory)
                .packageVersion(env.getOrDefault(
                        "RULE_CENTER_PACKAGE_VERSION", defaultVersion(env)))
                .createdAt(Instant.now().toString())
                .minimumAnalyzerVersion(valueOrDefault(env,
                        "RULE_CENTER_MINIMUM_ANALYZER_VERSION", "1.0.0"))
                .build());
    }

    private static void writeArtifacts(
            Path output,
            RuleCenterValidationResult result) throws IOException {
        Files.writeString(output.resolve("candidates.json"),
                GSON.toJson(result.getCandidates()), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("feedback.json"),
                GSON.toJson(result.getFeedbackItems()), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("audit.json"),
                GSON.toJson(result.getExtractions()), StandardCharsets.UTF_8);
        Files.copy(result.getAssembly().getPackageDirectory()
                        .resolve("verification/release-report.json"),
                output.resolve("release-report.json"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(output.resolve("feedback-summary.md"),
                feedbackSummary(result), StandardCharsets.UTF_8);
    }

    private static List<SourceDocumentArtifact> retainedSourceDocuments(
            Path repository,
            Map<String, String> env) throws IOException {
        String sourceValue = env.get("RULE_CENTER_BASELINE_SOURCE_MARKDOWN");
        String manifestValue = env.get("RULE_CENTER_BASELINE_MANIFEST");
        if (sourceValue == null || sourceValue.isBlank()) {
            return List.of();
        }
        Path sourceRoot = resolve(repository, sourceValue);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("baseline source-markdown directory does not exist");
        }
        Map<String, String> revisions = new HashMap<>();
        if (manifestValue != null && !manifestValue.isBlank()) {
            RulePackageManifest manifest = GSON.fromJson(
                    Files.readString(resolve(repository, manifestValue)),
                    RulePackageManifest.class);
            if (manifest != null && manifest.getSourceDocumentRevisions() != null) {
                for (RulePackageManifest.SourceDocumentRevision revision
                        : manifest.getSourceDocumentRevisions()) {
                    revisions.put(revision.getDocumentId(), revision.getRevision());
                }
            }
        }
        List<SourceDocumentArtifact> retained = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".md")).toList()) {
                String relative = sourceRoot.relativize(path).toString().replace('\\', '/');
                String documentId = withoutExtension(relative);
                String content = Files.readString(path);
                retained.add(SourceDocumentArtifact.builder()
                        .documentId(documentId)
                        .revision(revisions.getOrDefault(
                                documentId, sha256(content).substring(0, 12)))
                        .relativePath(relative)
                        .content(content)
                        .build());
            }
        }
        return List.copyOf(retained);
    }

    private static String feedbackSummary(RuleCenterValidationResult result) {
        StringBuilder summary = new StringBuilder("## DSL 规则文档校验结果\n\n");
        summary.append("发布门禁：`").append(result.getAssembly().getStatus())
                .append("`\n\n");
        for (DocumentConversionFeedback feedback : result.getFeedbackItems()) {
            summary.append("- `").append(feedback.getDocumentId()).append("`：")
                    .append(feedback.getConversionStatus())
                    .append("；发布 ").append(feedback.getSummary().getPublished())
                    .append("，说明更新 ").append(feedback.getSummary().getDescriptionOnly())
                    .append("，略过 ").append(feedback.getSummary().getSkipped())
                    .append("，需返工 ").append(feedback.getSummary().getValidationErrors())
                    .append("\n");
        }
        summary.append("\n完整候选、逐行证据与原因见本次 workflow artifacts。\n");
        return summary.toString();
    }

    private static void writeFailureArtifacts(Path output, Exception exception) throws IOException {
        JsonObject failure = new JsonObject();
        failure.addProperty("status", "failed");
        failure.addProperty("errorType", exception.getClass().getSimpleName());
        failure.addProperty("message", String.valueOf(exception.getMessage()));
        if (!Files.exists(output.resolve("audit.json"))) {
            Files.writeString(output.resolve("audit.json"), GSON.toJson(failure));
        }
        if (!Files.exists(output.resolve("candidates.json"))) {
            Files.writeString(output.resolve("candidates.json"), "[]");
        }
        if (!Files.exists(output.resolve("feedback.json"))) {
            Files.writeString(output.resolve("feedback.json"), "[]");
        }
        if (!Files.exists(output.resolve("release-report.json"))) {
            JsonObject report = new JsonObject();
            report.addProperty("status", "failed");
            JsonArray errors = new JsonArray();
            errors.add(String.valueOf(exception.getMessage()));
            report.add("errors", errors);
            Files.writeString(output.resolve("release-report.json"), GSON.toJson(report));
        }
        if (!Files.exists(output.resolve("feedback-summary.md"))) {
            Files.writeString(output.resolve("feedback-summary.md"),
                    "## DSL 规则文档校验失败\n\n`" + exception.getClass().getSimpleName()
                            + "`：" + String.valueOf(exception.getMessage()) + "\n");
        }
    }

    private static Path resolve(Path repository, String value) {
        Path path = Path.of(value);
        return (path.isAbsolute() ? path : repository.resolve(path)).normalize();
    }

    private static void resetWorkDirectory(Path output, Path workDirectory) throws IOException {
        Path normalizedOutput = output.toAbsolutePath().normalize();
        Path normalizedWork = workDirectory.toAbsolutePath().normalize();
        if (!normalizedWork.startsWith(normalizedOutput) || normalizedWork.equals(normalizedOutput)) {
            throw new IllegalArgumentException("work directory must be a child of output");
        }
        if (Files.exists(normalizedWork)) {
            try (var paths = Files.walk(normalizedWork)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(normalizedWork);
    }

    private static String withoutExtension(String path) {
        return path.substring(0, path.length() - 3);
    }

    private static String defaultVersion(Map<String, String> env) {
        String date = DateTimeFormatter.ofPattern("yyyy.MM.dd")
                .withZone(ZoneOffset.UTC).format(Instant.now());
        String run = env.getOrDefault("GITHUB_RUN_NUMBER", "0");
        return date + "." + (run.matches("\\d+") ? run : "0");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be configured");
        }
        return value;
    }

    private static String valueOrDefault(
            Map<String, String> env,
            String name,
            String defaultValue) {
        String value = env.get(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
