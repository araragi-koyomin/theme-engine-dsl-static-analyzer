package com.huawei.theme.analysis.syntax;

import com.intellij.lang.Language;

public class DslLanguage extends Language {
    public static final DslLanguage INSTANCE = new DslLanguage();

    private DslLanguage() {
        super("Dsl");
    }
}
