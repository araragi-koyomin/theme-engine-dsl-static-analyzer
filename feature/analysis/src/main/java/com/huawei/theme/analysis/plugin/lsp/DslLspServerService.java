package com.huawei.theme.analysis.plugin.lsp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.ide.plugins.PluginManagerCore;

/**
 * Project-level service that owns the LSP server process and the LSP4J client
 * launcher. Started by {@link DslLspStartupActivity} when the project opens;
 * disposed (process killed) when the project closes.
 *
 * <p>The server is launched as {@code java -jar <plugin>/lib/dsl-analyzer-lsp.jar
 * --stdio} (the fat jar produced by {@code :feature:lsp:buildLspFatJar} and
 * shipped inside the plugin). LSP4J wires the process stdin/stdout to a
 * {@link LanguageServer} proxy obtained via {@code getRemoteProxy()}.</p>
 */
public final class DslLspServerService implements Disposable {

    private static final Logger LOG = Logger.getLogger(DslLspServerService.class.getName());
    private static final String PLUGIN_ID = "com.huawei.theme.analysis";
    private static final String SERVER_JAR = "dsl-analyzer-lsp.jar";

    private final Project project;
    private final DslLspLanguageClient client;
    private volatile Process process;
    private volatile LanguageServer serverProxy;
    private volatile Future<?> listening;

    public DslLspServerService(Project project) {
        this.project = project;
        this.client = new DslLspLanguageClient(project);
    }

    public LanguageServer getServerProxy() {
        return serverProxy;
    }

    public DslLspLanguageClient getClient() {
        return client;
    }

    public synchronized void start() {
        if (serverProxy != null) {
            return;
        }
        try {
            Path jarPath = resolveServerJar();
            ProcessBuilder pb = new ProcessBuilder("java", "-jar",
                    jarPath.toString(), "--stdio");
            pb.redirectErrorStream(false);
            process = pb.start();
            startErrorPump(process.getErrorStream());
            var launcher = LSPLauncher.createClientLauncher(
                    client, process.getInputStream(), process.getOutputStream());
            serverProxy = launcher.getRemoteProxy();
            listening = launcher.startListening();
            initialize();
            LOG.info("DSL LSP server started: " + jarPath);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to start DSL LSP server", e);
        }
    }

    private void initialize() {
        InitializeParams params = new InitializeParams();
        params.setProcessId((int) ProcessHandle.current().pid());
        try {
            InitializeResult result = serverProxy.initialize(params).join();
            // Capabilities are not inspected — the client only uses
            // completion/hover/publishDiagnostics, all of which the server
            // declares in its InitializeResult.
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LSP initialize failed", e);
            return;
        }
        serverProxy.initialized(new InitializedParams());
    }

    private void startErrorPump(InputStream err) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(err))) {
                String line;
                while ((line = r.readLine()) != null) {
                    LOG.warning("LSP server stderr: " + line);
                }
            } catch (IOException ignored) {
            }
        }, "dsl-lsp-server-stderr");
        t.setDaemon(true);
        t.start();
    }

    private static Path resolveServerJar() {
        IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
        if (descriptor == null) {
            throw new IllegalStateException("Plugin " + PLUGIN_ID + " not found");
        }
        Path pluginDir = descriptor.getPath().toPath();
        Path inLib = pluginDir.resolve("lib").resolve(SERVER_JAR);
        if (Files.isRegularFile(inLib)) {
            return inLib;
        }
        Path atRoot = pluginDir.resolve(SERVER_JAR);
        if (Files.isRegularFile(atRoot)) {
            return atRoot;
        }
        throw new IllegalStateException("Server jar not found under " + pluginDir);
    }

    @Override
    public synchronized void dispose() {
        try {
            if (serverProxy != null) {
                try {
                    serverProxy.shutdown().join();
                } catch (Exception ignored) {
                }
                try {
                    serverProxy.exit();
                } catch (Exception ignored) {
                }
            }
        } finally {
            if (listening != null) {
                listening.cancel(true);
            }
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            serverProxy = null;
        }
    }
}
