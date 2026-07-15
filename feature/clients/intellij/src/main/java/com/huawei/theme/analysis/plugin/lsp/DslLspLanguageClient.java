package com.huawei.theme.analysis.plugin.lsp;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

/**
 * LSP client implementation: receives server notifications and stores the
 * latest diagnostic snapshot per document URI. On each
 * {@code publishDiagnostics} the daemon is restarted so the IntelliJ
 * {@link com.intellij.lang.annotation.Annotator} (see
 * {@link ThemeDslLspAnnotator}) re-reads the cache and renders the diagnostics.
 */
public final class DslLspLanguageClient implements LanguageClient {

    private static final Logger LOG = Logger.getLogger(DslLspLanguageClient.class.getName());

    private final Project project;
    private final Map<String, List<Diagnostic>> diagnosticsByUri = new ConcurrentHashMap<>();
    private final Map<String, List<Integer>> semanticTokensByUri = new ConcurrentHashMap<>();

    DslLspLanguageClient(Project project) {
        this.project = project;
    }

    /**
     * Returns the latest diagnostics pushed by the server for the given URI
     * (empty list if none).
     */
    List<Diagnostic> getDiagnostics(String uri) {
        return diagnosticsByUri.getOrDefault(uri, List.of());
    }

    List<Integer> getSemanticTokens(String uri) {
        return semanticTokensByUri.getOrDefault(uri, List.of());
    }

    void setSemanticTokens(String uri, List<Integer> data) {
        semanticTokensByUri.put(uri, data);
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams params) {
        String uri = params.getUri();
        diagnosticsByUri.put(uri,
                params.getDiagnostics() == null ? List.of() : params.getDiagnostics());
        // Re-highlighting must run on the UI thread.
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            DaemonCodeAnalyzer.getInstance(project).restart();
        });
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        // reserved for a future NotificationGroup; intentionally minimal
    }

    @Override
    public void logMessage(MessageParams messageParams) {
        LOG.info("LSP server: " + messageParams.getMessage());
    }

    @Override
    public java.util.concurrent.CompletableFuture<org.eclipse.lsp4j.MessageActionItem> showMessageRequest(
            org.eclipse.lsp4j.ShowMessageRequestParams params) {
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    @Override
    public void telemetryEvent(Object o) {
        // not used
    }
}
