package com.huawei.theme.analysis.core.quickfix;

import lombok.Builder;
import lombok.Data;

import com.huawei.theme.analysis.core.shared.model.FixActionType;

@Data
@Builder
public class FixActionIntent {
    FixActionType actionType;
    String targetName;
    String targetValue;
    String description;
}
