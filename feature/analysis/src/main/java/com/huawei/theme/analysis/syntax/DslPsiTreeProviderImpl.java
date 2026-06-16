package com.huawei.theme.analysis.syntax;

import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlTag;

public class DslPsiTreeProviderImpl implements PsiTreeProvider {
    private final Project project;

    public DslPsiTreeProviderImpl(Project project) {
        this.project = project;
    }

    @Override
    public DslFile getDslPsiTree(VirtualFile file) {
        if (file == null) {
            return null;
        }
        PsiManager psiManager = PsiManager.getInstance(project);
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile instanceof DslFile) {
            return (DslFile) psiFile;
        }
        return null;
    }

    @Override
    public List<PsiElement> findElementsByName(PsiFile file, String elementName) {
        if (file == null || elementName == null) {
            return List.of();
        }
        List<PsiElement> result = new ArrayList<>();
        file.accept(new XmlRecursiveElementVisitor() {
            @Override
            public void visitXmlTag(XmlTag tag) {
                if (tag.getName().equals(elementName)) {
                    result.add(tag);
                }
                super.visitXmlTag(tag);
            }
        });
        return result;
    }
}
