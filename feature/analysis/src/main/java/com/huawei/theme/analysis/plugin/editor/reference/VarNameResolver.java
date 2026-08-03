package com.huawei.theme.analysis.plugin.editor.reference;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import com.huawei.theme.analysis.core.macro.CompileTimeInterpolator;
import com.huawei.theme.analysis.core.macro.DemacroedAst;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.semanticanalysis.model.VarDeclaration;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.type.DslArrayType;
import com.huawei.theme.analysis.core.shared.type.DslStringType;
import com.huawei.theme.analysis.core.shared.type.DslType;
import com.huawei.theme.analysis.plugin.ast.DslAstService;
import com.huawei.theme.analysis.plugin.ast.DslAstTree;
import com.huawei.theme.analysis.plugin.editor.varname.VarNameElement;

public final class VarNameResolver {

    private VarNameResolver() {
    }

    @Nullable
    public static PsiElement resolveDeclaration(@NotNull Project project,
                                                @NotNull XmlFile hostFile,
                                                @Nullable XmlTag hostTag,
                                                @NotNull String varName) {
        return resolveDeclarationsMulti(project, hostFile, hostTag, varName).stream()
                .findFirst().orElse(null);
    }

    /**
     * Multi-resolve a (possibly macro-interpolated) reference to ALL its declaration targets.
     *
     * <p>A single source reference inside a {@code <For>} body, e.g. {@code #x_%{i}}, expands to
     * one demacoed copy per iteration. Each copy is interpolated with its own compile-time scope
     * ({@code {i:1}} → {@code x_1}, {@code {i:2}} → {@code x_2}), and each may resolve to a
     * different declaration. We collect the distinct targets (deduped by demacoed decl-node
     * identity) so that jump-to-def offers all of them and find-usages from any one declaration
     * still finds this reference.</p>
     */
    @NotNull
    public static List<PsiElement> resolveDeclarationsMulti(@NotNull Project project,
                                                             @NotNull XmlFile hostFile,
                                                             @Nullable XmlTag hostTag,
                                                             @NotNull String varName) {
        DslAstService svc = DslAstService.getInstance(project);
        DslAstTree normalTree = svc.getTree(hostFile);
        DemacroedAst demacroed = svc.getDemacroedTree(hostFile);
        List<DslElementNode> copies = svc.demacroedTargetsFor(hostFile, hostTag);
        // Dedup by the FINAL PSI target (the VarNameElement/XmlAttributeValue at the original source),
        // not by the intermediate demacoed decl node: N macro-expanded copies of one <Var> all two-hop
        // to the same original source, so they must collapse to a single resolve target. Distinct
        // source declarations (e.g. manually-defined x_1 and x_2) remain separate entries.
        Set<PsiElement> seenTargets = new LinkedHashSet<>();
        List<PsiElement> result = new ArrayList<>();
        for (DslElementNode copy : copies) {
            Map<String, Object> scope = demacroed.getCompileScope(copy);
            String resolvedName = CompileTimeInterpolator.interpolate(
                    varName, scope, new ArrayList<>(), copy, "resolve");
            Optional<VarDeclaration> declOpt = svc.scopeOfDemacroed(hostFile, copy).lookup(resolvedName);
            if (declOpt.isEmpty()) {
                continue;
            }
            VarDeclaration d = declOpt.get();
            if (d.isGlobal() || d.getAstNode() == null || d.getHostAttrName() == null) {
                continue;
            }
            // Two-hop: demacoed decl node -> normal node -> PSI XmlTag.
            Optional<DslElementNode> normalDecl = demacroed.getNormalNode(d.getAstNode());
            if (normalDecl.isEmpty()) {
                continue;
            }
            Optional<XmlTag> declTagOpt = normalTree.getTag(normalDecl.get());
            if (declTagOpt.isEmpty()) {
                continue;
            }
            XmlAttribute attr = declTagOpt.get().getAttribute(d.getHostAttrName());
            if (attr == null) {
                continue;
            }
            XmlAttributeValue nameValue = attr.getValueElement();
            if (nameValue == null) {
                continue;
            }
            VarNameElement vne = findVarNameElement(project, nameValue);
            PsiElement target = vne != null ? vne : nameValue;
            if (seenTargets.add(target)) {
                result.add(target);
            }
        }
        return result;
    }

    public static Optional<VarDeclaration> lookupDeclaration(@NotNull Project project,
                                                              @NotNull XmlFile hostFile,
                                                              @Nullable XmlTag hostTag,
                                                              @NotNull String varName) {
        DslAstService svc = DslAstService.getInstance(project);
        DslElementNode target = svc.demacroedTargetFor(hostFile, hostTag);
        if (target == null) {
            return Optional.empty();
        }
        // A raw reference name like v_%{i} (inside a <For> body) must be interpolated with
        // the demacroed copy's compile-time scope before lookup, so it matches the copy's
        // concrete name v_1 in the demacroed SymbolTable. Clean names pass through unchanged.
        Map<String, Object> scope = svc.compileScopeFor(hostFile, target);
        String resolvedName = CompileTimeInterpolator.interpolate(
                varName, scope, new ArrayList<>(), target, "resolve");
        SymbolTable symbolTable = svc.scopeOfDemacroed(hostFile, target);
        return symbolTable.lookup(resolvedName);
    }

    public static List<VarDeclaration> visibleDeclarations(@NotNull Project project,
                                                            @NotNull XmlFile hostFile,
                                                            @Nullable XmlTag hostTag) {
        DslAstService svc = DslAstService.getInstance(project);
        DslElementNode target = svc.demacroedTargetFor(hostFile, hostTag);
        if (target == null) {
            return List.of();
        }
        SymbolTable scope = svc.scopeOfDemacroed(hostFile, target);
        return scope.visibleDeclarations();
    }

    public static String sigilOf(@Nullable DslType type) {
        return type instanceof DslStringType ? "@" : "#";
    }

    public static String typeName(@Nullable DslType type) {
        if (type == null) {
            return "";
        }
        if (type instanceof DslArrayType arr) {
            String base = arr.getBaseType();
            return (base != null ? base : "number") + "[]";
        }
        return type.getName();
    }

    @Nullable
    public static VarNameElement findVarNameElement(@NotNull Project project,
                                                    @NotNull XmlAttributeValue nameValue) {
        List<Pair<PsiElement, TextRange>> injected =
                InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(nameValue);
        if (injected == null) {
            return null;
        }
        for (Pair<PsiElement, TextRange> entry : injected) {
            PsiElement e = entry.getFirst();
            VarNameElement found = e instanceof VarNameElement vne ? vne
                    : PsiTreeUtil.getChildOfType(e, VarNameElement.class);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public static final class ElementResolveResult implements ResolveResult {
        private final PsiElement element;

        public ElementResolveResult(@Nullable PsiElement element) {
            this.element = element;
        }

        @Override
        public @Nullable PsiElement getElement() {
            return element;
        }

        @Override
        public boolean isValidResult() {
            return element != null && element.isValid();
        }
    }
}
