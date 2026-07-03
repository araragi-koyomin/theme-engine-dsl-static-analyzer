package com.huawei.theme.analysis.plugin.editor;

import com.intellij.psi.tree.IElementType;

/** Token/element types for the VarName language. */
final class VarNameElementTypes {

    static final IElementType ID = new IElementType("VAR_NAME_ID", VarNameLanguage.INSTANCE);
    static final IElementType VAR_NAME = new IElementType("varName", VarNameLanguage.INSTANCE);

    private VarNameElementTypes() {
    }
}
