package com.huawei.theme.analysis.core.semanticanalysis.model;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DslContext {
    String filePath;
    RuleRepository ruleRepository;
    SymbolTable symbolTable;
}
