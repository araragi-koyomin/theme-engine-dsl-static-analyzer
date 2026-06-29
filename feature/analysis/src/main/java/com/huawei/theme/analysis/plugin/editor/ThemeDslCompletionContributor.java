package com.huawei.theme.analysis.plugin.editor;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import org.jetbrains.annotations.NotNull;

public class ThemeDslCompletionContributor extends CompletionContributor {

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        // TODO only for testing
        result.addElement(LookupElementBuilder.create("Hello"));
        result.addElement(LookupElementBuilder.create("World"));
    }
}
