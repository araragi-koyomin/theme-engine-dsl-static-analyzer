package com.huawei.theme.analysis.file;

import com.intellij.openapi.vfs.VirtualFile;

import com.intellij.psi.PsiFile;

public interface DslFileMatcher {
    boolean isDslFile(VirtualFile file);

    boolean isDslFile(PsiFile psiFile);
}
