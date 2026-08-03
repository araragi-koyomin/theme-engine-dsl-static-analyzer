package com.huawei.theme.analysis.plugin.editor.reference;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import com.huawei.theme.analysis.core.semanticanalysis.model.VarDeclaration;

/**
 * A host-side reference from an {@code @name}/{@code #name} usage (inside an
 * attribute value) to the declaring {@link com.huawei.theme.analysis.plugin.editor.varname.VarNameElement}.
 *
 * <p>Resolution goes through the AST {@link com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable}
 * (via {@link VarNameResolver}): the variable is looked up in the scope of the
 * reference's enclosing element, the declaration's AST node is mapped back to its
 * PSI {@link com.intellij.psi.xml.XmlTag} via the PSI↔AST map, then the existing
 * (untouched) language injection yields the {@code VarNameElement}. Soft so that
 * built-in global variables (no PSI declaration) do not show unresolved squiggles.</p>
 */
public class DslVariableReference extends PsiReferenceBase<XmlAttributeValue> {

    private final String varName;

    public DslVariableReference(@NotNull XmlAttributeValue element, @NotNull TextRange rangeInElement, @NotNull String varName) {
        super(element, rangeInElement, true);
        this.varName = varName;
    }

    @Override
    public @Nullable PsiElement resolve() {
        XmlFile file = getHostFile();
        if (file == null) {
            return null;
        }
        Project project = getElement().getProject();
        if (project == null) {
            return null;
        }
        XmlTag hostTag = PsiTreeUtil.getParentOfType(getElement(), XmlTag.class);
        return VarNameResolver.resolveDeclaration(project, file, hostTag, varName);
    }

    @Override
    public @NotNull Object @NotNull [] getVariants() {
        XmlFile file = getHostFile();
        if (file == null) {
            return EMPTY_ARRAY;
        }
        Project project = getElement().getProject();
        if (project == null) {
            return EMPTY_ARRAY;
        }
        XmlTag hostTag = PsiTreeUtil.getParentOfType(getElement(), XmlTag.class);
        List<LookupElement> variants = new ArrayList<>();
        for (VarDeclaration d : VarNameResolver.visibleDeclarations(project, file, hostTag)) {
            String typeText = d.isGlobal() ? VarNameResolver.typeName(d.getType()) : "Var";
            variants.add(LookupElementBuilder.create(d.getName())
                    .withIcon(AllIcons.Nodes.Variable)
                    .withTypeText(typeText));
        }
        return variants.toArray();
    }

    @Nullable
    private XmlFile getHostFile() {
        PsiElement element = getElement();
        if (element == null) {
            return null;
        }
        return element.getContainingFile() instanceof XmlFile xmlFile ? xmlFile : null;
    }
}
