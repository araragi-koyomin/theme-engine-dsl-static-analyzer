package com.huawei.theme.analysis.core.semanticanalysis;

import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;

import java.util.List;

public interface SymbolTableBuilder {
    SymbolTable build(DslFileNode fileNode);
}
