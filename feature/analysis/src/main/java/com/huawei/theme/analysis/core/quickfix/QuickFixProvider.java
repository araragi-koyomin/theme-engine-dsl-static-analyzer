package com.huawei.theme.analysis.core.quickfix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

public interface QuickFixProvider {
    List<FixAction> getFixActions(Diagnostic diagnostic);

    default List<FixAction> getFixActions(List<Diagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return Collections.emptyList();
        }
        List<FixAction> all = new ArrayList<>();
        for (Diagnostic diagnostic : diagnostics) {
            all.addAll(getFixActions(diagnostic));
        }
        return all;
    }
}
