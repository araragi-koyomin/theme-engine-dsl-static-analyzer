package com.huawei.theme.analysis.core.semanticanalysis.model;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SymbolTable {
    Map<String, VarDeclaration> declarations;
    List<VarReference> references;
}
