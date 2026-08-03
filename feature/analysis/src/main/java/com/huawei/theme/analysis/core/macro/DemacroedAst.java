package com.huawei.theme.analysis.core.macro;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

public final class DemacroedAst {

    private final DslFileNode demacroed;
    private final IdentityHashMap<DslElementNode, DslElementNode> demacroedToNormal;
    private final IdentityHashMap<DslElementNode, List<DslElementNode>> normalToDemacroed;
    private final IdentityHashMap<DslElementNode, Map<String, Object>> scopeByDemacroedNode;
    private final List<Diagnostic> macroDiagnostics;

    DemacroedAst(@NotNull DslFileNode demacroed,
                 @NotNull IdentityHashMap<DslElementNode, DslElementNode> demacroedToNormal,
                 @NotNull IdentityHashMap<DslElementNode, List<DslElementNode>> normalToDemacroed,
                 @NotNull IdentityHashMap<DslElementNode, Map<String, Object>> scopeByDemacroedNode,
                 @NotNull List<Diagnostic> macroDiagnostics) {
        this.demacroed = demacroed;
        this.demacroedToNormal = demacroedToNormal;
        this.normalToDemacroed = normalToDemacroed;
        this.scopeByDemacroedNode = scopeByDemacroedNode;
        this.macroDiagnostics = macroDiagnostics;
    }

    public DslFileNode getDemacroed() {
        return demacroed;
    }

    public Optional<DslElementNode> getNormalNode(@Nullable DslElementNode demacroedNode) {
        if (demacroedNode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(demacroedToNormal.get(demacroedNode));
    }

    public List<DslElementNode> getDemacroedNodes(@Nullable DslElementNode normalNode) {
        if (normalNode == null) {
            return Collections.emptyList();
        }
        return normalToDemacroed.getOrDefault(normalNode, Collections.emptyList());
    }

    /**
     * The active compile-time variable scope when this demacroed node was produced
     * (e.g. {@code {i:1}} for the first expansion of {@code <For name="i" from="1" to="3">}).
     * Empty for non-macro nodes. Used by the editor to interpolate raw reference names
     * like {@code v_%{i}} to the copy's concrete {@code v_1} at resolve time.
     */
    public Map<String, Object> getCompileScope(@Nullable DslElementNode demacroedNode) {
        if (demacroedNode == null) {
            return Collections.emptyMap();
        }
        return scopeByDemacroedNode.getOrDefault(demacroedNode, Collections.emptyMap());
    }

    public List<Diagnostic> getMacroDiagnostics() {
        return macroDiagnostics;
    }

    static Builder builder(@NotNull String filePath) {
        return new Builder(filePath);
    }

    static final class Builder {
        final String filePath;
        final IdentityHashMap<DslElementNode, DslElementNode> demacroedToNormal = new IdentityHashMap<>();
        final IdentityHashMap<DslElementNode, List<DslElementNode>> normalToDemacroed = new IdentityHashMap<>();
        final IdentityHashMap<DslElementNode, Map<String, Object>> scopeByDemacroedNode = new IdentityHashMap<>();
        final List<Diagnostic> diagnostics = new java.util.ArrayList<>();

        Builder(@NotNull String filePath) {
            this.filePath = filePath;
        }

        void put(@NotNull DslElementNode demacroed, @NotNull DslElementNode normal) {
            demacroedToNormal.put(demacroed, normal);
            normalToDemacroed.computeIfAbsent(normal, k -> new java.util.ArrayList<>()).add(demacroed);
        }

        void recordScope(@NotNull DslElementNode demacroed, @NotNull Map<String, Object> scope) {
            scopeByDemacroedNode.put(demacroed, scope);
        }

        List<Diagnostic> diagnostics() {
            return diagnostics;
        }

        String filePath() {
            return filePath;
        }

        DemacroedAst build(@NotNull DslFileNode demacroed) {
            return new DemacroedAst(demacroed, demacroedToNormal, normalToDemacroed,
                    scopeByDemacroedNode, diagnostics);
        }
    }
}
