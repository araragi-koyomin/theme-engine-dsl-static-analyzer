package com.huawei.theme.analysis.plugin.editor.themedsl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.XmlLikeFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ThemeDslFileType extends XmlLikeFileType {

    public static final ThemeDslFileType INSTANCE = new ThemeDslFileType();

    private ThemeDslFileType() {
        super(ThemeDslLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return "ThemeDSL";
    }

    @Override
    public @NotNull String getDescription() {
        return "Theme DSL file";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "";
    }

    @Override
    public Icon getIcon() {
        return AllIcons.FileTypes.Xml;
    }
}
