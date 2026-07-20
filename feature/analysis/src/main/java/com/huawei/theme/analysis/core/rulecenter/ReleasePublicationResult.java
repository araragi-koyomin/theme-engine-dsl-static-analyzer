package com.huawei.theme.analysis.core.rulecenter;

import java.nio.file.Path;
import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReleasePublicationResult {
    String packageVersion;
    String tagName;
    Path rulePackageZip;
    List<Path> assets;
    String releaseNotes;
}
