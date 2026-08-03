package com.huawei.theme.analysis.core.macro;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

public interface MacroHandler {

    boolean recognize(@NotNull DslElementNode node);

    /**
     * Expand {@code node} into zero or more demacroed elements under the active
     * compile-time variable {@code scope}. Callers recurse into children via
     * {@link MacroExpander#expandElement}. Macro errors are appended to
     * {@code builder.diagnostics()}. Each produced demacroed node must be
     * registered via {@code builder.put(demacroed, normal)} so the
     * demacroed↔normal map stays consistent.
     */
    @NotNull
    List<DslElementNode> expand(@NotNull DslElementNode node,
                                @NotNull Map<String, Object> scope,
                                @NotNull MacroExpander ctx,
                                @NotNull DemacroedAst.Builder builder);
}
