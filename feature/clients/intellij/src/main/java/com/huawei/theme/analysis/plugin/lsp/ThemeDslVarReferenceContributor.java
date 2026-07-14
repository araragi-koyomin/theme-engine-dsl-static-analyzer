package com.huawei.theme.analysis.plugin.lsp;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
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
 * Contributes PSI references for DSL variable names inside expression
 * attribute values: {@code #name} (numeric) and {@code @name} (string)
 * resolve to the {@code <Var name="...">} declaration.
 *
 * <p>Adapted from the psi-adapter branch's
 * {@code ThemeDslVariableReferenceContributor}, but simplified to work
 * without language injection: references are created directly on the host
 * {@link XmlAttributeValue}, with the variable-reference text range
 * calculated relative to the element. Whether an attribute carries an
 * expression is decided by {@link ExpressionParser#hasExpressionSyntax}
 * (core, no rule repository needed).</p>
 */
public final class ThemeDslVarReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(XmlAttributeValue.class),
                new PsiReferenceProvider() {
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
                        // Skip the <Var name="..."> declaration itself.
                        if ("name".equals(attr.getName())) {
                            XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
                            if (tag != null && "Var".equals(tag.getName())) {
                                return PsiReference.EMPTY_ARRAY;
                            }
                        }
                        // Only process expression attributes.
                        String attrValue = value.getValue();
                        if (!ExpressionParser.hasExpressionSyntax(attrValue, attr.getName())) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        if (attrValue == null || attrValue.isEmpty()) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        int valueStartInElement =
                                value.getValueTextRange().getStartOffset() - value.getTextRange().getStartOffset();
                        List<PsiReference> references = new ArrayList<>();
                        for (VarRef ref : scanVariableRefs(attrValue)) {
                            TextRange rangeInElement =
                                    TextRange.from(valueStartInElement + ref.nameStart, ref.nameEnd - ref.nameStart);
                            references.add(new DslVarReference(value, rangeInElement, ref.name));
                        }
                        return references.toArray(new PsiReference[0]);
                    }
                });
    }

    /**
     * Scans the expression text for {@code #name}/{@code @name} variable
     * references, skipping single-quoted string literals.
     */
    static List<VarRef> scanVariableRefs(String text) {
        List<VarRef> refs = new ArrayList<>();
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            // Skip single-quoted strings ('...' with escaped '' inside)
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
                while (j < n && isNameChar(text.charAt(j))) {
                    j++;
                }
                // Trim trailing dots
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

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }

    record VarRef(String name, int nameStart, int nameEnd) {
    }
}
