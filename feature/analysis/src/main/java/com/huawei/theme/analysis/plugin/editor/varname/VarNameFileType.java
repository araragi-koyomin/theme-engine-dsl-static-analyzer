package com.huawei.theme.analysis.plugin.editor.varname;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/** File type for the VarName language (injected only; no standalone files). */
public class VarNameFileType extends LanguageFileType {

    public static final VarNameFileType INSTANCE = new VarNameFileType();

    private VarNameFileType() {
        super(VarNameLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "VarName";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "DSL variable name";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return null;
    }
}
