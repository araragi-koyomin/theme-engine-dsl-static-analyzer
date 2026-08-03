package com.huawei.theme.analysis.core.semanticanalysis;

import org.jetbrains.annotations.NotNull;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;

public interface ScopeResolver {

    SymbolTable globalScope(@NotNull DslFileNode file, @NotNull RuleRepository repo);

    SymbolTable scopeOf(@NotNull DslFileNode file, @NotNull RuleRepository repo, @NotNull DslElementNode target);

    SymbolTable scopeOf(@NotNull DslFileNode file, @NotNull SymbolTable prebuiltGlobal,
                        @NotNull RuleRepository repo, @NotNull DslElementNode target);
}
