package com.huawei.theme.analysis.plugin.editor.themedsl;

import com.intellij.lang.xml.XMLLanguage;

public class ThemeDslLanguage extends XMLLanguage {

    public static final ThemeDslLanguage INSTANCE = new ThemeDslLanguage();

    private ThemeDslLanguage() {
        super(XMLLanguage.INSTANCE, "ThemeDSL");
    }
}
