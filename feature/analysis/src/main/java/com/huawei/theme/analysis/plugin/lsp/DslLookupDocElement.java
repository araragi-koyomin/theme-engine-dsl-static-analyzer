package com.huawei.theme.analysis.plugin.lsp;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.FakePsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Synthetic PSI element that carries the documentation markup for a
 * completion lookup item. Returned from
 * {@link com.intellij.lang.documentation.DocumentationProvider#getDocumentationElementForLookupItem}
 * so IntelliJ's completion documentation panel calls
 * {@link com.intellij.lang.documentation.DocumentationProvider#generateDoc}
 * on it, which {@link ThemeDslLspHoverProvider} intercepts to return the
 * carried markup directly (no server round-trip — the markup already came
 * from the server in the {@code CompletionItem.documentation} field).
 *
 * <p>Context methods ({@code getContainingFile}, {@code getProject},
 * {@code getManager}, {@code getParent}) delegate to the cursor PSI element
 * passed at construction, so IntelliJ can resolve the file/language context
 * without hitting null defaults from {@link FakePsiElement} (which would make
 * the documentation panel bail out silently).</p>
 */
final class DslLookupDocElement extends FakePsiElement {

    private final String name;
    private final String markup;
    private final PsiElement original;

    DslLookupDocElement(@NotNull String name, @NotNull String markup, @Nullable PsiElement original) {
        this.name = name;
        this.markup = markup;
        this.original = original;
    }

    @Override
    public @NotNull String getName() {
        return name;
    }

    @Override
    public @Nullable PsiElement getParent() {
        return original != null ? original.getParent() : null;
    }

    @Override
    public @Nullable PsiFile getContainingFile() {
        return original != null ? original.getContainingFile() : null;
    }

    @Override
    public @Nullable Project getProject() {
        return original != null ? original.getProject() : null;
    }

    @Override
    public @Nullable PsiManager getManager() {
        return original != null ? original.getManager() : null;
    }

    @Override
    public @Nullable String getPresentableText() {
        return name;
    }

    @Override
    public int getTextOffset() {
        return original != null ? original.getTextOffset() : 0;
    }

    @Override
    public String toString() {
        return "DslLookupDocElement[" + name + "]";
    }

    @NotNull
    String getMarkup() {
        return markup;
    }
}
