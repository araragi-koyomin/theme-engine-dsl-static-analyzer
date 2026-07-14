package com.huawei.theme.analysis.plugin.editor.varname;

import com.huawei.theme.analysis.plugin.editor.reference.DslVariableReference;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The injected {@code varName} node for a {@code <Var name="...">} declaration.
 *
 * <p>Extends {@link CompositePsiElement} (an AST node) so it can be created
 * directly by the {@code ASTFactory} via {@link ICompositeElementType} — this
 * works for injected PSI where {@code ParserDefinition.createElement} is not
 * called. Implements {@link PsiNameIdentifierOwner} so the platform offers
 * rename and find-usages on the variable name.</p>
 *
 * <p>{@link #setName(String)} renames via the HOST {@link XmlAttribute#setValue(String)}
 * rather than modifying the injected ID leaf. This is critical because during
 * rename refactoring, usages are renamed first (modifying the host document),
 * which invalidates the injected PSI. By the time {@code setName} is called,
 * the injected leaf is stale (parent is null). Going through the host avoids
 * this entirely.</p>
 */
public class VarNameElement extends CompositePsiElement implements PsiNameIdentifierOwner {

    public VarNameElement(IElementType type) {
        super(type);
    }

    @Nullable
    @Override
    public PsiElement getNameIdentifier() {
        for (PsiElement c = getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNode() != null && c.getNode().getElementType() == VarNameElementTypes.ID) {
                return c;
            }
        }
        return null;
    }

    @Override
    public String getName() {
        PsiElement id = getNameIdentifier();
        return id == null ? null : id.getText();
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        // Rename via the HOST XmlAttribute.setValue() — not by replacing the injected
        // ID leaf. During rename refactoring, usages are renamed first (modifying the
        // host document), which invalidates the injected PSI. By the time setName is
        // called, the injected leaf is stale (parent is null). Going through the host
        // avoids the PsiInvalidElementAccessException.
        Project project = getProject();
        if (project != null) {
            PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(project).getInjectionHost(this);
            if (host instanceof XmlAttributeValue hostValue) {
                XmlAttribute attr = PsiTreeUtil.getParentOfType(hostValue, XmlAttribute.class);
                if (attr != null) {
                    attr.setValue(name);
                    return this;
                }
            }
        }
        // Fallback: replace the ID leaf directly (for non-injected contexts)
        PsiElement id = getNameIdentifier();
        if (id != null) {
            id.replace(new LeafPsiElement(VarNameElementTypes.ID, name));
        }
        return this;
    }

    /**
     * Override the use scope to return the HOST file's scope, not the injected
     * DummyHolder's scope. This ensures {@code ReferencesSearch} searches the host
     * file where the {@link DslVariableReference}s live.
     */
    @Override
    public @NotNull SearchScope getUseScope() {
        Project project = getProject();
        if (project != null) {
            PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(project).getInjectionHost(this);
            if (host != null) {
                PsiFile hostFile = host.getContainingFile();
                if (hostFile != null) {
                    return new LocalSearchScope(hostFile);
                }
            }
        }
        return super.getUseScope();
    }
}
