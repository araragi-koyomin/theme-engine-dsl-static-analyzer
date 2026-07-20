package com.huawei.theme.analysis.core.rulecenter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseCatalogContractTest {

    private static final String CONTENT_SHA_V1 = "1".repeat(64);
    private static final String CONTENT_SHA_V2 = "2".repeat(64);
    private static final byte[] PACKAGE_V1 = "package-v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PACKAGE_V2 = "package-v2".getBytes(StandardCharsets.UTF_8);

    @Test
    void githubAndFixtureBackendsExposeTheSameStableSemantics() {
        ReleaseCatalog github = githubCatalog(false);
        ReleaseCatalog fixture = fixtureCatalog();

        assertContract(github);
        assertContract(fixture);
        assertEquals(
                fixture.findLatest("2026.07.20.1", "1.2.0"),
                github.findLatest("2026.07.20.1", "1.2.0"));
    }

    @Test
    void draftGitHubReleaseNeverAppearsThroughGateway() {
        ReleaseCatalog github = githubCatalog(true);

        Optional<LatestRelease> latest = github.findLatest("2026.07.20.1", "1.2.0");

        assertTrue(latest.isEmpty());
    }

    @Test
    void adapterRejectsDownloadedBytesThatDoNotMatchGitHubAssetDigest() {
        GitHubReleaseSource source = new TestGitHubSource(
                List.of(release("2026.07.20.2", CONTENT_SHA_V2, PACKAGE_V2, false)),
                "tampered".getBytes(StandardCharsets.UTF_8));
        ReleaseCatalog catalog = new GitHubReleaseCatalogAdapter(
                source, new GitHubReleaseBackend());
        catalog.findLatest(null, "1.2.0");

        assertThrows(IllegalStateException.class, () -> catalog.download("2026.07.20.2"));
    }

    @Test
    void stablePluginContractContainsNoGitHubTransportFields() {
        List<String> fieldNames = Arrays.stream(LatestRelease.class.getDeclaredFields())
                .map(field -> field.getName().toLowerCase())
                .toList();
        List<String> methodNames = Arrays.stream(ReleaseCatalog.class.getDeclaredMethods())
                .map(method -> method.getName().toLowerCase())
                .toList();

        assertFalse(fieldNames.stream().anyMatch(this::isGitHubTransportName));
        assertFalse(methodNames.stream().anyMatch(this::isGitHubTransportName));
    }

    private void assertContract(ReleaseCatalog catalog) {
        Optional<LatestRelease> latest = catalog.findLatest("2026.07.20.1", "1.2.0");
        assertTrue(latest.isPresent());
        assertEquals("2026.07.20.2", latest.orElseThrow().getPackageVersion());
        assertEquals(CONTENT_SHA_V2, latest.orElseThrow().getContentSha256());
        assertEquals("changes-2026.07.20.2", latest.orElseThrow().getChangeSummary());

        assertTrue(catalog.findLatest("2026.07.20.2", "1.2.0").isEmpty());
        assertTrue(catalog.findLatest(null, "0.9.0").isEmpty());
        assertEquals(
                "2026.07.20.1",
                catalog.findVersion("2026.07.20.1", "1.2.0")
                        .orElseThrow()
                        .getPackageVersion());

        RulePackageArtifact artifact = catalog.download("2026.07.20.2");
        assertArrayEquals(PACKAGE_V2, artifact.getBytes());
        assertEquals(CONTENT_SHA_V2, artifact.getContentSha256());
        assertEquals(sha256(PACKAGE_V2), artifact.getArtifactSha256());
    }

    private ReleaseCatalog githubCatalog(boolean latestDraft) {
        List<GitHubReleaseDescriptor> releases = new ArrayList<>();
        releases.add(release("2026.07.20.1", CONTENT_SHA_V1, PACKAGE_V1, false));
        releases.add(release("2026.07.20.2", CONTENT_SHA_V2, PACKAGE_V2, latestDraft));
        return new GitHubReleaseCatalogAdapter(
                new TestGitHubSource(releases, null),
                new GitHubReleaseBackend());
    }

    private ReleaseCatalog fixtureCatalog() {
        return new FixtureReleaseCatalog(List.of(
                fixtureEntry("2026.07.20.1", CONTENT_SHA_V1, PACKAGE_V1),
                fixtureEntry("2026.07.20.2", CONTENT_SHA_V2, PACKAGE_V2)));
    }

    private FixtureReleaseEntry fixtureEntry(String version, String contentSha, byte[] bytes) {
        return FixtureReleaseEntry.builder()
                .metadata(ReleaseMetadata.builder()
                        .packageVersion(version)
                        .createdAt("2026-07-20T10:00:00Z")
                        .contentSha256(contentSha)
                        .artifactSha256(sha256(bytes))
                        .minimumAnalyzerVersion("1.0.0")
                        .changeSummary("changes-" + version)
                        .build())
                .bytes(bytes)
                .build();
    }

    private GitHubReleaseDescriptor release(
            String version,
            String contentSha,
            byte[] packageBytes,
            boolean draft) {
        String artifactSha = sha256(packageBytes);
        List<GitHubReleaseAsset> assets = new ArrayList<>();
        assets.add(GitHubReleaseAsset.builder()
                .name("rule-package.zip")
                .downloadUrl("memory://" + version)
                .state("uploaded")
                .digest("sha256:" + artifactSha)
                .build());
        String manifest = "{\"schemaVersion\":1,\"packageVersion\":\"" + version
                + "\",\"channel\":\"approved\","
                + "\"createdAt\":\"2026-07-20T10:00:00Z\","
                + "\"contentSha256\":\"" + contentSha + "\","
                + "\"minimumAnalyzerVersion\":\"1.0.0\","
                + "\"inventory\":{\"ruleFiles\":[\"rules/elements/view/Image.json\"],"
                + "\"functionFiles\":[\"functions/dsl_functions.json\"]},"
                + "\"sourceDocumentRevisions\":[]}";
        assets.add(GitHubReleaseAsset.builder()
                .name("manifest.json")
                .downloadUrl("memory://" + version + "/manifest")
                .state("uploaded")
                .digest("sha256:" + sha256(manifest.getBytes(StandardCharsets.UTF_8)))
                .content(manifest)
                .build());
        String report = "{\"packageVersion\":\"" + version + "\","
                + "\"manifestContentSha256\":\"" + contentSha + "\","
                + "\"status\":\"passed\",\"candidateCounts\":{},"
                + "\"candidatesByStatus\":{},\"carriedForwardCandidateIds\":[],"
                + "\"constraintVerifications\":[],\"jsonSchemaValid\":true,"
                + "\"packageComplete\":true,\"errors\":[]}";
        assets.add(GitHubReleaseAsset.builder()
                .name("release-report.json")
                .downloadUrl("memory://" + version + "/report")
                .state("uploaded")
                .digest("sha256:" + sha256(report.getBytes(StandardCharsets.UTF_8)))
                .content(report)
                .build());
        return GitHubReleaseDescriptor.builder()
                .tagName("rules-v" + version)
                .draft(draft)
                .prerelease(false)
                .publishedAt("2026-07-20T10:05:00Z")
                .body("changes-" + version)
                .assets(assets)
                .build();
    }

    private boolean isGitHubTransportName(String name) {
        return name.contains("github")
                || name.contains("url")
                || name.contains("token")
                || name.contains("repository");
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class TestGitHubSource implements GitHubReleaseSource {
        private final List<GitHubReleaseDescriptor> releases;
        private final byte[] forcedBytes;

        private TestGitHubSource(List<GitHubReleaseDescriptor> releases, byte[] forcedBytes) {
            this.releases = releases;
            this.forcedBytes = forcedBytes;
        }

        @Override
        public List<GitHubReleaseDescriptor> listReleases() {
            return releases;
        }

        @Override
        public byte[] download(String packageDownloadUrl) {
            if (forcedBytes != null) {
                return forcedBytes;
            }
            return packageDownloadUrl.endsWith("2026.07.20.1")
                    ? PACKAGE_V1 : PACKAGE_V2;
        }
    }
}
