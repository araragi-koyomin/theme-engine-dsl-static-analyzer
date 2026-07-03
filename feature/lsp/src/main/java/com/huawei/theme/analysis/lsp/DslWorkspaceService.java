package com.huawei.theme.analysis.lsp;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * Minimal workspace service. Configuration-driven rule customization is a
 * later iteration; for now both notifications are no-ops.
 */
final class DslWorkspaceService implements WorkspaceService {

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        // reserved for future InspectionConfig / rule overrides
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        // external file changes are not tracked in the first version
    }
}
