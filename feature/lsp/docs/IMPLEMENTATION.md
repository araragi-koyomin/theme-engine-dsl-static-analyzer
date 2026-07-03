# DSL Analyzer LSP Server — 实现文档

> 模块路径：`feature/lsp`（Gradle project `:feature:lsp`）
> 包名：`com.huawei.theme.analysis.lsp`
> 面向读者：模块维护者、阶段二/三开发者
> 接入指南（面向用户）见同目录 [`README.md`](../README.md)

本文档描述 LSP server 的架构、数据流、类职责、设计决策与构建运行模型。

---

## 1. 概览

`feature:lsp` 是一个**独立运行的 LSP server**，把 `feature:analysis` 中已有的纯 Java 分析引擎（`core.*`）暴露为 Language Server Protocol 服务，使任意 LSP 客户端（VS Code / Neovim / coc.nvim / Helix）无需 IntelliJ 即可获得 DSL 静态分析能力。

**首版能力**（"三件套"）：
- `textDocument/publishDiagnostics` — 语法 + 语义诊断（含嵌入表达式）
- `textDocument/completion` — 元素标签名 + 规范属性名补全
- `textDocument/hover` — 标签签名、必填/选填属性、合法父元素

**核心约束**：`core.*` 零改动。所有 IntelliJ ↔ core 的适配都在 `feature:lsp` 内完成。

---

## 2. 模块定位

```
settings.gradle
├── common:base
├── feature:analysis     ← core 引擎 + (待废弃) plugin.editor
└── feature:lsp          ← 本模块：LSP server
```

### 依赖关系

```
feature:lsp ──implementation──> feature:analysis (取 core.* 类 + rules/functions 资源)
            ──implementation──> org.eclipse.lsp4j:0.21.2
            ──testImplementation──> junit-jupiter:5.9.3
```

`feature:lsp` **不** apply `org.jetbrains.intellij` 插件。根 `build.gradle` 用
`if (project.path != ':feature:lsp')` 守卫，使本模块跳过 IntelliJ SDK 下载与插件打包，
只作为纯 Java `application` 构建。

### 与 `feature:analysis` 的关系

- 复用其 `com.huawei.theme.analysis.core.**` 编译产物（`AstBuilder`、`DiagnosticProviderImpl`、
  `RuleRepository`、`JsonRuleLoader`、`ExpressionParser`、`DslFileIdentifier` 等）。
- 复用其 `src/main/resources` 下的 `rules/` 与 `functions/` 资源（打包进 fat jar）。
- **不**依赖 `plugin.editor`（IntelliJ 原生 PSI 适配层），后者将在阶段三被本 server 替代。

---

## 3. 架构分层

自上而下三层：

```
┌─────────────────────────────────────────────────────────────┐
│  LSP4J 传输层 (org.eclipse.lsp4j)                            │
│  stdio JSON-RPC, 请求/响应, 通知                              │
├─────────────────────────────────────────────────────────────┤
│  适配层 (com.huawei.theme.analysis.lsp)                      │
│  ┌──────────────┐ ┌──────────────┐ ┌────────────────────┐   │
│  │ LanguageServer│ │TextDocService│ │ WorkspaceService  │   │
│  └──────┬───────┘ └──────┬───────┘ └────────────────────┘   │
│         │                 │                                  │
│  ┌──────┴─────────────────┴──────────────────────────────┐  │
│  │ AnalysisService │ DiagnosticPublisher │ PositionMapper │  │
│  │ CompletionProvider │ HoverProvider │ ContextResolver   │  │
│  │ RuleRepositoryFactory │ ClasspathResourceExtractor     │  │
│  └────────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│  复用层 (com.huawei.theme.analysis.core.*) — 零改动           │
│  AstBuilder(SAX) → DiagnosticProviderImpl(analyzers)         │
│  RuleRepository(JsonRuleLoader) │ ExpressionParser(ANTLR)    │
└─────────────────────────────────────────────────────────────┘
```

