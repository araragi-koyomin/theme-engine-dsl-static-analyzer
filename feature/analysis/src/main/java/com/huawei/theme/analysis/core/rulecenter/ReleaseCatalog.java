package com.huawei.theme.analysis.core.rulecenter;

import java.util.Optional;

public interface ReleaseCatalog {
    Optional<LatestRelease> findLatest(String currentVersion, String analyzerVersion);

    Optional<ReleaseMetadata> findVersion(String packageVersion, String analyzerVersion);

    RulePackageArtifact download(String packageVersion);
}
