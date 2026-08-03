package com.huawei.theme.analysis.plugin.ast;

import java.lang.reflect.Field;
import java.util.Map;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightPlatformTestCase;

public class DslAstServiceCacheTest extends LightPlatformTestCase {

    public void testServiceDoesNotOwnProjectLifetimeFileMap() {
        for (Field field : DslAstService.class.getDeclaredFields()) {
            assertFalse("Project service must not retain per-file entries in a strong Map: " + field.getName(),
                    Map.class.isAssignableFrom(field.getType()));
        }
    }

    public void testFileOwnedCacheReusesAndInvalidatesByPsiModificationStamp() {
        XmlFile file = (XmlFile) PsiFileFactory.getInstance(getProject())
                .createFileFromText("test.xml", XmlFileType.INSTANCE, "<Lockscreen/>");
        DslAstService service = DslAstService.getInstance(getProject());

        DslAstTree first = service.getTree(file);
        assertSame(first, service.getTree(file));

        XmlTag root = file.getRootTag();
        assertNotNull(root);
        ApplicationManager.getApplication().runWriteAction(
                (Runnable) () -> root.setAttribute("name", "changed"));

        DslAstTree rebuilt = service.getTree(file);
        assertNotSame(first, rebuilt);
        assertEquals("changed", rebuilt.getAst().getRootElement().getAttributes().get(0).getValue().getRawValue());
    }
}
