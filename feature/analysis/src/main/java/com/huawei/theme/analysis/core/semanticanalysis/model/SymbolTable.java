package com.huawei.theme.analysis.core.semanticanalysis.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SymbolTable {
    @Builder.Default Map<String, VarDeclaration> declarations = Collections.emptyMap();
    @Builder.Default List<VarReference> references = Collections.emptyList();
}
