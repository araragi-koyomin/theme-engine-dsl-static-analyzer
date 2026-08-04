package com.huawei.theme.analysis.plugin.ast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.cli.PipelineMode;
import com.huawei.theme.analysis.core.macro.ContextRootResolver;
import com.huawei.theme.analysis.core.macro.DemacroedAst;
import com.huawei.theme.analysis.core.macro.ForeachHandler;
import com.huawei.theme.analysis.core.macro.ForHandler;
import com.huawei.theme.analysis.core.macro.IfHandler;
import com.huawei.theme.analysis.core.macro.IncludeHandler;
import com.huawei.theme.analysis.core.macro.MacroExpander;
import com.huawei.theme.analysis.core.macro.MacroFileLoader;
import com.huawei.theme.analysis.core.macro.MacroHandler;
import com.huawei.theme.analysis.core.macro.NormalAstFactory;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProviderImpl;
import com.huawei.theme.analysis.core.semanticanalysis.ScopeResolverImpl;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilderImpl;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;
import com.huawei.theme.analysis.plugin.rule.RuleRepositoryService;

public final class DslAstService {

    private static final Key<DslAnalysisContext> CACHE_KEY = Key.create("DslAstService.rootContext");
    private static final List<MacroHandler> HANDLERS =
            List.of(new ForHandler(), new ForeachHandler(), new IfHandler(), new IncludeHandler());

    private final Project project;
    private final PsiAstBuilder astBuilder = new PsiAstBuilder();
    private final ScopeResolverImpl scopeResolver = new ScopeResolverImpl(new SymbolTableBuilderImpl());

    public DslAstService(Project project) {
        this.project = project;
    }

    public static DslAstService getInstance(@NotNull Project project) {
        return project.getService(DslAstService.class);
    }

    @NotNull
    public List<DslAnalysisContext> getAnalysisContexts(@NotNull XmlFile xmlFile) {
        XmlFile physicalFile = physicalFileOf(xmlFile);
        if (!isFunctionFile(physicalFile.getName()) || physicalFile.getVirtualFile() == null) {
            return List.of(contextForRoot(physicalFile));
        }
        RuleRepository repository = getRuleRepository();
        VirtualFile anchorDirectory = physicalFile.getVirtualFile().getParent();
        MacroFileLoader loader = createVfsFileLoader(anchorDirectory);
        MacroExpander resolverExpander = new MacroExpander(
                repository, HANDLERS, loader, NormalAstFactory.text(repository));
        List<DslAnalysisContext> contexts = new ArrayList<>();
        for (String rootPath : new ContextRootResolver(resolverExpander).findContextRoots(pathOf(physicalFile))) {
            findXmlFile(rootPath, anchorDirectory).ifPresent(root -> contexts.add(contextForRoot(root)));
        }
        contexts.sort(Comparator.comparing(DslAnalysisContext::getRootFilePath));
        return List.copyOf(contexts);
    }

    public DslAstTree getTree(@NotNull XmlFile xmlFile) {
        XmlFile physicalFile = physicalFileOf(xmlFile);
        DslAnalysisContext context = primaryContext(physicalFile);
        return context.treeFor(pathOf(physicalFile)).orElse(context.getRootTree());
    }

    public DemacroedAst getDemacroedTree(@NotNull XmlFile xmlFile) {
        return primaryContext(physicalFileOf(xmlFile)).getDemacroed();
    }

    public SymbolTable demacroedGlobalScope(@NotNull XmlFile xmlFile) {
        return primaryContext(physicalFileOf(xmlFile)).getGlobalScope();
    }

    public SymbolTable scopeOfDemacroed(@NotNull XmlFile xmlFile, @NotNull DslElementNode demacroedTarget) {
        for (DslAnalysisContext context : contextsOrStandalone(physicalFileOf(xmlFile))) {
            if (context.getDemacroed().getNormalNode(demacroedTarget).isPresent()
                    || context.getDemacroed().getDemacroed().getRootElement() == demacroedTarget) {
                return scopeOf(context, demacroedTarget);
            }
        }
        DslAnalysisContext context = primaryContext(physicalFileOf(xmlFile));
        return scopeOf(context, demacroedTarget);
    }

