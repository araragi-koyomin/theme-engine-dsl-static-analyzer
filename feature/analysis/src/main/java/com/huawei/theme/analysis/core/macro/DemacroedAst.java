package com.huawei.theme.analysis.core.macro;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

public final class DemacroedAst {

    private final DslFileNode demacroed;
    private final String mainFilePath;
    private final Map<DslElementNode, DslElementNode> demacroedToNormal;
    private final Map<DslElementNode, List<DslElementNode>> normalToDemacroed;
    private final Map<DslElementNode, Map<String, Object>> scopeByDemacroedNode;
    private final Map<DslElementNode, String> normalNodeFilePath;
    private final List<Diagnostic> macroDiagnostics;
    private final List<IncludeInstance> includeInstances;
    private final Map<DslElementNode, IncludeInstance> includeInstanceByNode;

    DemacroedAst(@NotNull DslFileNode demacroed,
                 @NotNull String mainFilePath,
                 @NotNull IdentityHashMap<DslElementNode, DslElementNode> demacroedToNormal,
                 @NotNull IdentityHashMap<DslElementNode, List<DslElementNode>> normalToDemacroed,
                 @NotNull IdentityHashMap<DslElementNode, Map<String, Object>> scopeByDemacroedNode,
                 @NotNull IdentityHashMap<DslElementNode, String> normalNodeFilePath,
                 @NotNull List<Diagnostic> macroDiagnostics,
                 @NotNull List<Builder.MutableIncludeInstance> mutableIncludeInstances,
                 @NotNull IdentityHashMap<DslElementNode, Integer> includeInstanceIdByNode) {
        this.demacroed = demacroed;
        this.mainFilePath = mainFilePath;
        this.demacroedToNormal = immutableIdentityMap(demacroedToNormal);
        this.normalToDemacroed = immutableNodeLists(normalToDemacroed);
        this.scopeByDemacroedNode = immutableScopes(scopeByDemacroedNode);
        this.normalNodeFilePath = immutableIdentityMap(normalNodeFilePath);
        this.macroDiagnostics = macroDiagnostics.stream().map(DemacroedAst::copyDiagnostic).toList();
        List<IncludeInstance> instances = new ArrayList<>();
        for (Builder.MutableIncludeInstance mutable : mutableIncludeInstances) {
            instances.add(new IncludeInstance(mutable.id, mutable.parentId, mutable.filePath,
                    mutable.includeNode, mutable.compileScope, mutable.generatedNodes));
        }
        this.includeInstances = List.copyOf(instances);
        IdentityHashMap<DslElementNode, IncludeInstance> instanceMap = new IdentityHashMap<>();
        includeInstanceIdByNode.forEach((node, id) -> instanceMap.put(node, instances.get(id)));
        this.includeInstanceByNode = Collections.unmodifiableMap(instanceMap);
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
        return normalToDemacroed.getOrDefault(normalNode, List.of());
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
        return macroDiagnostics.stream().map(DemacroedAst::copyDiagnostic).toList();
    }

    public List<IncludeInstance> getIncludeInstances() {
        return includeInstances;
    }

    public List<IncludeInstance> getIncludeInstances(@NotNull String filePath) {
        String normalized = normalizePath(filePath);
        return includeInstances.stream()
                .filter(instance -> normalizePath(instance.getFilePath()).equals(normalized))
                .toList();
    }

    public Optional<IncludeInstance> getIncludeInstance(@Nullable DslElementNode demacroedNode) {
        return Optional.ofNullable(includeInstanceByNode.get(demacroedNode));
    }

    public String getMainFilePath() {
        return mainFilePath;
    }

    /**
     * All distinct file paths of included sub-files (from the {@code normalNodeFilePath} map).
     * Used by the find-usages handler to scan included sub-files' PSI for references.
     */
    public java.util.Set<String> getIncludedFilePaths() {
        return java.util.Collections.unmodifiableSet(new java.util.HashSet<>(normalNodeFilePath.values()));
    }

    /**
     * The file path that the given NORMAL node belongs to. For main-file nodes this is
     * the main file's path (the default); for nodes pulled in via {@code <Include>} it is
     * the included sub-file's path (recorded by {@link IncludeHandler}). Used by the editor
     * to look up the right per-file PSI↔normal map when resolving a demacroed
     * declaration back to PSI.
     */
    public String getFilePathOfNormalNode(@Nullable DslElementNode normalNode) {
        if (normalNode == null) {
            return mainFilePath;
        }
        return normalNodeFilePath.getOrDefault(normalNode, mainFilePath);
    }

