package com.huawei.theme.analysis.plugin.lsp;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageServer;

import java.awt.Font;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.JBColor;
import com.intellij.util.messages.MessageBusConnection;

/**
 * Bridges IntelliJ document lifecycle events to LSP text document
 * notifications (full-sync: the whole document text is sent on every change).
 *
 * <p>File open/close → didOpen/didClose; document edits → didChange. Only
 * files matching the DSL file pattern (script.xml / script_*.xml) are synced.
 * The server-side debounce absorbs bursts; here we forward each change as-is
 * to keep the client simple.</p>
 */
final class DslLspDocumentSync implements Disposable {

    private static final Logger LOG = Logger.getLogger(DslLspDocumentSync.class.getName());

    private final Project project;
    private final DslLspServerService serverService;
    private final MessageBusConnection connection;
    private final ScheduledExecutorService debounceScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "dsl-lsp-debounce");
                t.setDaemon(true);
                return t;
            });
    private final Map<String, ScheduledFuture<?>> pendingSync = new ConcurrentHashMap<>();

    DslLspDocumentSync(Project project, DslLspServerService serverService) {
        this.project = project;
        this.serverService = serverService;
        this.connection = project.getMessageBus().connect();
    }

    void start() {
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
                new FileEditorManagerListener() {
                    @Override
                    public void fileOpened(FileEditorManager source, VirtualFile file) {
                        onFileOpened(file);
                    }

                    @Override
                    public void fileClosed(FileEditorManager source, VirtualFile file) {
                        if (!isDsl(file)) {
                            return;
                        }
                        sendDidClose(file.getUrl());
                    }
                });

        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(DocumentEvent event) {
                Document doc = event.getDocument();
                VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
                if (file == null || !isDsl(file)) {
                    return;
                }
                if (PsiDocumentManager.getInstance(project).getPsiFile(doc) == null) {
                    return;
                }
                // Debounce: cancel any pending sync, schedule a new one after 300ms.
                // This avoids sending didChange + semanticTokensFull on every keystroke,
                // which would flood the server and block the EDT with markup updates.
                final String uri = file.getUrl();
                final String text = doc.getText();
                scheduleSync(uri, text, 300);
            }
        }, connection);

        // Replay didOpen for files already open before this listener was
        // registered (e.g. files restored when the project opened) — otherwise
        // the server has no document text and hover/completion return null
        // until the user edits the file.
        for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
            onFileOpened(file);
        }
    }

    private void onFileOpened(VirtualFile file) {
        if (!isDsl(file)) {
            return;
        }
        Document doc = FileDocumentManager.getInstance().getDocument(file);
        if (doc == null) {
            return;
        }
        sendDidOpen(file.getUrl(), doc.getText());
    }

    private static boolean isDsl(VirtualFile file) {
        if (file == null) {
            return false;
        }
        String name = file.getName();
        boolean xml = "xml".equalsIgnoreCase(file.getExtension());
        boolean nameMatch = "script.xml".equals(name) || name.startsWith("script_");
        return xml && nameMatch;
    }

    private void sendDidOpen(String uri, String text) {
        LanguageServer s = serverService.getServerProxy();
        if (s == null) {
            return;
        }
        DidOpenTextDocumentParams p = new DidOpenTextDocumentParams();
        p.setTextDocument(new TextDocumentItem(uri, "xml", 1, text));
        s.getTextDocumentService().didOpen(p);
        scheduleSync(uri, text, 100);
    }

    private void sendDidChange(String uri, String text) {
        LanguageServer s = serverService.getServerProxy();
        if (s == null) {
            return;
        }
        DidChangeTextDocumentParams p = new DidChangeTextDocumentParams();
        p.setTextDocument(new VersionedTextDocumentIdentifier(uri, (int) System.currentTimeMillis()));
        p.setContentChanges(List.of(new TextDocumentContentChangeEvent(text)));
        s.getTextDocumentService().didChange(p);
    }

    private void sendDidClose(String uri) {
        LanguageServer s = serverService.getServerProxy();
        if (s == null) {
            return;
        }
        DidCloseTextDocumentParams p = new DidCloseTextDocumentParams();
        p.setTextDocument(new TextDocumentIdentifier(uri));
        s.getTextDocumentService().didClose(p);
    }

    private void scheduleSync(String uri, String text, long delayMs) {
        ScheduledFuture<?> prev = pendingSync.get(uri);
        if (prev != null) {
            prev.cancel(false);
        }
        ScheduledFuture<?> future = debounceScheduler.schedule(() -> {
            pendingSync.remove(uri);
            sendDidChange(uri, text);
            requestSemanticTokens(uri);
        }, delayMs, TimeUnit.MILLISECONDS);
        pendingSync.put(uri, future);
    }

    private void requestSemanticTokens(String uri) {
        LanguageServer s = serverService.getServerProxy();
        if (s == null) {
            return;
        }
        SemanticTokensParams params = new SemanticTokensParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        s.getTextDocumentService().semanticTokensFull(params).thenAccept(tokens -> {
            DslLspLanguageClient client = serverService.getClient();
            if (client == null || tokens == null || tokens.getData() == null) {
                return;
            }
            client.setSemanticTokens(uri, tokens.getData());
            ApplicationManager.getApplication().invokeLater(() -> {
                if (project.isDisposed()) {
                    return;
                }
                applySemanticTokensToEditor(uri, tokens.getData());
            });
        }).exceptionally(e -> {
            LOG.warning("requestSemanticTokens failed: " + e.getMessage());
            return null;
        });
    }

    private static final int SEMANTIC_LAYER = 7000;
    private static final TextAttributes VARIABLE_ATTR =
            new TextAttributes(JBColor.BLUE, null, null, null, Font.PLAIN);
    private static final TextAttributes FUNCTION_ATTR =
            new TextAttributes(JBColor.YELLOW, null, null, null, Font.ITALIC);
    private static final TextAttributes NUMBER_ATTR =
            new TextAttributes(JBColor.CYAN, null, null, null, Font.BOLD);
    private static final TextAttributes STRING_ATTR =
            new TextAttributes(JBColor.GREEN, null, null, null, Font.PLAIN);

    private void applySemanticTokensToEditor(String uri, List<Integer> data) {
        VirtualFile vf = VirtualFileManager.getInstance().findFileByUrl(uri);
        if (vf == null) {
            return;
        }
        Document doc = FileDocumentManager.getInstance().getDocument(vf);
        if (doc == null) {
            return;
        }
        MarkupModel mm = DocumentMarkupModel.forDocument(doc, project, true);
        for (RangeHighlighter h : mm.getAllHighlighters()) {
            if (h.getLayer() == SEMANTIC_LAYER) {
                mm.removeHighlighter(h);
            }
        }
        int line = 0;
        int col = 0;
        for (int i = 0; i + 4 < data.size(); i += 5) {
            int deltaLine = data.get(i);
            int deltaStart = data.get(i + 1);
            int length = data.get(i + 2);
            int type = data.get(i + 3);
            line += deltaLine;
            col = (deltaLine == 0) ? col + deltaStart : deltaStart;
            if (line < 0 || line >= doc.getLineCount()) {
                continue;
            }
            int start = doc.getLineStartOffset(line) + col;
            int end = Math.min(start + length, doc.getTextLength());
            if (start >= end) {
                continue;
            }
            TextAttributes attrs = attrsForType(type);
            if (attrs == null) {
                continue;
            }
            mm.addRangeHighlighter(start, end, SEMANTIC_LAYER, attrs, HighlighterTargetArea.EXACT_RANGE);
        }
    }

    private static TextAttributes attrsForType(int type) {
        switch (type) {
            case 0:
                return VARIABLE_ATTR;
            case 1:
                return FUNCTION_ATTR;
            case 2:
                return NUMBER_ATTR;
            case 3:
                return STRING_ATTR;
            default:
                return null;
        }
    }

    @Override
    public void dispose() {
        connection.dispose();
        debounceScheduler.shutdownNow();
    }
}
