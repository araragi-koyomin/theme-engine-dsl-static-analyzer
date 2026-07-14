package com.huawei.theme.analysis.plugin.editor;

import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NotNull;

public class ThemeDslParserDefinition extends XMLParserDefinition {

    private static final IFileElementType FILE_ELEMENT_TYPE = new IFileElementType(ThemeDslLanguage.INSTANCE);

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return FILE_ELEMENT_TYPE;
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new XmlFileImpl(viewProvider, FILE_ELEMENT_TYPE);
    }
}
