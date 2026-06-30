package com.huawei.theme.analysis.core.semanticanalysis.model;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import lombok.Data;

@Data
public class DslContext {
    RuleRepository ruleRepository;
    SymbolTable symbolTable;
}
