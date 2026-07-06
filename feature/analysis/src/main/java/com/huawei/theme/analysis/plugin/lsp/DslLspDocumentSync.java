package com.huawei.theme.analysis.plugin.lsp;

import java.util.List;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageServer;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
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

    private final Project project;
    private final DslLspServerService serverService;
    private final MessageBusConnection connection;

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
                // Only sync documents that belong to this project.
                if (PsiDocumentManager.getInstance(project).getPsiFile(doc) == null) {
                    return;
                }
                sendDidChange(file.getUrl(), doc.getText());
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

    @Override
    public void dispose() {
        connection.dispose();
    }
}
