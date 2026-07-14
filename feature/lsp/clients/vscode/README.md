# DSL Analyzer — VS Code 客户端

> 模块路径：`feature/lsp/clients/vscode`
> 对应 server 模块：`:feature:lsp`（fat jar `dsl-analyzer-lsp.jar`）

本文档说明如何构建、安装、配置 VS Code 扩展，使其通过 LSP 接入 Theme Engine DSL 静态分析 server。扩展基于 [`vscode-languageclient`](https://code.visualstudio.com/api/language-extensions/language-server-extension-guide) 实现，启动 `java -jar dsl-analyzer-lsp.jar --stdio` 并把诊断/补全/hover/codeAction/语义高亮桥接到 VS Code。

---

## 1. 前置要求

- **Node.js ≥ 18** 与 **npm**（用于编译/打包扩展；构建 host 需在 PATH 中可执行 `npm`）。
- **JDK 17+**（运行 server jar；扩展默认用 `java`，可在设置中覆盖）。
- **Gradle 8.2**（项目构建，见根 `AGENTS.md`）。

---

## 2. 一键构建（推荐）

构建会：① 编译 LSP server fat jar；② 把 jar 复制到扩展的 `server/` 目录；③ `npm ci` 安装依赖；④ `tsc` + `esbuild` + `vsce package` 产出 `.vsix`。

```bash
gradle :feature:lsp:buildVscodeExtension
```

产物：

```
feature/lsp/clients/vscode/dsl-analyzer-lsp-<version>.vsix
```

`.vsix` 自包含 server jar（`server/dsl-analyzer-lsp.jar`），安装后无需再配置 `server.path` 即可使用。

> 该任务**不**接入默认 `build`（避免每次构建都要求 Node/npm）。需要时显式执行。

### 仅编译（不打包 .vsix）

开发期增量编译 + watch：

```bash
cd feature/lsp/clients/vscode
npm install
npm run compile     # 一次性：tsc 类型检查 + esbuild 打包到 out/extension.js
npm run watch       # esbuild watch
```

按 F5 在 `clients/vscode` 目录打开的 VS Code 实例里调试（需 `.vscode/launch.json`，可按 vscode-languageclient 文档自行添加）。

---

## 3. 安装 .vsix

**命令行安装：**

```bash
code --install-extension feature/lsp/clients/vscode/dsl-analyzer-lsp-<version>.vsix
```

**GUI 安装：** VS Code → 扩展面板 → ⋯ → "从 VSIX 安装…" → 选择 `.vsix`。

安装后 reload 窗口（`Ctrl+Shift+P` → `Developer: Reload Window`）。

---

## 4. 配置

打开 `settings.json`（`Ctrl+Shift+P` → `Preferences: Open Settings (JSON)`）。

### 4.1 server 路径

`.vsix` 已内置 server jar，**默认无需配置**。如需指向自行构建/版本的 jar，覆盖：

```json
{
  "dsl-analyzer-lsp.server.path": "C:/path/to/dsl-analyzer-lsp.jar",
  "dsl-analyzer-lsp.server.javaPath": "java"
}
```

| 设置 | 默认 | 说明 |
|---|---|---|
| `dsl-analyzer-lsp.server.path` | `""` | server jar 绝对路径。留空时回退到扩展内置 `server/dsl-analyzer-lsp.jar`；都不存在则扩展不激活并告警。 |
| `dsl-analyzer-lsp.server.javaPath` | `"java"` | 启动 server 用的 Java 17+ 可执行文件。 |

### 4.2 文件匹配

扩展只为 DSL 文件激活（与 IntelliJ 插件文件类型一致）：

- `**/script.xml`
- `**/script_*.xml`

且 `language` 为 `xml`（VS Code 内置 XML TextMate 语法提供结构高亮；server 再叠加 `textDocument/semanticTokens` 高亮嵌入表达式，见 `feature/lsp/docs/IMPLEMENTATION.md`）。

### 4.3 检查配置（InspectionConfig）

`dsl-analyzer.config` 对象在 `initialize` 时作为 `initializationOptions` 传给 server，并在 `workspace/didChangeConfiguration` 时热重载（server 立即重包装规则库并重分析所有打开的文档）。

```json
{
  "dsl-analyzer.config": {
    "rootElementNames": ["Lockscreen", "Widget"],
    "enabledRuleIds": ["SEM-TYPE-001"],
    "disabledRuleIds": ["SYN-003"],
    "severityOverrides": { "SEM-REQ-001": "warning" }
  }
}
```

| 字段 | 说明 |
|---|---|
| `rootElementNames` | 覆盖被视为 DSL 根元素的标签集合（影响文件识别）。 |
| `enabledRuleIds` | 非空时仅这些规则生效。与 `disabledRuleIds` 互斥。 |
| `disabledRuleIds` | 抑制这些规则。 |
| `severityOverrides` | `ruleId -> "error" \| "warning" \| "info"`，覆盖规则默认严重级。 |

修改后保存 `settings.json` 即触发热重载，无需重启。

> 也可用 `--config <path>` 文件方式启动 server（CLI 场景），JSON 形状相同，见 `feature/lsp/README.md`。

---

## 5. 提供的语言特性

| LSP 方法 | VS Code 表现 |
|---|---|
| `textDocument/publishDiagnostics` | "问题"面板与编辑器内波浪线。 |
| `textDocument/completion` | 补全列表：元素名（`Class` 图标，detail=category）、属性名（`Field`/`Property`，选中插入 `attr=""` 并把光标放引号内）、enum 属性值（`EnumMember`）。补全项携带 `documentation`（markdown），选中时右侧文档面板显示。 |
| `textDocument/hover` | hover 元素名/属性名/属性值时显示 markdown 文档（category / required / optional / allowed parents / inherits，或属性 type / default / enum / aliases / expression）。 |
| `textDocument/codeAction` | 光标在诊断上时提供 QuickFix（需 server 端注册 `FixActionGenerator`，见 `feature/lsp/docs/IMPLEMENTATION.md` §codeAction）。 |
| `textDocument/semanticTokens` | 全文档语义高亮：标签名/属性名/注释/声明 + 嵌入表达式变量/函数/字面量。标准 token 类型，VS Code 自动映射主题色。 |

> server 端能力声明见 `DslLanguageServer.initialize`（`feature/lsp/src/main/java/.../DslLanguageServer.java`）。

---

## 6. 排错

- **扩展未激活 / 提示 "no bundled server jar"**：用 `gradle :feature:lsp:buildVscodeExtension` 重新构建（含内置 jar），或设置 `dsl-analyzer-lsp.server.path` 指向有效 jar。
- **诊断不出现**：确认文件名为 `script.xml`/`script_*.xml` 且根标签是 DSL 根元素（`Lockscreen`/`Widget`/`Wallpaper`/`LongTake`/`ChargingSkin`）。server 对非 DSL 文件清空诊断。
- **hover/补全文档无格式**：server 产出 markdown；VS Code 原生渲染。若显示原始 `###`/`**`，确认 server jar 是最新构建（`buildLspFatJar` / `buildVscodeExtension` 已重跑）。
- **server 日志**：扩展用 `java -jar ... --stdio` 启动 server 子进程；server stderr 不直接可见。可在 `DslLspServerService`（IntelliJ 端）等价路径加日志，或临时用 `--inspect` 调试。
- **输出面板**：VS Code "输出" → 选 "DSL Analyzer" 查看客户端侧日志（如未配置可按 `vscode-languageclient` 文档开启）。

---

## 7. 目录结构

```
feature/lsp/clients/vscode/
├── .gitignore              # 忽略 node_modules/ out/ server/ *.vsix
├── .vscodeignore           # vsce 打包排除项（src/、*.ts、sourcemap 等）
├── package.json            # 扩展清单、依赖、npm scripts、contributes.configuration
├── package-lock.json       # 锁定依赖
├── tsconfig.json
├── src/extension.ts        # 客户端：启动 server、转发配置、documentSelector
└── server/                 # （构建产物，gitignored）buildVscodeExtension 复制的 fat jar
```

构建产物：

- `out/extension.js` — esbuild 打包的客户端入口。
- `dsl-analyzer-lsp-<version>.vsix` — 可安装的扩展包。

---

## 8. 与其它编辑器

server 是通用 LSP，其它编辑器配置见 `feature/lsp/README.md`（Neovim / coc.nvim / Helix）。
