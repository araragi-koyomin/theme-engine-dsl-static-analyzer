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
gradle :lsp:buildLspFatJar
```

Produces `lsp/build/lsp/dsl-analyzer-lsp.jar` — a self-contained jar with
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
                                   ├── WorkspaceService     (reserved for config)
                                   ├── AnalysisService      → core AstBuilder + DiagnosticProvider
                                   ├── CompletionProvider   → core RuleRepository
                                   ├── HoverProvider        → core RuleRepository
                                   └── RuleRepositoryFactory→ core JsonRuleLoader (built-in / --rule-dir)
```

The server reuses `com.huawei.theme.analysis.core.*` unchanged: `AstBuilder`
(JDK SAX) builds the AST and parses embedded expressions, the analyzer
registry produces `Diagnostic`s (1-based line / 0-based column), and
`PositionMapper` converts them to LSP positions.

## Roadmap

- core AST end positions → AST-based context resolution
- `textDocument/codeAction` (QuickFix) from `QuickFixProvider`
- configuration via `initializationOptions` / `workspace/configuration`
- `textDocument/semanticTokens` for expression highlighting