适配层的职责是**协议翻译**：把 LSP 请求/通知翻译为 core 调用，把 core 结果翻译为 LSP 响应/通知。

---

## 4. 核心数据流

### 4.1 文档同步与诊断

```
client                          feature:lsp                         core
  │                                  │                                │
  │ didOpen{text,uri}                │                                │
  │─────────────────────────────────>│                                │
  │                              DslTextDocuments.open(uri,text)      │
  │                              analyzeAndPublish(uri) ──────────────┤
  │                              DslFileIdentifier.isDslFile?         │
  │                                  │   yes                          │
  │                              AnalysisService.analyze(uri,text)    │
  │                                  │   AstBuilder.getDslAst ────────>│ SAX 解析
  │                                  │   DiagnosticProvider.analyze ─>│ 8 analyzers
  │                                  │   <─ List<core.Diagnostic> ────┤
  │                              PositionMapper(text).toRange(d)      │
  │                              DiagnosticPublisher.toLspDiagnostics │
  │ <─publishDiagnostics{uri,[]}─────│                                │
  │                                  │                                │
  │ didChange{fullText}              │                                │
  │─────────────────────────────────>│                                │
  │                              DslTextDocuments.update(uri,text)    │
  │                              scheduleAnalyze(uri) ─[300ms]─►      │
  │                              (debounce: 取消上次 pending)          │
  │                              [300ms 后] analyzeAndPublish(uri) ───┤ ...同上
  │ <─publishDiagnostics──────────────│                                │
```

要点：
- **全量同步**（`TextDocumentSyncKind.Full`）：客户端每次变更发送整篇文本，server 不做增量 patch，直接覆盖。
- **debounce 300ms**：`didChange` 不立即重解析，而是用 `ScheduledExecutorService` 延迟 300ms，期间新 `didChange` 取消上次 pending，避免逐键解析。
- **首测直发**：`didOpen` 不 debounce，立即分析一次（首次打开要尽快反馈）。
- **文件识别**：`DslFileIdentifier`（core 复用）做双重判定——`.xml` 后缀 + 根标签 ∈ `RuleRepository.getRootElementNames()`。非 DSL 文件清空诊断。
- **诊断→位置映射**：core `Diagnostic` 携带 1-based line / 0-based column + `astNode.text`；`PositionMapper.toRange` 转为 LSP `Range`，`DiagnosticPublisher` 补 severity/code/source。

### 4.2 补全

```
client                                  feature:lsp
  │ completion{position}                      │
  │──────────────────────────────────────────>│
  │                                    DslTextDocuments.get(uri) -> text
  │                                    PositionMapper.toOffset(line,char) -> offset
  │                                    ContextResolver(text).resolve(offset) -> Context
  │                                        ├ type=ELEMENT_NAME -> CompletionProvider.elementNameItems(word)
  │                                        │   RuleRepository.getAllElementNames()
  │                                        └ type=ATTRIBUTE_NAME -> attributeNameItems(tagName,word)
  │                                            RuleRepository.getCanonicalAttrNames(tagName)
  │                                            (必填属性 sortText 前缀 "0_"，选填 "1_")
  │ <─ CompletionList{items} ─────────────────│
```

要点：
- **无 PSI 上下文**：IntelliJ 插件靠 `PsiElement.parent` 区分标签名/属性名；LSP server 无 PSI，改用 `ContextResolver` 从 cursor 向左扫描文本反查。
- **prefix 过滤**：server 自身按 cursor 处已输入 word 做前缀过滤，不依赖客户端过滤。
- **返回 `Either.forLeft(List<CompletionItem>)`**（非 `CompletionList`，`isIncomplete` 隐含 false）。

### 4.3 悬停

