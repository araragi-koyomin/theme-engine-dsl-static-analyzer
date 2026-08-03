package com.huawei.theme.analysis.core.macro;

import java.util.ArrayList;
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
        return interpolateWithSourceMap(value, scope, diagnostics, errorAnchor, filePath).getValue();
    }

    @NotNull
    public static InterpolationResult interpolateWithSourceMap(
            @Nullable String value,
            @NotNull Map<String, Object> scope,
            @NotNull List<Diagnostic> diagnostics,
            @NotNull DslAstNode errorAnchor,
            @NotNull String filePath) {
        if (value == null || value.indexOf('%') < 0) {
            return InterpolationResult.identity(value);
        }
        StringBuilder out = new StringBuilder(value.length());
        List<Integer> sourceOffsets = new ArrayList<>(value.length() + 1);
        sourceOffsets.add(0);
        int i = 0;
        int n = value.length();
        while (i < n) {
            char c = value.charAt(i);
            if (c == '%' && i + 1 < n && value.charAt(i + 1) == '{') {
                int end = value.indexOf('}', i + 2);
                if (end < 0) {
                    addFailure(diagnostics, errorAnchor, filePath,
                            "Compile-time interpolation is not closed: " + value.substring(i));
                    appendIdentity(out, sourceOffsets, value, i, n);
                    break;
                }
                String expr = value.substring(i + 2, end).trim();
                try {
                    String result = new Expression(expr).withValues(scope).evaluate().getStringValue();
                    if (result == null) {
                        throw new IllegalStateException("interpolation produced null");
                    }
                    appendReplacement(out, sourceOffsets, result, i, end + 1);
                } catch (Exception e) {
                    addFailure(diagnostics, errorAnchor, filePath,
                            "Compile-time interpolation failed: %{" + expr + "} — " + e.getMessage());
                    appendIdentity(out, sourceOffsets, value, i, end + 1);
                }
                i = end + 1;
            } else {
                appendIdentity(out, sourceOffsets, value, i, i + 1);
                i++;
            }
        }
        return new InterpolationResult(out.toString(), toArray(sourceOffsets));
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

    private static void appendIdentity(StringBuilder out, List<Integer> sourceOffsets,
                                       String source, int start, int end) {
        for (int i = start; i < end; i++) {
            out.append(source.charAt(i));
            sourceOffsets.add(i + 1);
        }
    }

    private static void appendReplacement(StringBuilder out, List<Integer> sourceOffsets,
                                          String replacement, int sourceStart, int sourceEnd) {
        if (replacement.isEmpty()) {
            sourceOffsets.set(sourceOffsets.size() - 1, sourceEnd);
            return;
        }
        for (int i = 0; i < replacement.length(); i++) {
            out.append(replacement.charAt(i));
            sourceOffsets.add(i == replacement.length() - 1 ? sourceEnd : sourceStart);
        }
    }

    private static void addFailure(List<Diagnostic> diagnostics, DslAstNode errorAnchor,
                                   String filePath, String message) {
        diagnostics.add(Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_INTERP_FAIL)
                .message(message)
                .filePath(filePath)
                .astNode(errorAnchor)
                .build());
    }

    private static int[] toArray(List<Integer> offsets) {
        int[] result = new int[offsets.size()];
        for (int i = 0; i < offsets.size(); i++) {
            result[i] = offsets.get(i);
        }
        return result;
    }

    public static final class InterpolationResult {
        private final String value;
        private final int[] sourceOffsets;

        private InterpolationResult(@Nullable String value, int[] sourceOffsets) {
            this.value = value;
            this.sourceOffsets = sourceOffsets.clone();
        }

        private static InterpolationResult identity(@Nullable String value) {
            if (value == null) {
                return new InterpolationResult(null, new int[]{0});
            }
            int[] offsets = new int[value.length() + 1];
            for (int i = 0; i <= value.length(); i++) {
                offsets[i] = i;
            }
            return new InterpolationResult(value, offsets);
        }

        @Nullable
        public String getValue() {
            return value;
        }

        public int sourceOffsetAt(int outputOffset) {
            int bounded = Math.max(0, Math.min(outputOffset, sourceOffsets.length - 1));
            return sourceOffsets[bounded];
        }
    }
}
