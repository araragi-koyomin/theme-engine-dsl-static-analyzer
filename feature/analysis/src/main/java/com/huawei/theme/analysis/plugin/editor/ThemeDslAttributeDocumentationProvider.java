package com.huawei.theme.analysis.plugin.editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;

/**
 * Quick Documentation / hover provider for ThemeDSL <em>attributes</em>
 * ({@link XmlAttribute}).
 *
 * <p>Single responsibility: this provider only handles elements that resolve to a
 * {@link XmlAttribute} (the attribute name, equals sign, or value). It returns
 * {@code null} for everything else, so {@link ThemeDslTagDocumentationProvider}
 * handles tag contexts. Both are registered as {@code lang.documentationProvider}
 * for {@code ThemeDSL}; the platform composes them via
 * {@code CompositeDocumentationProvider}.</p>
 *
 * <p>The documentation body is a placeholder until the real doc files are available.</p>
 */
public class ThemeDslAttributeDocumentationProvider extends AbstractDocumentationProvider {

    @Override
    public @Nullable String generateDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
        XmlAttribute attribute = findAttribute(element);
        if (attribute == null) {
            return null;
        }
        return placeholderDoc("Attr", attribute.getName());
    }

    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        XmlAttribute attribute = findAttribute(element);
        if (attribute == null) {
            return null;
        }
        return "ThemeDSL attribute: " + attribute.getName();
    }

    /**
     * Explicitly resolves the target element for View | Quick Documentation and
     * hover, because the platform's default resolution for XML doesn't reliably
     * pick the {@link XmlAttribute}. Returns the enclosing {@link XmlAttribute}
     * for ThemeDSL files; {@code null} otherwise (so the tag provider handles
     * tag contexts).
     */
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

    /**
     * Walks up from {@code element} to the enclosing {@link XmlAttribute}.
     */
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

    private static String placeholderDoc(String kind, String signature) {
        return DocumentationMarkup.DEFINITION_START
                + "<b>" + kind + "</b> <code>" + signature + "</code>"
                + DocumentationMarkup.DEFINITION_END
                + DocumentationMarkup.CONTENT_START
                + "Documentation is not yet available."
                + DocumentationMarkup.CONTENT_END;
    }
}