```
client                                  feature:lsp
  │ hover{position}                          │
  │──────────────────────────────────────────>│
  │                                    ContextResolver.resolve(offset) -> Context{tagName}
  │                                    HoverProvider.hover(ctx)
  │                                        RuleRepository.getElementRule(tagName)
  │                                        renderTag: elementName/category/required/optional/allowedParents/inherits
  │ <─ Hover{MarkupContent(markdown)} ───────│
  │ (tagName 为空或未知规则 → 返回 null)       │
```

首版仅标签悬停；属性悬停待规则库暴露 per-attribute 描述后补。

---

## 5. 类参考

包 `com.huawei.theme.analysis.lsp`，共 15 个类。

### 入口与协议层

| 类 | 职责 |
|---|---|
| `DslLspLauncher` | `main()` 入口。解析 `--rule-dir`，创建 `DslLanguageServer`，`LSPLauncher.createServerLauncher(server, in, out)`（3 参数版，用 LSP4J 内部 executor），`getRemoteProxy()` 取 client，`server.connect(client)`，`startListening().get()` 阻塞，结束后 `System.exit(0)` 保证退出。 |
| `DslLanguageServer` | 实现 `LanguageServer`。构造时 `AnalyzerRegistry.init()`（**必调**，否则诊断全空）+ `RuleRepositoryFactory.create()`。`initialize` 返回 `ServerCapabilities`（Full 同步 + completion + hover）。`connect(client)` 注入 client 并构造 `DslTextDocumentService`。`shutdown` 关闭 scheduler；`exit` 调 `System.exit(0)`。 |
| `DslTextDocumentService` | 实现 `TextDocumentService`。`didOpen/didChange(全量)/didClose/didSave(空)/completion/hover`。持有 debounce scheduler 与 `pending` map（uri→ScheduledFuture）。`analyzeAndPublish` 是诊断核心：文件识别 → 分析 → 发布。 |
| `DslWorkspaceService` | 实现 `WorkspaceService`。`didChangeConfiguration/didChangeWatchedFiles` 空实现，预留配置化（阶段二）。 |

### 状态与装配

| 类 | 职责 |
|---|---|
| `DslTextDocuments` | `ConcurrentHashMap<uri, text>`。`open/update/close/get`。 |
| `RuleRepositoryFactory` | 装配 `RuleRepository`：`ClasspathResourceExtractor.extractBuiltinResources()` 解压内置 `rules/`+`functions/` 到临时目录；`--rule-dir` 覆盖规则目录；`JsonFunctionSignatureLoader` 加载函数签名；`JsonRuleLoader.loadFromDirectory(ruleDir, functionLibrary)`。失败降级为空规则库。 |
| `ClasspathResourceExtractor` | 把 classpath `rules/` 与 `functions/` 解压到临时目录。两种模式：`jar:` 协议走 `JarURLConnection` 遍历 entries；`file:` 协议（开发期）走 `Files.walk`。目的是让 `JsonRuleLoader`（用 `Files.walk`）能统一加载 JAR 内资源。 |

### 分析与翻译

| 类 | 职责 |
|---|---|
| `AnalysisService` | `analyze(filePath, content)`：`new AstBuilder(repo).getDslAst` → `new DiagnosticProviderImpl().analyze(ast, repo, new SymbolTableBuilderImpl())` → `List<Diagnostic>`。异常兜底返回空列表（单文件不拖垮 server）；`CancellationException` 透传。 |
| `DiagnosticPublisher` | `toLspDiagnostics(core, mapper)`：每条 core `Diagnostic` → LSP `Diagnostic(range, message, severity, source="dsl-analyzer")`。severity 映射 ERROR→Error/WARNING→Warning/INFO→Information。`ruleId` → `code`（`Either.forLeft`）。 |
| `PositionMapper` | 行列↔LSP 位置双向映射。`buildLineStarts` 预算每行起始 offset。`toPosition(line1Based, col0Based)` → LSP `Position`（line-1, col，clamp 到行末）。`toRange(Diagnostic)` 用 `astNode.text.length()` 算 end（单行内）。`toOffset(lspLine, lspChar)` 反向。 |
| `ContextResolver` | 文本启发式反查 cursor 上下文。`findTagOpen` 向左找最近 `<`（遇 `>` 则属上一标签）；区分 `ELEMENT_NAME`（`<` 后 name token 内）、`ATTRIBUTE_NAME`（标签名后、非引号值区）、`OTHER`（闭合标签/属性值/标签外）。返回 `Context{type, tagName, word}`。 |

