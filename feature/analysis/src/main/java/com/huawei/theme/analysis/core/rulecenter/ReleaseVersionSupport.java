package com.huawei.theme.analysis.core.rulecenter;

import java.math.BigInteger;

final class ReleaseVersionSupport {
    private ReleaseVersionSupport() {
    }

    static int compare(String left, String right) {
        BigInteger[] leftParts = parse(left);
        BigInteger[] rightParts = parse(right);
        if (leftParts == null || rightParts == null) {
            return left.compareTo(right);
        }
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            BigInteger leftValue = index < leftParts.length
                    ? leftParts[index] : BigInteger.ZERO;
            BigInteger rightValue = index < rightParts.length
                    ? rightParts[index] : BigInteger.ZERO;
            int comparison = leftValue.compareTo(rightValue);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    static boolean compatible(String analyzerVersion, String minimumAnalyzerVersion) {
        if (minimumAnalyzerVersion == null || minimumAnalyzerVersion.isEmpty()) {
            return true;
        }
        BigInteger[] current = parse(analyzerVersion);
        BigInteger[] minimum = parse(minimumAnalyzerVersion);
        if (current == null || minimum == null) {
            return false;
        }
        return compare(analyzerVersion, minimumAnalyzerVersion) >= 0;
    }

    private static BigInteger[] parse(String version) {
        if (version == null || version.isEmpty()) {
            return null;
        }
        String[] parts = version.split("\\.");
        BigInteger[] values = new BigInteger[parts.length];
        try {
            for (int index = 0; index < parts.length; index++) {
                if (!parts[index].matches("\\d+")) {
                    return null;
                }
                values[index] = new BigInteger(parts[index]);
            }
            return values;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
