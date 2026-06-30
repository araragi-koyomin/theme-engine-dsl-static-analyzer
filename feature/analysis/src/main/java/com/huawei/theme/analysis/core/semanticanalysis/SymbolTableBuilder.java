package com.huawei.theme.analysis.core.semanticanalysis;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;

public interface SymbolTableBuilder {
    SymbolTable buildGlobal(DslFileNode fileNode, RuleRepository ruleRepository);
    SymbolTable build(DslElementNode elementNode, SymbolTable parent, RuleRepository ruleRepository);
}
