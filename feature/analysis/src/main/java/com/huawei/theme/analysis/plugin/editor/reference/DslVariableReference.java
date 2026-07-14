package com.huawei.theme.analysis.plugin.editor.reference;

import java.util.ArrayList;
import java.util.List;

import com.huawei.theme.analysis.plugin.editor.varname.VarNameElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.plugin.rule.RuleRepositoryService;

/**
 * A host-side reference from an {@code @name}/{@code #name} usage (inside an
 * attribute value) to the declaring {@code VarNameElement} (the injected
 * {@link com.intellij.psi.PsiNameIdentifierOwner} inside {@code <Var name="...">}'s
 * {@code name} attribute value).
 *
 * <p>Resolving to the {@link VarNameElement} (not the {@code <Var>} tag) ensures
 * that {@code ReferencesSearch} finds ALL references when renaming from the
 * declaration side — e.g. in {@code #a + #b + #a}, both {@code #a} references
 * are found and renamed, not just the first.
 *
 * <p>{@link #handleElementRename(String)} (inherited from {@link PsiReferenceBase})
 * rewrites just the name via the {@link XmlAttributeValue} element manipulator,
 * preserving the {@code @}/{@code #} sigil. References are soft so that built-in
 * global variables (which have no PSI declaration) do not show unresolved-error
 * squiggles.</p>
 */
public class DslVariableReference extends PsiReferenceBase<XmlAttributeValue> {

    private final String varName;

    public DslVariableReference(@NotNull XmlAttributeValue element, @NotNull TextRange rangeInElement, @NotNull String varName) {
        super(element, rangeInElement, true);
        this.varName = varName;
    }

    @Override
    public @Nullable PsiElement resolve() {
        XmlFile file = getHostFile();
        if (file == null) {
            return null;
        }
        for (XmlTag tag : PsiTreeUtil.findChildrenOfType(file, XmlTag.class)) {
            if (!"Var".equals(tag.getName())) {
                continue;
            }
            XmlAttribute nameAttr = tag.getAttribute("name");
            if (nameAttr != null && varName.equals(nameAttr.getValue())) {
                XmlAttributeValue nameValue = nameAttr.getValueElement();
                if (nameValue == null) {
                    return null;
                }
                // Resolve to the injected VarNameElement (PsiNameIdentifierOwner) so that
                // ReferencesSearch finds all references when renaming from the declaration.
                VarNameElement varNameElement = findVarNameElement(nameValue);
                return varNameElement != null ? varNameElement : nameValue;
            }
        }
        return null;
    }

    @Override
    public @NotNull Object @NotNull [] getVariants() {
        XmlFile file = getHostFile();
        List<LookupElement> variants = new ArrayList<>();
        if (file != null) {
            for (XmlTag tag : PsiTreeUtil.findChildrenOfType(file, XmlTag.class)) {
                if (!"Var".equals(tag.getName())) {
                    continue;
                }
                XmlAttribute nameAttr = tag.getAttribute("name");
                if (nameAttr != null && nameAttr.getValue() != null && !nameAttr.getValue().isEmpty()) {
                    variants.add(LookupElementBuilder.create(nameAttr.getValue())
                            .withIcon(AllIcons.Nodes.Variable)
                            .withTypeText("Var"));
                }
            }
        }
        RuleRepository repo = RuleRepositoryService.getInstance().getRuleRepository();
        for (DslGlobalVar globalVar : repo.getAllGlobalVars()) {
            variants.add(LookupElementBuilder.create(globalVar.getName())
                    .withIcon(AllIcons.Nodes.Variable)
                    .withTypeText(globalVar.getType()));
        }
        return variants.toArray();
    }

    @Nullable
    private XmlFile getHostFile() {
        PsiElement element = getElement();
        if (element == null) {
            return null;
        }
        return element.getContainingFile() instanceof XmlFile xmlFile ? xmlFile : null;
    }

    @Nullable
    private VarNameElement findVarNameElement(XmlAttributeValue nameValue) {
        Project project = getElement().getProject();
        if (project == null) {
            return null;
        }
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
}
