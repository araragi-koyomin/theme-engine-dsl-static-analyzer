package com.huawei.theme.analysis.core.semanticanalysis;

import org.jetbrains.annotations.NotNull;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;

public final class ScopeResolverImpl implements ScopeResolver {

    private final SymbolTableBuilder builder;

    public ScopeResolverImpl(SymbolTableBuilder builder) {
        this.builder = builder;
    }

    @Override
    public SymbolTable globalScope(@NotNull DslFileNode file, @NotNull RuleRepository repo) {
        return builder.buildGlobal(file, repo);
    }

    @Override
    public SymbolTable scopeOf(@NotNull DslFileNode file, @NotNull RuleRepository repo,
                               @NotNull DslElementNode target) {
        return scopeOf(file, builder.buildGlobal(file, repo), repo, target);
    }

    @Override
    public SymbolTable scopeOf(@NotNull DslFileNode file, @NotNull SymbolTable prebuiltGlobal,
                               @NotNull RuleRepository repo, @NotNull DslElementNode target) {
        DslElementNode root = file.getRootElement();
        if (root == null || target == root) {
            return prebuiltGlobal;
        }
        return descendTo(root, prebuiltGlobal, repo, target);
    }

    private SymbolTable descendTo(@NotNull DslElementNode element, @NotNull SymbolTable elementScope,
                                   @NotNull RuleRepository repo, @NotNull DslElementNode target) {
        if (element == target) {
            return elementScope;
        }
        if (element.getChildElements() != null) {
            for (DslElementNode child : element.getChildElements()) {
                if (child == target) {
                    return builder.build(element, elementScope, repo);
                }
                if (isAncestor(child, target)) {
                    SymbolTable childScope = builder.build(element, elementScope, repo);
                    return descendTo(child, childScope, repo, target);
                }
            }
        }
        return elementScope;
    }

    private static boolean isAncestor(@NotNull DslElementNode ancestor, @NotNull DslElementNode target) {
        DslAstNode p = target.getParent();
        while (p instanceof DslElementNode e) {
            if (e == ancestor) {
                return true;
            }
            p = e.getParent();
        }
        return false;
    }
}
