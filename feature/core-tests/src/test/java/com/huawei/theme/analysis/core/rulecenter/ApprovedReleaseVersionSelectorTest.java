package com.huawei.theme.analysis.core.rulecenter;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApprovedReleaseVersionSelectorTest {

    @Test
    void selectsHighestNumericVersionIndependentOfInputOrder() {
        assertEquals("rules-v10.0.0", ApprovedReleaseVersionSelector.selectLatestTag(List.of(
                "rules-v2.20.0", "not-a-rule-tag", "rules-v10.0.0", "rules-v2.100.0")));
    }

    @Test
    void rejectsCandidateEquivalentAfterZeroPadding() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ApprovedReleaseVersionSelector.selectLatestAndRequireNewer(
                        List.of("rules-v1.0"), "1.0.0"));

        assertEquals("Package version must be greater than latest approved version 1.0",
                error.getMessage());
    }

    @Test
    void acceptsStrictlyHigherCandidateAndReturnsLatestTag() {
        assertEquals("rules-v2.100.0",
                ApprovedReleaseVersionSelector.selectLatestAndRequireNewer(
                        List.of("rules-v2.20.0", "rules-v2.100.0"), "2.100.1"));
        assertEquals("", ApprovedReleaseVersionSelector.selectLatestAndRequireNewer(
                List.of(), "1.0.0"));
    }
}
