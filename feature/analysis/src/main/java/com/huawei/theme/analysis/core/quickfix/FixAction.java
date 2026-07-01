package com.huawei.theme.analysis.core.quickfix;

import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Data;

import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;

@Data
@Builder
public class FixAction {
    String fixType;
    TextRange targetRange;
    String replacementText;
    @Builder.Default
    List<CandidateItem> candidates = Collections.emptyList();
    String description;
}
