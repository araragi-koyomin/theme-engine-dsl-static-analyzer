package com.huawei.theme.analysis.core.rulecenter;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

final class ApprovedReleaseVersionSelector {
    private static final String TAG_PREFIX = "rules-v";
    private static final Pattern VERSION = Pattern.compile("[0-9]+(?:\\.[0-9]+)+");

    private ApprovedReleaseVersionSelector() {
    }

    static String selectLatestTag(List<String> tags) {
        Objects.requireNonNull(tags, "tags");
        return tags.stream()
                .filter(Objects::nonNull)
                .filter(tag -> tag.startsWith(TAG_PREFIX))
                .filter(tag -> VERSION.matcher(version(tag)).matches())
                .max(Comparator.comparing(
                                ApprovedReleaseVersionSelector::version,
                                ReleaseVersionSupport::compare)
                        .thenComparing(Comparator.naturalOrder()))
                .orElse("");
    }

    static String selectLatestAndRequireNewer(List<String> tags, String packageVersion) {
        if (packageVersion == null || !VERSION.matcher(packageVersion).matches()) {
            throw new IllegalArgumentException("Package version must be numeric dot-separated");
        }
        String latestTag = selectLatestTag(tags);
        if (latestTag.isEmpty()) {
            return "";
        }
        String latestVersion = version(latestTag);
        if (ReleaseVersionSupport.compare(packageVersion, latestVersion) <= 0) {
            throw new IllegalArgumentException(
                    "Package version must be greater than latest approved version "
                            + latestVersion);
        }
        return latestTag;
    }

    private static String version(String tag) {
        return tag.substring(TAG_PREFIX.length());
    }
}
