package com.huawei.theme.analysis.plugin.ast;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import com.huawei.theme.analysis.core.macro.DemacroedAst;
import com.huawei.theme.analysis.core.macro.MacroExpander;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.ScopeResolverImpl;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilderImpl;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.plugin.rule.RuleRepositoryService;

public final class DslAstService {

    private static final Key<Entry> CACHE_KEY = Key.create("DslAstService.entry");

    private final PsiAstBuilder astBuilder = new PsiAstBuilder();
    private final ScopeResolverImpl scopeResolver =
            new ScopeResolverImpl(new SymbolTableBuilderImpl());

    public DslAstService(Project project) {
    }

    public static DslAstService getInstance(@NotNull Project project) {
        return project.getService(DslAstService.class);
    }

    public DslAstTree getTree(@NotNull XmlFile xmlFile) {
        return entryFor(xmlFile).tree;
    }

    public DemacroedAst getDemacroedTree(@NotNull XmlFile xmlFile) {
        return entryFor(xmlFile).demacroed;
    }

    public SymbolTable demacroedGlobalScope(@NotNull XmlFile xmlFile) {
        return entryFor(xmlFile).demacroedGlobal;
    }

    public SymbolTable scopeOfDemacroed(@NotNull XmlFile xmlFile, @NotNull DslElementNode demacroedTarget) {
        Entry e = entryFor(xmlFile);
        return scopeResolver.scopeOf(e.demacroed.getDemacroed(), e.demacroedGlobal, getRuleRepository(), demacroedTarget);
    }

    public List<Diagnostic> getMacroDiagnostics(@NotNull XmlFile xmlFile) {
        return entryFor(xmlFile).demacroed.getMacroDiagnostics();
    }

    /**
     * The compile-time variable scope active when {@code demacroedTarget} was produced
     * (e.g. {@code {i:1}} for the first expansion of a {@code <For>}). Used by the editor
     * to interpolate a raw reference name {@code v_%{i}} to the copy's concrete {@code v_1}.
     */
    @NotNull
    public Map<String, Object> compileScopeFor(@NotNull XmlFile xmlFile, @Nullable DslElementNode demacroedTarget) {
        if (demacroedTarget == null) {
            return Collections.emptyMap();
        }
        return entryFor(xmlFile).demacroed.getCompileScope(demacroedTarget);
    }

    /**
     * Map a cursor's host {@link XmlTag} to the demacroed AST node whose scope applies.
     * A non-macro element maps 1:1; a {@code <For>} body element maps to its first
     * expanded copy (uniform scope); a {@code <For>} element itself has no demacroed
     * counterpart, so we walk up to the nearest ancestor that has one.
     */
    @Nullable
    public DslElementNode demacroedTargetFor(@NotNull XmlFile xmlFile, @Nullable XmlTag hostTag) {
        List<DslElementNode> copies = demacroedTargetsFor(xmlFile, hostTag);
        return copies.isEmpty() ? null : copies.get(0);
    }

    /**
     * All demacroed copies of the cursor's host element — needed for multi-resolve: a single
     * source reference inside a {@code <For>} body (e.g. {@code #x_%{i}}) expands to one copy
     * per iteration, each resolving to a different declaration ({@code x_1}, {@code x_2}, ...).
     * Returns the copies of the hostTag, or of the nearest ancestor that has any (a {@code <For>}
     * element itself has zero copies); falls back to the demacroed root.
     */
    @NotNull
    public List<DslElementNode> demacroedTargetsFor(@NotNull XmlFile xmlFile, @Nullable XmlTag hostTag) {
        Entry e = entryFor(xmlFile);
        XmlTag t = hostTag;
        while (t != null) {
            Optional<DslElementNode> normalNode = e.tree.getNode(t);
            if (normalNode.isPresent()) {
                List<DslElementNode> copies = e.demacroed.getDemacroedNodes(normalNode.get());
                if (!copies.isEmpty()) {
                    return copies;
                }
            }
            t = PsiTreeUtil.getParentOfType(t, XmlTag.class);
        }
        DslElementNode root = e.demacroed.getDemacroed().getRootElement();
        return root != null ? List.of(root) : List.of();
    }

    private Entry entryFor(@NotNull XmlFile xmlFile) {
        long stamp = xmlFile.getModificationStamp();
        Entry cached = xmlFile.getUserData(CACHE_KEY);
        if (cached != null && cached.modStamp == stamp) {
            return cached;
        }
        RuleRepository repo = getRuleRepository();
        DslAstTree tree = astBuilder.build(xmlFile, repo);
        DemacroedAst demacroed = new MacroExpander(repo).expand(tree.getAst());
        SymbolTable demacroedGlobal = scopeResolver.globalScope(demacroed.getDemacroed(), repo);
        Entry entry = new Entry(tree, demacroed, demacroedGlobal, stamp);
        xmlFile.putUserData(CACHE_KEY, entry);
        return entry;
    }

    private RuleRepository getRuleRepository() {
        return RuleRepositoryService.getInstance().getRuleRepository();
    }

    private static final class Entry {
        final DslAstTree tree;
        final DemacroedAst demacroed;
        final SymbolTable demacroedGlobal;
        final long modStamp;

        Entry(DslAstTree tree, DemacroedAst demacroed, SymbolTable demacroedGlobal, long modStamp) {
            this.tree = tree;
            this.demacroed = demacroed;
            this.demacroedGlobal = demacroedGlobal;
            this.modStamp = modStamp;
        }
    }
}
