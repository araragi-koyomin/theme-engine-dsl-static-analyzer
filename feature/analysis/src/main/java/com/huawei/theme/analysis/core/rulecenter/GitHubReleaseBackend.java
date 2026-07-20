package com.huawei.theme.analysis.core.rulecenter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

public class GitHubReleaseBackend {
    private static final List<String> REQUIRED_ASSETS = List.of(
            "rule-package.zip", "manifest.json", "release-report.json");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    private final Gson gson = new Gson();

    public GitHubReleaseEvaluation evaluate(
            GitHubReleaseDescriptor descriptor,
            String analyzerVersion) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (descriptor.isDraft() || descriptor.isPrerelease()) {
            return rejected(GitHubReleaseRejection.NOT_APPROVED_RELEASE);
        }
        Map<String, GitHubReleaseAsset> assets = indexAssets(descriptor.getAssets());
        if (!assets.keySet().containsAll(REQUIRED_ASSETS)) {
            return rejected(GitHubReleaseRejection.MISSING_REQUIRED_ASSET);
        }
        GitHubReleaseAsset packageAsset = assets.get("rule-package.zip");
        if (!allAssetsUploaded(assets) || packageAsset.getDownloadUrl() == null) {
            return rejected(GitHubReleaseRejection.INVALID_RELEASE_METADATA);
        }
        String artifactSha256 = parseSha256Digest(packageAsset.getDigest());
        if (artifactSha256 == null) {
            return rejected(GitHubReleaseRejection.INVALID_ASSET_DIGEST);
        }
        if (!contentMatchesAssetDigest(assets.get("manifest.json"))
                || !contentMatchesAssetDigest(assets.get("release-report.json"))) {
            return rejected(GitHubReleaseRejection.INVALID_ASSET_DIGEST);
        }

        RulePackageManifest manifest;
        RulePackageReleaseReport report;
        try {
            manifest = gson.fromJson(assets.get("manifest.json").getContent(), RulePackageManifest.class);
            report = gson.fromJson(
                    assets.get("release-report.json").getContent(),
                    RulePackageReleaseReport.class);
        } catch (JsonParseException | NullPointerException exception) {
            return rejected(GitHubReleaseRejection.INVALID_RELEASE_METADATA);
        }
        if (!validManifest(manifest) || !validReport(report)) {
            return rejected(GitHubReleaseRejection.INVALID_RELEASE_METADATA);
        }
        if (report.getStatus() != ReleaseReportStatus.PASSED
                && report.getStatus() != ReleaseReportStatus.PASSED_WITH_EXCLUSIONS) {
            return rejected(GitHubReleaseRejection.REPORT_NOT_PUBLISHABLE);
        }
        if (!report.isJsonSchemaValid()
                || !report.isPackageComplete()
                || !report.getErrors().isEmpty()) {
            return rejected(GitHubReleaseRejection.REPORT_NOT_PUBLISHABLE);
        }
        String expectedTag = "rules-v" + manifest.getPackageVersion();
        if (!expectedTag.equals(descriptor.getTagName())
                || !manifest.getPackageVersion().equals(report.getPackageVersion())) {
            return rejected(GitHubReleaseRejection.VERSION_MISMATCH);
        }
        if (!manifest.getContentSha256().equals(report.getManifestContentSha256())) {
            return rejected(GitHubReleaseRejection.DIGEST_MISMATCH);
        }
        if (!isCompatible(analyzerVersion, manifest.getMinimumAnalyzerVersion())) {
            return rejected(GitHubReleaseRejection.INCOMPATIBLE_ANALYZER);
        }

        return GitHubReleaseEvaluation.builder()
                .approved(true)
                .release(GitHubApprovedRelease.builder()
                        .packageVersion(manifest.getPackageVersion())
                        .createdAt(manifest.getCreatedAt())
                        .publishedAt(descriptor.getPublishedAt())
                        .contentSha256(manifest.getContentSha256())
                        .artifactSha256(artifactSha256)
                        .minimumAnalyzerVersion(manifest.getMinimumAnalyzerVersion())
                        .changeSummary(descriptor.getBody())
                        .packageDownloadUrl(packageAsset.getDownloadUrl())
                        .build())
                .build();
    }

    private Map<String, GitHubReleaseAsset> indexAssets(List<GitHubReleaseAsset> assets) {
        Map<String, GitHubReleaseAsset> indexed = new HashMap<>();
        if (assets == null) {
            return indexed;
        }
        for (GitHubReleaseAsset asset : assets) {
            if (asset != null && asset.getName() != null) {
                indexed.putIfAbsent(asset.getName(), asset);
            }
        }
        return indexed;
    }

    private boolean allAssetsUploaded(Map<String, GitHubReleaseAsset> assets) {
        for (String required : REQUIRED_ASSETS) {
            GitHubReleaseAsset asset = assets.get(required);
            if (asset == null || !"uploaded".equals(asset.getState())) {
                return false;
            }
        }
        return true;
    }

    private boolean validManifest(RulePackageManifest manifest) {
        return manifest != null
                && manifest.getSchemaVersion() == 1
                && "approved".equals(manifest.getChannel())
                && hasText(manifest.getPackageVersion())
                && hasText(manifest.getCreatedAt())
                && manifest.getContentSha256() != null
                && SHA256.matcher(manifest.getContentSha256()).matches()
                && manifest.getInventory() != null
                && manifest.getInventory().getRuleFiles() != null
                && manifest.getInventory().getFunctionFiles() != null;
    }

    private boolean validReport(RulePackageReleaseReport report) {
        return report != null
                && hasText(report.getPackageVersion())
                && report.getStatus() != null
                && report.getErrors() != null
                && report.getManifestContentSha256() != null;
    }

    private String parseSha256Digest(String digest) {
        if (digest == null || !digest.startsWith("sha256:")) {
            return null;
        }
        String value = digest.substring("sha256:".length());
        return SHA256.matcher(value).matches() ? value.toLowerCase() : null;
    }

    private boolean contentMatchesAssetDigest(GitHubReleaseAsset asset) {
        if (asset == null || asset.getContent() == null) {
            return false;
        }
        String expected = parseSha256Digest(asset.getDigest());
        return expected != null && expected.equals(sha256(asset.getContent()));
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean isCompatible(String analyzerVersion, String minimumAnalyzerVersion) {
        if (minimumAnalyzerVersion == null || minimumAnalyzerVersion.isEmpty()) {
            return true;
        }
        int[] current = parseVersion(analyzerVersion);
        int[] minimum = parseVersion(minimumAnalyzerVersion);
        if (current == null || minimum == null) {
            return false;
        }
        int length = Math.max(current.length, minimum.length);
        for (int index = 0; index < length; index++) {
            int left = index < current.length ? current[index] : 0;
            int right = index < minimum.length ? minimum[index] : 0;
            if (left != right) {
                return left > right;
            }
        }
        return true;
    }

    private int[] parseVersion(String version) {
        if (version == null || version.isEmpty()) {
            return null;
        }
        String[] parts = version.split("\\.");
        int[] values = new int[parts.length];
        try {
            for (int index = 0; index < parts.length; index++) {
                if (!parts[index].matches("\\d+")) {
                    return null;
                }
                values[index] = Integer.parseInt(parts[index]);
            }
            return values;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private GitHubReleaseEvaluation rejected(GitHubReleaseRejection rejection) {
        return GitHubReleaseEvaluation.builder()
                .approved(false)
                .rejection(rejection)
                .build();
    }
}
