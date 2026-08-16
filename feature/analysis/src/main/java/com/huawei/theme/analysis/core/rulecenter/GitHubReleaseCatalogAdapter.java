package com.huawei.theme.analysis.core.rulecenter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class GitHubReleaseCatalogAdapter implements ReleaseCatalog {
    private final GitHubReleaseSource source;
    private final GitHubReleaseBackend backend;
    private final Map<String, GitHubApprovedRelease> approvedByVersion = new HashMap<>();

    public GitHubReleaseCatalogAdapter(
            GitHubReleaseSource source,
            GitHubReleaseBackend backend) {
        this.source = Objects.requireNonNull(source);
        this.backend = Objects.requireNonNull(backend);
    }

    @Override
    public Optional<LatestRelease> findLatest(
            String currentVersion,
            String analyzerVersion) {
        return approvedReleases(analyzerVersion).values().stream()
                .filter(release -> currentVersion == null
                        || ReleaseVersionSupport.compare(
                                release.getPackageVersion(), currentVersion) > 0)
                .max(Comparator.comparing(
                        GitHubApprovedRelease::getPackageVersion,
                        ReleaseVersionSupport::compare))
                .map(this::latest);
    }

    @Override
    public Optional<ReleaseMetadata> findVersion(
            String packageVersion,
            String analyzerVersion) {
        GitHubApprovedRelease release = approvedReleases(analyzerVersion).get(packageVersion);
        return Optional.ofNullable(release).map(this::metadata);
    }

    @Override
    public RulePackageArtifact download(String packageVersion) {
        GitHubApprovedRelease release = approvedByVersion.get(packageVersion);
        if (release == null) {
            throw new IllegalStateException("release must be queried before download");
        }
        byte[] bytes = source.download(release.getPackageDownloadUrl());
        String observed = sha256(bytes);
        if (!observed.equals(release.getArtifactSha256())) {
            throw new IllegalStateException("downloaded artifact digest mismatch");
        }
        return new RulePackageArtifact(
                packageVersion,
                release.getContentSha256(),
                release.getArtifactSha256(),
                bytes);
    }

    private Map<String, GitHubApprovedRelease> approvedReleases(String analyzerVersion) {
        Map<String, GitHubApprovedRelease> compatible = new HashMap<>();
        for (GitHubReleaseDescriptor descriptor : source.listReleases()) {
            GitHubReleaseEvaluation evaluation = backend.evaluate(descriptor, analyzerVersion);
            if (evaluation.isApproved()) {
                GitHubApprovedRelease release = evaluation.getRelease();
                compatible.put(release.getPackageVersion(), release);
                approvedByVersion.put(release.getPackageVersion(), release);
            }
        }
        return compatible;
    }

    private LatestRelease latest(GitHubApprovedRelease release) {
        return LatestRelease.builder()
                .packageVersion(release.getPackageVersion())
                .createdAt(release.getCreatedAt())
                .contentSha256(release.getContentSha256())
                .artifactSha256(release.getArtifactSha256())
                .minimumAnalyzerVersion(release.getMinimumAnalyzerVersion())
                .changeSummary(release.getChangeSummary())
                .build();
    }

    private ReleaseMetadata metadata(GitHubApprovedRelease release) {
        return ReleaseMetadata.builder()
                .packageVersion(release.getPackageVersion())
                .createdAt(release.getCreatedAt())
                .contentSha256(release.getContentSha256())
                .artifactSha256(release.getArtifactSha256())
                .minimumAnalyzerVersion(release.getMinimumAnalyzerVersion())
                .changeSummary(release.getChangeSummary())
                .build();
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
