package com.huawei.theme.analysis.core.macro;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

public final class DiagnosticDedup {

    private DiagnosticDedup() {
    }

    public static List<Diagnostic> dedup(@NotNull List<Diagnostic> diagnostics) {
        if (diagnostics.isEmpty()) {
            return diagnostics;
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Diagnostic> out = new ArrayList<>(diagnostics.size());
        for (Diagnostic d : diagnostics) {
            String key = d.getRuleId() + "|" + d.getLine() + ":" + d.getColumn()
                    + "|" + d.getEndLine() + ":" + d.getEndColumn()
                    + "|" + (d.getMessage() == null ? "" : d.getMessage());
            if (seen.add(key)) {
                out.add(d);
            }
        }
        return out;
    }
}
