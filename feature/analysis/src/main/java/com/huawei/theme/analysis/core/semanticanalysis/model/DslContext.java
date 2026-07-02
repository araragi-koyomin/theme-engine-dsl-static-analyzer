package com.huawei.theme.analysis.core.semanticanalysis.model;

import com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import lombok.Data;

@Data
public class DslContext {
    RuleRepository ruleRepository;
    SymbolTable symbolTable;
    String filePath;
    FunctionSignatureLibrary functionSignatureLibrary;

    public DslContext(RuleRepository ruleRepository, SymbolTable symbolTable, String filePath) {
        this(ruleRepository, symbolTable, filePath, null);
    }

    public DslContext(RuleRepository ruleRepository, SymbolTable symbolTable, String filePath,
                      FunctionSignatureLibrary functionSignatureLibrary) {
        this.ruleRepository = ruleRepository;
        this.symbolTable = symbolTable;
        this.filePath = filePath;
        this.functionSignatureLibrary = functionSignatureLibrary;
    }
}
