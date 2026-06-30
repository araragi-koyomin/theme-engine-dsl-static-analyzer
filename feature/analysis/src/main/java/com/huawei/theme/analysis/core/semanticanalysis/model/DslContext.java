package com.huawei.theme.analysis.core.semanticanalysis.model;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import lombok.Data;

@Data
public class DslContext {
    RuleRepository ruleRepository;
    SymbolTable symbolTable;
    String filePath;

    public DslContext(RuleRepository ruleRepository, SymbolTable symbolTable, String filePath) {
        this.ruleRepository = ruleRepository;
        this.symbolTable = symbolTable;
        this.filePath = filePath;
    }
}
