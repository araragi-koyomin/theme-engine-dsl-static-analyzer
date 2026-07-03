package com.huawei.theme.analysis.lsp;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.AnalyzerRegistry;

/**
 * The LSP language server entry point.
 *
 * <p>On construction it initializes the analyzer registry and loads the rule
 * repository. {@link #connect(LanguageClient)} wires the remote client (for
 * publishing diagnostics) and builds the text document service.</p>
 */
final class DslLanguageServer implements LanguageServer {

    private final RuleRepository ruleRepository;
    private final DslWorkspaceService workspaceService = new DslWorkspaceService();
    private volatile DslTextDocumentService textDocumentService;
    private volatile LanguageClient client;

    DslLanguageServer(String ruleDir) {
        // AnalyzerRegistry uses static registration; must be triggered once
        // before DiagnosticProviderImpl runs, otherwise diagnostics are empty.
        AnalyzerRegistry.init();
        this.ruleRepository = new RuleRepositoryFactory(ruleDir).create();
    }

    void connect(LanguageClient languageClient) {
        this.client = languageClient;
        this.textDocumentService = new DslTextDocumentService(client, ruleRepository);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ServerCapabilities caps = new ServerCapabilities();
        caps.setTextDocumentSync(Either.forLeft(TextDocumentSyncKind.Full));
        caps.setCompletionProvider(new CompletionOptions(false, List.of("<", " ", "=")));
        caps.setHoverProvider(Either.forLeft(true));
        return CompletableFuture.completedFuture(new InitializeResult(caps));
    }

    @Override
    public void initialized(InitializedParams params) {
        // no-op
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        DslTextDocumentService tds = textDocumentService;
        if (tds != null) {
            tds.shutdown();
        }
        return CompletableFuture.completedFuture(new Object());
    }

    @Override
    public void exit() {
        System.exit(0);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }
}
