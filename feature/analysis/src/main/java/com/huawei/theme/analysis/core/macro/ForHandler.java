package com.huawei.theme.analysis.core.macro;

import java.math.BigDecimal;
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

public final class ForHandler implements MacroHandler {

    public static final String TAG = "For";
    public static final String RULE_FOR_INVALID = "MACRO-002";

    private static final String NAME_ATTR = "name";
    private static final String FROM_ATTR = "from";
    private static final String TO_ATTR = "to";

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
        String fromRaw = attrValue(node, FROM_ATTR);
        String toRaw = attrValue(node, TO_ATTR);
        String filePath = builder.filePath();

        String fromStr = CompileTimeInterpolator.interpolate(fromRaw, scope, builder.diagnostics(), node, filePath);
        String toStr = CompileTimeInterpolator.interpolate(toRaw, scope, builder.diagnostics(), node, filePath);

        if (name == null || name.isEmpty() || fromStr == null || toStr == null) {
            builder.diagnostics().add(macro002(node, filePath, "<For> requires non-empty name/from/to"));
            return List.of();
        }
        Integer from = parseInt(fromStr);
        Integer to = parseInt(toStr);
        if (from == null) {
            builder.diagnostics().add(macro002(node, filePath, "<For> from=\"" + fromStr + "\" is not an integer"));
            return List.of();
        }
        if (to == null) {
            builder.diagnostics().add(macro002(node, filePath, "<For> to=\"" + toStr + "\" is not an integer"));
            return List.of();
        }

        List<DslElementNode> result = new ArrayList<>();
        for (long v = from; v <= to; v++) {
            if (!builder.tryConsumeLoopIteration(node)) {
                break;
            }
            Map<String, Object> childScope = new HashMap<>(scope);
            childScope.put(name, BigDecimal.valueOf(v));
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

    @Nullable
    private static Integer parseInt(@NotNull String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Diagnostic macro002(@NotNull DslElementNode node, @NotNull String filePath, @NotNull String message) {
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(RULE_FOR_INVALID)
                .message(message)
                .filePath(filePath)
                .astNode(node)
                .build();
    }
}
