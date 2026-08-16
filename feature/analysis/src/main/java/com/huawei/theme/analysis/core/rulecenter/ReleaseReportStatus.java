package com.huawei.theme.analysis.core.rulecenter;

import com.google.gson.annotations.SerializedName;

public enum ReleaseReportStatus {
    @SerializedName("passed")
    PASSED,
    @SerializedName("passed-with-exclusions")
    PASSED_WITH_EXCLUSIONS,
    @SerializedName("failed")
    FAILED
}
