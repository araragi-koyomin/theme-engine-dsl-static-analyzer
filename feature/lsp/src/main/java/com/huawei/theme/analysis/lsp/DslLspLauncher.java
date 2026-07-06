package com.huawei.theme.analysis.lsp;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.cli.InspectionConfigLoader;

/**
 * Process entry point for the DSL analyzer LSP server.
 *
 * <p>Communicates over stdio and is launched by the client as
 * {@code java -jar dsl-analyzer-lsp.jar --stdio [--rule-dir <path>] [--config <path>]}.
 * <ul>
 *   <li>{@code --rule-dir <path>} overrides the built-in rule resources;</li>
 *   <li>{@code --config <path>} loads an {@link InspectionConfig} JSON file
 *       (rule enable/disable, severity overrides, root element override)
 *       applied at startup. Runtime configuration via LSP
 *       {@code initializationOptions} / {@code didChangeConfiguration} is
 *       handled by the server itself.</li>
 * </ul></p>
 */
public final class DslLspLauncher {

    public static void main(String[] args) throws Exception {
        String ruleDir = parseArg(args, "--rule-dir");
        String configPath = parseArg(args, "--config");
        InspectionConfig cliConfig = loadCliConfig(configPath);

        DslLanguageServer server = new DslLanguageServer(ruleDir, cliConfig);

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

    private static InspectionConfig loadCliConfig(String configPath) {
        if (configPath == null || configPath.isEmpty()) {
            return null;
        }
        try {
            return new InspectionConfigLoader().load(configPath);
        } catch (RuntimeException e) {
            System.err.println("Failed to load config " + configPath + ": " + e.getMessage());
            return null;
        }
    }

    private static String parseArg(String[] args, String name) {
        for (int i = 0; i < args.length; i++) {
            if (name.equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return null;
    }

    private DslLspLauncher() {
    }
}
