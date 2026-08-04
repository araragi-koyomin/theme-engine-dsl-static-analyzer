package com.huawei.theme.analysis.core.macro;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.huawei.theme.analysis.core.shared.ast.DslElementNode;

public final class IncludeInstance {

    private final int id;
    private final Integer parentId;
    private final String filePath;
    private final DslElementNode includeNode;
    private final Map<String, Object> compileScope;
    private final List<DslElementNode> generatedNodes;

    IncludeInstance(int id,
                    @Nullable Integer parentId,
                    @NotNull String filePath,
                    @NotNull DslElementNode includeNode,
                    @NotNull Map<String, Object> compileScope,
                    @NotNull List<DslElementNode> generatedNodes) {
        this.id = id;
        this.parentId = parentId;
        this.filePath = filePath;
        this.includeNode = includeNode;
        this.compileScope = Collections.unmodifiableMap(new HashMap<>(compileScope));
        this.generatedNodes = List.copyOf(generatedNodes);
    }

    public int getId() {
        return id;
    }

    @Nullable
    public Integer getParentId() {
        return parentId;
    }

    @NotNull
    public String getFilePath() {
        return filePath;
    }

    @NotNull
    public DslElementNode getIncludeNode() {
        return includeNode;
    }

    @NotNull
    public Map<String, Object> getCompileScope() {
        return compileScope;
    }

    @NotNull
    public List<DslElementNode> getGeneratedNodes() {
        return generatedNodes;
    }
}
