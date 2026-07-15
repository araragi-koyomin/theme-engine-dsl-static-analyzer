# DSL Analyzer LSP Server

Standalone Language Server for Huawei Theme Engine DSL, built on the same
`core` analysis engine as the IntelliJ plugin. Communicates over stdio and
works with any LSP-capable editor.

## Features (first version)

- **Diagnostics** (`textDocument/publishDiagnostics`) — syntax + semantic
  checks from the rule library, including embedded expression validation.
- **Completion** (`textDocument/completion`) — element tag names and
  canonical attribute names (required attributes sorted first).
- **Hover** (`textDocument/hover`) — tag signature, required/optional
  attributes and allowed parents from the rule library.

## Build

```bash
gradle :feature:lsp:buildLspFatJar
```

Produces `feature/lsp/build/lsp/dsl-analyzer-lsp.jar` — a self-contained jar with
the server, the `core` classes, the built-in `rules/` and `functions/`
resources, gson, antlr4-runtime and lsp4j. No IntelliJ SDK is bundled, so it
runs on a plain JRE 17+.

## Run

```bash
java -jar dsl-analyzer-lsp.jar --stdio
```

Options:

- `--stdio` — use stdio as the LSP transport (standard).
- `--rule-dir <path>` — load rules from an external directory instead of the
  built-in resources. Useful for iterating on rules without rebuilding.
- `--config <path>` — load an inspection config JSON file (see
  [Configuration](#configuration)) applied at startup. Runtime configuration
  via LSP `initializationOptions` / `didChangeConfiguration` is also supported.

## Configuration

The server accepts an inspection config that customizes rule behavior without
rebuilding: enable/disable rule ids, override diagnostic severities, and
override the recognized root element names. The same JSON shape is used for
the `--config` file, LSP `initializationOptions`, and
`workspace/didChangeConfiguration` settings.

```json
{
  "rootElementNames": ["Lockscreen", "Widget"],
  "enabledRuleIds": ["SEM-TYPE-001"],
  "disabledRuleIds": ["SYN-003"],
  "severityOverrides": { "SEM-REQ-001": "error" }
}
```

- `enabledRuleIds` / `disabledRuleIds` — mutually exclusive. When `enabled`
  is non-empty, only those rules fire; when `disabled` is non-empty, those
  rules are suppressed.
- `severityOverrides` — maps a rule id to `"error"` / `"warning"` / `"info"`.
- `rootElementNames` — overrides the set of tags treated as DSL roots
  (affects file identification).

Runtime updates: a `workspace/didChangeConfiguration` notification carrying
the same shape triggers a hot reload — the server re-wraps the rule
repository and re-analyzes every open document, so severity/disabled changes
take effect immediately without restarting.

### VS Code

```json
{
  "dsl-analyzer.config": {
    "disabledRuleIds": ["SYN-003"],
    "severityOverrides": { "SEM-REQ-001": "warning" }
  }
}
```
The client extension forwards `dsl-analyzer.config` as `initializationOptions`
on `initialize` and as settings on `didChangeConfiguration`.

### Neovim

```lua
lspconfig.dsl_analyzer.setup({
  settings = {
    disabledRuleIds = { "SYN-003" },
    severityOverrides = { ["SEM-REQ-001"] = "warning" },
  },
})
```

## Document conventions

The server treats a file as DSL when **both** hold:

- the path ends with `.xml`, and
- the root tag is a known DSL root element (e.g. `Lockscreen`, `Widget`,
  `Wallpaper`, `LongTake`, `ChargingSkin`).

Editor clients should register the language for files named `script.xml` or
matching `script_*.xml`, matching the IntelliJ plugin's file-type binding.

## Editor integration

### VS Code

Install the `vscode-languageclient` based extension, or configure a server
in `settings.json`:

```json
{
  "dsl-analyzer-lsp.server.path": "/absolute/path/to/dsl-analyzer-lsp.jar",
  "dsl-analyzer-lsp.server.javaPath": "java"
}
```

A minimal client extension launches `java -jar <jar> --stdio` and connects
to this server. Document selectors should target `script.xml` /
`script_*.xml` with the `xml` language id.

### Neovim (nvim-lspconfig)

```lua
local lspconfig = require('lspconfig')
lspconfig.dsl_analyzer = {
  default_config = {
    cmd = { 'java', '-jar', '/path/to/dsl-analyzer-lsp.jar', '--stdio' },
    filetypes = { 'xml' },
    root_pattern = { 'script.xml', 'script_.*%.xml' },
    settings = {},
  },
}
lspconfig.dsl_analyzer.setup({})
```

### coc.nvim

In `coc-settings.json`:

```json
{
  "languageserver": {
    "dsl-analyzer": {
      "command": "java",
      "args": ["-jar", "/path/to/dsl-analyzer-lsp.jar", "--stdio"],
      "filetypes": ["xml"],
      "filenamePatterns": ["script.xml", "script_*.xml"]
    }
  }
}
```

### Helix

In `~/.config/helix/languages.toml`:

```toml
[[language]]
name = "xml"
language-servers = ["dsl-analyzer"]

[language-server.dsl-analyzer]
command = "java"
args = ["-jar", "/path/to/dsl-analyzer-lsp.jar", "--stdio"]
```

## Architecture

```
LSP client (editor)  ──stdio/JSON-RPC──>  DslLspLauncher
                                              │
                                   DslLanguageServer
                                   ├── TextDocumentService  (didOpen/Change/Close, completion, hover)
                                    ├── WorkspaceService     (applies config, hot reload)
                                   ├── AnalysisService      → core AstBuilder + DiagnosticProvider
                                   ├── CompletionProvider   → core RuleRepository
                                   ├── HoverProvider        → core RuleRepository
                                   └── RuleRepositoryFactory→ core JsonRuleLoader (built-in / --rule-dir)
```

The server reuses `com.huawei.theme.analysis.core.*` unchanged: `AstBuilder`
(StAX) builds the AST and parses embedded expressions, the analyzer
registry produces `Diagnostic`s (1-based line / 0-based column), and
`PositionMapper` converts them to LSP positions.

## Roadmap

- AST-based context resolution (replace text-heuristic `ContextResolver`)
- `textDocument/codeAction` (QuickFix) from `QuickFixProvider`
- `textDocument/semanticTokens` for expression highlighting
