package com.huawei.theme.analysis.core.macro;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

import com.huawei.theme.analysis.core.expression.ExpressionNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

/**
 * {@code <Include name="function_xxx.xml" param1="v1" param2="v2">} — loads the named
 * sub-file, parses + demacros it with the param key/value pairs as compile-time variables
 * in its scope, and inserts the sub-file's root element in place of the {@code <Include>}.
 *
 * <ul>
 *   <li>{@code name} must match {@code function_*.xml} (else MACRO-006); it is interpolated
 *       with the active scope first, so it can reference outer compile-time vars.</li>
 *   <li>every other attribute becomes a compile-time var for the sub-file's scope, its value
 *       interpolated with the active (outer) scope before being bound.</li>
 *   <li>Include has an independent total budget and a stack-safety nesting limit.</li>
 *   <li>every demacoed sub node's position is remapped to the {@code <Include>} node's
 *       position, so diagnostics raised inside the sub-file land on the include site
 *       (per the "highlight on the Include node" requirement) and are deduped by
 *       {@link DiagnosticDedup}.</li>
 *   <li>each sub normal node is tagged with the sub-file's path via
 *       {@link DemacroedAst.Builder#recordFile} so the editor can resolve a demacoed
 *       declaration back to the right per-file PSI.</li>
 * </ul>
 *
 * <p>Recursion (a function file that itself contains {@code <Include>}) is handled by the
 * recursive {@link MacroExpander#expandElement} call. Sub-file not found → MACRO-007.</p>
 */
public final class IncludeHandler implements MacroHandler {

    public static final String TAG = "Include";
    public static final String RULE_INCLUDE_INVALID_NAME = "MACRO-006";
    public static final String RULE_INCLUDE_NOT_FOUND = "MACRO-007";

    private static final String NAME_ATTR = "name";
    private static final Pattern FUNCTION_FILE = Pattern.compile("function_[^/\\\\]+\\.xml");

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
        String filePath = builder.filePath();
        String nameRaw = attrValue(node, NAME_ATTR);
        String name = CompileTimeInterpolator.interpolate(nameRaw, scope, builder.diagnostics(), node, filePath);
        if (name == null || name.isEmpty()) {
            builder.diagnostics().add(macro(filePath, RULE_INCLUDE_INVALID_NAME, node,
                    "<Include> requires a non-empty name"));
            return List.of();
        }
        if (!FUNCTION_FILE.matcher(name).matches()) {
            builder.diagnostics().add(macro(filePath, RULE_INCLUDE_INVALID_NAME, node,
                    "<Include> name=\"" + name + "\" must match function_*.xml"));
            return List.of();
        }
        String subPath = resolveSibling(filePath, name);
        String content = ctx.loadFile(subPath);
        if (content == null) {
            builder.diagnostics().add(macro(filePath, RULE_INCLUDE_NOT_FOUND, node,
                    "<Include> file not found: " + name));
            return List.of();
        }
        DslFileNode subNormal = ctx.buildNormalAst(subPath, content, builder);
        if (subNormal.getRootElement() == null) {
            builder.diagnostics().add(macro(filePath, RULE_INCLUDE_NOT_FOUND, node,
                    "<Include> file unparseable: " + name));
            return List.of();
        }
        recordFileForTree(subNormal.getRootElement(), subPath, builder);

        Map<String, Object> paramScope = new HashMap<>();
        if (node.getAttributes() != null) {
            for (DslAttributeNode a : node.getAttributes()) {
                if (!NAME_ATTR.equals(a.getName()) && a.getValue() != null && a.getName() != null) {
                    String v = a.getValue().getRawValue();
                    String interpolated = CompileTimeInterpolator.interpolate(
                            v, scope, builder.diagnostics(), a, filePath);
                    paramScope.put(a.getName(), interpolated != null ? interpolated : "");
                }
            }
        }

        int includeInstanceId = builder.tryBeginInclude(subPath, node, paramScope);
        if (includeInstanceId < 0) {
            return List.of();
        }
        List<DslElementNode> demacroedSub;
        try {
            demacroedSub = ctx.expandElement(subNormal.getRootElement(), paramScope, builder);
        } finally {
            builder.endInclude(includeInstanceId);
        }
        for (DslElementNode sub : demacroedSub) {
            remapPositions(sub, node);
        }
        return demacroedSub;
    }

    private static void recordFileForTree(@NotNull DslElementNode node, @NotNull String filePath,
                                          @NotNull DemacroedAst.Builder builder) {
        builder.recordFile(node, filePath);
        if (node.getChildElements() != null) {
            for (DslElementNode c : node.getChildElements()) {
                recordFileForTree(c, filePath, builder);
            }
        }
    }

    private static void remapPositions(@NotNull DslElementNode node, @NotNull DslElementNode anchor) {
        node.setLine(anchor.getLine());
        node.setColumn(anchor.getColumn());
        node.setEndLine(anchor.getEndLine());
        node.setEndColumn(anchor.getEndColumn());
        if (node.getAttributes() != null) {
            for (DslAttributeNode a : node.getAttributes()) {
                a.setLine(anchor.getLine());
                a.setColumn(anchor.getColumn());
                a.setEndLine(anchor.getEndLine());
                a.setEndColumn(anchor.getEndColumn());
                if (a.getValue() != null) {
                    a.getValue().setLine(anchor.getLine());
                    a.getValue().setColumn(anchor.getColumn());
                    a.getValue().setEndLine(anchor.getEndLine());
                    a.getValue().setEndColumn(anchor.getEndColumn());
                    // Also remap the expression AST tree inside the value, so that diagnostics
                    // raised on expression sub-nodes (e.g. SEM-REF-001 for an undefined #var)
                    // land on the <Include> node, not the sub-file's source positions.
                    a.getValue().getExpression().ifPresent(expr -> {
                        if (expr instanceof ExpressionNode exprNode) {
                            remapExpressionPositions(exprNode, anchor);
                        }
                    });
                }
            }
        }
        if (node.getChildElements() != null) {
            for (DslElementNode c : node.getChildElements()) {
                remapPositions(c, anchor);
            }
        }
    }

    private static void remapExpressionPositions(@NotNull ExpressionNode node, @NotNull DslElementNode anchor) {
        node.setLine(anchor.getLine());
        node.setColumn(anchor.getColumn());
        node.setEndLine(anchor.getEndLine());
        node.setEndColumn(anchor.getEndColumn());
        if (node.getChildren() != null) {
            for (ExpressionNode child : node.getChildren()) {
                remapExpressionPositions(child, anchor);
            }
        }
        if (node.getIndexExpression() != null) {
            remapExpressionPositions(node.getIndexExpression(), anchor);
        }
    }

    private static String resolveSibling(String mainPath, String name) {
        int slash = Math.max(mainPath.lastIndexOf('/'), mainPath.lastIndexOf('\\'));
        if (slash < 0) {
            return name;
        }
        return mainPath.substring(0, slash + 1) + name;
    }

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

    private static Diagnostic macro(@NotNull String filePath, @NotNull String ruleId,
                                    @NotNull DslElementNode node, @NotNull String message) {
        return Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId(ruleId)
                .message(message)
                .filePath(filePath)
                .astNode(node)
                .build();
    }
}
