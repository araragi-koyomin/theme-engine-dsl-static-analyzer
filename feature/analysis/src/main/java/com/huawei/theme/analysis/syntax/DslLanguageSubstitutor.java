package com.huawei.theme.analysis.syntax;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutor;

import com.huawei.theme.analysis.DslAnalysisService;

public class DslLanguageSubstitutor extends LanguageSubstitutor {
    @Override
    public Language getLanguage(VirtualFile file, Project project) {
        DslAnalysisService service = ApplicationManager.getApplication().getService(DslAnalysisService.class);
        if (service != null && service.getFileMatcher().isDslFile(file)) {
            return DslLanguage.INSTANCE;
        }
        return null;
    }
}
