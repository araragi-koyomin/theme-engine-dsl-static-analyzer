package com.huawei.theme.analysis.plugin.lsp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;

/**
 * Starts the per-project LSP server and wires document synchronization when
 * the project opens. Registered as a {@code postStartupActivity} in
 * {@code plugin.xml}.
 */
public final class DslLspStartupActivity implements StartupActivity {

    @Override
    public void runActivity(Project project) {
        DslLspServerService service = project.getService(DslLspServerService.class);
        service.start();
        DslLspDocumentSync sync = new DslLspDocumentSync(project, service);
        sync.start();
        Disposer.register(project, sync);
    }
}
