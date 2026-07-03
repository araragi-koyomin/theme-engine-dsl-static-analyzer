package com.huawei.theme.analysis.plugin.editor;

import java.util.List;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

/**
 * Injects the {@link VarNameLanguage} into the {@code name} attribute value of {@code <Var>}
 * tags, turning the variable name into a {@link VarNameElement} (a {@link
 * com.intellij.psi.PsiNameIdentifierOwner}) so rename and find-usages work from the
 * declaration.
 */
public class ThemeDslVarNameInjector implements MultiHostInjector {

    @Override
    public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(XmlAttributeValue.class);
    }

    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (!(context instanceof XmlAttributeValue value)) {
            return;
        }
        XmlAttribute attr = PsiTreeUtil.getParentOfType(value, XmlAttribute.class);
        if (attr == null || !"name".equals(attr.getName())) {
            return;
        }
        XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
        if (tag == null || !"Var".equals(tag.getName())) {
            return;
        }
        String text = value.getValue();
        if (text == null || text.isEmpty()) {
            return;
        }
        // addPlace expects a range relative to the host element.
        TextRange rangeInsideHost =
                value.getValueTextRange().shiftLeft(value.getTextRange().getStartOffset());
        registrar.startInjecting(VarNameLanguage.INSTANCE)
                .addPlace(null, null, (PsiLanguageInjectionHost) value, rangeInsideHost)
                .doneInjecting();
    }
}
