package com.huawei.theme.analysis.syntax;

import java.util.List;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public interface PsiTreeProvider {
    DslFile getDslPsiTree(VirtualFile file);
    List<PsiElement> findElementsByName(PsiFile file, String elementName);
}
