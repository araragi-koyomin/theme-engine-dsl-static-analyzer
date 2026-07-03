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
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import com.huawei.theme.analysis.core.fileidentification.DslFileIdentifier;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;

/**
 * Handles text document notifications (open/change/close) and language
 * feature requests (completion, hover).
 *
 * <p>Document synchronization is full-sync: the client sends the entire
 * document text on every change. Diagnostics are recomputed on a debounced
 * schedule (300 ms) to avoid re-parsing on every keystroke.</p>
 */
final class DslTextDocumentService implements TextDocumentService {

    private static final long DEBOUNCE_MS = 300;

    private final LanguageClient client;
    private final AnalysisService analysisService;
    private final DiagnosticPublisher diagnosticPublisher;
    private final CompletionProvider completionProvider;
    private final HoverProvider hoverProvider;
    private final DslFileIdentifier fileIdentifier;
    private final DslTextDocuments documents = new DslTextDocuments();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "dsl-lsp-analyzer");
                t.setDaemon(true);
                return t;
            });
    private final Map<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    DslTextDocumentService(LanguageClient client, RuleRepository ruleRepository) {
        this.client = client;
        this.analysisService = new AnalysisService(ruleRepository);
        this.diagnosticPublisher = new DiagnosticPublisher();
        this.completionProvider = new CompletionProvider(ruleRepository);
        this.hoverProvider = new HoverProvider(ruleRepository);
        this.fileIdentifier = new DslFileIdentifier(ruleRepository);
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
        ContextResolver.Context ctx = new ContextResolver(text).resolve(offset);
        List<CompletionItem> items = completionProvider.complete(ctx);
        return CompletableFuture.completedFuture(Either.forLeft(items));
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
        ContextResolver.Context ctx = new ContextResolver(text).resolve(offset);
        return CompletableFuture.completedFuture(hoverProvider.hover(ctx));
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
            lspDiags = List.of();
        } else {
            List<com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic> core =
                    analysisService.analyze(uri, text);
            PositionMapper mapper = new PositionMapper(text);
            lspDiags = diagnosticPublisher.toLspDiagnostics(core, mapper);
        }
        client.publishDiagnostics(new PublishDiagnosticsParams(uri, lspDiags));
    }

    void shutdown() {
        scheduler.shutdownNow();
    }
}
