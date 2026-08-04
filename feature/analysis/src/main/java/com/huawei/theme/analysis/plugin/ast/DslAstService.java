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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.cli.PipelineMode;
import com.huawei.theme.analysis.core.macro.CompileTimeInterpolator;
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
import com.huawei.theme.analysis.core.semanticanalysis.model.VarDeclaration;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.type.DslType;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;
import com.huawei.theme.analysis.plugin.rule.RuleRepositoryService;

public final class DslAstService {

    private static final Key<DslAnalysisContext> CACHE_KEY = Key.create("DslAstService.rootContext");
    private static final Key<DirectoryContextIndex> DIRECTORY_INDEX_KEY =
            Key.create("DslAstService.directoryContextIndex");
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
    List<DslAnalysisContext> getAnalysisContexts(@NotNull XmlFile xmlFile) {
        XmlFile physicalFile = physicalFileOf(xmlFile);
        if (!isFunctionFile(physicalFile.getName()) || physicalFile.getVirtualFile() == null) {
            return List.of(contextForRoot(physicalFile));
        }
        VirtualFile directory = physicalFile.getVirtualFile().getParent();
        if (directory == null) {
            return List.of();
        }
        return directoryContextIndex(directory).contextsFor(pathOf(physicalFile));
    }

    public int getAnalysisContextCount(@NotNull XmlFile xmlFile) {
        return getAnalysisContexts(xmlFile).size();
    }

