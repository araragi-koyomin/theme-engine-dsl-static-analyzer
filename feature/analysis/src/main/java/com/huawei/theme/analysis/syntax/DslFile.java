package com.huawei.theme.analysis.syntax;

import com.intellij.lang.Language;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.tree.IElementType;

public class DslFile extends XmlFileImpl {
    public DslFile(FileViewProvider viewProvider) {
        super(viewProvider, DslElementTypes.DSL_FILE);
    }

    @Override
    public Language getLanguage() {
        return DslLanguage.INSTANCE;
    }

    @Override
    public String toString() {
        return "DslFile:" + getName();
    }
}