    static Builder builder(@NotNull String filePath) {
        return new Builder(filePath);
    }

    private static <V> Map<DslElementNode, V> immutableIdentityMap(
            IdentityHashMap<DslElementNode, V> source) {
        IdentityHashMap<DslElementNode, V> copy = new IdentityHashMap<>();
        copy.putAll(source);
        return Collections.unmodifiableMap(copy);
    }

    private static Map<DslElementNode, List<DslElementNode>> immutableNodeLists(
            IdentityHashMap<DslElementNode, List<DslElementNode>> source) {
        IdentityHashMap<DslElementNode, List<DslElementNode>> copy = new IdentityHashMap<>();
        source.forEach((node, copies) -> copy.put(node, List.copyOf(copies)));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<DslElementNode, Map<String, Object>> immutableScopes(
            IdentityHashMap<DslElementNode, Map<String, Object>> source) {
        IdentityHashMap<DslElementNode, Map<String, Object>> copy = new IdentityHashMap<>();
        source.forEach((node, scope) ->
                copy.put(node, Collections.unmodifiableMap(new HashMap<>(scope))));
        return Collections.unmodifiableMap(copy);
    }

    private static Diagnostic copyDiagnostic(@NotNull Diagnostic source) {
        Diagnostic.DiagnosticBuilder builder = Diagnostic.builder()
                .severity(source.getSeverity())
                .ruleId(source.getRuleId())
                .message(source.getMessage())
                .filePath(source.getFilePath())
                .line(source.getLine())
                .column(source.getColumn())
                .endLine(source.getEndLine())
                .endColumn(source.getEndColumn())
                .suggestedFixes(source.getSuggestedFixes() == null
                        ? List.of() : List.copyOf(source.getSuggestedFixes()))
                .ruleDocUrl(source.getRuleDocUrl());
        if (source.getAstNode() != null) {
            builder.astNode(source.getAstNode());
        }
        return builder.build();
    }

    static final class Builder {
        final String filePath;
        final IdentityHashMap<DslElementNode, DslElementNode> demacroedToNormal = new IdentityHashMap<>();
        final IdentityHashMap<DslElementNode, List<DslElementNode>> normalToDemacroed = new IdentityHashMap<>();
        final IdentityHashMap<DslElementNode, Map<String, Object>> scopeByDemacroedNode = new IdentityHashMap<>();
        final IdentityHashMap<DslElementNode, String> normalNodeFilePath = new IdentityHashMap<>();
        final List<Diagnostic> diagnostics = new java.util.ArrayList<>();
        final List<MutableIncludeInstance> includeInstances = new ArrayList<>();
        final IdentityHashMap<DslElementNode, Integer> includeInstanceIdByNode = new IdentityHashMap<>();
        final Deque<MutableIncludeInstance> includeStack = new ArrayDeque<>();
        final Map<String, CachedNormalAst> normalAstByPath = new HashMap<>();
        int loopIterations;
        int includeExpansions;
        boolean expansionBudgetExceeded;
        boolean includeBudgetExceeded;

        Builder(@NotNull String filePath) {
            this.filePath = filePath;
        }

        void put(@NotNull DslElementNode demacroed, @NotNull DslElementNode normal) {
            demacroedToNormal.put(demacroed, normal);
            normalToDemacroed.computeIfAbsent(normal, k -> new java.util.ArrayList<>()).add(demacroed);
            if (!includeStack.isEmpty()) {
                MutableIncludeInstance current = includeStack.peek();
                current.generatedNodes.add(demacroed);
                includeInstanceIdByNode.put(demacroed, current.id);
            }
        }

        int tryBeginInclude(@NotNull String filePath,
                            @NotNull DslElementNode includeNode,
                            @NotNull Map<String, Object> compileScope) {
            if (includeExpansions >= MacroExpander.MAX_TOTAL_INCLUDE_EXPANSIONS) {
                reportIncludeBudget(includeNode, "Macro expansion exceeded the total Include budget of "
                        + MacroExpander.MAX_TOTAL_INCLUDE_EXPANSIONS + " expansions");
                return -1;
            }
            if (includeStack.size() >= MacroExpander.MAX_INCLUDE_NESTING_DEPTH) {
                reportIncludeBudget(includeNode, "Macro expansion exceeded the Include nesting limit of "
                        + MacroExpander.MAX_INCLUDE_NESTING_DEPTH);
                return -1;
            }
            includeExpansions++;
            int id = includeInstances.size();
            Integer parentId = includeStack.isEmpty() ? null : includeStack.peek().id;
            MutableIncludeInstance instance = new MutableIncludeInstance(
                    id, parentId, filePath, includeNode, compileScope);
            includeInstances.add(instance);
            includeStack.push(instance);
            return id;
        }

        private void reportIncludeBudget(@NotNull DslElementNode anchor, @NotNull String message) {
            if (!includeBudgetExceeded) {
                diagnostics.add(Diagnostic.builder()
                        .severity(DiagnosticSeverity.ERROR)
                        .ruleId(MacroExpander.RULE_INCLUDE_BUDGET)
                        .message(message)
                        .filePath(filePath)
                        .astNode(anchor)
                        .build());
            }
            includeBudgetExceeded = true;
        }

        void endInclude(int id) {
            if (!includeStack.isEmpty() && includeStack.peek().id == id) {
                includeStack.pop();
            }
        }

        void recordScope(@NotNull DslElementNode demacroed, @NotNull Map<String, Object> scope) {
            scopeByDemacroedNode.put(demacroed, new HashMap<>(scope));
        }

        /**
         * Record that a normal-AST node belongs to a particular file (the main file by default;
         * an included sub-file's path for nodes pulled in via {@code <Include>}). Lets the editor
         * pick the right per-file PSI↔normal map when resolving a demacroed node back to PSI.
         */
        void recordFile(@NotNull DslElementNode normalNode, @NotNull String filePath) {
            normalNodeFilePath.put(normalNode, filePath);
        }

        @NotNull
        DslFileNode getOrBuildNormalAst(@NotNull String path, @NotNull String content,
                                        @NotNull NormalAstFactory factory) {
            String normalizedPath = normalizePath(path);
            CachedNormalAst cached = normalAstByPath.get(normalizedPath);
            if (cached != null && cached.content.equals(content)) {
                return cached.ast;
            }
            DslFileNode ast = factory.build(path, content);
            normalAstByPath.put(normalizedPath, new CachedNormalAst(content, ast));
            return ast;
        }

        boolean tryConsumeLoopIteration(@NotNull DslElementNode anchor) {
            if (loopIterations >= MacroExpander.MAX_TOTAL_LOOP_ITERATIONS) {
                if (!expansionBudgetExceeded) {
                    diagnostics.add(Diagnostic.builder()
                            .severity(DiagnosticSeverity.ERROR)
                            .ruleId(MacroExpander.RULE_EXPANSION_BUDGET)
                            .message("Macro expansion exceeded the total loop budget of "
                                    + MacroExpander.MAX_TOTAL_LOOP_ITERATIONS + " iterations")
                            .filePath(filePath)
                            .astNode(anchor)
                            .build());
                }
                expansionBudgetExceeded = true;
                return false;
            }
            loopIterations++;
            return true;
        }

        boolean isExpansionBudgetExceeded() {
            return expansionBudgetExceeded;
        }

        List<Diagnostic> diagnostics() {
            return diagnostics;
        }

        String filePath() {
            return filePath;
        }

        DemacroedAst build(@NotNull DslFileNode demacroed) {
            return new DemacroedAst(demacroed, filePath, demacroedToNormal, normalToDemacroed,
                    scopeByDemacroedNode, normalNodeFilePath, diagnostics,
                    includeInstances, includeInstanceIdByNode);
        }

        static final class MutableIncludeInstance {
            final int id;
            final Integer parentId;
            final String filePath;
            final DslElementNode includeNode;
            final Map<String, Object> compileScope;
            final List<DslElementNode> generatedNodes = new ArrayList<>();

            MutableIncludeInstance(int id,
                                   @Nullable Integer parentId,
                                   @NotNull String filePath,
                                   @NotNull DslElementNode includeNode,
                                   @NotNull Map<String, Object> compileScope) {
                this.id = id;
                this.parentId = parentId;
                this.filePath = filePath;
                this.includeNode = includeNode;
                this.compileScope = new HashMap<>(compileScope);
            }
        }

        private static final class CachedNormalAst {
            private final String content;
            private final DslFileNode ast;

            private CachedNormalAst(@NotNull String content, @NotNull DslFileNode ast) {
                this.content = content;
                this.ast = ast;
            }
        }
    }

    private static String normalizePath(@NotNull String path) {
        return path.replace('\\', '/');
    }
}
