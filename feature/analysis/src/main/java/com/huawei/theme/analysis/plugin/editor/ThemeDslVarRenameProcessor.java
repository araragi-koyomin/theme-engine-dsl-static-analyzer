package com.huawei.theme.analysis.plugin.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
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
 * Rename processor for ThemeDSL variables ({@code <Var name="...">}).
 *
 * <p>Handles rename from three entry points:</p>
 * <ul>
 *   <li>The {@code <Var>} tag (redirected to the {@code name} value via
 *       {@link #substituteElementToRename})</li>
 *   <li>The {@code name} attribute or its value ({@link XmlAttributeValue})</li>
 *   <li>The injected {@link VarNameElement} (the {@link com.intellij.psi.PsiNameIdentifierOwner})</li>
 * </ul>
 *
 * <p>For {@link VarNameElement}, the host {@link XmlAttribute} is found BEFORE
 * the usage rename (while the injected PSI is still valid). Then usages are
 * renamed (via {@link PsiReference#handleElementRename} on host-side
 * {@link DslVariableReference}s), and finally the declaration is renamed via
 * {@link XmlAttribute#setValue(String)}. This order is critical: renaming usages
 * modifies the host document, which invalidates the injected PSI. If the
 * declaration were renamed via the injected {@link VarNameElement#setName}, it
 * would crash with {@code PsiInvalidElementAccessException}.</p>
 */
public class ThemeDslVarRenameProcessor extends RenamePsiElementProcessor {

    @Override
    public boolean canProcessElement(@NotNull PsiElement element) {
        if (element instanceof XmlTag tag) {
            return "Var".equals(tag.getName()) && tag.getAttribute("name") != null;
        }
        if (element instanceof XmlAttribute attr) {
            XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
            return "name".equals(attr.getName()) && tag != null && "Var".equals(tag.getName());
        }
        if (element instanceof XmlAttributeValue value) {
            XmlAttribute attr = PsiTreeUtil.getParentOfType(value, XmlAttribute.class);
            if (attr == null || !"name".equals(attr.getName())) {
                return false;
            }
            XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
            return tag != null && "Var".equals(tag.getName());
        }
        if (element instanceof VarNameElement) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isInplaceRenameSupported() {
        return false;
    }

    @Override
    public PsiElement substituteElementToRename(@NotNull PsiElement element, @Nullable Editor editor) {
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
                return value;
            }
        }
        return super.substituteElementToRename(element, editor);
    }

    @Override
    public void renameElement(@NotNull PsiElement element,
                              @NotNull String newName,
                              @NotNull UsageInfo @NotNull [] usages,
                              @Nullable RefactoringElementListener listener) {
        // Find the host XmlAttribute BEFORE renaming usages (element is still valid).
        // For VarNameElement, the injected PSI is invalidated after usage rename,
        // so we must cache the host reference now.
        XmlAttribute nameAttr = findHostNameAttribute(element);
        if (nameAttr == null) {
            return;
        }

        // Rename usages in REVERSE document order so that renaming a later reference
        // doesn't shift the range of an earlier one in the same attribute value
        // (e.g. "#a + #b + #a" — rename the second #a first, then the first).
        List<UsageInfo> sortedUsages = new ArrayList<>(Arrays.asList(usages));
        sortedUsages.sort((a, b) -> Integer.compare(getReferenceStart(b), getReferenceStart(a)));
        for (UsageInfo usage : sortedUsages) {
            PsiReference ref = usage.getReference();
            if (ref != null) {
                try {
                    ref.handleElementRename(newName);
                } catch (IncorrectOperationException ignored) {
                }
            }
        }

        // Rename the declaration: name="oldName" -> name="newName"
        // The XmlAttribute is still valid (only expression attributes were modified
        // by the usage rename, not the name attribute).
        try {
            nameAttr.setValue(newName);
        } catch (IncorrectOperationException ignored) {
            return;
        }

        if (listener != null) {
            listener.elementRenamed(element);
        }
    }

    /**
     * Returns the document offset of a reference's start, for sorting.
     */
    private static int getReferenceStart(UsageInfo usage) {
        PsiReference ref = usage.getReference();
        if (ref == null || ref.getElement() == null) {
            return 0;
        }
        return ref.getElement().getTextRange().getStartOffset() + ref.getRangeInElement().getStartOffset();
    }

    /**
     * Finds the host {@code name} {@link XmlAttribute} from the rename target.
     * Works for {@link VarNameElement} (via {@link InjectedLanguageManager#getInjectionHost}),
     * {@link XmlTag}, {@link XmlAttribute}, and {@link XmlAttributeValue}.
     */
    @Nullable
    private static XmlAttribute findHostNameAttribute(@NotNull PsiElement element) {
        if (element instanceof XmlTag tag) {
            return tag.getAttribute("name");
        }
        if (element instanceof XmlAttribute attr) {
            return attr;
        }
        if (element instanceof XmlAttributeValue value) {
            return PsiTreeUtil.getParentOfType(value, XmlAttribute.class);
        }
        if (element instanceof VarNameElement) {
            Project project = element.getProject();
            if (project != null) {
                PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(project).getInjectionHost(element);
                if (host != null) {
                    return PsiTreeUtil.getParentOfType(host, XmlAttribute.class);
                }
            }
        }
        return null;
    }
}
