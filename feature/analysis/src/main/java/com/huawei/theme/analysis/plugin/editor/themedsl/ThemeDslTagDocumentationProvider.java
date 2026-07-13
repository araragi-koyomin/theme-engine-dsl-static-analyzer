package com.huawei.theme.analysis.plugin.editor.themedsl;

import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.plugin.rule.RuleRepositoryService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;

import java.util.stream.Collectors;

/**
 * Quick Documentation / hover provider for ThemeDSL <em>tags</em> ({@link XmlTag}).
 *
 * <p>Single responsibility: this provider only handles elements that resolve to a
 * {@link XmlTag}. It deliberately yields (returns {@code null}) when the element is
 * inside an {@link XmlAttribute}, so {@link ThemeDslAttributeDocumentationProvider}
 * takes over for attribute contexts. Both are registered as
 * {@code lang.documentationProvider} for {@code ThemeDSL}; the platform composes
 * them via {@code CompositeDocumentationProvider}.</p>
 *
 * <p>The documentation body is a placeholder until the real doc files are available.</p>
 */
public class ThemeDslTagDocumentationProvider extends AbstractDocumentationProvider {

    @Override
    public @Nullable String generateDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
        XmlTag tag = findTag(element);
        if (tag == null) {
            return null;
        }

        String name = tag.getName();
        var rules = RuleRepositoryService.getInstance().getRuleRepository().getElementRule(name);

        return rules.map(dslElementRule -> placeholderDoc(dslElementRule.getCategory(), getSignature(dslElementRule)))
                .orElseGet(() -> placeholderDoc("Unknown Tag", name));
    }

    private String getSignature(DslElementRule rule) {
        var nameHtml = "<b>"+rule.getElementName()+"</b>";
        var required = rule.getRequiredAttrs().stream().map(it -> "<b>"+it+"</b>").collect(Collectors.joining(" "));
        var optional = rule.getOptionalAttrs().stream().map(it -> "<i>"+it+"</i>").collect(Collectors.joining(" "));
        return "&lt"+(nameHtml+" "+required+" "+optional).replace("  "," ").trim()+"/&gt";
    }

    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        XmlTag tag = findTag(element);
        if (tag == null) {
            return null;
        }
        return "ThemeDSL tag: " + tag.getName();
    }

    /**
     * Explicitly resolves the target element for View | Quick Documentation and
     * hover, because the platform's default resolution for XML doesn't reliably
     * pick the {@link XmlTag}. Returns the enclosing {@link XmlTag} for ThemeDSL
     * files, yielding (returning {@code null}) inside attribute contexts so the
     * attribute provider takes over.
     */
    @Override
    public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor,
                                                              @NotNull PsiFile file,
                                                              @Nullable PsiElement contextElement,
                                                              int targetOffset) {
        if (contextElement == null || file.getFileType() != ThemeDslFileType.INSTANCE) {
            return null;
        }
        return findTag(contextElement);
    }

    /**
     * Walks up from {@code element} to the enclosing {@link XmlTag}, but returns
     * {@code null} if an {@link XmlAttribute} is encountered first (so attribute
     * contexts are left to the attribute provider).
     */
    @Nullable
    private static XmlTag findTag(PsiElement element) {
        PsiElement e = element;
        while (e != null) {
            if (e instanceof XmlAttribute) {
                return null;
            }
            if (e instanceof XmlTag tag) {
                return tag;
            }
            e = e.getParent();
        }
        return null;
    }

    private static String placeholderDoc(String kind, String signature) {
        return DocumentationMarkup.DEFINITION_START
                + "<b>" + kind + "</b> <code>" + signature + "</code>"
                + DocumentationMarkup.DEFINITION_END
                + DocumentationMarkup.CONTENT_START
                + "Documentation is not yet available."
                + DocumentationMarkup.CONTENT_END;
    }
}
