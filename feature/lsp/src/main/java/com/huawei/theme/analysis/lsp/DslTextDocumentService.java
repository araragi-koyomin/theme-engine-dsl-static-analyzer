package com.huawei.theme.analysis.lsp;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import com.huawei.theme.analysis.core.fileidentification.DslFileIdentifier;
import com.huawei.theme.analysis.core.quickfix.QuickFixProvider;
import com.huawei.theme.analysis.core.quickfix.QuickFixProviderImpl;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

/**
 * Handles text document notifications (open/change/close) and language
 * feature requests (completion, hover).
 *
 * <p>Document synchronization is full-sync: the client sends the entire
 * document text on every change. Diagnostics are recomputed on a debounced
 * schedule (300 ms) to avoid re-parsing on every keystroke.</p>
 */
public final class DslTextDocumentService implements TextDocumentService {

    private static final long DEBOUNCE_MS = 300;

    private volatile LanguageClient client;
    private final DiagnosticPublisher diagnosticPublisher;
    // These depend on the rule repository and are rebuilt when the
    // configuration changes (updateRuleRepository). Volatile for visibility.
    private volatile AnalysisService analysisService;
    private volatile CompletionProvider completionProvider;
    private volatile DefinitionProvider definitionProvider;
    private volatile HoverProvider hoverProvider;
    private volatile DslFileIdentifier fileIdentifier;
    private volatile SemanticTokensProvider semanticTokensProvider;
    private volatile CodeActionProvider codeActionProvider;
    // Cached core diagnostics per uri, used to resolve codeAction requests.
    // Updated on every analyzeAndPublish and cleared on didClose.
    private final Map<String, List<Diagnostic>> cachedDiagnostics = new ConcurrentHashMap<>();
    private final DslTextDocuments documents = new DslTextDocuments();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "dsl-lsp-analyzer");
                t.setDaemon(true);
                return t;
            });
    private final Map<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    DslTextDocumentService(RuleRepository ruleRepository) {
        this.diagnosticPublisher = new DiagnosticPublisher();
        rebuildProviders(ruleRepository);
    }

    /**
     * Injects the remote client. {@code LSPLauncher.createServerLauncher}
     * scans {@code getTextDocumentService()} for RPC methods at launcher
     * creation time (before the client proxy exists), so the service must be
     * constructed without a client and wired here, after the launcher is
     * created but before {@code startListening}.
     */
    void setClient(LanguageClient client) {
        this.client = client;
    }

    /**
     * Rebuilds the rule-dependent providers with a new repository (e.g. after
     * a configuration change) and re-analyzes every open document so
     * diagnostics reflect the new rule set / severity overrides immediately.
     */
    synchronized void updateRuleRepository(RuleRepository ruleRepository) {
        rebuildProviders(ruleRepository);
        for (String uri : documents.openUris()) {
            analyzeAndPublish(uri);
        }
    }

    private void rebuildProviders(RuleRepository ruleRepository) {
        this.analysisService = new AnalysisService(ruleRepository);
        this.completionProvider = new CompletionProvider(ruleRepository);
        this.hoverProvider = new HoverProvider(ruleRepository);
        this.definitionProvider = new DefinitionProvider(ruleRepository);
        this.fileIdentifier = new DslFileIdentifier(ruleRepository);
        this.semanticTokensProvider = new SemanticTokensProvider(ruleRepository);
        QuickFixProvider quickFixProvider = new QuickFixProviderImpl();
        this.codeActionProvider = new CodeActionProvider(quickFixProvider);
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String text = params.getTextDocument().getText();
        documents.open(uri, text);
        analyzeAndPublish(uri);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        List<TextDocumentContentChangeEvent> changes = params.getContentChanges();
        if (changes == null || changes.isEmpty()) {
            return;
        }
        // Full sync: the whole document text is carried in the single change.
        documents.update(uri, changes.get(0).getText());
        scheduleAnalyze(uri);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        documents.close(uri);
        cachedDiagnostics.remove(uri);
        ScheduledFuture<?> f = pending.remove(uri);
        if (f != null) {
            f.cancel(false);
        }
        client.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
    }

    @Override
    public void didSave(org.eclipse.lsp4j.DidSaveTextDocumentParams params) {
        // full-sync server: diagnostics are driven by didChange, nothing to do here
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
            CompletionParams params) {
        String uri = params.getTextDocument().getUri();
        String text = documents.get(uri);
        if (text == null) {
            return CompletableFuture.completedFuture(Either.forLeft(List.of()));
        }
        int offset = new PositionMapper(text).toOffset(
                params.getPosition().getLine(),
                params.getPosition().getCharacter());
        // Collect element names and Var declarations present in the document
        // for completion sorting (present items sorted last) and variable
        // completion (offering #declaredVar inside expressions).
        java.util.Set<String> presentElementNames = new java.util.HashSet<>();
        java.util.Set<String> declaredVars = new java.util.HashSet<>();
        try {
            DslFileNode ast = analysisService.parse(uri, text);
            if (ast != null && ast.getRootElement() != null) {
                collectElementAndVarNames(ast.getRootElement(), presentElementNames, declaredVars);
            }
        } catch (RuntimeException ignored) {
            // AST parse failure — proceed with empty sets (fallback).
        }
        ContextResolver.Context ctx = resolveContext(uri, text, offset);
        List<CompletionItem> items = completionProvider.complete(ctx, presentElementNames, declaredVars);
        return CompletableFuture.completedFuture(Either.forLeft(items));
    }

    private void collectElementAndVarNames(DslElementNode element,
                                           java.util.Set<String> elementNames,
                                           java.util.Set<String> varNames) {
        if (element == null) {
            return;
        }
        String tag = element.getTagName();
        if (tag != null && !tag.isEmpty()) {
            elementNames.add(tag);
        }
        if ("Var".equals(tag) && element.getAttributes() != null) {
            for (DslAttributeNode attr : element.getAttributes()) {
                if ("name".equals(attr.getName()) && attr.getValue() != null
                        && attr.getValue().getRawValue() != null) {
                    varNames.add(attr.getValue().getRawValue());
                }
            }
        }
        if (element.getChildElements() != null) {
            for (DslElementNode child : element.getChildElements()) {
                collectElementAndVarNames(child, elementNames, varNames);
            }
        }
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        String uri = params.getTextDocument().getUri();
        String text = documents.get(uri);
        if (text == null) {
            return CompletableFuture.completedFuture(null);
        }
        int offset = new PositionMapper(text).toOffset(
                params.getPosition().getLine(),
                params.getPosition().getCharacter());
        ContextResolver.Context ctx = resolveContext(uri, text, offset);
        return CompletableFuture.completedFuture(hoverProvider.hover(ctx));
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
            definition(DefinitionParams params) {
        String uri = params.getTextDocument().getUri();
        String text = documents.get(uri);
        if (text == null) {
            return CompletableFuture.completedFuture(Either.forLeft(List.of()));
        }
        PositionMapper mapper = new PositionMapper(text);
        int offset = mapper.toOffset(
                params.getPosition().getLine(),
                params.getPosition().getCharacter());
        DslFileNode ast = analysisService.parse(uri, text);
        ContextResolver.Context ctx = new AstContextResolver(text).resolve(offset, ast);
        if (ctx == null) {
            ctx = new ContextResolver(text).resolve(offset);
        }
        List<Location> locations = definitionProvider.definition(ctx, ast, uri, mapper);
        return CompletableFuture.completedFuture(Either.forLeft(locations));
    }

    /**
     * Resolves the cursor context preferring AST-based precision and falling
     * back to the text-scan heuristic when the AST is unavailable or the
     * cursor is outside any start tag (XML declaration, closing tags, text
     * content, malformed XML).
     */
    private ContextResolver.Context resolveContext(String uri, String text, int offset) {
        DslFileNode ast = analysisService.parse(uri, text);
        ContextResolver.Context ctx = new AstContextResolver(text).resolve(offset, ast);
        if (ctx != null) {
            return ctx;
        }
        return new ContextResolver(text).resolve(offset);
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        String uri = params.getTextDocument().getUri();
        String text = documents.get(uri);
        if (text == null || semanticTokensProvider == null) {
            return CompletableFuture.completedFuture(new SemanticTokens(List.of()));
        }
        return CompletableFuture.completedFuture(
                new SemanticTokens(semanticTokensProvider.collect(uri, text)));
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        String uri = params.getTextDocument().getUri();
        String text = documents.get(uri);
        if (text == null || codeActionProvider == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<Diagnostic> core = cachedDiagnostics.getOrDefault(uri, List.of());
        PositionMapper mapper = new PositionMapper(text);
        List<Either<Command, CodeAction>> actions = codeActionProvider.build(
                uri, mapper, core, params.getRange(), diagnosticPublisher);
        return CompletableFuture.completedFuture(actions);
    }

    private void scheduleAnalyze(String uri) {
        ScheduledFuture<?> prev = pending.get(uri);
        if (prev != null) {
            prev.cancel(false);
        }
        ScheduledFuture<?> future = scheduler.schedule(
                () -> analyzeAndPublish(uri), DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        pending.put(uri, future);
    }

    private void analyzeAndPublish(String uri) {
        pending.remove(uri);
        String text = documents.get(uri);
        if (text == null) {
            return;
        }
        List<org.eclipse.lsp4j.Diagnostic> lspDiags;
        if (!fileIdentifier.isDslFile(uri, text)) {
            cachedDiagnostics.put(uri, List.of());
            lspDiags = List.of();
        } else {
            List<Diagnostic> core = analysisService.analyze(uri, text);
            cachedDiagnostics.put(uri, core);
            PositionMapper mapper = new PositionMapper(text);
            lspDiags = diagnosticPublisher.toLspDiagnostics(core, mapper);
        }
        client.publishDiagnostics(new PublishDiagnosticsParams(uri, lspDiags));
    }

    void shutdown() {
        scheduler.shutdownNow();
    }
}
