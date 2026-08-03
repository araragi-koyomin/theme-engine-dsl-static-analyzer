package com.huawei.theme.analysis.plugin.editor.reference;

import java.util.ArrayList;
import java.util.List;

import com.huawei.theme.analysis.plugin.editor.varname.VarNameElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;

import com.huawei.theme.analysis.core.expression.ExpressionParser;

/**
 * Contributes references for ThemeDSL variable names:
 * <ol>
 *   <li>A <b>self-reference</b> on the {@code <Var name="...">} declaration's {@code name}
 *       value, resolving to the injected {@link VarNameElement}. This gives find-usages and
 *       rename an entry point on the <em>declaration</em> side (same mechanism as the
 *       {@code @x}/{@code #x} references on the usage side) — without it, the platform sees
 *       only the host {@link XmlAttributeValue} (not a {@link com.intellij.psi.PsiNamedElement})
 *       and reports "cannot search / rename from this location".</li>
 *   <li>{@link DslVariableReference}s for {@code @name}/{@code #name} in expression attribute
 *       values (the usage side), resolving to the same {@link VarNameElement}.</li>
 * </ol>
 */
public class ThemeDslVariableReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(XmlAttributeValue.class),
                new ThemeDslVariableReferenceProvider());
    }

    private static final class ThemeDslVariableReferenceProvider extends PsiReferenceProvider {
        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                               @NotNull ProcessingContext context) {
            if (!(element instanceof XmlAttributeValue value)) {
                return PsiReference.EMPTY_ARRAY;
            }
            XmlAttribute attr = PsiTreeUtil.getParentOfType(value, XmlAttribute.class);
            if (attr == null) {
                return PsiReference.EMPTY_ARRAY;
            }
            // <Var name="...">: declaration — no reference needed (VarNameElement is the PsiNameIdentifierOwner).
            if ("name".equals(attr.getName())) {
                XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
                if (tag != null && "Var".equals(tag.getName())) {
                    return PsiReference.EMPTY_ARRAY;
                }
                // <VariableCommand name="...">: reference to the <Var> declaration.
                if (tag != null && "VariableCommand".equals(tag.getName())) {
                    String name = value.getValue();
                    if (name == null || name.isEmpty()) {
                        return PsiReference.EMPTY_ARRAY;
                    }
                    TextRange range =
                            value.getValueTextRange().shiftLeft(value.getTextRange().getStartOffset());
                    return new PsiReference[]{new DslVariableReference(value, range, name)};
                }
            }
            // Expression attribute: @x/#x usage references.
            if (!isExpressionAttribute(value)) {
                return PsiReference.EMPTY_ARRAY;
            }
            String text = value.getValue();
            if (text == null || text.isEmpty()) {
                return PsiReference.EMPTY_ARRAY;
            }
            int valueStartInElement =
                    value.getValueTextRange().getStartOffset() - value.getTextRange().getStartOffset();
            List<PsiReference> references = new ArrayList<>();
            for (VarRef ref : scanVariableRefs(text)) {
                TextRange rangeInElement =
                        TextRange.from(valueStartInElement + ref.nameStart, ref.nameEnd - ref.nameStart);
                references.add(new DslVariableReference(value, rangeInElement, ref.name));
            }
            return references.toArray(new PsiReference[0]);
        }
    }

    /**
     * A self-reference on the {@code <Var name="...">} declaration value, resolving to the
     * injected {@link VarNameElement} (a {@link com.intellij.psi.PsiNameIdentifierOwner}).
     * {@link #handleElementRename} is a no-op because the declaration is renamed via
     * {@link VarNameElement#setName(String)}, not via the reference.
     */
    private static final class VarNameSelfReference extends PsiReferenceBase<XmlAttributeValue> {

        VarNameSelfReference(@NotNull XmlAttributeValue element, @NotNull TextRange rangeInElement) {
            super(element, rangeInElement, false);
        }

        @Override
        public @Nullable PsiElement resolve() {
            XmlAttributeValue value = getElement();
            Project project = value.getProject();
            if (project == null) {
                return value;
            }
            List<Pair<PsiElement, TextRange>> injected =
                    InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(value);
            if (injected != null) {
                for (Pair<PsiElement, TextRange> entry : injected) {
                    PsiElement e = entry.getFirst();
                    VarNameElement vne = e instanceof VarNameElement v ? v
                            : PsiTreeUtil.getChildOfType(e, VarNameElement.class);
                    if (vne != null) {
                        return vne;
                    }
                }
            }
            return value;
        }

        @Override
        public PsiElement handleElementRename(@NotNull String newElementName) {
            return getElement();
        }
    }

    private static boolean isExpressionAttribute(XmlAttributeValue value) {
        XmlAttribute attr = PsiTreeUtil.getParentOfType(value, XmlAttribute.class);
        if (attr == null) {
            return false;
        }
        return ExpressionParser.hasExpressionSyntax(attr.getValue(), attr.getName());
    }

    static List<VarRef> scanVariableRefs(String text) {
        List<VarRef> refs = new ArrayList<>();
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\'') {
                i++;
                while (i < n) {
                    if (text.charAt(i) == '\\' && i + 1 < n && text.charAt(i + 1) == '\'') {
                        i += 2;
                        continue;
                    }
                    if (text.charAt(i) == '\'') {
                        i++;
                        break;
                    }
                    i++;
                }
                continue;
            }
            if (c == '@' || c == '#') {
                int nameStart = i + 1;
                int j = nameStart;
                while (j < n) {
                    char ch = text.charAt(j);
                    if (isNameChar(ch)) {
                        j++;
                        continue;
                    }
                    // Consume a compile-time interpolation %{...} as part of the name, so a
                    // reference like #x_%{i} scans as one ref (x_%{i}), not a truncated x_.
                    if (ch == '%' && j + 1 < n && text.charAt(j + 1) == '{') {
                        int close = indexOfCloseBrace(text, j + 2, n);
                        if (close < 0) {
                            break;
                        }
                        j = close + 1;
                        continue;
                    }
                    break;
                }
                while (j > nameStart && text.charAt(j - 1) == '.') {
                    j--;
                }
                if (j > nameStart) {
                    refs.add(new VarRef(text.substring(nameStart, j), nameStart, j));
                } else {
                    i++;
                }
                i = Math.max(i, j);
                continue;
            }
            i++;
        }
        return refs;
    }

    private static int indexOfCloseBrace(String text, int from, int end) {
        for (int k = from; k < end; k++) {
            if (text.charAt(k) == '}') {
                return k;
            }
        }
        return -1;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }

    record VarRef(String name, int nameStart, int nameEnd) {
    }
}
