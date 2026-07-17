package com.huawei.theme.analysis.plugin.lsp;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.FakePsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Carries the exact caret/hover offset (captured by
 * {@link com.intellij.lang.documentation.DocumentationProvider#getCustomDocumentationElement})
 * through to {@link ThemeDslLspHoverProvider#generateDoc}, so the server
 * receives the precise position the user is hovering on — not the PSI
 * element's start offset (which for {@code XmlAttributeValue} is always the
 * value start, not the individual expression token).
 *
 * <p>Delegates {@code getContainingFile}/{@code getProject}/{@code getManager}
 * to the original cursor PSI so IntelliJ can resolve the file/project context.</p>
 */
final class DslHoverElement extends FakePsiElement {

    private final PsiElement original;
    private final int offset;

    DslHoverElement(@Nullable PsiElement original, int offset) {
        this.original = original;
        this.offset = offset;
    }

    PsiElement getOriginal() {
        return original;
    }

    int getOffset() {
        return offset;
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
    public int getTextOffset() {
        return offset;
    }

    @Override
    public @Nullable String getPresentableText() {
        return "DslHoverElement[" + offset + "]";
    }

    @Override
    public String toString() {
        return "DslHoverElement[" + offset + "]";
    }
}
