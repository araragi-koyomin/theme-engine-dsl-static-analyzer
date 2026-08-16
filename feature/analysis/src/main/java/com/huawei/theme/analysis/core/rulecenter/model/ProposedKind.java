package com.huawei.theme.analysis.core.rulecenter.model;

import com.google.gson.annotations.SerializedName;

public enum ProposedKind {
    @SerializedName("description")
    DESCRIPTION,
    @SerializedName("constraint")
    CONSTRAINT,
    @SerializedName("skipped")
    SKIPPED
}
