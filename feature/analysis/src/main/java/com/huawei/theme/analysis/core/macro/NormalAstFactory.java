package com.huawei.theme.analysis.core.macro;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.huawei.theme.analysis.core.shared.ast.DslFileNode;

/**
 * Builds a normal (pre-demacro) AST for a file, given its path + text content.
 *
 * <p>The default (text) implementation uses {@code AstBuilder} — fine for the CLI and the
 * text-based diagnostic annotator. The editor supplies a PSI-backed implementation
 * that loads the sub-file's {@code XmlFile} via {@code PsiManager.findFile} and builds the
 * normal AST via {@code PsiAstBuilder}, so the demacroed↔normal map's sub entries are
 * PSI-aligned (same node instances the editor's per-file {@code DslAstTree} maps to PSI).
 * This lets {@code VarNameResolver} two-hop a demacoed sub declaration → normal node →
 * the right per-file PSI, so jump-to-def into an included sub-file navigates to the
 * sub-file's source.</p>
 */
@FunctionalInterface
public interface NormalAstFactory {

    @NotNull
    DslFileNode build(@NotNull String path, @NotNull String content);

    /**
     * Text-based default: {@code new AstBuilder(ruleRepository).getDslAst(path, content)}.
     * Pass a null repository to treat all attribute values as literals (no expression embedding).
     */
    static NormalAstFactory text(@Nullable com.huawei.theme.analysis.core.rulelibrary.RuleRepository repo) {
        return (path, content) -> new com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder(repo).getDslAst(path, content);
    }
}
