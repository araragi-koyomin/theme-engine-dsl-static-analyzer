package com.huawei.theme.analysis.plugin.editor;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DslExpressionFileType extends LanguageFileType {

    public static final DslExpressionFileType INSTANCE = new DslExpressionFileType();

    private DslExpressionFileType() {
        super(DslExpressionLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return "DslExpression";
    }

    @Override
    public @NotNull String getDescription() {
        return "DSL expression file";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "de";
    }

    @Override
    public @Nullable Icon getIcon() {
        return null;
    }
}
