package com.huawei.theme.analysis.plugin.ast;

import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.ScopeResolverImpl;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilderImpl;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.plugin.rule.RuleRepositoryService;

public final class DslAstService {

    private final Project project;
    private final PsiAstBuilder astBuilder = new PsiAstBuilder();
    private final ScopeResolverImpl scopeResolver =
            new ScopeResolverImpl(new SymbolTableBuilderImpl());
    private final ConcurrentHashMap<Object, Entry> cache = new ConcurrentHashMap<>();

    public DslAstService(Project project) {
        this.project = project;
    }

    public static DslAstService getInstance(@NotNull Project project) {
        return project.getService(DslAstService.class);
    }

    public DslAstTree getTree(@NotNull XmlFile xmlFile) {
        return entryFor(xmlFile).tree;
    }

    public SymbolTable globalScope(@NotNull XmlFile xmlFile) {
        return entryFor(xmlFile).globalScope;
    }

    public SymbolTable scopeOf(@NotNull XmlFile xmlFile, @NotNull DslElementNode target) {
        Entry e = entryFor(xmlFile);
        return scopeResolver.scopeOf(e.tree.getAst(), e.globalScope, getRuleRepository(), target);
    }

    private Entry entryFor(@NotNull XmlFile xmlFile) {
        VirtualFile vf = xmlFile.getVirtualFile();
        Document doc = xmlFile.getViewProvider().getDocument();
        long stamp = doc != null ? doc.getModificationStamp() : Long.MIN_VALUE;
        Object key = vf != null ? vf : xmlFile;
        Entry cached = cache.get(key);
        if (cached != null && cached.modStamp == stamp) {
            return cached;
        }
        RuleRepository repo = getRuleRepository();
        DslAstTree tree = astBuilder.build(xmlFile, repo);
        SymbolTable global = scopeResolver.globalScope(tree.getAst(), repo);
        Entry entry = new Entry(tree, global, stamp);
        cache.put(key, entry);
        return entry;
    }

    private RuleRepository getRuleRepository() {
        return RuleRepositoryService.getInstance().getRuleRepository();
    }

    private static final class Entry {
        final DslAstTree tree;
        final SymbolTable globalScope;
        final long modStamp;

        Entry(DslAstTree tree, SymbolTable globalScope, long modStamp) {
            this.tree = tree;
            this.globalScope = globalScope;
            this.modStamp = modStamp;
        }
    }
}
