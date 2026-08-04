package com.huawei.theme.analysis.plugin.ast;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.huawei.theme.analysis.core.macro.DemacroedAst;
import com.huawei.theme.analysis.core.macro.IncludeInstance;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

public final class DslAnalysisContext {

    private final String rootFilePath;
    private final DslAstTree rootTree;
    private final DemacroedAst demacroed;
    private final SymbolTable globalScope;
    private final Map<String, DslAstTree> treesByPath;
    private final Set<String> filePaths;
    private final List<Diagnostic> diagnostics;
    private final long fingerprint;

    DslAnalysisContext(@NotNull String rootFilePath,
                       @NotNull DslAstTree rootTree,
                       @NotNull DemacroedAst demacroed,
                       @NotNull SymbolTable globalScope,
                       @NotNull Map<String, DslAstTree> treesByPath,
                       @NotNull List<Diagnostic> diagnostics,
                       long fingerprint) {
        this.rootFilePath = normalizePath(rootFilePath);
        this.rootTree = rootTree;
        this.demacroed = demacroed;
        this.globalScope = globalScope;
        this.treesByPath = Collections.unmodifiableMap(new LinkedHashMap<>(treesByPath));
        this.filePaths = Collections.unmodifiableSet(new LinkedHashSet<>(treesByPath.keySet()));
        this.diagnostics = List.copyOf(diagnostics);
        this.fingerprint = fingerprint;
    }

    @NotNull
    public String getRootFilePath() {
        return rootFilePath;
    }

    @NotNull
    DemacroedAst getDemacroed() {
        return demacroed;
    }

    @NotNull
    public Set<String> getFilePaths() {
        return filePaths;
    }

    @NotNull
    List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    @NotNull
    List<IncludeInstance> getIncludeInstances(@NotNull String filePath) {
        return demacroed.getIncludeInstances(filePath);
    }

    DslAstTree getRootTree() {
        return rootTree;
    }

    SymbolTable getGlobalScope() {
        return globalScope;
    }

    long getFingerprint() {
        return fingerprint;
    }

    @NotNull
    Optional<DslAstTree> treeFor(@NotNull String path) {
        return Optional.ofNullable(treesByPath.get(normalizePath(path)));
    }

    private static String normalizePath(@NotNull String path) {
        return path.replace('\\', '/');
    }
}
