package com.huawei.theme.analysis.core.rulelibrary.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuggestedFix {
    String text;
    String type;
    String target;
    String value;
    String range;
}
