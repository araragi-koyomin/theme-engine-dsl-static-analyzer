package com.huawei.theme.analysis.plugin.editor.reference;

import java.util.List;
import java.util.Optional;

import com.huawei.theme.analysis.plugin.editor.themedsl.ThemeDslFileType;
import com.huawei.theme.analysis.plugin.editor.expr.DslExpressionLanguage;
import org.jetbrains.annotations.NotNull;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;

import com.huawei.theme.analysis.core.expression.ExpressionParser;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.plugin.rule.RuleRepositoryService;

/**
 * Injects the DslExpression language into ThemeDSL XML attribute values that
 * support expressions, so the DE syntax highlighter / annotator (and other code
 * insight) run inside the attribute value.
 *
 * <p>An attribute is injected when, and only when:</p>
 * <ol>
 *     <li>the host file is a ThemeDSL file ({@link ThemeDslFileType});</li>
 *     <li>the enclosing tag's rule ({@link RuleRepository#getElementRule})
 *         defines the attribute with {@link AttrTypeSpec#isSupportsExpression()};
 *         attribute aliases are also resolved;</li>
 *     <li>the value actually has expression syntax
 *         ({@link ExpressionParser#hasExpressionSyntax}) - so plain literals
 *         and hex colors like {@code "#FF0000"} are left untouched.</li>
 * </ol>
 *
 * <p>Registered via {@code <multiHostInjector>} in {@code plugin.xml}.</p>
 */
public class ThemeDslExpressionInjector implements MultiHostInjector {

    private static final List<Class<? extends PsiElement>> HOSTS =
            List.of(XmlAttributeValue.class);

    @Override
    public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return HOSTS;
    }

    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (!(context instanceof XmlAttributeValue value)) {
            return;
        }
        if (context.getContainingFile() == null) {
            return;
        }
        FileType fileType = context.getContainingFile().getFileType();
        if (fileType != ThemeDslFileType.INSTANCE) {
            return;
        }

        XmlAttribute attribute = PsiTreeUtil.getParentOfType(value, XmlAttribute.class);
        if (attribute == null) {
            return;
        }
        XmlTag tag = PsiTreeUtil.getParentOfType(attribute, XmlTag.class);
        if (tag == null) {
            return;
        }

        String attrName = attribute.getName();
        String attrValue = value.getValue();

        RuleRepository repo = RuleRepositoryService.getInstance().getRuleRepository();
        Optional<DslElementRule> ruleOpt = repo.getElementRule(tag.getName());
        if (ruleOpt.isEmpty()) {
            return;
        }
        AttrTypeSpec typeSpec = findAttrSpec(ruleOpt.get(), attrName);
        if (typeSpec == null || !typeSpec.isSupportsExpression()) {
            return;
        }
        if (!ExpressionParser.hasExpressionSyntax(attrValue, attrName)) {
            return;
        }

        // addPlace expects a range relative to the host element; XmlAttributeValue.getValueTextRange()
        // returns absolute document offsets, so shift it left by the host's start offset.
        TextRange rangeInsideHost =
                value.getValueTextRange().shiftLeft(value.getTextRange().getStartOffset());
        registrar.startInjecting(DslExpressionLanguage.INSTANCE)
                .addPlace(null, null, (PsiLanguageInjectionHost) value, rangeInsideHost)
                .doneInjecting();
    }

    private static AttrTypeSpec findAttrSpec(DslElementRule rule, String attrName) {
        AttrTypeSpec spec = rule.getAttrTypes().get(attrName);
        if (spec != null) {
            return spec;
        }
        for (AttrTypeSpec candidate : rule.getAttrTypes().values()) {
            if (candidate.getAliases() != null && candidate.getAliases().contains(attrName)) {
                return candidate;
            }
        }
        return null;
    }
}
