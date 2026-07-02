package com.huawei.theme.analysis.core.quickfix;

import java.util.List;

import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

public interface FixActionGenerator {
    String getRuleId();

    List<FixAction> generate(Diagnostic diagnostic);
}
