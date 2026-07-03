package com.huawei.theme.analysis.plugin.editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;

/**
 * Enables renaming a ThemeDSL variable declared as {@code <Var name="x">}.
 *
 * <p>{@link DslVariableReference#resolve()} returns the {@code <Var>} tag, so
 * rename can be invoked from a usage ({@code @x}/{@code #x}, resolves to the tag)
 * or directly on the declaration (the tag or its {@code name} value). The tag is
 * platform PSI; its {@link XmlTag#getName()} is the tag name "Var", not the
 * variable name, so the default rename machinery would rename the tag. This
 * processor intercepts and redirects the rename to the {@code name} attribute
 * value:
 * <ul>
 *   <li>usages are renamed first via each reference's {@link PsiReference#handleElementRename}
 *       (rewrites the {@code @x}/{@code #x} identifier through the
 *       {@link XmlAttributeValue} element manipulator, preserving the sigil);</li>
 *   <li>the declaration is then renamed via {@link XmlAttribute#setValue(String)},
 *       i.e. {@code name="x"} &rarr; {@code name="newName"}.</li>
 * </ul>
 *
 * <p>Registered via {@code com.intellij.renamePsiElementProcessor}.</p>
 */
public class ThemeDslVarRenameProcessor extends RenamePsiElementProcessor {

    @Override
    public boolean canProcessElement(@NotNull PsiElement element) {
        boolean result;
        if (element instanceof XmlTag tag) {
            result = "Var".equals(tag.getName()) && tag.getAttribute("name") != null;
        } else if (element instanceof XmlAttribute attr) {
            XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
            result = "name".equals(attr.getName()) && tag != null && "Var".equals(tag.getName());
        } else if (element instanceof XmlAttributeValue value) {
            XmlAttribute attr = PsiTreeUtil.getParentOfType(value, XmlAttribute.class);
            if (attr == null || !"name".equals(attr.getName())) {
                result = false;
            } else {
                XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
                result = tag != null && "Var".equals(tag.getName());
            }
        } else {
            result = false;
        }
        System.err.println("[DSL-RENAME] canProcessElement element=" + element.getClass().getSimpleName()
                + " -> " + result);
        return result;
    }

    /** Force the dialog rename path (which calls {@link #renameElement}); the inline path bypasses it. */
    @Override
    public boolean isInplaceRenameSupported() {
        return false;
    }

    /**
     * Redirect rename invoked on the {@code <Var>} tag or its {@code name} attribute to the
     * {@code name} attribute <em>value</em>, which is the actual variable identifier. Without this,
     * the platform would rename the tag name "Var" or the attribute name "name".
     */
    @Override
    public PsiElement substituteElementToRename(@NotNull PsiElement element, @Nullable Editor editor) {
        System.err.println("[DSL-RENAME] substituteElementToRename element=" + element.getClass().getSimpleName());
        XmlAttribute nameAttr = null;
        if (element instanceof XmlTag tag && "Var".equals(tag.getName())) {
            nameAttr = tag.getAttribute("name");
        } else if (element instanceof XmlAttribute attr && "name".equals(attr.getName())) {
            XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
            if (tag != null && "Var".equals(tag.getName())) {
                nameAttr = attr;
            }
        }
        if (nameAttr != null) {
            XmlAttributeValue value = nameAttr.getValueElement();
            if (value != null) {
                System.err.println("[DSL-RENAME] substitute -> name value");
                return value;
            }
        }
        return super.substituteElementToRename(element, editor);
    }

    @Override
    public void prepareRenaming(@NotNull PsiElement element,
                               @NotNull String newName,
                               @NotNull java.util.Map<PsiElement, String> allRenames) {
        System.err.println("[DSL-RENAME] prepareRenaming element=" + element.getClass().getSimpleName()
                + " newName='" + newName + "'");
        super.prepareRenaming(element, newName, allRenames);
    }

    @Override
    public void renameElement(@NotNull PsiElement element,
                              @NotNull String newName,
                              @NotNull UsageInfo @NotNull [] usages,
                              @Nullable RefactoringElementListener listener) {
        System.err.println("[DSL-RENAME] renameElement element=" + element.getClass().getSimpleName()
                + " newName='" + newName + "' usages=" + usages.length);
        XmlTag tag = element instanceof XmlTag t ? t
                : PsiTreeUtil.getParentOfType(PsiTreeUtil.getParentOfType(element, XmlAttribute.class), XmlTag.class);
        if (tag == null) {
            System.err.println("[DSL-RENAME] renameElement: no tag");
            return;
        }
        XmlAttribute nameAttr = tag.getAttribute("name");
        if (nameAttr == null) {
            System.err.println("[DSL-RENAME] renameElement: no name attr");
            return;
        }
        // Rename usages (@x/#x references) while they still resolve to the old declaration.
        for (UsageInfo usage : usages) {
            PsiReference ref = usage.getReference();
            if (ref != null) {
                try {
                    ref.handleElementRename(newName);
                } catch (IncorrectOperationException ignored) {
                    // skip references that cannot be renamed
                }
            }
        }
        // Rename the declaration: name="x" -> name="newName"
        try {
            nameAttr.setValue(newName);
        } catch (IncorrectOperationException ignored) {
            return;
        }
        if (listener != null) {
            // The tag persists across the attribute-value change.
            listener.elementRenamed(tag);
        }
    }
}
