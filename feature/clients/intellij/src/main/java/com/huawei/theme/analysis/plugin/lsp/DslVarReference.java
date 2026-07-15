package com.huawei.theme.analysis.plugin.lsp;

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

/**
 * Soft PSI reference from an {@code @name}/{@code #name} usage (inside an
 * expression attribute value) to the declaring {@code <Var name="...">}'s
 * {@code name} attribute value.
 *
 * <p>Resolving to the {@link XmlAttributeValue} of the {@code name} attribute
 * makes Ctrl+Click / Ctrl+B land on the name value. Soft references ensure
 * built-in global variables (which have no {@code <Var>} declaration) do not
 * show unresolved-error squiggles.</p>
 */
public final class DslVarReference extends PsiReferenceBase<XmlAttributeValue> {

    private final String varName;

    public DslVarReference(@NotNull XmlAttributeValue element, @NotNull TextRange rangeInElement,
                           @NotNull String varName) {
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
                return nameAttr.getValueElement();
            }
        }
        return null;
    }

    @Override
    public @NotNull Object @NotNull [] getVariants() {
        XmlFile file = getHostFile();
        List<LookupElement> variants = new ArrayList<>();
        if (file == null) {
            return variants.toArray();
        }
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
