package com.huawei.theme.analysis.plugin.editor;

import com.intellij.lang.Language;

/** A tiny injected language for {@code <Var name="...">} identifiers, so the name is a
 *  {@link com.intellij.psi.PsiNameIdentifierOwner} (renameable + find-usages target). */
public class VarNameLanguage extends Language {

    public static final VarNameLanguage INSTANCE = new VarNameLanguage();

    private VarNameLanguage() {
        super("VarName");
    }
}
