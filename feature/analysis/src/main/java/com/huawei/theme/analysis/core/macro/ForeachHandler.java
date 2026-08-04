package com.huawei.theme.analysis.core.macro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

/**
 * {@code <Foreach name="side" in="left,right,middle">…</Foreach>} — iterates the body
 * once per item in the comma-separated {@code in} list, binding the compile-time
 * variable {@code name} to the (string) item value. {@code in} is interpolated with
 * the active scope first, so it may reference outer compile-time vars. Empty items
 * (e.g. trailing comma) are skipped. The {@code <Foreach>} node itself is removed.
 */
public final class ForeachHandler implements MacroHandler {

    public static final String TAG = "Foreach";
    public static final String RULE_FOREACH_INVALID = "MACRO-004";

    private static final String NAME_ATTR = "name";
    private static final String IN_ATTR = "in";

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
        String name = attrValue(node, NAME_ATTR);
        String inRaw = attrValue(node, IN_ATTR);
        String filePath = builder.filePath();
        String inStr = CompileTimeInterpolator.interpolate(inRaw, scope, builder.diagnostics(), node, filePath);
        if (name == null || name.isEmpty() || inStr == null || inStr.isEmpty()) {
            builder.diagnostics().add(macro004(node, filePath, "<Foreach> requires non-empty name/in"));
            return List.of();
        }
        List<DslElementNode> result = new ArrayList<>();
        for (String item : inStr.split(",")) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Map<String, Object> childScope = new HashMap<>(scope);
            childScope.put(name, trimmed);
            if (node.getChildElements() != null) {
                for (DslElementNode child : node.getChildElements()) {
                    result.addAll(ctx.expandElement(child, childScope, builder));
                }
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

    private static Diagnostic macro004(@NotNull DslElementNode node, @NotNull String filePath, @NotNull String message) {
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_FOREACH_INVALID)
                .message(message)
                .filePath(filePath)
                .astNode(node)
                .build();
    }
}
