package com.huawei.theme.analysis.plugin.editor.expr;

import com.intellij.lang.Language;

public class DslExpressionLanguage extends Language {

    public static final DslExpressionLanguage INSTANCE = new DslExpressionLanguage();

    private DslExpressionLanguage() {
        super("DslExpression");
    }
}
