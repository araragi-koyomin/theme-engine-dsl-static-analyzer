package com.huawei.theme.analysis.core.rulecenter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

public class RulePackagePublicationPreparer {
    private static final List<String> REQUIRED_PACKAGE_PATHS = List.of(
            "manifest.json",
            "rules/elements",
            "rules/global_vars.json",
            "rules/rule_sources.json",
            "functions/dsl_functions.json",
            "source-markdown",
            "verification/release-report.json");

    private final Gson gson = new Gson();

    public ReleasePublicationResult prepare(Path packageDirectory, Path releaseDirectory) {
        Objects.requireNonNull(packageDirectory, "packageDirectory");
        Objects.requireNonNull(releaseDirectory, "releaseDirectory");
        try {
            ValidatedPackage validated = validate(packageDirectory);
            Files.createDirectories(releaseDirectory);
            Path manifestAsset = releaseDirectory.resolve("manifest.json");
            Path reportAsset = releaseDirectory.resolve("release-report.json");
            Path zipAsset = releaseDirectory.resolve("rule-package.zip");
            Files.copy(packageDirectory.resolve("manifest.json"), manifestAsset,
                    StandardCopyOption.REPLACE_EXISTING);
            Files.copy(packageDirectory.resolve("verification/release-report.json"), reportAsset,
                    StandardCopyOption.REPLACE_EXISTING);
            writeDeterministicZip(packageDirectory, zipAsset);
            String notes = releaseNotes(validated.manifest, validated.report);
            Files.writeString(releaseDirectory.resolve("release-notes.md"),
                    notes, StandardCharsets.UTF_8);
            return ReleasePublicationResult.builder()
                    .packageVersion(validated.manifest.getPackageVersion())
                    .tagName("rules-v" + validated.manifest.getPackageVersion())
                    .rulePackageZip(zipAsset)
                    .assets(List.of(zipAsset, manifestAsset, reportAsset))
                    .releaseNotes(notes)
                    .build();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to prepare rule package release", exception);
        }
    }

    private ValidatedPackage validate(Path packageDirectory) throws IOException {
        if (!Files.isDirectory(packageDirectory)) {
            throw new IllegalArgumentException("rule package directory does not exist");
        }
        for (String path : REQUIRED_PACKAGE_PATHS) {
            if (!Files.exists(packageDirectory.resolve(path))) {
                throw new IllegalArgumentException("rule package is incomplete: " + path);
            }
        }
        RulePackageManifest manifest;
        RulePackageReleaseReport report;
        try {
            manifest = gson.fromJson(Files.readString(packageDirectory.resolve("manifest.json")),
                    RulePackageManifest.class);
            report = gson.fromJson(Files.readString(
                    packageDirectory.resolve("verification/release-report.json")),
                    RulePackageReleaseReport.class);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("release metadata is invalid JSON", exception);
        }
        if (manifest == null || report == null) {
            throw new IllegalArgumentException("release metadata must not be null");
        }
        if (manifest.getSchemaVersion() != 1 || !"approved".equals(manifest.getChannel())) {
            throw new IllegalArgumentException("manifest is not an approved schema-v1 package");
        }
        if (manifest.getPackageVersion() == null || manifest.getPackageVersion().isEmpty()
                || !manifest.getPackageVersion().equals(report.getPackageVersion())) {
            throw new IllegalArgumentException("manifest and report package versions differ");
        }
        if (report.getStatus() != ReleaseReportStatus.PASSED
                && report.getStatus() != ReleaseReportStatus.PASSED_WITH_EXCLUSIONS) {
            throw new IllegalArgumentException("release report status is not publishable");
        }
        if (!report.isJsonSchemaValid() || !report.isPackageComplete()
                || report.getErrors() == null || !report.getErrors().isEmpty()) {
            throw new IllegalArgumentException("release report gates are not satisfied");
        }
        String actualDigest = RulePackageDigest.compute(packageDirectory);
        if (!actualDigest.equals(manifest.getContentSha256())
                || !actualDigest.equals(report.getManifestContentSha256())) {
            throw new IllegalArgumentException("rule package digest does not match release metadata");
        }
        return new ValidatedPackage(manifest, report);
    }

    private void writeDeterministicZip(Path packageDirectory, Path zipPath) throws IOException {
        List<Path> entries = new ArrayList<>();
        try (var paths = Files.walk(packageDirectory)) {
            for (Path path : paths.filter(item -> !item.equals(packageDirectory)).toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IllegalArgumentException("symbolic links are not allowed in rule package");
                }
                entries.add(path);
            }
        }
        entries.sort(Comparator.comparing(path -> relative(packageDirectory, path)));
        try (OutputStream output = Files.newOutputStream(zipPath);
                ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Path path : entries) {
                boolean directory = Files.isDirectory(path);
                String name = relative(packageDirectory, path) + (directory ? "/" : "");
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(0L);
                zip.putNextEntry(entry);
                if (!directory) {
                    try (InputStream input = Files.newInputStream(path)) {
                        input.transferTo(zip);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private String releaseNotes(
            RulePackageManifest manifest,
            RulePackageReleaseReport report) {
        int skipped = count(report, "skipped");
        int validationErrors = count(report, "validation-error");
        return "# DSL rules " + manifest.getPackageVersion() + "\n\n"
                + "- Status: `" + report.getStatus().name().toLowerCase().replace('_', '-') + "`\n"
                + "- Content SHA-256: `" + manifest.getContentSha256() + "`\n"
                + "- Minimum analyzer version: `"
                + (manifest.getMinimumAnalyzerVersion() == null
                        ? "not specified" : manifest.getMinimumAnalyzerVersion()) + "`\n"
                + "- Skipped candidates: " + skipped + "\n"
                + "- Validation errors: " + validationErrors + "\n";
    }

    private int count(RulePackageReleaseReport report, String status) {
        if (report.getCandidateCounts() == null) {
            return 0;
        }
        return report.getCandidateCounts().getOrDefault(status, 0);
    }

    private String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private record ValidatedPackage(
            RulePackageManifest manifest,
            RulePackageReleaseReport report) {
    }
}
