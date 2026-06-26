package com.huawei.theme.analysis.core.shared.diagnostic;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TextRange {
    int startLine;
    int startColumn;
    int endLine;
    int endColumn;
}
