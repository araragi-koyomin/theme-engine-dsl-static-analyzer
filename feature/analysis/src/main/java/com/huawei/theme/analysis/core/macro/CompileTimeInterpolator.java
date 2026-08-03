package com.huawei.theme.analysis.core.macro;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.ezylang.evalex.Expression;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

public final class CompileTimeInterpolator {

    public static final String RULE_INTERP_FAIL = "MACRO-001";

    private CompileTimeInterpolator() {
    }

    public static String interpolate(@Nullable String value, @NotNull Map<String, Object> scope,
                                     @NotNull List<Diagnostic> diagnostics, @NotNull DslAstNode errorAnchor,
                                     @NotNull String filePath) {
        if (value == null || value.indexOf('%') < 0) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length());
        int i = 0;
        int n = value.length();
        while (i < n) {
            char c = value.charAt(i);
            if (c == '%' && i + 1 < n && value.charAt(i + 1) == '{') {
                int end = value.indexOf('}', i + 2);
                if (end < 0) {
                    out.append(value, i, n);
                    break;
                }
                String expr = value.substring(i + 2, end).trim();
                try {
                    String result = new Expression(expr).withValues(scope).evaluate().getStringValue();
                    if (result == null) {
                        throw new IllegalStateException("interpolation produced null");
                    }
                    out.append(result);
                } catch (Exception e) {
                    diagnostics.add(Diagnostic.builder()
                            .severity(DiagnosticSeverity.ERROR)
                            .ruleId(RULE_INTERP_FAIL)
                            .message("Compile-time interpolation failed: %{" + expr + "} — " + e.getMessage())
                            .filePath(filePath)
                            .astNode(errorAnchor)
                            .build());
                    out.append(value, i, end + 1);
                }
                i = end + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    public static boolean hasInterpolation(@Nullable String value) {
        if (value == null || value.length() < 3) {
            return false;
        }
        int idx = value.indexOf('%');
        while (idx >= 0 && idx + 1 < value.length()) {
            if (value.charAt(idx + 1) == '{') {
                return true;
            }
            idx = value.indexOf('%', idx + 1);
        }
        return false;
    }
}
