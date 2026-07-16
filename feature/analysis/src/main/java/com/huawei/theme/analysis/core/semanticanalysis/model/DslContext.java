package com.huawei.theme.analysis.core.semanticanalysis.model;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.VerboseCollector;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DslContext {
    RuleRepository ruleRepository;
    SymbolTable symbolTable;
    String filePath;
    DslFileNode rootNode;
    VerboseCollector verboseCollector;

    public DslContext(RuleRepository ruleRepository, SymbolTable symbolTable, String filePath) {
        this(ruleRepository, symbolTable, filePath, null, null);
    }

    public DslContext(RuleRepository ruleRepository, SymbolTable symbolTable, String filePath,
                      DslFileNode rootNode) {
        this(ruleRepository, symbolTable, filePath, rootNode, null);
    }
}