    public DslAstTree getTree(@NotNull XmlFile xmlFile) {
        XmlFile physicalFile = physicalFileOf(xmlFile);
        DslAnalysisContext context = primaryContext(physicalFile);
        return context.treeFor(pathOf(physicalFile)).orElse(context.getRootTree());
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
    public String getOccurrenceKey(@NotNull ContextTarget target) {
        String suffix = target.context.getDemacroed().getIncludeInstance(target.node)
                .map(instance -> Integer.toString(instance.getId()))
                .orElse("root");
        return target.context.getRootFilePath() + "#" + suffix;
    }

    @NotNull
    public String interpolateName(@NotNull ContextTarget target, @NotNull String rawName) {
        return CompileTimeInterpolator.interpolate(rawName,
                target.context.getDemacroed().getCompileScope(target.node),
                new ArrayList<>(), target.node, "resolve");
    }

    @NotNull
    public Optional<ContextDeclaration> lookupDeclaration(@NotNull ContextTarget target,
                                                           @NotNull String name) {
        return scopeOf(target.context, target.node).lookup(name).map(ContextDeclaration::new);
    }

    @NotNull
    public List<ContextDeclaration> visibleDeclarations(@NotNull ContextTarget target) {
        return scopeOf(target.context, target.node).visibleDeclarations().stream()
                .map(ContextDeclaration::new)
                .toList();
    }

    @NotNull
    public Optional<XmlAttributeValue> getDeclarationValue(@NotNull ContextTarget target,
                                                            @NotNull ContextDeclaration declaration) {
        if (declaration.astNode == null || declaration.hostAttrName == null) {
            return Optional.empty();
        }
        DemacroedAst demacroed = target.context.getDemacroed();
        Optional<DslElementNode> normal = demacroed.getNormalNode(declaration.astNode);
        if (normal.isEmpty()) {
            return Optional.empty();
        }
        String ownerPath = demacroed.getFilePathOfNormalNode(normal.get());
        DslAstTree tree = target.context.treeFor(ownerPath).orElse(target.context.getRootTree());
        Optional<XmlTag> tag = tree.getTag(normal.get());
        if (tag.isEmpty()) {
            return Optional.empty();
        }
        XmlAttribute attribute = tag.get().getAttribute(declaration.hostAttrName);
        return attribute == null ? Optional.empty() : Optional.ofNullable(attribute.getValueElement());
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
        XmlFile physicalFile = physicalFileOf(xmlFile);
        VirtualFile virtualFile = physicalFile.getVirtualFile();
        if (isFunctionFile(physicalFile.getName()) && virtualFile != null && virtualFile.getParent() != null) {
            return directoryContextIndex(virtualFile.getParent()).fingerprint;
        }
        DslAnalysisContext context = contextForRoot(physicalFile);
        return context.getFingerprint();
    }

    @NotNull
    public List<Diagnostic> getProjectedDiagnostics(@NotNull XmlFile xmlFile) {
        XmlFile physicalFile = physicalFileOf(xmlFile);
        return ContextDiagnosticProjector.project(physicalFile, getAnalysisContexts(physicalFile));
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

    private DslAnalysisContext contextForRoot(@NotNull XmlFile rootFile) {
        DslAnalysisContext cached = rootFile.getUserData(CACHE_KEY);
        if (cached != null
                && cached.getFingerprint() == contextFingerprint(rootFile, cached.getFilePaths())) {
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
        long fingerprint = contextFingerprint(rootFile, treesByPath.keySet());
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

    private long contextFingerprint(@NotNull XmlFile rootFile, @NotNull Set<String> dependencyPaths) {
        VirtualFile virtualFile = rootFile.getVirtualFile();
        if (virtualFile == null || virtualFile.getParent() == null) {
            return rootFile.getModificationStamp();
        }
        VirtualFile directory = virtualFile.getParent();
        long fingerprint = directory.getModificationStamp();
        List<VirtualFile> files = new ArrayList<>();
        for (String path : dependencyPaths) {
            findVirtualFile(path, directory).ifPresent(files::add);
        }
        if (!files.contains(virtualFile)) {
            files.add(virtualFile);
        }
        files = new ArrayList<>(new LinkedHashSet<>(files));
        files.sort(Comparator.comparing(VirtualFile::getPath));
        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile file : files) {
            PsiFile psiFile = psiManager.findFile(file);
            long stamp = psiFile != null ? psiFile.getModificationStamp() : file.getModificationStamp();
            fingerprint = 31 * fingerprint + normalizePath(file.getPath()).hashCode();
            fingerprint = 31 * fingerprint + stamp;
        }
        return fingerprint;
    }

    @NotNull
    private DirectoryContextIndex directoryContextIndex(@NotNull VirtualFile directory) {
        long fingerprint = directoryFingerprint(directory);
        DirectoryContextIndex cached = directory.getUserData(DIRECTORY_INDEX_KEY);
        if (cached != null && cached.fingerprint == fingerprint) {
            return cached;
        }
        Map<String, List<DslAnalysisContext>> contextsByIncludedPath = new LinkedHashMap<>();
        List<VirtualFile> roots = new ArrayList<>();
        for (VirtualFile child : directory.getChildren()) {
            if (!child.isDirectory() && isRootFile(child.getName())) {
                roots.add(child);
            }
        }
        roots.sort(Comparator.comparing(VirtualFile::getName));
        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile root : roots) {
            PsiFile psiFile = psiManager.findFile(root);
            if (!(psiFile instanceof XmlFile xmlRoot)) {
                continue;
            }
            DslAnalysisContext context = contextForRoot(xmlRoot);
            for (String includedPath : context.getFilePaths()) {
                if (!context.getIncludeInstances(includedPath).isEmpty()) {
                    contextsByIncludedPath.computeIfAbsent(normalizePath(includedPath), ignored -> new ArrayList<>())
                            .add(context);
                }
            }
        }
        DirectoryContextIndex rebuilt = new DirectoryContextIndex(fingerprint, contextsByIncludedPath);
        directory.putUserData(DIRECTORY_INDEX_KEY, rebuilt);
        return rebuilt;
    }

    private long directoryFingerprint(@NotNull VirtualFile directory) {
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
        return isRootFile(name) || isFunctionFile(name);
    }

    private static boolean isRootFile(@NotNull String name) {
        return "script.xml".equals(name) || name.startsWith("script_") && name.endsWith(".xml");
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

    private static final class DirectoryContextIndex {
        private final long fingerprint;
        private final Map<String, List<DslAnalysisContext>> contextsByIncludedPath;

        private DirectoryContextIndex(long fingerprint,
                                      @NotNull Map<String, List<DslAnalysisContext>> contextsByIncludedPath) {
            this.fingerprint = fingerprint;
            Map<String, List<DslAnalysisContext>> immutable = new LinkedHashMap<>();
            contextsByIncludedPath.forEach((path, contexts) -> immutable.put(path, List.copyOf(contexts)));
            this.contextsByIncludedPath = Collections.unmodifiableMap(immutable);
        }

        @NotNull
        private List<DslAnalysisContext> contextsFor(@NotNull String filePath) {
            return contextsByIncludedPath.getOrDefault(normalizePath(filePath), List.of());
        }
    }

    public static final class ContextDeclaration {
        private final String name;
        private final DslType type;
        private final boolean global;
        private final String hostAttrName;
        private final DslElementNode astNode;

        private ContextDeclaration(@NotNull VarDeclaration source) {
            this.name = source.getName();
            this.type = source.getType();
            this.global = source.isGlobal();
            this.hostAttrName = source.getHostAttrName();
            this.astNode = source.getAstNode();
        }

        public String getName() {
            return name;
        }

        public DslType getType() {
            return type;
        }

        public boolean isGlobal() {
            return global;
        }

        public String getHostAttrName() {
            return hostAttrName;
        }
    }

    public static final class ContextTarget {
        private final DslAnalysisContext context;
        private final DslElementNode node;

        ContextTarget(@NotNull DslAnalysisContext context, @NotNull DslElementNode node) {
            this.context = context;
            this.node = node;
        }

    }

}
