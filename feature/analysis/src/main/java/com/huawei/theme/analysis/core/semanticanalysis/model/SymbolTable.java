package com.huawei.theme.analysis.core.semanticanalysis.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
@Builder
public class SymbolTable {
    /**
     * 他的上一层变量scope。
     */
    @Nullable
    SymbolTable parent = null;

    @Builder.Default Map<String, VarDeclaration> declarations = Collections.emptyMap();
//    @Builder.Default List<VarReference> references = Collections.emptyList();
}