### 语言特性

| 类 | 职责 |
|---|---|
| `CompletionProvider` | `complete(Context)`：`ELEMENT_NAME` → `getAllElementNames()` 前缀过滤；`ATTRIBUTE_NAME` → `getCanonicalAttrNames(tagName)`，必填属性 `sortText="0_"`、选填 `"1_"`，kind Field/Property。 |
| `HoverProvider` | `hover(Context)`：取 `getElementRule(tagName)`，渲染 Markdown（elementName · category / Required / Optional / Allowed parents / Inherits）。无匹配返回 `null`。 |

---

## 6. 关键设计决策

### 6.1 core 零改动
首版不碰 `core.*`，适配全部在 `feature:lsp`。好处：`feature:analysis` 既有测试零回归；core 仍可被 CLI（`buildFatJar`）与未来 IntelliJ 客户端复用。代价：`ContextResolver` 用文本启发式而非 AST 精确定位（因 core AST 节点无 end 位置）——留待阶段二补强。

### 6.2 全量同步 + debounce
core 的 `AstBuilder` 是全量 SAX 解析。LSP 增量同步（range patch）复杂且 core 不支持，故用 `TextDocumentSyncKind.Full`：客户端每次发整篇文本，server 重解析。DSL 文件小（通常数百行），300ms debounce 后重解析延迟可忽略，且省去增量 patch 的复杂度与状态机。

### 6.3 文本启发式 ContextResolver
IntelliJ 插件靠 PSI `parent instanceof XmlTag/XmlAttribute` 区分标签名/属性名。LSP server 无 PSI，`ContextResolver` 从 cursor 向左扫描：找最近 `<`（跳过已闭合 `>`）→ 标签名 token 内为 `ELEMENT_NAME`，标签名后非引号区为 `ATTRIBUTE_NAME`。DSL 结构简单（无 CDATA/注释内标签），启发式足够；边缘 case（注释内）不处理。

### 6.4 规则资源解压临时目录
`JsonRuleLoader` 用 `Files.walk`（文件系统），不能直读 JAR 内资源。`ClasspathResourceExtractor` 启动时把 classpath `rules/`+`functions/` 解压到临时目录，再 `loadFromDirectory(tmp/rules)`。两种模式：生产（fat jar，`jar:` 协议）走 `JarURLConnection`；开发（IDE/gradle run，`file:` 协议）走 `Files.walk`。零资源清单维护，自动适应新增规则文件。

### 6.5 PositionMapper 的 UTF-16 一致性
core 行列来自 `AstBuilder` 的 SAX `Locator`：`getLineNumber()` 1-based，`getColumnNumber()` 1-based，`AstBuilder` 转成 0-based。因 JDK XML 解析器基于 Java `String`，列已是 UTF-16 code unit 计数，与 LSP "character" 定义一致。故映射即 `line-1`/`column`（clamp 到行末），无需 UTF-16 换算。`toRange` 用 `astNode.text.length()` 延伸 end。

### 6.6 嵌入表达式天然包含
`AstBuilder` 在建 AST 时就对支持表达式的属性值调用 `ExpressionParser.parseExpression`（`AstBuilder` 的 `AstContentHandler`），结果挂在 `DslAttributeValueNode.expression`。`DiagnosticProviderImpl` 的各 analyzer（含 `TypeAnalyzer`/`VarRefAnalyzer`）会对表达式 AST 产诊断。因此 LSP server **无需复刻** IntelliJ 的 `MultiHostInjector`——表达式诊断已由 core 在宿主 AST 上完成，直接经 `DiagnosticPublisher` 输出。这是相比 IntelliJ 插件层的架构红利。

