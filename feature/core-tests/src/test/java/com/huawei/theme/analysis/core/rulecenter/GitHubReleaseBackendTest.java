package com.huawei.theme.analysis.core.rulecenter;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubReleaseBackendTest {

    private static final String CONTENT_SHA = "a".repeat(64);
    private static final String ARTIFACT_SHA = "b".repeat(64);
    private final GitHubReleaseBackend backend = new GitHubReleaseBackend();

    @Test
    void mapsApprovedReleaseAndKeepsGitHubFieldsInsideBackend() {
        GitHubReleaseEvaluation result = backend.evaluate(release(), "1.2.0");

        assertTrue(result.isApproved());
        assertEquals(null, result.getRejection());
        assertEquals("2026.07.20.1", result.getRelease().getPackageVersion());
        assertEquals(CONTENT_SHA, result.getRelease().getContentSha256());
        assertEquals(ARTIFACT_SHA, result.getRelease().getArtifactSha256());
        assertEquals("https://github.test/rule-package.zip",
                result.getRelease().getPackageDownloadUrl());
        assertEquals("DSL rule changes", result.getRelease().getChangeSummary());
    }

    @Test
    void rejectsDraftAndPrerelease() {
        GitHubReleaseDescriptor draft = release();
        draft.setDraft(true);
        GitHubReleaseDescriptor prerelease = release();
        prerelease.setPrerelease(true);

        assertRejected(draft, GitHubReleaseRejection.NOT_APPROVED_RELEASE);
        assertRejected(prerelease, GitHubReleaseRejection.NOT_APPROVED_RELEASE);
    }

    @Test
    void rejectsFailedReportAndMissingFixedAsset() {
        GitHubReleaseDescriptor failed = release();
        asset(failed, "release-report.json").setContent(reportJson("failed"));
        GitHubReleaseDescriptor missing = release();
        missing.getAssets().removeIf(asset -> asset.getName().equals("manifest.json"));

        assertRejected(failed, GitHubReleaseRejection.REPORT_NOT_PUBLISHABLE);
        assertRejected(missing, GitHubReleaseRejection.MISSING_REQUIRED_ASSET);
    }

    @Test
    void rejectsTagDigestAndCompatibilityMismatch() {
        GitHubReleaseDescriptor tagMismatch = release();
        tagMismatch.setTagName("rules-v2026.07.20.2");
        GitHubReleaseDescriptor digestMismatch = release();
        asset(digestMismatch, "release-report.json")
                .setContent(reportJson("passed").replace(CONTENT_SHA, "c".repeat(64)));

        assertRejected(tagMismatch, GitHubReleaseRejection.VERSION_MISMATCH);
        assertRejected(digestMismatch, GitHubReleaseRejection.DIGEST_MISMATCH);
        assertRejected(release(), "0.9.0", GitHubReleaseRejection.INCOMPATIBLE_ANALYZER);
    }

    @Test
    void rejectsPackageAssetWithoutGitHubSha256Digest() {
        GitHubReleaseDescriptor release = release();
        asset(release, "rule-package.zip").setDigest(null);

        assertRejected(release, GitHubReleaseRejection.INVALID_ASSET_DIGEST);
    }

    private GitHubReleaseDescriptor release() {
        List<GitHubReleaseAsset> assets = new ArrayList<>();
        assets.add(GitHubReleaseAsset.builder()
                .name("rule-package.zip")
                .downloadUrl("https://github.test/rule-package.zip")
                .state("uploaded")
                .digest("sha256:" + ARTIFACT_SHA)
                .build());
        assets.add(GitHubReleaseAsset.builder()
                .name("manifest.json")
                .downloadUrl("https://github.test/manifest.json")
                .state("uploaded")
                .digest("sha256:" + "d".repeat(64))
                .content(manifestJson())
                .build());
        assets.add(GitHubReleaseAsset.builder()
                .name("release-report.json")
                .downloadUrl("https://github.test/release-report.json")
                .state("uploaded")
                .digest("sha256:" + "e".repeat(64))
                .content(reportJson("passed"))
                .build());
        return GitHubReleaseDescriptor.builder()
                .tagName("rules-v2026.07.20.1")
                .draft(false)
                .prerelease(false)
                .publishedAt("2026-07-20T10:05:00Z")
                .body("DSL rule changes")
                .assets(assets)
                .build();
    }

    private String manifestJson() {
        return "{"
                + "\"schemaVersion\":1,"
                + "\"packageVersion\":\"2026.07.20.1\","
                + "\"channel\":\"approved\","
                + "\"createdAt\":\"2026-07-20T10:00:00Z\","
                + "\"contentSha256\":\"" + CONTENT_SHA + "\","
                + "\"minimumAnalyzerVersion\":\"1.0.0\","
                + "\"sourceDocumentRevisions\":[]}";
    }

    private String reportJson(String status) {
        return "{"
                + "\"packageVersion\":\"2026.07.20.1\","
                + "\"manifestContentSha256\":\"" + CONTENT_SHA + "\","
                + "\"status\":\"" + status + "\","
                + "\"candidateCounts\":{},\"candidatesByStatus\":{},"
                + "\"carriedForwardCandidateIds\":[],"
                + "\"constraintVerifications\":[],"
                + "\"jsonSchemaValid\":true,\"packageComplete\":true,\"errors\":[]}";
    }

    private GitHubReleaseAsset asset(GitHubReleaseDescriptor release, String name) {
        return release.getAssets().stream()
                .filter(asset -> asset.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private void assertRejected(
            GitHubReleaseDescriptor release,
            GitHubReleaseRejection expected) {
        assertRejected(release, "1.2.0", expected);
    }

    private void assertRejected(
            GitHubReleaseDescriptor release,
            String analyzerVersion,
            GitHubReleaseRejection expected) {
        GitHubReleaseEvaluation result = backend.evaluate(release, analyzerVersion);

        assertFalse(result.isApproved());
        assertEquals(expected, result.getRejection());
        assertEquals(null, result.getRelease());
    }
}
