package com.huawei.theme.analysis.plugin.editor;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * IntelliJ entry point that supplies a {@link DslExpressionSyntaxHighlighter}
 * for editors editing DslExpression (.{@code de}) files.
 *
 * <p>Registered via {@code <lang.syntaxHighlighter language="DslExpression">}
 * in {@code plugin.xml}.</p>
 */
public class DslExpressionSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

    @NotNull
    @Override
    public SyntaxHighlighter getSyntaxHighlighter(Project project, VirtualFile virtualFile) {
        return new DslExpressionSyntaxHighlighter();
    }
}
