package com.huawei.theme.analysis.core.semanticanalysis;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

import java.util.List;

public interface DslAnalyzer {
    List<Diagnostic> analyze(DslAstNode element, RuleRepository ruleRepo, SymbolTable symbolTable);
}