### 6.7 AnalyzerRegistry.init 必调
`DiagnosticProviderImpl.analyze` 遍历 `AnalyzerRegistry.getAnalyzers()`。该 registry 用静态注册，必须 `init()` 一次（注册 8 个 analyzer）才能产出诊断。`DslLanguageServer` 构造时调用。若遗漏，诊断全空且无报错——这是隐蔽 bug，已在构造时保证。

### 6.8 签名文件排除
fat jar 重新打包 lsp4j（已签名）时，其 `META-INF/*.SF|*.RSA|*.DSA` 失效，导致 JVM 启动报 `Invalid signature file digest`。`buildLspFatJar` 用 `exclude 'META-INF/*.SF'` 等剔除。

### 6.9 System.exit 保证退出
LSP4J 3 参数 `createServerLauncher` 用内部非 daemon `ExecutorService`，stdin EOF 后 listening future 完成但 executor 线程存活，JVM 不退出。`DslLspLauncher` 在 `listening.get()` 后 `System.exit(0)`，保证客户端断开/`exit` 通知后干净退出。

---

## 7. LSP 能力声明

`DslLanguageServer.initialize` 返回：

```json
{
  "capabilities": {
    "textDocumentSync": 1,
    "hoverProvider": true,
    "completionProvider": {
      "resolveProvider": false,
      "triggerCharacters": ["<", " ", "="]
    }
  }
}
```

- `textDocumentSync: 1` = `Full`
- `completionProvider.triggerCharacters`：`<` 触发元素名补全，空格触发属性名补全，`=` 后不补全但作分隔
- `resolveProvider: false`：所有 item 一次返回完整，无 `completionItem/resolve`
- 未声明的能力（definition/references/rename/formatting/semanticTokens/codeAction）首版不支持

---

## 8. 构建与产物

### 任务

```bash
gradle :feature:lsp:buildLspFatJar
```

### `buildLspFatJar` 内容组成（`feature/lsp/build.gradle`）

| 来源 | 包含 |
|---|---|
| `sourceSets.main.output` | 本模块 15 个类 |
| `project(':feature:analysis').sourceSets.main.output.classesDirs` | `com/huawei/theme/analysis/core/**`（仅 core，排除 plugin.editor） |
| `project(':feature:analysis').sourceSets.main.output.resourcesDir` | `rules/**` + `functions/**` |
| `configurations.runtimeClasspath` 过滤 | gson + antlr4-runtime + lsp4j（**不含** IntelliJ SDK） |

排除：`META-INF/*.SF|*.RSA|*.DSA|INDEX.LIST`（签名）。
`duplicatesStrategy = EXCLUDE`。

### 产物

```
feature/lsp/build/lsp/dsl-analyzer-lsp.jar   (~1751 entries)
```

自包含：core 类 + 规则/函数资源 + gson + antlr4-runtime + lsp4j。无 IntelliJ SDK，plain JRE 17+ 可运行。

---

## 9. 运行时模型

```
java -jar dsl-analyzer-lsp.jar --stdio [--rule-dir <path>]
```

- **传输**：stdio（`System.in/out`）
- **启动序列**：`main` → `new DslLanguageServer(ruleDir)`（`AnalyzerRegistry.init` + 规则库装配，解压资源到临时目录）→ `createServerLauncher` → `connect(client)` → `startListening` 阻塞
- **线程**：LSP4J 内部 cached executor 处理 JSON-RPC；`DslTextDocumentService` 单线程 daemon `ScheduledExecutorService`（"dsl-lsp-analyzer"）跑 debounce 分析；`completion`/`hover` 在 LSP4J 请求线程同步返回（`CompletableFuture.completedFuture`）
- **`--rule-dir`**：覆盖内置规则目录（外部规则迭代用，免重新打包）
- **临时目录**：`ClasspathResourceExtractor` 创建 `dsl-lsp-resources*` 临时目录，进程退出后由 OS 清理

