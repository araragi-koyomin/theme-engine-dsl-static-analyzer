package com.huawei.theme.analysis.plugin.ast;

import java.io.IOException;
import java.util.List;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.LightPlatformTestCase;

import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

public class DslAnalysisContextTest extends LightPlatformTestCase {

    public void testOneRootContextTracksTwoIncludeInstances() {
        projectFile("script.xml", "<Lockscreen>"
                + "<Var name='present'/>"
                + "<Include name='function_shared.xml' target='present'/>"
                + "<Include name='function_shared.xml' target='missing'/>"
                + "</Lockscreen>");
        XmlFile child = projectFile("function_shared.xml",
                "<Var name='sub_%{target}' expression='#%{target}'/>");

        DslAstService service = DslAstService.getInstance(getProject());
        List<DslAnalysisContext> contexts = service.getAnalysisContexts(child);

        assertEquals(1, contexts.size());
        assertEquals(2, contexts.get(0).getIncludeInstances(child.getVirtualFile().getPath()).size());
    }

    public void testChildDiagnosticsAreAggregatedAcrossIncludeInstances() {
        projectFile("script.xml", "<Lockscreen>"
                + "<Var name='present'/>"
                + "<Include name='function_shared.xml' target='present'/>"
                + "<Include name='function_shared.xml' target='missing'/>"
                + "</Lockscreen>");
        XmlFile child = projectFile("function_shared.xml",
                "<Var name='sub_%{target}' expression='#%{target}'/>");

        List<Diagnostic> diagnostics = DslAstService.getInstance(getProject())
                .getProjectedDiagnostics(child);
        Diagnostic unresolved = diagnostics.stream()
                .filter(diagnostic -> "SEM-REF-001".equals(diagnostic.getRuleId()))
                .findFirst()
                .orElse(null);

        assertNotNull("one include instance must retain the unresolved-reference error; actual=" + diagnostics,
                unresolved);
        assertTrue(unresolved.getMessage(), unresolved.getMessage().contains("1/2 include contexts"));
        assertEquals(child.getVirtualFile().getPath().replace('\\', '/'), unresolved.getFilePath());
    }

    public void testChildDiagnosticProjectsToItsOriginalSourceElement() {
        projectFile("script.xml", "<Lockscreen>"
                + "<Include name='function_location.xml'/>"
                + "</Lockscreen>");
        XmlFile child = projectFile("function_location.xml", "<Group>\n"
                + "  <Var name='first'/>\n"
                + "  <Var name='second' expression='#missing'/>\n"
                + "</Group>");

        Diagnostic unresolved = DslAstService.getInstance(getProject()).getProjectedDiagnostics(child).stream()
                .filter(diagnostic -> "SEM-REF-001".equals(diagnostic.getRuleId()))
                .findFirst()
                .orElse(null);

        assertNotNull(unresolved);
        assertEquals("the diagnostic must project to the second Var in the physical function file",
                3, unresolved.getLine());
        assertTrue(unresolved.getMessage().contains("1/1 include contexts"));
    }

    private XmlFile projectFile(String name, String content) {
        XmlFile[] result = new XmlFile[1];
        ApplicationManager.getApplication().runWriteAction((Runnable) () -> {
            try {
                VirtualFile sourceRoot = getSourceRoot();
                VirtualFile directory = sourceRoot.findChild("analysis-context");
                if (directory == null) {
                    directory = sourceRoot.createChildDirectory(this, "analysis-context");
                }
                VirtualFile file = directory.findChild(name);
                if (file == null) {
                    file = directory.createChildData(this, name);
                }
                VfsUtil.saveText(file, content);
                result[0] = (XmlFile) getPsiManager().findFile(file);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        assertNotNull(result[0]);
        return result[0];
    }
}
