package com.huawei.theme.analysis.plugin.editor.reference;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.huawei.theme.analysis.plugin.editor.varname.VarNameElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;

/**
 * Find Usages handler factory that redirects find-usages from the host XML
 * ({@code <Var>} tag, {@code name} attribute, or attribute value) to the
 * injected {@link VarNameElement}, and unions two reference-finding strategies
 * in {@link #processElementUsages}:
 *
 * <ol>
 *   <li>{@code super} (= {@code ReferencesSearch}, a word search on the
 *       declaration name + {@code isReferenceTo}/{@code multiResolve} check) —
 *       catches references whose text matches the declaration, including ones
 *       whose expression failed to parse (so {@code multiResolve} is empty)
 *       but still text-match.</li>
 *   <li>A direct scan of every host-side {@link DslVariableReference}, checking
 *       {@code multiResolve} against the target — catches macro-interpolated
 *       references (e.g. {@code #hello_%{i}}) whose text doesn't word-match the
 *       declaration (e.g. {@code hello_%{i+3}}) but which resolve to it via the
 *       demacroed AST + per-copy interpolation. This bypasses the word-search
 *       pre-filter that would otherwise miss them.</li>
 * </ol>
 *
 * <p>Results are deduped by reference identity.</p>
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
        return new FindUsagesHandler(target) {
            @Override
            public boolean processElementUsages(@NotNull PsiElement element,
                                                @NotNull Processor<? super UsageInfo> processor,
                                                @NotNull FindUsagesOptions options) {
                Set<PsiReference> processed = new LinkedHashSet<>();
                // 1. ReferencesSearch (word search + isReferenceTo/multiResolve).
                //    Catches text-matching refs, including parse failures that still text-match.
                Processor<PsiReference> refProcessor = ref -> {
                    if (processed.add(ref)) {
                        return processor.process(new UsageInfo(ref));
                    }
                    return true;
                };
                SearchScope scope = options.searchScope;
                if (!ReferencesSearch.search(createSearchParameters(element, scope, options)).forEach(refProcessor)) {
                    return false;
                }
                // 2. Direct multi-resolve scan — bypasses the word-search pre-filter;
                //    finds macro-interpolated refs (#hello_%{i}) that don't text-match hello_%{i+3}.
                return processMultiResolveUsages(target, processor, processed);
            }
        };
    }

    private static boolean processMultiResolveUsages(@NotNull VarNameElement target,
                                                      @NotNull Processor<? super UsageInfo> processor,
                                                      @NotNull Set<PsiReference> processed) {
        Project project = target.getProject();
        if (project == null) {
            return true;
        }
        InjectedLanguageManager ilm = InjectedLanguageManager.getInstance(project);
        PsiLanguageInjectionHost host = ilm.getInjectionHost(target);
        PsiElement scopeRoot = host != null ? host.getContainingFile() : target.getContainingFile();
        if (scopeRoot == null) {
            return true;
        }
        for (XmlAttributeValue value : PsiTreeUtil.findChildrenOfType(scopeRoot, XmlAttributeValue.class)) {
            for (PsiReference ref : value.getReferences()) {
                if (ref instanceof DslVariableReference dvr
                        && !processed.contains(ref)
                        && multiResolvesTo(dvr, target)) {
                    processed.add(ref);
                    if (!processor.process(new UsageInfo(ref))) {
                        return false;
                    }
                    break;
                }
            }
        }
        return true;
    }

    private static boolean multiResolvesTo(@NotNull DslVariableReference ref, @NotNull PsiElement target) {
        for (ResolveResult r : ref.multiResolve(false)) {
            PsiElement e = r.getElement();
            if (e != null && e.isEquivalentTo(target)) {
                return true;
            }
        }
        return false;
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
