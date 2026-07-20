package com.huawei.theme.analysis.core.rulecenter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FixtureReleaseCatalog implements ReleaseCatalog {
    private final Map<String, FixtureReleaseEntry> entries;

    public FixtureReleaseCatalog(List<FixtureReleaseEntry> entries) {
        this.entries = new HashMap<>();
        for (FixtureReleaseEntry entry : entries) {
            this.entries.put(entry.getMetadata().getPackageVersion(), entry);
        }
    }

    @Override
    public Optional<LatestRelease> findLatest(
            String currentVersion,
            String analyzerVersion) {
        return entries.values().stream()
                .map(FixtureReleaseEntry::getMetadata)
                .filter(metadata -> ReleaseVersionSupport.compatible(
                        analyzerVersion, metadata.getMinimumAnalyzerVersion()))
                .filter(metadata -> currentVersion == null
                        || ReleaseVersionSupport.compare(
                                metadata.getPackageVersion(), currentVersion) > 0)
                .max(Comparator.comparing(
                        ReleaseMetadata::getPackageVersion,
                        ReleaseVersionSupport::compare))
                .map(this::latest);
    }

    @Override
    public Optional<ReleaseMetadata> findVersion(
            String packageVersion,
            String analyzerVersion) {
        FixtureReleaseEntry entry = entries.get(packageVersion);
        if (entry == null || !ReleaseVersionSupport.compatible(
                analyzerVersion, entry.getMetadata().getMinimumAnalyzerVersion())) {
            return Optional.empty();
        }
        return Optional.of(entry.getMetadata());
    }

    @Override
    public RulePackageArtifact download(String packageVersion) {
        FixtureReleaseEntry entry = entries.get(packageVersion);
        if (entry == null) {
            throw new IllegalArgumentException("unknown fixture release: " + packageVersion);
        }
        byte[] bytes = entry.getBytes();
        ReleaseMetadata metadata = entry.getMetadata();
        if (!sha256(bytes).equals(metadata.getArtifactSha256())) {
            throw new IllegalStateException("fixture artifact digest mismatch");
        }
        return new RulePackageArtifact(
                packageVersion,
                metadata.getContentSha256(),
                metadata.getArtifactSha256(),
                bytes);
    }

    private LatestRelease latest(ReleaseMetadata metadata) {
        return LatestRelease.builder()
                .packageVersion(metadata.getPackageVersion())
                .createdAt(metadata.getCreatedAt())
                .contentSha256(metadata.getContentSha256())
                .artifactSha256(metadata.getArtifactSha256())
                .minimumAnalyzerVersion(metadata.getMinimumAnalyzerVersion())
                .changeSummary(metadata.getChangeSummary())
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
