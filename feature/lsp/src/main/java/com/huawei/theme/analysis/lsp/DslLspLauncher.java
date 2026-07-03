package com.huawei.theme.analysis.lsp;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * Process entry point for the DSL analyzer LSP server.
 *
 * <p>Communicates over stdio (the standard LSP transport) and is launched by
 * the client as {@code java -jar dsl-analyzer-lsp.jar --stdio [--rule-dir
 * <path>]}. The {@code --rule-dir} argument optionally overrides the built-in
 * rule resources.</p>
 */
public final class DslLspLauncher {

    public static void main(String[] args) throws Exception {
        String ruleDir = parseRuleDir(args);
        DslLanguageServer server = new DslLanguageServer(ruleDir);

        InputStream in = System.in;
        OutputStream out = System.out;

        var launcher = LSPLauncher.createServerLauncher(server, in, out);
        LanguageClient client = launcher.getRemoteProxy();
        server.connect(client);

        Future<?> listening = launcher.startListening();
        listening.get();
        // LSP4J's internal executor uses non-daemon threads; ensure the
        // process exits when the client disconnects (stdin EOF) or after the
        // LSP exit notification has been handled.
        System.exit(0);
    }

    private static String parseRuleDir(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--rule-dir".equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return null;
    }

    private DslLspLauncher() {
    }
}
