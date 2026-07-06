package com.huawei.theme.analysis.plugin.editor;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;

/**
 * Find Usages handler factory that redirects find-usages from the host XML
 * ({@code <Var>} tag, {@code name} attribute, or attribute value) to the
 * injected {@link VarNameElement} (the {@link com.intellij.psi.PsiNameIdentifierOwner}).
 *
 * <p>Without this, Find Usages on {@code timeTest} in {@code name="timeTest"}
 * targets the {@code <Var>} tag (name "Var") and searches for "Var" — not
 * "timeTest". The factory redirects to the {@link VarNameElement} (name
 * "timeTest"), so the search finds {@code #timeTest}/{@code @timeTest} usages
 * via the host-side {@link DslVariableReference}s.
 *
 * <p>Registered via {@code com.intellij.findUsagesHandlerFactory} EP.
 */
public class ThemeDslVarFindUsagesHandlerFactory extends FindUsagesHandlerFactory {

    @Override
    public boolean canFindUsages(@NotNull PsiElement element) {
        if (element instanceof VarNameElement) {
            return true;
        }
        if (element instanceof XmlAttributeValue value) {
            return isVarNameValue(value);
        }
        if (element instanceof XmlTag tag) {
            return "Var".equals(tag.getName()) && tag.getAttribute("name") != null;
        }
        return false;
    }

    @Override
    public @Nullable FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element,
                                                               boolean forHighlightUsages) {
        VarNameElement target = resolveToVarNameElement(element);
        if (target == null) {
            return null;
        }
        // The default FindUsagesHandler uses ReferencesSearch.search(target).
        // VarNameElement.getUseScope() returns the host file's scope, so the search
        // finds the host-side DslVariableReferences that resolve to the VarNameElement.
        return new FindUsagesHandler(target) { };
    }

    private static boolean isVarNameValue(XmlAttributeValue value) {
        XmlAttribute attr = PsiTreeUtil.getParentOfType(value, XmlAttribute.class);
        if (attr == null || !"name".equals(attr.getName())) {
            return false;
        }
        XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
        return tag != null && "Var".equals(tag.getName());
    }

    @Nullable
    private static VarNameElement resolveToVarNameElement(@NotNull PsiElement element) {
        if (element instanceof VarNameElement vne) {
            return vne;
        }
        if (element instanceof XmlTag tag && "Var".equals(tag.getName())) {
            XmlAttribute nameAttr = tag.getAttribute("name");
            if (nameAttr != null) {
                XmlAttributeValue value = nameAttr.getValueElement();
                if (value != null) {
                    return resolveToVarNameElement(value);
                }
            }
        }
        if (element instanceof XmlAttributeValue value && isVarNameValue(value)) {
            Project project = value.getProject();
            if (project != null) {
                List<Pair<PsiElement, TextRange>> injected =
                        InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(value);
                if (injected != null) {
                    for (Pair<PsiElement, TextRange> entry : injected) {
                        PsiElement e = entry.getFirst();
                        VarNameElement found = e instanceof VarNameElement vne ? vne
                                : PsiTreeUtil.getChildOfType(e, VarNameElement.class);
                        if (found != null) {
                            return found;
                        }
                    }
                }
            }
        }
        return null;
    }
}
