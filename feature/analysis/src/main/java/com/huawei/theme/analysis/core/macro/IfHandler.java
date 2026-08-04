package com.huawei.theme.analysis.core.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.ezylang.evalex.Expression;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

/**
 * {@code <If cond="i%2==1">…</If>} — keeps the body when the compile-time boolean
 * {@code cond} evaluates true, removes it when false. The {@code cond} is an EvalEx
 * expression evaluated against the active compile-time scope (same engine as
 * {@code %{...}} interpolation), so it can reference any in-scope compile-time
 * variable by name (e.g. {@code i}, {@code side}). The {@code <If>} node itself is
 * removed; on evaluation failure a MACRO-003 diagnostic is emitted and the body
 * is dropped (to avoid cascading errors from an unresolved condition).
 */
public final class IfHandler implements MacroHandler {

    public static final String TAG = "If";
    public static final String RULE_IF_COND_FAIL = "MACRO-003";

    private static final String COND_ATTR = "cond";

    @Override
    public boolean recognize(@NotNull DslElementNode node) {
        return TAG.equals(node.getTagName());
    }

    @Override
    @NotNull
    public List<DslElementNode> expand(@NotNull DslElementNode node,
                                      @NotNull Map<String, Object> scope,
                                      @NotNull MacroExpander ctx,
                                      @NotNull DemacroedAst.Builder builder) {
        String condRaw = attrValue(node, COND_ATTR);
        String filePath = builder.filePath();
        if (condRaw == null || condRaw.isEmpty()) {
            builder.diagnostics().add(macro003(node, filePath, "<If> requires a non-empty cond"));
            return List.of();
        }
        boolean keep;
        try {
            keep = new Expression(condRaw).withValues(scope).evaluate().getBooleanValue();
        } catch (Exception e) {
            builder.diagnostics().add(macro003(node, filePath,
                    "<If> cond=\"" + condRaw + "\" failed: " + e.getMessage()));
            return List.of();
        }
        if (!keep) {
            return List.of();
        }
        List<DslElementNode> result = new ArrayList<>();
        if (node.getChildElements() != null) {
            for (DslElementNode child : node.getChildElements()) {
                result.addAll(ctx.expandElement(child, scope, builder));
            }
        }
        return result;
    }

    @Nullable
    private static String attrValue(@NotNull DslElementNode node, @NotNull String attrName) {
        if (node.getAttributes() == null) {
            return null;
        }
        for (DslAttributeNode a : node.getAttributes()) {
            if (attrName.equals(a.getName()) && a.getValue() != null) {
                return a.getValue().getRawValue();
            }
        }
        return null;
    }

    private static Diagnostic macro003(@NotNull DslElementNode node, @NotNull String filePath, @NotNull String message) {
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_IF_COND_FAIL)
                .message(message)
                .filePath(filePath)
                .astNode(node)
                .build();
    }
}