---

## 10. 测试

`feature/lsp/src/test/java`，13 个测试，`gradle :feature:lsp:test` 全绿。

| 测试类 | 覆盖 |
|---|---|
| `PositionMapperTest` | 1-based→0-based 行映射、列 clamp、行越界 clamp、`toOffset` 往返 |
| `ContextResolverTest` | 元素名位、`<` 后空 token、属性名位、属性值区(OTHER)、闭合标签(OTHER)、标签外(OTHER) |
| `AnalysisServiceTest` | 端到端：内置规则库加载（`getAllElementNames` 非空）、合法 DSL 分析不抛异常、畸形 XML（未闭合标签）不崩 |

`AnalysisServiceTest` 通过 `RuleRepositoryFactory(null)` 触发 `ClasspathResourceExtractor` 从 test classpath 解压真实规则，验证整条装配链（解压→加载→AST→analyzers）。

**smoke test**（手动）：`java -jar ... --stdio` 发 `initialize` 请求，返回正确 capabilities（验证 fat jar 启动 + 规则加载 + LSP 握手）。

---

## 11. 与 IntelliJ 插件的关系

当前 `feature:analysis` 仍含 `plugin.editor`（4 个原生 PSI 类），与 `feature:lsp` 并存。

- **阶段一（当前）**：LSP server 独立服务通用编辑器；`plugin.editor` 不受影响。
- **阶段三（规划）**：IntelliJ 插件改为 **LSP4J 客户端**（自写，不依赖 LSP4IJ），`ProcessBuilder` 启动 `dsl-analyzer-lsp.jar`，用原生 `Annotator`/`CompletionContributor`/`DocumentationProvider` 桥接 LSP 结果，**废弃 `plugin.editor`**。届时 `feature:lsp` 的 server 被两端（通用编辑器 + IntelliJ）共用，消除双套适配层。

`feature:analysis` 的 `checkCoreIntellijDependency` 任务保证 `core.*` 不 import `com.intellij.*`，是双轨/单轨切换的基石。

---

## 12. 限制与后续路线

### 当前限制
1. **AST 无 end 位置** → `ContextResolver` 用文本启发式，注释/CDATA 内可能误判；诊断 range 用 `astNode.text` 延伸，跨行节点退化为零宽。
2. **无 QuickFix** → `core.quickfix.QuickFixProvider` 已就绪但未接 `codeAction`。
3. **无配置化** → `ConfigAwareRuleRepository`（规则启用/禁用/严重度覆盖）未接 `initializationOptions`/`workspace/configuration`。
4. **无语义高亮** → `textDocument/semanticTokens` 未实现。
5. **悬停仅标签** → 属性悬停待规则库 per-attribute 描述。
6. **全量重解析** → 超大文件 + 高频编辑下 debounce 后仍可能偶发卡顿（DSL 场景罕见）。

### 阶段二（core 增强，为两端共用铺路）
1. `DslAstNode` 加 `endLine/endColumn`；`AstBuilder` 的 `endElement` 记 locator → `ContextResolver` 改 AST 精确定位，诊断 range 用真实 end。
2. `textDocument/codeAction` ← `QuickFixProvider.getFixActions` → `FixAction` → `TextEdit`。
3. `initializationOptions`/`workspace/configuration` → `InspectionConfig` → `ConfigAwareRuleRepository`。
4. `textDocument/semanticTokens`（表达式 token 着色）。

### 阶段三（IntelliJ 客户端）
- `feature:analysis` 新增 `plugin.lsp` 客户端层（替代 `plugin.editor`）。
- `ProcessBuilder` 起 server + LSP4J `createClientLauncher` + 原生扩展点桥接。
- 删除 `plugin.editor`，`feature:lsp` server 被通用编辑器与 IntelliJ 共用。
