package com.huawei.theme.analysis.plugin.editor.reference;

import java.util.List;

import com.huawei.theme.analysis.plugin.editor.varname.VarNameElement;
import com.huawei.theme.analysis.plugin.editor.varname.VarNameLanguage;
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
 * Injects the {@link VarNameLanguage} into attribute values that *declare* a
 * variable name, turning the name into a {@link VarNameElement} (a
 * {@link com.intellij.psi.PsiNameIdentifierOwner}) so rename and find-usages
 * work from the declaration. Two declaration sites:
 *
 * <ul>
 *   <li>{@code <Var name="...">} — the variable's name attribute</li>
 *   <li>{@code <Array indexFlag="...">} / {@code <CycleCommand indexFlag="...">}
 *   — the loop-index local declared by {@code indexFlag}</li>
 * </ul>
 *
 * <p>{@code <VariableCommand name="...">} is NOT a declaration — it's a reference.
 * It's handled by {@link ThemeDslVariableReferenceContributor} instead, which creates
 * a {@link DslVariableReference} on its {@code name} value resolving to the
 * {@link VarNameElement} of the matching {@code <Var>}.</p>
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
        if (attr == null) {
            return;
        }
        String attrName = attr.getName();
        XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
        if (tag == null) {
            return;
        }
        String tagName = tag.getName();
        boolean isVarName = "Var".equals(tagName) && "name".equals(attrName);
        boolean isIndexFlag = ("Array".equals(tagName) || "CycleCommand".equals(tagName))
                && "indexFlag".equals(attrName);
        if (!isVarName && !isIndexFlag) {
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
