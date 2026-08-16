package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

final class RuleCenterDocumentResolver {
    List<RuleDocumentRevision> resolve(
            Path repository,
            Map<String, String> env) throws IOException {
        Path documentList = resolvePath(repository, required(env, "RULE_CENTER_DOCUMENT_LIST"));
        Path docsRoot = resolvePath(repository, env.getOrDefault(
                "RULE_CENTER_DOCUMENT_ROOT", "rule-center/docs"));
        if (!Files.isDirectory(docsRoot)) {
            throw new IllegalArgumentException("rule center document root does not exist");
        }
        Path realDocsRoot = docsRoot.toRealPath();
        List<RuleDocumentRevision> documents = new ArrayList<>();
        for (String line : Files.readAllLines(documentList, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            Path documentPath = resolvePath(repository, line.trim());
            if (!Files.isRegularFile(documentPath)
                    || !documentPath.getFileName().toString().endsWith(".md")) {
                throw invalidDocument(line);
            }
            Path realDocument = documentPath.toRealPath();
            if (!realDocument.startsWith(realDocsRoot)
                    || !realDocument.equals(documentPath.toAbsolutePath().normalize())) {
                throw invalidDocument(line);
            }
            String markdown = Files.readString(realDocument);
            String relativeText = realDocsRoot.relativize(realDocument)
                    .toString().replace('\\', '/');
            documents.add(RuleDocumentRevision.builder()
                    .documentId(withoutExtension(relativeText))
                    .revision(revision(env, markdown))
                    .markdown(markdown)
                    .sourceMarkdownRelativePath(relativeText)
                    .build());
        }
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("document list contains no Markdown files");
        }
        return List.copyOf(documents);
    }

    private IllegalArgumentException invalidDocument(String value) {
        return new IllegalArgumentException(
                "document must be a real Markdown file under the configured document root: "
                        + value);
    }

    private String revision(Map<String, String> env, String markdown) {
        String explicit = env.get("RULE_CENTER_DOCUMENT_REVISION");
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        return env.getOrDefault("GITHUB_SHA", sha256(markdown).substring(0, 12));
    }

    private Path resolvePath(Path repository, String value) {
        Path path = Path.of(value);
        return (path.isAbsolute() ? path : repository.resolve(path))
                .toAbsolutePath().normalize();
    }

    private String withoutExtension(String path) {
        return path.substring(0, path.length() - 3);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be configured");
        }
        return value;
    }
}
