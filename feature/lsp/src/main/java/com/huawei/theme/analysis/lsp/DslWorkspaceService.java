package com.huawei.theme.analysis.lsp;

import java.util.function.Consumer;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

import com.huawei.theme.analysis.core.cli.InspectionConfig;

/**
 * Workspace service that forwards configuration changes to the language server.
 *
 * <p>{@code workspace/didChangeConfiguration} carries a settings object shaped
 * like an {@link InspectionConfig}; it is parsed and, if valid, handed to the
 * server's {@code updateConfig} callback for hot reload.</p>
 */
public final class DslWorkspaceService implements WorkspaceService {

    private final InspectionConfigParser parser;
    private final Consumer<InspectionConfig> onUpdate;

    DslWorkspaceService(InspectionConfigParser parser, Consumer<InspectionConfig> onUpdate) {
        this.parser = parser;
        this.onUpdate = onUpdate;
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        InspectionConfig config = parser.parse(params.getSettings());
        if (config != null) {
            onUpdate.accept(config);
        }
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        // external file changes are not tracked in this version
    }
}
