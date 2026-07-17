package com.huawei.theme.analysis.lsp;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import com.huawei.theme.analysis.core.cli.ConfigAwareRuleRepository;
import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.AnalyzerRegistry;

/**
 * The LSP language server entry point.
 *
 * <p>On construction it initializes the analyzer registry and loads the
 * delegate rule repository. {@link #connect(LanguageClient)} wires the remote
 * client and builds the text document service. Configuration (rule enable/disable,
 * severity overrides, root element override) is applied in three ways:
 * <ul>
 *   <li>CLI {@code --config <path>} file — applied at construction;</li>
 *   <li>LSP {@code initializationOptions} — applied during {@code initialize};</li>
 *   <li>{@code workspace/didChangeConfiguration} — applied at runtime via
 *       {@link #updateConfig(InspectionConfig)}, which re-wraps the delegate
 *       and re-analyzes all open documents.</li>
 * </ul>
 * The config wraps the immutable delegate in a {@link ConfigAwareRuleRepository};
 * the delegate itself is never rebuilt, so rule loading cost is paid once.
 */
public final class DslLanguageServer implements LanguageServer {

    private final RuleRepository delegate;
    private final InspectionConfigParser configParser = new InspectionConfigParser();
    private final DslWorkspaceService workspaceService;
    private volatile InspectionConfig currentConfig;
    private volatile RuleRepository currentRepo;
    private volatile DslTextDocumentService textDocumentService;
    private volatile LanguageClient client;

    DslLanguageServer(String ruleDir, InspectionConfig cliConfig) {
        // AnalyzerRegistry uses static registration; must be triggered once
        // before DiagnosticProviderImpl runs, otherwise diagnostics are empty.
        AnalyzerRegistry.init();
        this.delegate = new RuleRepositoryFactory(ruleDir).create();
        this.currentConfig = cliConfig;
        this.currentRepo = wrap(delegate, cliConfig);
        this.textDocumentService = new DslTextDocumentService(currentRepo);
        this.workspaceService = new DslWorkspaceService(configParser, this::updateConfig);
    }

    private static RuleRepository wrap(RuleRepository delegateRepo, InspectionConfig config) {
        return config == null ? delegateRepo : new ConfigAwareRuleRepository(delegateRepo, config);
    }

    void connect(LanguageClient languageClient) {
        this.client = languageClient;
        this.textDocumentService.setClient(languageClient);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        InspectionConfig optionsConfig = configParser.parse(params.getInitializationOptions());
        if (optionsConfig != null) {
            updateConfig(optionsConfig);
        }
        ServerCapabilities caps = new ServerCapabilities();
        caps.setTextDocumentSync(Either.forLeft(TextDocumentSyncKind.Full));
        caps.setCompletionProvider(new CompletionOptions(false, List.of("<", " ", "=", "\"")));
        caps.setHoverProvider(Either.forLeft(true));
        caps.setCodeActionProvider(Either.forLeft(true));
        caps.setDefinitionProvider(Either.forLeft(true));
        SemanticTokensLegend legend = new SemanticTokensLegend(
                SemanticTokensProvider.TOKEN_TYPES, SemanticTokensProvider.TOKEN_MODIFIERS);
        SemanticTokensWithRegistrationOptions stOptions = new SemanticTokensWithRegistrationOptions(legend);
        stOptions.setFull(true);
        caps.setSemanticTokensProvider(stOptions);
        return CompletableFuture.completedFuture(new InitializeResult(caps));
    }

    /**
     * Applies a new inspection config at runtime (hot reload): re-wraps the
     * delegate rule repository and triggers re-analysis of every open
     * document so diagnostics reflect the new rule set / severity overrides.
     */
    synchronized void updateConfig(InspectionConfig config) {
        this.currentConfig = config;
        this.currentRepo = wrap(delegate, config);
        DslTextDocumentService tds = textDocumentService;
        if (tds != null) {
            tds.updateRuleRepository(currentRepo);
        }
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
    @org.eclipse.lsp4j.jsonrpc.services.JsonDelegate
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    @org.eclipse.lsp4j.jsonrpc.services.JsonDelegate
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }
}
