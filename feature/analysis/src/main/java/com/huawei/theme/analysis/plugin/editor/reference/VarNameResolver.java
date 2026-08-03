package com.huawei.theme.analysis.plugin.editor.reference;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

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
        Optional<VarDeclaration> declOpt = lookupDeclaration(project, hostFile, hostTag, varName);
        if (declOpt.isEmpty()) {
            return null;
        }
        VarDeclaration d = declOpt.get();
        if (d.isGlobal() || d.getAstNode() == null || d.getHostAttrName() == null) {
            return null;
        }
        DslAstTree tree = DslAstService.getInstance(project).getTree(hostFile);
        Optional<XmlTag> declTagOpt = tree.getTag(d.getAstNode());
        if (declTagOpt.isEmpty()) {
            return null;
        }
        XmlAttribute attr = declTagOpt.get().getAttribute(d.getHostAttrName());
        if (attr == null) {
            return null;
        }
        XmlAttributeValue nameValue = attr.getValueElement();
        if (nameValue == null) {
            return null;
        }
        VarNameElement vne = findVarNameElement(project, nameValue);
        return vne != null ? vne : nameValue;
    }

    public static List<VarDeclaration> visibleDeclarations(@NotNull Project project,
                                                            @NotNull XmlFile hostFile,
                                                            @Nullable XmlTag hostTag) {
        Optional<DslElementNode> astTag = astTagOf(project, hostFile, hostTag);
        if (astTag.isEmpty()) {
            return List.of();
        }
        SymbolTable scope = DslAstService.getInstance(project).scopeOf(hostFile, astTag.get());
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

    public static Optional<VarDeclaration> lookupDeclaration(@NotNull Project project,
                                                              @NotNull XmlFile hostFile,
                                                              @Nullable XmlTag hostTag,
                                                              @NotNull String varName) {
        Optional<DslElementNode> astTag = astTagOf(project, hostFile, hostTag);
        if (astTag.isEmpty()) {
            return Optional.empty();
        }
        SymbolTable scope = DslAstService.getInstance(project).scopeOf(hostFile, astTag.get());
        return scope.lookup(varName);
    }

    private static Optional<DslElementNode> astTagOf(@NotNull Project project,
                                                      @NotNull XmlFile hostFile,
                                                      @Nullable XmlTag hostTag) {
        if (hostTag == null) {
            return Optional.empty();
        }
        DslAstTree tree = DslAstService.getInstance(project).getTree(hostFile);
        return tree.getNode(hostTag);
    }
}
