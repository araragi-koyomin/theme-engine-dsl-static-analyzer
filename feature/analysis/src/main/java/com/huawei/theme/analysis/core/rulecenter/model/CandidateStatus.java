package com.huawei.theme.analysis.core.rulecenter.model;

import com.google.gson.annotations.SerializedName;

public enum CandidateStatus {
    @SerializedName("extracted")
    EXTRACTED,
    @SerializedName("skipped")
    SKIPPED,
    @SerializedName("validating")
    VALIDATING,
    @SerializedName("repairing")
    REPAIRING,
    @SerializedName("validation-error")
    VALIDATION_ERROR,
    @SerializedName("verified")
    VERIFIED,
    @SerializedName("published")
    PUBLISHED
}
