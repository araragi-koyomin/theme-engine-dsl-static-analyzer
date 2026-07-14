import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    DidChangeConfigurationNotification,
} from 'vscode-languageclient/node';

let client: LanguageClient | undefined;

function dslConfig(): unknown {
    return vscode.workspace.getConfiguration('dsl-analyzer').get('config') ?? {};
}

/**
 * Resolves the server jar path: the `dsl-analyzer-lsp.server.path` setting if
 * set, otherwise the jar bundled inside the extension at `server/dsl-analyzer-lsp.jar`
 * (produced by `gradle :feature:lsp:buildVscodeExtension`). Returns undefined
 * when neither is present, in which case the extension reports a warning and
 * stays inactive.
 */
function resolveServerJar(context: vscode.ExtensionContext): string | undefined {
    const cfg = vscode.workspace.getConfiguration('dsl-analyzer-lsp');
    const configured = cfg.get<string>('server.path');
    if (configured && configured.trim().length > 0) {
        return configured;
    }
    const bundled = path.join(context.extensionPath, 'server', 'dsl-analyzer-lsp.jar');
    return fs.existsSync(bundled) ? bundled : undefined;
}

export function activate(context: vscode.ExtensionContext) {
    const cfg = vscode.workspace.getConfiguration('dsl-analyzer-lsp');
    const javaPath = cfg.get<string>('server.javaPath') || 'java';
    const jarPath = resolveServerJar(context);
    if (!jarPath) {
        vscode.window
            .showWarningMessage(
                'dsl-analyzer-lsp.server.path is not set and no bundled server jar was found; DSL analyzer disabled. Build it via `gradle :feature:lsp:buildVscodeExtension` or set the setting.',
                'Open Settings',
            )
            .then((action) => {
                if (action === 'Open Settings') {
                    vscode.commands.executeCommand(
                        'workbench.action.openSettings',
                        'dsl-analyzer-lsp.server.path',
                    );
                }
            });
        return;
    }

    const serverOptions: ServerOptions = {
        run: { command: javaPath, args: ['-jar', jarPath, '--stdio'] },
        debug: { command: javaPath, args: ['-jar', jarPath, '--stdio'] },
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [
            { scheme: 'file', language: 'xml', pattern: '**/script.xml' },
            { scheme: 'file', language: 'xml', pattern: '**/script_*.xml' },
        ],
        initializationOptions: dslConfig(),
    };

    const languageClient = new LanguageClient(
        'dsl-analyzer',
        'DSL Analyzer',
        serverOptions,
        clientOptions,
    );
    client = languageClient;

    // Hot-reload: push the bare InspectionConfig as workspace/didChangeConfiguration.
    // The server (DslWorkspaceService) parses params.settings as InspectionConfig.
    context.subscriptions.push(
        vscode.workspace.onDidChangeConfiguration((e) => {
            if (e.affectsConfiguration('dsl-analyzer.config') && client) {
                client.sendNotification(DidChangeConfigurationNotification.type, {
                    settings: dslConfig(),
                });
            }
        }),
    );

    context.subscriptions.push({ dispose: () => { void languageClient.stop(); } });

    languageClient.start().catch((err) => {
        vscode.window.showErrorMessage(`DSL analyzer failed to start: ${err}`);
    });
}

export function deactivate(): Thenable<void> | undefined {
    return client?.stop();
}
