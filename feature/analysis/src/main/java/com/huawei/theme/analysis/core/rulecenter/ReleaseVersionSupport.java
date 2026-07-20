package com.huawei.theme.analysis.core.rulecenter;

final class ReleaseVersionSupport {
    private ReleaseVersionSupport() {
    }

    static int compare(String left, String right) {
        int[] leftParts = parse(left);
        int[] rightParts = parse(right);
        if (leftParts == null || rightParts == null) {
            return left.compareTo(right);
        }
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            int leftValue = index < leftParts.length ? leftParts[index] : 0;
            int rightValue = index < rightParts.length ? rightParts[index] : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    static boolean compatible(String analyzerVersion, String minimumAnalyzerVersion) {
        if (minimumAnalyzerVersion == null || minimumAnalyzerVersion.isEmpty()) {
            return true;
        }
        int[] current = parse(analyzerVersion);
        int[] minimum = parse(minimumAnalyzerVersion);
        if (current == null || minimum == null) {
            return false;
        }
        return compare(analyzerVersion, minimumAnalyzerVersion) >= 0;
    }

    private static int[] parse(String version) {
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
}
