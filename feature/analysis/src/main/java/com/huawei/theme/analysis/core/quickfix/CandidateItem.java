package com.huawei.theme.analysis.core.quickfix;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CandidateItem {
    String description;
    String previewText;
    double similarityScore;
}
