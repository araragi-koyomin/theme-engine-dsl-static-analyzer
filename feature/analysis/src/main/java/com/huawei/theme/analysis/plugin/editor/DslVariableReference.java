package com.huawei.theme.analysis.plugin.editor;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
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
 * A reference from an {@code @name}/{@code #name} usage (inside an attribute value)
 * to the declaring {@code <Var name="name">} element in the same ThemeDSL file.
 *
 * <p>The reference range (set by the contributor) covers only the identifier, so
 * {@link #handleElementRename(String)} (inherited from {@link PsiReferenceBase})
 * rewrites just the name via the {@link XmlAttributeValue} element manipulator,
 * preserving the {@code @}/{@code #} sigil. References are soft so that built-in
 * global variables (which have no PSI declaration) and not-yet-declared names do
 * not show unresolved-error squiggles.</p>
 *
 * <p>{@link #resolve()} returns the {@link XmlAttributeValue} of the matching
 * {@code <Var>}'s {@code name} attribute, so Go-to-Declaration lands on the
 * declaration name and Find Usages works from it. Declaration rename is handled by
 * {@link ThemeDslVarRenameProcessor}.</p>
 */
class DslVariableReference extends PsiReferenceBase<XmlAttributeValue> {

    private final String varName;

    DslVariableReference(@NotNull XmlAttributeValue element, @NotNull TextRange rangeInElement, @NotNull String varName) {
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
                return tag;
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
}
