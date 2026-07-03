package com.huawei.theme.analysis.plugin.editor;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The injected {@code varName} node for a {@code <Var name="...">} declaration.
 *
 * <p>Implements {@link PsiNameIdentifierOwner} so the platform offers rename and find-usages
 * on the variable name (stopping the walk-up here instead of at the {@code <Var>} tag or
 * {@code name} attribute). {@link #setName(String)} rewrites the ID leaf, which propagates
 * to the host XML attribute value via the injection.</p>
 */
public class VarNameElement extends ASTWrapperPsiElement implements PsiNameIdentifierOwner {

    public VarNameElement(@NotNull ASTNode node) {
        super(node);
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
        PsiElement id = getNameIdentifier();
        if (id != null) {
            id.replace(new LeafPsiElement(VarNameElementTypes.ID, name));
        }
        return this;
    }
}
