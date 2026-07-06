package com.huawei.theme.analysis.plugin.editor.varname;

import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;

final class VarNameElementTypes {

    static final IElementType ID = new IElementType("VAR_NAME_ID", VarNameLanguage.INSTANCE);

    static final IElementType VAR_NAME = new VarNameElementType();

    private VarNameElementTypes() {
    }

    /**
     * An {@link IElementType} that implements {@link ICompositeElementType} so the
     * platform's {@code ASTFactory} creates a {@link VarNameElement} directly via
     * {@link #createCompositeNode()} — this works even for injected PSI, where
     * {@code ParserDefinition.createElement} is not called.
     */
    private static final class VarNameElementType extends IElementType implements ICompositeElementType {
        VarNameElementType() {
            super("varName", VarNameLanguage.INSTANCE);
        }

        @Override
        public @org.jetbrains.annotations.NotNull CompositeElement createCompositeNode() {
            return new VarNameElement(this);
        }
    }
}
