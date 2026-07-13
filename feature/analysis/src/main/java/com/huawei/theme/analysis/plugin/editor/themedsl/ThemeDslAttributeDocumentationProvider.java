package com.huawei.theme.analysis.plugin.editor.themedsl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.plugin.rule.RuleRepositoryService;

import java.util.Optional;

/**
 * Quick Documentation / hover provider for ThemeDSL <em>attributes</em>
 * ({@link XmlAttribute}).
 *
 * <p>Shows the attribute description from the rule library JSON.</p>
 */
public class ThemeDslAttributeDocumentationProvider extends AbstractDocumentationProvider {

    @Override
    public @Nullable String generateDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
        XmlAttribute attribute = findAttribute(element);
        if (attribute == null) {
            return null;
        }

        XmlTag tag = PsiTreeUtil.getParentOfType(attribute, XmlTag.class);
        if (tag == null) {
            return null;
        }

        RuleRepository repo = RuleRepositoryService.getInstance().getRuleRepository();
        Optional<DslElementRule> ruleOpt = repo.getElementRule(tag.getName());
        if (ruleOpt.isEmpty()) {
            return null;
        }

        Optional<AttrTypeSpec> specOpt = repo.getAttrTypeSpec(tag.getName(), attribute.getName());
        if (specOpt.isEmpty()) {
            return null;
        }

        AttrTypeSpec spec = specOpt.get();
        String desc = spec.getDescription();
        if (desc == null || desc.isEmpty()) {
            desc = "No description available.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(DocumentationMarkup.DEFINITION_START);
        sb.append("<b>").append(attribute.getName()).append("</b>");
        sb.append(" <code>").append(spec.getType()).append("</code>");
        if (spec.getDefaultValue() != null) {
            sb.append(" <i>(default: ").append(spec.getDefaultValue()).append(")</i>");
        }
        if (spec.isSupportsExpression()) {
            sb.append(" <i>supports expression</i>");
        }
        sb.append(DocumentationMarkup.DEFINITION_END);
        sb.append(DocumentationMarkup.CONTENT_START);
        sb.append(desc);
        if (!spec.getEnumValues().isEmpty()) {
            sb.append("<br><br><b>Allowed values:</b> ");
            sb.append(String.join(", ", spec.getEnumValues()));
        }
        sb.append(DocumentationMarkup.CONTENT_END);
        return sb.toString();
    }

    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        XmlAttribute attribute = findAttribute(element);
        if (attribute == null) {
            return null;
        }
        return "ThemeDSL attribute: " + attribute.getName();
    }

    @Override
    public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor,
                                                              @NotNull PsiFile file,
                                                              @Nullable PsiElement contextElement,
                                                              int targetOffset) {
        if (contextElement == null || file.getFileType() != ThemeDslFileType.INSTANCE) {
            return null;
        }
        return findAttribute(contextElement);
    }

    @Nullable
    private static XmlAttribute findAttribute(PsiElement element) {
        PsiElement e = element;
        while (e != null) {
            if (e instanceof XmlAttribute attribute) {
                return attribute;
            }
            e = e.getParent();
        }
        return null;
    }
}
