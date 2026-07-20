package com.huawei.theme.analysis.core.rulecenter.model;

import com.google.gson.annotations.SerializedName;

public enum TargetKind {
    @SerializedName("element")
    ELEMENT,
    @SerializedName("elementAttribute")
    ELEMENT_ATTRIBUTE,
    @SerializedName("parentChildRelation")
    PARENT_CHILD_RELATION,
    @SerializedName("globalVariable")
    GLOBAL_VARIABLE,
    @SerializedName("functionSignature")
    FUNCTION_SIGNATURE,
    @SerializedName("ruleSource")
    RULE_SOURCE
}