    public List<Diagnostic> getMacroDiagnostics(@NotNull XmlFile xmlFile) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (DslAnalysisContext context : getAnalysisContexts(physicalFileOf(xmlFile))) {
            diagnostics.addAll(context.getDemacroed().getMacroDiagnostics());
        }
        return List.copyOf(diagnostics);
    }

    @NotNull
    public Map<String, Object> compileScopeFor(@NotNull XmlFile xmlFile,
                                               @Nullable DslElementNode demacroedTarget) {
        if (demacroedTarget == null) {
            return Collections.emptyMap();
        }
        for (DslAnalysisContext context : contextsOrStandalone(physicalFileOf(xmlFile))) {
            if (context.getDemacroed().getNormalNode(demacroedTarget).isPresent()) {
                return context.getDemacroed().getCompileScope(demacroedTarget);
            }
        }
        return Collections.emptyMap();
    }

    @Nullable
    public DslElementNode demacroedTargetFor(@NotNull XmlFile xmlFile, @Nullable XmlTag hostTag) {
        List<ContextTarget> targets = demacroedTargetsWithContext(xmlFile, hostTag);
        return targets.isEmpty() ? null : targets.get(0).getNode();
    }

    @NotNull
    public List<DslElementNode> demacroedTargetsFor(@NotNull XmlFile xmlFile, @Nullable XmlTag hostTag) {
        return demacroedTargetsWithContext(xmlFile, hostTag).stream().map(ContextTarget::getNode).toList();
    }

    @NotNull
    public List<ContextTarget> demacroedTargetsWithContext(@NotNull XmlFile xmlFile,
                                                           @Nullable XmlTag hostTag) {
        XmlFile physicalFile = physicalFileOf(xmlFile);
        XmlTag physicalTag = physicalTagOf(physicalFile, hostTag);
        List<ContextTarget> targets = new ArrayList<>();
        for (DslAnalysisContext context : getAnalysisContexts(physicalFile)) {
            DslAstTree hostTree = context.treeFor(pathOf(physicalFile)).orElse(context.getRootTree());
            XmlTag tag = physicalTag;
            while (tag != null) {
                Optional<DslElementNode> normalNode = hostTree.getNode(tag);
                if (normalNode.isPresent()) {
                    for (DslElementNode copy : context.getDemacroed().getDemacroedNodes(normalNode.get())) {
                        targets.add(new ContextTarget(context, copy));
                    }
                    if (!targets.isEmpty()) {
                        break;
                    }
                }
                tag = PsiTreeUtil.getParentOfType(tag, XmlTag.class);
            }
            if (physicalTag == null && context.getDemacroed().getDemacroed().getRootElement() != null) {
                targets.add(new ContextTarget(context, context.getDemacroed().getDemacroed().getRootElement()));
            }
        }
        return List.copyOf(targets);
    }

    @NotNull
    public DslAstTree getTreeForFile(@NotNull XmlFile contextFile, @Nullable String ownerFilePath) {
        DslAnalysisContext context = primaryContext(physicalFileOf(contextFile));
        if (ownerFilePath != null) {
            Optional<DslAstTree> ownerTree = context.treeFor(ownerFilePath);
            if (ownerTree.isPresent()) {
                return ownerTree.get();
            }
        }
        return context.treeFor(pathOf(physicalFileOf(contextFile))).orElse(context.getRootTree());
    }

    @NotNull
    public DslAstTree getTreeForFile(@NotNull ContextTarget target, @Nullable String ownerFilePath) {
        if (ownerFilePath != null) {
            Optional<DslAstTree> ownerTree = target.getContext().treeFor(ownerFilePath);
            if (ownerTree.isPresent()) {
                return ownerTree.get();
            }
        }
        return target.getContext().getRootTree();
    }

    @NotNull
    public Set<String> getContextFilePaths(@NotNull XmlFile xmlFile) {
        Set<String> paths = new LinkedHashSet<>();
        for (DslAnalysisContext context : getAnalysisContexts(physicalFileOf(xmlFile))) {
            paths.addAll(context.getFilePaths());
        }
        return Collections.unmodifiableSet(paths);
    }

    @NotNull
    public List<XmlFile> getContextFiles(@NotNull XmlFile xmlFile) {
        XmlFile physicalFile = physicalFileOf(xmlFile);
        VirtualFile anchorDirectory = physicalFile.getVirtualFile() != null
                ? physicalFile.getVirtualFile().getParent() : null;
        List<XmlFile> files = new ArrayList<>();
        for (String path : getContextFilePaths(physicalFile)) {
            findXmlFile(path, anchorDirectory).ifPresent(files::add);
        }
        return List.copyOf(new LinkedHashSet<>(files));
    }

    public long getContextVersion(@NotNull XmlFile xmlFile) {
        return PsiModificationTracker.getInstance(project).getModificationCount();
    }

    @NotNull
    public List<Diagnostic> getProjectedDiagnostics(@NotNull XmlFile xmlFile) {
        XmlFile physicalFile = physicalFileOf(xmlFile);
        return ContextDiagnosticProjector.project(physicalFile, getAnalysisContexts(physicalFile));
    }

    @NotNull
    public SymbolTable scopeOf(@NotNull ContextTarget target) {
        return scopeOf(target.getContext(), target.getNode());
    }

    @NotNull
    private SymbolTable scopeOf(@NotNull DslAnalysisContext context, @NotNull DslElementNode target) {
        return scopeResolver.scopeOf(context.getDemacroed().getDemacroed(), context.getGlobalScope(),
                getRuleRepository(), target);
    }

    @NotNull
    private DslAnalysisContext primaryContext(@NotNull XmlFile physicalFile) {
        List<DslAnalysisContext> contexts = getAnalysisContexts(physicalFile);
        return contexts.isEmpty() ? contextForRoot(physicalFile) : contexts.get(0);
    }

    @NotNull
    private List<DslAnalysisContext> contextsOrStandalone(@NotNull XmlFile physicalFile) {
        List<DslAnalysisContext> contexts = getAnalysisContexts(physicalFile);
        return contexts.isEmpty() ? List.of(contextForRoot(physicalFile)) : contexts;
    }

    private DslAnalysisContext contextForRoot(@NotNull XmlFile rootFile) {
        long fingerprint = contextFingerprint(rootFile);
        DslAnalysisContext cached = rootFile.getUserData(CACHE_KEY);
        if (cached != null && cached.getFingerprint() == fingerprint) {
            return cached;
        }

        RuleRepository repository = getRuleRepository();
        VirtualFile anchorDirectory = rootFile.getVirtualFile() != null
                ? rootFile.getVirtualFile().getParent() : null;
        MacroFileLoader loader = createVfsFileLoader(anchorDirectory);
        Map<String, DslAstTree> treesByPath = new LinkedHashMap<>();
        DslAstTree rootTree = astBuilder.build(rootFile, repository);
        treesByPath.put(normalizePath(pathOf(rootFile)), rootTree);
        MacroExpander expander = new MacroExpander(repository, HANDLERS, loader,
                createPsiNormalAstFactory(treesByPath, repository, anchorDirectory));
        DemacroedAst demacroed = expander.expand(rootTree.getAst());
        SymbolTable globalScope = scopeResolver.globalScope(demacroed.getDemacroed(), repository);
        List<Diagnostic> diagnostics = new ArrayList<>(demacroed.getMacroDiagnostics());
        diagnostics.addAll(new DiagnosticProviderImpl().analyze(
                demacroed.getDemacroed(), repository, new SymbolTableBuilderImpl(),
                PipelineMode.FULL, InspectionConfig.builder().build(), null));
        DslAnalysisContext context = new DslAnalysisContext(pathOf(rootFile), rootTree, demacroed,
                globalScope, treesByPath, diagnostics, fingerprint);
        rootFile.putUserData(CACHE_KEY, context);
        return context;
    }

    @NotNull
    private NormalAstFactory createPsiNormalAstFactory(@NotNull Map<String, DslAstTree> treesByPath,
                                                        @NotNull RuleRepository repository,
                                                        @Nullable VirtualFile anchorDirectory) {
        return (path, content) -> {
            String normalizedPath = normalizePath(path);
            DslAstTree existing = treesByPath.get(normalizedPath);
            if (existing != null) {
                return existing.getAst();
            }
            Optional<XmlFile> xmlFile = findXmlFile(path, anchorDirectory);
            if (xmlFile.isPresent()) {
                DslAstTree tree = astBuilder.build(xmlFile.get(), repository);
                treesByPath.put(normalizedPath, tree);
                return tree.getAst();
            }
            return new AstBuilder(repository).getDslAst(path, content);
        };
    }

    private MacroFileLoader createVfsFileLoader(@Nullable VirtualFile anchorDirectory) {
        return new MacroFileLoader() {
            @Override
            public @Nullable String loadFile(@NotNull String path) {
                Optional<VirtualFile> virtualFile = findVirtualFile(path, anchorDirectory);
                if (virtualFile.isEmpty()) {
                    return MacroFileLoader.DISK.loadFile(path);
                }
                PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile.get());
                if (psiFile != null) {
                    return psiFile.getText();
                }
                try {
                    return VfsUtilCore.loadText(virtualFile.get());
                } catch (IOException e) {
                    return null;
                }
            }

            @Override
            public @Nullable List<String> listFiles(@NotNull String dirPath) {
                Optional<VirtualFile> directory = findVirtualFile(dirPath, anchorDirectory);
                if (directory.isEmpty() || !directory.get().isDirectory()) {
                    return MacroFileLoader.DISK.listFiles(dirPath);
                }
                List<String> names = new ArrayList<>();
                for (VirtualFile child : directory.get().getChildren()) {
                    if (!child.isDirectory()) {
                        names.add(child.getName());
                    }
                }
                return names;
            }
        };
    }

    @NotNull
    private Optional<XmlFile> findXmlFile(@NotNull String path, @Nullable VirtualFile anchorDirectory) {
        Optional<VirtualFile> virtualFile = findVirtualFile(path, anchorDirectory);
        if (virtualFile.isEmpty()) {
            return Optional.empty();
        }
        PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile.get());
        return psiFile instanceof XmlFile xmlFile ? Optional.of(xmlFile) : Optional.empty();
    }

    @NotNull
    private Optional<VirtualFile> findVirtualFile(@NotNull String path, @Nullable VirtualFile anchorDirectory) {
        String normalizedPath = normalizePath(path);
        if (anchorDirectory != null) {
            String anchorPath = normalizePath(anchorDirectory.getPath());
            if (normalizedPath.isEmpty() || normalizedPath.equals(anchorPath)) {
                return Optional.of(anchorDirectory);
            }
            String anchorPrefix = anchorPath.endsWith("/") ? anchorPath : anchorPath + "/";
            if (normalizedPath.startsWith(anchorPrefix)) {
                VirtualFile child = anchorDirectory.findFileByRelativePath(
                        normalizedPath.substring(anchorPrefix.length()));
                if (child != null) {
                    return Optional.of(child);
                }
            }
        }
        VirtualFile local = LocalFileSystem.getInstance().findFileByPath(normalizedPath);
        if (local != null) {
            return Optional.of(local);
        }
        for (VirtualFile root : ProjectRootManager.getInstance(project).getContentRoots()) {
            String rootPath = normalizePath(root.getPath());
            String rootPrefix = rootPath.endsWith("/") ? rootPath : rootPath + "/";
            if (normalizedPath.equals(rootPath)) {
                return Optional.of(root);
            }
            if (normalizedPath.startsWith(rootPrefix)) {
                VirtualFile child = root.findFileByRelativePath(normalizedPath.substring(rootPrefix.length()));
                if (child != null) {
                    return Optional.of(child);
                }
            }
        }
        String fileName = fileNameOf(normalizedPath);
        for (VirtualFile candidate : FilenameIndex.getVirtualFilesByName(
                fileName, GlobalSearchScope.projectScope(project))) {
            if (normalizePath(candidate.getPath()).equals(normalizedPath)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private long contextFingerprint(@NotNull XmlFile rootFile) {
        VirtualFile virtualFile = rootFile.getVirtualFile();
        if (virtualFile == null || virtualFile.getParent() == null) {
            return rootFile.getModificationStamp();
        }
        VirtualFile directory = virtualFile.getParent();
        long fingerprint = directory.getModificationStamp();
        List<VirtualFile> files = new ArrayList<>();
        for (VirtualFile child : directory.getChildren()) {
            if (!child.isDirectory() && isContextFile(child.getName())) {
                files.add(child);
            }
        }
        files.sort(Comparator.comparing(VirtualFile::getName));
        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile file : files) {
            PsiFile psiFile = psiManager.findFile(file);
            long stamp = psiFile != null ? psiFile.getModificationStamp() : file.getModificationStamp();
            fingerprint = 31 * fingerprint + file.getName().hashCode();
            fingerprint = 31 * fingerprint + stamp;
        }
        return fingerprint;
    }

    private RuleRepository getRuleRepository() {
        return RuleRepositoryService.getInstance().getRuleRepository();
    }

    @NotNull
    private static XmlFile physicalFileOf(@NotNull XmlFile file) {
        PsiFile original = file.getOriginalFile();
        return original instanceof XmlFile xmlFile ? xmlFile : file;
    }

    @Nullable
    private static XmlTag physicalTagOf(@NotNull XmlFile physicalFile, @Nullable XmlTag tag) {
        if (tag == null || tag.getContainingFile() == physicalFile) {
            return tag;
        }
        int offset = Math.min(tag.getTextOffset(), Math.max(physicalFile.getTextLength() - 1, 0));
        PsiElement element = physicalFile.findElementAt(offset);
        return PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
    }

    private static boolean isContextFile(@NotNull String name) {
        return "script.xml".equals(name)
                || (name.startsWith("script_") || name.startsWith("function_")) && name.endsWith(".xml");
    }

    private static boolean isFunctionFile(@NotNull String name) {
        return name.startsWith("function_") && name.endsWith(".xml");
    }

    @NotNull
    private static String pathOf(@NotNull XmlFile file) {
        return file.getVirtualFile() != null ? file.getVirtualFile().getPath() : file.getName();
    }

    @NotNull
    private static String normalizePath(@NotNull String path) {
        return path.replace('\\', '/');
    }

    @NotNull
    private static String fileNameOf(@NotNull String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    public static final class ContextTarget {
        private final DslAnalysisContext context;
        private final DslElementNode node;

        ContextTarget(@NotNull DslAnalysisContext context, @NotNull DslElementNode node) {
            this.context = context;
            this.node = node;
        }

        @NotNull
        public DslAnalysisContext getContext() {
            return context;
        }

        @NotNull
        public DslElementNode getNode() {
            return node;
        }
    }

}
