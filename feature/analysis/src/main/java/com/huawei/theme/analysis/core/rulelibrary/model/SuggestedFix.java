package com.huawei.theme.analysis.core.rulelibrary.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SuggestedFix {
    String text;
    String type;
    String target;
    String value;
    String range;
}
