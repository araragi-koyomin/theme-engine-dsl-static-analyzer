package com.huawei.theme.analysis.core.semanticanalysis.model;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import lombok.Data;

@Data
public class DslContext {
    RuleRepository ruleRepository;
    SymbolTable symbolTable;

    public DslContext(RuleRepository ruleRepository, SymbolTable symbolTable) {
        this.ruleRepository = ruleRepository;
        this.symbolTable = symbolTable;
    }
}
