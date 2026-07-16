---
module_ids: [CORE]
doc_kind: report
status: active
created: 2026-07-14
---
# Theme Engine DSL 静态分析器当前阶段开发总结

> 基准日期：2026-07-14
> 主线快照：`main@e9e9bcd`
> LSP 并行线快照：`origin/lsp-server@864a233`
> 调查范围：`docs/Architecture.md`、`docs/architecture/`、PRD/TDD/CLI/Editor 文档、主线生产代码、LSP 分支代码、测试源码与本轮 Gradle 测试结果。

## 1. 结论先行

项目已经完成静态分析主体能力建设，当前最准确的阶段判断是：**Core 主体成熟，CLI 主链路可用，Editor/PSI 基础能力已合入 main，LSP 作为独立并行路线持续开发；下一阶段重点应从“继续堆功能”转向“补齐交付闭环、性能证据、扩展机制与端到端测试”。**

核心结论如下：

1. **Core M0–M4 已形成完整分析底座。** 表达式与规则 DSL、文件识别、JSON 规则库、独立 AST、符号表、9 个 Analyzer、类型推断、函数签名和声明式约束均已有生产实现。
2. **M3 与 M5 的主体实现存在，但生产接线仍需收口。** 结构 `SyntaxChecker` 未进入 CLI/Editor 的统一生产链；Quick Fix generator 已实现，但生产入口没有初始化 `FixActionRegistry`。
3. **CLI/M7 已完成文件与目录扫描、三格式报告、配置和 fat jar 交付。** 当前主要缺口是 `syntax-only`、`semantic-only`、`no-type-check`、`quiet`、`verbose` 的真实语义，以及内部异常的结果契约。
4. **Editor/PSI 已完成主线编辑器基础交付。** main 已具备标签/属性/枚举补全、表达式注入与高亮、文档、实时诊断、变量跳转、Find Usages 和 Rename；Quick Fix UI、ToolWindow、右键批量检查和插件级验证尚未完成。
5. **LSP 与 Editor/PSI 是两条互不阻塞的 IDEA 集成探索。** LSP 分支已有独立 server、IntelliJ client、诊断、补全、Hover、semantic tokens 和运行时配置更新；它不属于 main 当前里程碑的前置条件，也不应作为项目进度风险。
6. **本轮测试已经恢复全绿。** 在 4 个测试文件对齐最新规则语义后，以 `main@e9e9bcd` 的生产代码为基础，Gradle 结果为 **71 suites / 777 tests / 0 failures / 0 errors / 0 skipped**。
7. **性能仍只有目标，没有达标证据。** PRD/TDD 定义了 Editor、CLI、批量与类型推断的响应时间目标，但仓库中尚无 benchmark、JMH、性能报告或持续性能基线。
8. **架构文档明显滞后于实现。** 最典型的偏差是 SAX 与 StAX、统一 PSI Adapter 与当前直接 PSI 映射、CLI 参数语义、Editor UI 完成度，以及 LSP 文档落后于 semantic tokens 等现有代码。

## 2. 当前阶段总览

| 领域 | 当前状态 | 阶段判断 | 关键证据 |
|---|---|---|---|
| Core M0 | 已完成 | 表达式 grammar、规则 DSL、函数签名库和代码生成已落地 | `feature/analysis/build.gradle:24-54`；`feature/analysis/src/main/java/com/huawei/theme/analysis/core/expression/grammar/DslExpression.g4:1`；`core/ruledsl/grammar/DslRuleCondition.g4:1` |
| Core M1 | 已完成 | Core 支持 `.xml + 根标签` 双重识别 | `core/fileidentification/DslFileIdentifier.java:37-50` |
| Core M2 | 已完成 | JSON 规则、全局变量、来源和函数库可从目录/classpath 加载 | `core/rulelibrary/JsonRuleLoader.java:92-150,345-445` |
| Core M3 | 进行中 | StAX AST、位置映射和表达式嵌入已完成；结构 SyntaxChecker 未进入生产链 | `core/syntaxanalysis/AstBuilder.java:43-77,80-205`；`SyntaxChecker.java:16-40`；`DiagnosticProviderImpl.java:17-22` |
| Core M4 | 已完成 | 9 个 Analyzer、符号表、类型推断、引用与约束分析已形成 | `core/semanticanalysis/AnalyzerRegistry.java:34-48`；`DiagnosticProviderImpl.java:17-78` |
| Core M5 | 进行中 | 修复模型、注册表和 6 类 generator 已完成；生产初始化与 IDE UI 未闭环 | `core/quickfix/FixActionRegistry.java:22-48`；`core/quickfix/generators/` |
| CLI / M7 | 进行中 | 扫描、报告、fat jar 与基本退出码已完成；模式、参数和异常语义待收口 | `core/cli/CliMain.java:47-127`；`core/batchinspection/BatchInspectionRunnerImpl.java:53-138` |
| Editor / PSI | 进行中 | 编辑器基础能力已合入 main；类型一致性、Quick Fix UI、ToolWindow 与插件验证待补 | `feature/analysis/src/main/resources/META-INF/plugin.xml:14-83` |
| LSP 并行线 | 进行中 | 独立 server + IntelliJ client 已有可运行实现，继续补标准 LSP 能力和验证 | `origin/lsp-server:feature/lsp/`；`origin/lsp-server:feature/analysis/src/main/java/com/huawei/theme/analysis/plugin/lsp/` |
| 性能 | 未完成 | 有明确目标，无可复核实测 | `docs/PRD.md:189-193`；`docs/TDD.md:581-597` |
| CI/CD | 未完成 | 有本地构建约束，没有仓库级自动门禁与发布流水线 | `feature/analysis/build.gradle:60-99`；仓库无 `.github/` |

## 3. 已完成内容

### 3.1 Core M0：解析器基础设施

- 已有 ANTLR4 表达式 grammar 和规则条件 grammar：
  - `feature/analysis/src/main/java/com/huawei/theme/analysis/core/expression/grammar/DslExpression.g4`
  - `feature/analysis/src/main/java/com/huawei/theme/analysis/core/ruledsl/grammar/DslRuleCondition.g4`
- Gradle 在编译前自动生成两套 lexer/parser/visitor，见 `feature/analysis/build.gradle:24-54`。
- `ExpressionParser`、`TypeInferenceEngine`、`DefaultRuleDslEvaluator` 已形成表达式解析、类型推断和声明式条件执行链：
  - `core/expression/ExpressionParser.java:15,54`
  - `core/expression/TypeInferenceEngine.java`
  - `core/ruledsl/DefaultRuleDslEvaluator.java:18,30`
- 函数签名支持 classpath 与目录加载，见 `core/function/JsonFunctionSignatureLoader.java:27,53,78`。

### 3.2 Core M1：文件识别

- `DslFileIdentifier` 先检查 `.xml` 扩展名，再从内容提取根标签并与规则库根元素集合匹配，见 `core/fileidentification/DslFileIdentifier.java:37-50`。
- 轻量根标签扫描允许畸形 XML 继续进入后续语法阶段，设计意图见 `DslFileIdentifier.java:7-21`。

### 3.3 Core M2：规则库与静态资产

- `JsonRuleLoader` 已覆盖元素规则、全局变量、规则来源、目录加载与 classpath/JAR 加载，见 `core/rulelibrary/JsonRuleLoader.java:92-150,345-445`。
- `DefaultRuleRepository` 提供元素、根标签、属性、父子关系、全局变量、规则来源与函数库统一查询，见 `core/rulelibrary/DefaultRuleRepository.java:29,113-118,196`。
- 生产规则和函数静态资产已落地：
  - `feature/analysis/src/main/resources/rules/elements/`
  - `feature/analysis/src/main/resources/rules/global_vars.json`
  - `feature/analysis/src/main/resources/rules/rule_sources.json`
  - `feature/analysis/src/main/resources/functions/dsl_functions.json`

### 3.4 Core M3：独立 AST 与表达式嵌入主体

- 实际生产实现使用安全 StAX `XMLStreamReader`，禁用 DTD 与外部实体，见 `core/syntaxanalysis/AstBuilder.java:52-77`。
- `AstBuilder` 已构建 `DslFileNode`、元素、属性、父子关系和源范围，见 `AstBuilder.java:80-166`。
- 支持表达式的属性会按规则库元数据进入 ANTLR 表达式解析，并把相对位置平移为文档位置，见 `AstBuilder.java:180-236`。
- XML 解析失败会转为 `hasError` 节点，后续 `SyntaxErrorAnalyzer` 可生成 `SYN-SAX-001`，见 `AstBuilder.java:58-69,351-361`、`core/semanticanalysis/analyzers/SyntaxErrorAnalyzer.java:11-27`。

### 3.5 Core M4：语义、类型与符号分析

- 注册表当前包含 9 个 Analyzer：XML 错误、声明式约束、父子关系、作用域、必填属性、字面量类型、枚举、变量/函数引用、类型推断，见 `core/semanticanalysis/AnalyzerRegistry.java:34-48`。
- `DiagnosticProviderImpl` 构建全局符号表、递归分析元素并追加表达式语法诊断，见 `DiagnosticProviderImpl.java:17-78`。
- `TypeAnalyzer` 已处理表达式期望类型、函数参数、变量类型、数组边界和复杂表达式传播，入口见 `core/semanticanalysis/analyzers/TypeAnalyzer.java:44-75`。
- `SymbolTableBuilderImpl` 收集预置全局变量与文件内 `<Var>`，并支持重复声明信息，见 `core/semanticanalysis/SymbolTableBuilderImpl.java:22,35`。
- 最近一轮测试期望已对齐：跨上下文函数签名回退、元素属性引用统一为 `SEM-REF-001`、函数参数错误归类为 `SEM-TYPE-002`、重复变量每一处均报告 `SEM-REF-003`。

### 3.6 Core M5：修复描述能力

- `FixAction`、`CandidateItem`、`FixActionIntent`、`QuickFixProvider` 和 `FixActionRegistry` 已形成可扩展修复模型。
- 当前 generator 包含：插入属性、替换枚举、数值 clamp、表达式修正、移除属性和声明式约束 fallback，见 `core/quickfix/FixActionRegistry.java:22-33`、`core/quickfix/generators/`。
- 单元与集成测试覆盖 registry、provider 和各 generator，见 `feature/analysis/src/test/java/com/huawei/theme/analysis/core/quickfix/`。

### 3.7 CLI / M7 主链路

- CLI 已实现参数解析、互斥校验、规则/函数加载、文件/目录分派、报告导出和退出码，见 `core/cli/CliMain.java:47-127,139-230`。
- `BatchInspectionRunnerImpl` 已支持单文件与递归目录扫描、非 DSL 跳过、诊断计数和修复结果聚合，见 `core/batchinspection/BatchInspectionRunnerImpl.java:53-105`。
- `ReportExporterImpl`、`JsonReportSerializer`、`TerminalFormatter` 已支持 Terminal、JSON、Markdown 和写文件，见 `ReportExporterImpl.java:31-88`。
- `buildFatJar` 只打包 Core、规则、函数、GSON 和 ANTLR runtime，入口为 `CliMain`，见 `feature/analysis/build.gradle:105-133`。

### 3.8 Editor / PSI 主线基础能力

- main 的 `plugin.xml` 已注册：ThemeDSL FileType、XML ParserDefinition、标签/属性/枚举补全、表达式语言、表达式补全/文档/高亮、ThemeDSL 诊断、标签分类、文档、变量 Rename、Find Usages、ReferenceContributor 和语言注入，见 `feature/analysis/src/main/resources/META-INF/plugin.xml:14-83`。
- ThemeDSL 基于 IntelliJ XML PSI：`plugin/editor/themedsl/ThemeDslLanguage.java:5-11`、`ThemeDslParserDefinition.java:10-22`。
- 标签、属性与枚举值补全已从规则库动态生成，见：
  - `ThemeDslElementNameCompletionContributor.java:28-42`
  - `ThemeDslAttributeCompletionContributor.java:53-134`
- 表达式属性通过 `MultiHostInjector` 注入独立的 DslExpression 语言，见 `plugin/editor/reference/ThemeDslExpressionInjector.java:64-110`。
- 表达式补全已覆盖预置变量、用户变量、局部 indexFlag 和函数签名，见 `plugin/editor/expr/DslExpressionCompletionContributor.java:62-141`。
- 实时诊断按 PSI modificationStamp 缓存，内部复用 Core `AstBuilder` 和 `DiagnosticProviderImpl`，见 `ThemeDslDiagnosticAnnotator.java:104-150`。
- 变量跳转、Find Usages 与 Rename 已有生产实现：
  - `plugin/editor/reference/DslVariableReference.java:55-78`
  - `ThemeDslVarFindUsagesHandlerFactory.java:37-60`
  - `ThemeDslVarRenameProcessor.java:49-134`

### 3.9 LSP 并行线已有成果

LSP 不是 main Editor 的替代前置条件，而是独立并行探索。当前分支已经完成：

- `:feature:lsp` 独立 Gradle 模块与无 IntelliJ SDK 的 fat jar，见 `origin/lsp-server:feature/lsp/build.gradle:1-60`。
- Full 文档同步、诊断、元素/属性补全、Tag Hover、semantic tokens 能力声明，见 `origin/lsp-server:feature/lsp/src/main/java/com/huawei/theme/analysis/lsp/DslLanguageServer.java:73-87`。
- 300ms debounce 分析、打开/变更/关闭文档、补全、Hover 与 semantic tokens 请求，见 `DslTextDocumentService.java:36-59,98-199`。
- 运行时检查配置更新会重新包装 `ConfigAwareRuleRepository` 并重分析打开文档，见 `DslLanguageServer.java:91-100`。
- IntelliJ client 已实现 server 生命周期、文档同步、诊断/semantic token 渲染、补全、Hover 和本地变量跳转，代码位于 `origin/lsp-server:feature/analysis/src/main/java/com/huawei/theme/analysis/plugin/lsp/`。

## 4. 进行中内容

### 4.1 M3 结构语法接线

`SyntaxChecker` 已能生成根元素、未知标签和未知属性诊断，见 `core/syntaxanalysis/SyntaxChecker.java:16-40`。但当前 `DiagnosticProviderImpl` 只追加 `ExpressionSyntaxChecker`，未调用 `SyntaxChecker`，见 `DiagnosticProviderImpl.java:17-22`。

直接影响是：`BatchInspectionRunnerImpl` 在 `SYNTAX_ONLY` 模式下跳过唯一的 `DiagnosticProvider`，见 `BatchInspectionRunnerImpl.java:119-126`。因此文档描述的“只执行 M3 并输出语法诊断”尚未形成真实生产链。

### 4.2 M5 生产初始化与 IDE 消费

`FixActionRegistry.init(ruleRepository)` 只在测试中调用，生产代码没有调用点。`CliMain` 虽构造 `QuickFixProviderImpl`，见 `CliMain.java:109-116`，但未初始化 generator 注册表，因此实际 `fixActions` 可能为空。

Editor 端还没有把 Core `FixAction` 转为 `IntentionAction`，也没有候选选择、diff 预览和批量 Fix UI。

### 4.3 CLI 模式、参数和失败语义

- `--syntax-only`：当前会跳过诊断 Provider，而不是只运行结构语法。
- `--semantic-only`：仍会遍历注册表中的 `TypeAnalyzer`，与文档“跳过类型推断”不一致。
- `--no-type-check`：已解析并写入 `InspectionConfig`，但没有控制 `AnalyzerRegistry` 或 `TypeAnalyzer`。
- `--quiet`：已解析，未在输出或诊断列表中执行 WARNING/INFO 过滤。
- `--verbose`：除输出报告路径外，尚未输出文档承诺的 AST、耗时、类型链和符号表摘要，见 `CliMain.java:236-240`。
- 配置文件中的 enabled/disabled/severity 当前只包装声明式 `RuleConstraint`，见 `core/cli/ConfigAwareRuleRepository.java:123-157`；固定 Analyzer 规则没有统一开关。
- Runner 对 AST、诊断、修复异常降级为空列表，见 `BatchInspectionRunnerImpl.java:111-138`；结果退出码只看 ERROR 数量，见 `ExitCodeCalculator.java:10-19`，因此内部异常仍可能被呈现为成功结果。

### 4.4 Editor / PSI 一致性和交互产品化

- `RuleRepositoryService` 加载元素规则、全局变量和规则来源，但没有加载函数签名库，见 `plugin/rule/RuleRepositoryService.java:65-79`。
- `TypeAnalyzer` 在 `functionLibrary == null` 时直接返回空，见 `TypeAnalyzer.java:53-56`，因此 main Editor 的类型/函数签名诊断与 CLI 尚未完全一致。
- Plugin FileType 只注册 `script.xml` 和 `script_*.xml`，见 `plugin.xml:19-24`；Core 的通用 `.xml + 根标签` 识别没有成为动态 FileType 绑定逻辑。
- 当前导航直接扫描整个 XML 文件的同名 `<Var>`，见 `DslVariableReference.java:55-78`，基础功能可用，但尚未使用 Core 符号表表达嵌套作用域。
- Quick Fix UI、ToolWindow/诊断面板、右键批量检查、IDEA 进度条和报告导出仍在开发范围内。

### 4.5 LSP 标准能力与工程验证

- server 当前未声明标准 `textDocument/definition`、references、rename、codeAction、formatting；IntelliJ 的变量 Ctrl+Click 是 client 本地 PSI 实现，通用 LSP 编辑器尚不能获得相同导航/重构体验。
- 补全上下文仍由文本启发式 `ContextResolver` 判断，见 `origin/lsp-server:feature/lsp/src/main/java/com/huawei/theme/analysis/lsp/ContextResolver.java:3-11,35-83`。
- Full Sync + 300ms debounce 已适合当前原型，但还没有增量 AST、真实输入延迟基线和多编辑器协议兼容测试。
- 分支现有 5 个测试类、22 个 `@Test`，主要覆盖 AnalysisService、配置、ContextResolver 和 PositionMapper；semantic tokens、IntelliJ client 生命周期和通用编辑器集成仍需覆盖。

## 5. 未完成内容

### 5.1 功能

- 统一接入结构 `SyntaxChecker`，使 FULL、SYNTAX_ONLY、CLI、Editor 和 LSP 共享一致的结构语法结果。
- 完成 `FixActionRegistry` 生产初始化，并实现 Editor `IntentionAction`/候选选择/批量修复。
- 实现 ToolWindow 诊断面板、右键批量检查、进度与报告导出。
- 使 CLI 参数和配置真正控制 Analyzer、严重级别、过滤和详细输出。
- 补齐 LSP definition/references/rename/codeAction 等标准能力；该工作保持独立并行，不阻塞 main Editor 交付。

### 5.2 性能

- PRD 目标尚未实测：Editor 单文件 ≤50ms、CLI 单文件 ≤100ms、100 文件 ≤5s、单属性类型推断 ≤5ms，见 `docs/PRD.md:189-193`。
- 尚无 benchmark/JMH、固定规模数据集、冷启动/热路径区分和持续趋势报告。
- main Editor 当前是同步全文件 Core AST 重建，只按 modificationStamp 缓存；尚未实现 TDD 描述的 PSI 增量节点分析和后台任务。
- LSP 当前 Full Sync + 全量分析；后续需要基于真实 DSL 文件规模决定是否引入增量同步、AST 复用和并发调度。

### 5.3 扩展性

- 主线规则库/函数库文件热更新和缓存失效机制。
- Analyzer 与 FixActionGenerator 的自动发现或实例级注册，降低静态全局注册表的测试与生命周期耦合。
- 规则编辑器或规则校验工具，避免 JSON 资产质量完全依赖人工与单测。
- Core、CLI、Editor、LSP 共享的契约测试，保证同一输入在不同交付面产生一致诊断。
- 基于符号表作用域的 Editor/LSP 导航与重构，而不是全文件第一个同名声明。

### 5.4 测试覆盖

- main Plugin 目前 33 个生产 Java 文件，但 `feature/analysis/src/test/java/com/huawei/theme/analysis/plugin/` 只有 1 个 lexer 测试。
- 需要增加 IntelliJ fixture 测试：FileType、completion、documentation、Annotator、注入、Ctrl+Click、Find Usages、Rename 和未来 Quick Fix。
- 需要给 syntax-only、内部异常、`no-type-check`、quiet、verbose 和规则配置增加真实输出契约测试。
- 需要把真实 JAR 进程级 E2E、clean/negative fixture、性能 fixture 与 LSP JSON-RPC smoke 纳入固定复核流程。

## 6. 三条真实数据流

### 6.1 CLI 数据流（main）

```text
CliMain 参数/配置
  → JsonRuleLoader + JsonFunctionSignatureLoader
  → CliDslFileMatcher（扩展名 + 根标签）
  → AstBuilder（StAX → DslFileNode + 表达式 AST）
  → BatchInspectionRunnerImpl
  → DiagnosticProviderImpl（9 Analyzer + ExpressionSyntaxChecker）
  → QuickFixProvider
  → ReportExporter（Terminal / JSON / Markdown）
  → ExitCodeCalculator（0 / 1；顶层参数/加载异常为 2）
```

入口证据：`core/cli/CliMain.java:47-127,184-257`；单文件编排：`core/batchinspection/BatchInspectionRunnerImpl.java:107-138`。

当前断点：结构 `SyntaxChecker` 和 `FixActionRegistry.init()` 不在此真实链路中；Runner 内部异常会降级为空结果。

### 6.2 Editor / PSI 数据流（main）

```text
IDEA 按 script.xml / script_*.xml 绑定 ThemeDSL XML PSI
  → Completion / Documentation / MultiHostInjector / Annotator
  → RuleRepositoryService（应用级规则缓存）
  → ThemeDslDiagnosticAnnotator 读取整文件文本
  → AstBuilder + DiagnosticProviderImpl
  → Diagnostic line/column 映射到 XmlTag / XmlAttribute
  → IDEA Annotation 展示

表达式与变量交互：
XML AttributeValue
  → 注入 DslExpression PSI
  → 高亮 / 补全 / 文档 / PsiReference
  → 宿主 XML 中 <Var> 声明
  → Ctrl+Click / Find Usages / Rename
```

证据：`plugin.xml:19-82`、`ThemeDslDiagnosticAnnotator.java:104-150`、`ThemeDslExpressionInjector.java:64-110`、`DslVariableReference.java:55-78`。

这条链没有文档所描述的独立 `DslPsiBridge` 包；当前是 Core AST 重建后直接映射到 PSI。

### 6.3 LSP 数据流（并行分支）

```text
通用编辑器或 IntelliJ LSP client
  ↔ stdio / JSON-RPC
  ↔ DslLanguageServer
  → DslTextDocumentService（Full Sync + 300ms debounce）
  → AnalysisService
  → Core AstBuilder + DiagnosticProviderImpl
  → DiagnosticPublisher
  → publishDiagnostics

并行语言能力：
ContextResolver + RuleRepository → completion / hover
AstBuilder + Expression AST → semanticTokens
```

证据：`origin/lsp-server:feature/lsp/src/main/java/com/huawei/theme/analysis/lsp/DslLanguageServer.java:73-100`、`DslTextDocumentService.java:89-199`、`AnalysisService.java:40-56`。

## 7. 本轮测试与 E2E 证据

### 7.1 本轮测试快照

本轮没有修改生产代码；为使测试期望与已合入的最新规则语义一致，更新了 4 个测试文件，改动尚未提交：

1. `feature/analysis/src/test/java/com/huawei/theme/analysis/core/expression/TypeInferenceEngineTest.java`
   - 将“函数不适用上下文返回 null”调整为“跨上下文回退到可用函数签名”。
2. `feature/analysis/src/test/java/com/huawei/theme/analysis/core/semanticanalysis/SemanticAnalysisIntegrationTest.java`
   - 元素属性引用统一期望 `SEM-REF-001`；跨上下文函数参数错误期望 `SEM-TYPE-002`。
3. `feature/analysis/src/test/java/com/huawei/theme/analysis/core/semanticanalysis/TypeAnalyzerTest.java`
   - 验证跨上下文找到函数且参数合法时不产生误报。
4. `feature/analysis/src/test/java/com/huawei/theme/analysis/core/semanticanalysis/VarRefAnalyzerTest.java`
   - 对齐元素属性引用 Rule ID、消息与文档 URL；重复变量的每个声明位置均期望 `SEM-REF-003`。

验证结果：

| 指标 | 结果 |
|---|---:|
| Test suites | 71 |
| Tests | 777 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Gradle 报告成功率 | 100% |
| Gradle 报告耗时 | 3.295s |

证据：

- `feature/analysis/build/reports/tests/test/index.html`
- `feature/analysis/build/test-results/test/TEST-*.xml`

该结果的精确定义是：**`main@e9e9bcd` 的生产代码 + 上述 4 个本地测试期望调整** 全绿。它证明当前生产行为与最新规则策略一致，不代表性能、Plugin UI 或 LSP 分支已经全部验证。

按功能域汇总本轮 777 个测试：

| 功能域 | 测试数 | 主要覆盖 |
|---|---:|---|
| 批量检查、报告与 CLI | 304 | runner、mode、配置、三格式、退出码、CLI 入口 |
| 文件识别、AST 与表达式 | 154 | FileIdentifier、StAX AST、表达式嵌入、ANTLR visitor、类型推断 |
| 语义、类型与引用 | 140 | Analyzer、SymbolTable、多分析器集成 |
| 规则、函数与 Rule DSL | 111 | 规则批次加载、函数签名加载、声明式条件执行 |
| FixAction | 58 | registry、provider、6 类 generator、修复集成 |
| 共享模型 | 9 | AST、Diagnostic、DslType 数据模型 |
| Editor 插件 | 1 | 表达式高亮 lexer 回归 |

### 7.2 E2E 层次

当前验证体系实际分为四层：

| 层次 | 主要目的 | 代表测试/证据 | 典型输入 |
|---|---|---|---|
| L1 单元与组件 | grammar、loader、Repository、Analyzer、Fix generator 的局部契约 | `feature/analysis/src/test/java/com/huawei/theme/analysis/core/**` | 内联 XML、mock Repository、单表达式 |
| L2 Core 管线集成 | 文件识别、AST、诊断、修复、报告和退出码的组合 | `BatchInspectionIntegrationTest`、`BatchInspectionRealScenarioTest`、`PipelineEndToEndTest` | `fixtures/batch-inspection/`、`fixtures/e2e-pipeline/` |
| L3 CLI 入口 E2E（同 JVM） | 参数、配置、输出格式、目录扫描、互斥与错误路径 | `core/cli/CliMainE2ETest.java`（34 tests）、`CliMainIntegrationTest.java` | 临时文件 + 内置 fixture，直接调用 `CliMain.run()` |
| L4 真实 fat JAR E2E | 验证 classpath 资源、JAR 打包、进程入口和真实报告 | 本轮直接执行 `feature/analysis/build/cli/dsl-analyzer.jar`；历史全量见 `docs/bugfix-summary.md:514-572` | `e2e-pipeline`、5 组演示 fixture、历史 14 bug + 1 clean |

注意：L3 是“入口级 E2E”，但仍在测试 JVM 内直接调用 `CliMain.run()`，不等同于另起 `java -jar` 进程；L4 才覆盖真实 fat jar 和 classpath 资源装配。

### 7.3 典型 fixture

- Clean 基线：
  - `feature/analysis/src/test/resources/fixtures/e2e-pipeline/clean/lockscreen_valid.xml`
  - `feature/analysis/src/test/resources/fixtures/batch-inspection/clean/lockscreen_valid.xml`
- 类型与引用组合：
  - `fixtures/e2e-pipeline/lockscreen_type_and_ref.xml`
  - `fixtures/complex/type_inference_edge_cases.xml`
  - `fixtures/complex/variable_lifecycle_errors.xml`
- 多诊断与跨文件/目录：
  - `fixtures/batch-inspection/lockscreen_multi_error.xml`
  - `fixtures/e2e-pipeline/widget_multi_violation.xml`
- 父子结构、命令与约束：
  - `fixtures/e2e-pipeline/charging_skin_cmd_nest.xml`
  - `fixtures/complex/trigger_command_combos.xml`
  - `fixtures/complex/constraint_edge_cases.xml`
- 表达式与函数链：
  - `fixtures/complex_expressions/chained_function_hell.xml`
  - `fixtures/complex_expressions/multi_element_expression_blast.xml`
  - `fixtures/complex_expressions/string_expression_errors.xml`
- 枚举与边界：
  - `fixtures/e2e-pipeline/wallpaper_constraint_enum.xml`
  - `fixtures/complex/enum_boundary_tests.xml`
  - `fixtures/complex_expressions/precision_boundary_tests.xml`

本轮直接运行当前 Fat JAR 得到的演示结果：

| Fixture | 当前结果 | 主要覆盖 |
|---|---:|---|
| `e2e-pipeline/clean/lockscreen_valid.xml` | 0E / 0W | 合法规则、函数与变量引用的无误报基线 |
| `e2e-pipeline/wallpaper_constraint_enum.xml` | 5E / 1W | constraint、图片属性冲突、范围、枚举和严重度 |
| `e2e-pipeline/lockscreen_type_and_ref.xml` | 8E | 重复声明、未定义引用、函数参数、类型不匹配 |
| `e2e-pipeline/charging_skin_cmd_nest.xml` | 8E | 嵌套、scope、命令互斥、Trigger 和修复候选 |
| `complex/expression_syntax_errors.xml` | 9E / 2W | `-#var`、精度、引号、花括号、ANTLR 和函数 |
| `e2e-pipeline/widget_multi_violation.xml` | 7E | 必填属性、Trigger、嵌套、scope、范围及三格式输出 |

整个 `fixtures/e2e-pipeline` 目录本轮直接 JAR 扫描结果为 **33 errors / 1 warning / 0 info**，错误集退出码为 1。Terminal、JSON、Markdown 演示产物位于 `C:\Users\30991\AppData\Local\Temp\theme-dsl-demo-20260714`。

## 8. Architecture 文档与实现偏差

### 8.1 XML 解析器：文档写 SAX，生产实现是 StAX

文档在多个位置声明 XML 使用 JDK SAX：

- `docs/Architecture.md:11,29,62,240-241,263,310,323`
- `docs/PRD.md:52-57,208`
- `docs/TDD.md:35,54-60`
- `docs/architecture/M3-SyntaxAnalysis.md:5-17,105-175`

实际 `AstBuilder` 使用 `javax.xml.stream.XMLStreamReader`，见 `core/syntaxanalysis/AstBuilder.java:13-16,52-77,80-105`。阶段文档和架构图应统一改为 **StAX → 独立 AST**。

### 8.2 文档中的统一 PSI Adapter 包不存在

`docs/Architecture.md:35-40,67-75,257-302` 描述了：

- `plugin/psiadapter`
- `plugin/navigation`
- `plugin/ui`
- `plugin/quickfixui`
- `DslPsiBridge`、`FixActionAdapter`、`SymbolTableAdapter`

main 的实际包是 `plugin/editor/**` 与 `plugin/rule/**`。诊断由 `ThemeDslDiagnosticAnnotator` 直接按行列映射到 PSI；导航直接扫描 XML PSI。`docs/architecture/PSI-Adapter.md` 当前更接近目标设计，不是已实现模块说明。

### 8.3 M3 与 `--syntax-only` 文档高估当前能力

`docs/architecture/M3-SyntaxAnalysis.md:256-293` 和 `docs/CLI-Usage.md:68-76` 声称 `--syntax-only` 会输出 XML、结构和表达式语法诊断。

实际 `BatchInspectionRunnerImpl.java:119-126` 在 SYNTAX_ONLY 时不调用 `DiagnosticProvider`；`SyntaxChecker` 也没有进入生产链。因此该模式的文档与实现不一致。

### 8.4 M5 与 Editor Quick Fix 文档高估交付完整度

`docs/Architecture.md:248-280`、`docs/architecture/M5-QuickFix.md:75-106,199-206` 描述了 Core FixAction、Plugin IntentionAction、候选对话框和批量 Fix 的完整链路。

实际仅 Core generator 已实现；生产未初始化 registry，Plugin 无 `IntentionAction`、Quick Fix UI 或批量 Fix 注册。

### 8.5 Plugin 后台、增量和事件机制未按文档实现

`docs/TDD.md:44-47,590-597` 与 `docs/Architecture.md:219-230,257-302` 声称使用：

- DumbService 后台线程
- PSI 增量节点分析
- Dispatcher 事件
- LocalInspectionTool
- ToolWindow

main 当前 `ThemeDslDiagnosticAnnotator.java:104-150` 是同步全文件 Core AST 重建，只按 modificationStamp 缓存。仓库中未发现上述生产类或 EP。

### 8.6 CLI 参数名称和真实语义不一致

`docs/architecture/M4-SemanticAnalysis.md:258-267`、`M7-BatchInspection.md:228-242` 使用 `--type-check` 并声称可控制 TypeAnalyzer、quiet 过滤、verbose 输出分析细节。

实际帮助参数是 `--no-type-check`，见 `core/cli/CliMain.java:28-36`；typeCheck/quiet/verbose 主要被存入配置，没有完成下游行为。

### 8.7 `--semantic-only` 仍执行 TypeAnalyzer

`docs/architecture/M4-SemanticAnalysis.md:262-264,303-311` 声称 semantic-only 跳过类型推断。实际模式只是进入同一个 `DiagnosticProviderImpl`，而 `AnalyzerRegistry` 中仍包含 `TypeAnalyzer`。

### 8.8 自定义规则目录与函数库语义不同

`docs/architecture/M0-ParserInfrastructure.md:276-295` 暗示自定义 `--rule-dir` 包含或控制 `functions/`。

main 的 `CliMain.java:184-206,223-229` 始终先从 classpath 加载函数库，自定义目录只交给 `JsonRuleLoader` 加载规则。

### 8.9 Plugin FileType 范围与 PRD 不一致

`docs/PRD.md:119-123` 描述任意 `.xml + 根标签` 自动识别并显示自定义图标；实际 `plugin.xml:19-24` 只绑定 `script.xml`/`script_*.xml`，`ThemeDslFileType.java:27-35` 使用通用 XML 图标。

`docs/Editor.md:1-6` 反而与当前实现一致，因此 PRD 和 Editor 文档需要统一产品口径。

### 8.10 Editor 类型分析缺少函数库接线

Architecture/TDD 把 CLI 与 Editor 描述为共享同一 Core 类型分析。实际 `RuleRepositoryService.java:65-79` 构建规则库时没有传函数签名库，而 `TypeAnalyzer.java:53-56` 在函数库为空时直接退出。

表达式 completion 自行加载函数库并不代表 Editor 诊断链已完成类型分析。

### 8.11 导航实现方式与 M8 文档不同

`docs/architecture/M8-Navigation.md:21-102` 描述 Core SymbolTable → PSI Adapter → Reference。

实际 `DslVariableReference.java:55-78`、`DslVariableRefElement.java:92-117` 直接扫描整个 XML 文件中的 `<Var>`。跳转、Find Usages 和 Rename 已实现，但作用域解析模型和架构文档不同。

### 8.12 LSP 定位文档与项目真实方向不一致

分支文档 `origin/lsp-server:feature/lsp/docs/IMPLEMENTATION.md:46-52,316-332` 和分支 `plugin.xml:6-10` 把 LSP 描述为替代原生 PSI。

当前已确认的项目方向是：**Editor/PSI 与 LSP 是两条并行探索，互不阻塞；main 使用已合入的 Editor/PSI，LSP 独立开发。** 后续 LSP 文档应按这一口径修正。

### 8.13 LSP 文档落后于分支代码

- `IMPLEMENTATION.md:232-252,336-355` 仍写 semantic tokens 未支持；当前 `DslLanguageServer.java:82-86` 与 `SemanticTokensProvider.java` 已实现。
- `IMPLEMENTATION.md:300-312` 写 13 tests；当前分支实际有 5 个测试类、22 个 `@Test`。
- LSP 文档仍沿用 SAX AstBuilder 描述，而共享 Core 已改为 StAX。

### 8.14 总架构状态标签未维护

`docs/Architecture.md:333-343`：

- 引用不存在的 `docs/Development-Plan.md`。
- 将已经存在的 M0–M8 与 PSI 文档继续标为“待创建/待重构”。

这些标签不再能反映当前阶段。

### 8.15 版本口径不一致

- Gradle 工程版本：`gradle.properties:2` 为 `0.0.1`。
- CLI 版本：`core/cli/CliMain.java:23` 为 `0.1.0`。

## 9. 后续开发方向

### 9.1 功能优先级

1. **统一语法生产链**：接入 `SyntaxChecker`，让 FULL/SYNTAX_ONLY/CLI/Editor/LSP 共享相同结构诊断。
2. **收口 CLI 契约**：落实 mode、type-check、quiet、verbose、配置开关和内部异常退出语义。
3. **完成 FixAction 交付**：初始化生产 registry，先保证 CLI 报告真实包含修复动作，再接 Editor IntentionAction。
4. **补齐 Editor 交互**：函数库接线、Quick Fix UI、ToolWindow、批量检查、报告导出。
5. **推进 LSP 独立能力**：definition/references/rename/codeAction、属性级 range、通用编辑器 smoke；不把该工作设为 main Editor 的阻塞项。

### 9.2 性能方向

1. 建立四项 PRD 指标的可复现 benchmark：Editor 热路径、CLI 单文件、100 文件批量、单属性类型推断。
2. 分离冷启动与热分析：规则/函数装载、ANTLR 初始化、首个 AST 与后续增量分析分别计时。
3. Editor 将全量重建优化为后台执行、缓存复用或基于 PSI 变更范围的增量分析。
4. LSP 先测量 Full Sync + 300ms debounce 在真实主题文件上的 P50/P95，再决定增量同步和 AST 复用。
5. 为规则库、函数库和符号表建立明确缓存失效协议，避免“常驻缓存”与“热更新”相互冲突。

### 9.3 扩展性方向

1. 规则/函数热更新与校验：文件监听、原子替换、错误回退、版本标识。
2. Analyzer/FixActionGenerator 实例级注册或 ServiceLoader，减少静态全局状态。
3. 统一诊断契约：Rule ID、severity、source range、fix intent 和文档链接在 CLI、Editor、LSP 间一致。
4. 作用域感知的符号解析：复用 Core SymbolTable 支撑 Editor 与未来 LSP 导航。
5. 规则数据质量工具：schema 校验、重复 ID、失效 URL、缺失描述和约束语法检查。

### 9.4 测试覆盖方向

1. 将本轮 4 个测试期望调整整理为正式提交前的独立测试变更，避免与生产修复混杂。
2. 为所有 CLI 模式和参数增加“输入—输出—退出码”黄金契约。
3. 为 Editor 引入 IntelliJ Platform fixture 测试，覆盖补全、文档、Annotator、注入、导航与 Rename。
4. 为 LSP 增加 semantic tokens、JSON-RPC 生命周期、配置热更新、多文件和通用编辑器集成测试。
5. 固化真实 JAR E2E：版本化 fixture、机器可读 answer key、strict/partial/FP/FN 统一分母。
6. 增加 clean、negative、边界、性能和异常注入 fixture，防止只验证“能报错”而忽略误报与失败语义。

## 10. CI/CD 与发布治理（辅助项）

CI/CD 不是当前技术总结的主轴，但应作为已完成能力的自动化承载：

- 已有本地构建约束：`feature/analysis/build.gradle:60-99` 会检查 Core 不引入 IntelliJ SDK；`buildFatJar` 和 Gradle test 已存在。
- 当前仓库无 `.github/` 工作流，也未发现 coverage、Plugin Verifier、性能基准或自动 Release。
- 建议最小门禁仅包含：Java 17/Gradle 8.2 构建、777 tests、真实 JAR smoke、Core 隔离检查、Plugin Verifier、版本一致性检查。
- 性能和 E2E 匹配率应先稳定口径，再决定是否设为阻断门禁。

## 11. 证据边界

- 本总结的主线代码判断基于 `main@e9e9bcd`。
- 本轮 Gradle 全绿包含 4 个未提交的测试期望调整；生产代码没有因此被修改。
- 71 suites / 777 tests 来自本轮 `feature/analysis/build/test-results/test/TEST-*.xml` 与 `build/reports/tests/test/index.html`。
- 本轮已直接使用当前 fat JAR 重跑 `e2e-pipeline` 和演示 fixture；历史 14 bug + 1 clean 的完整逐文件归档仍来自 `docs/bugfix-summary.md:514-572`。两者与 777 个 JUnit 是不同层级证据。
- LSP 结论基于 `origin/lsp-server@864a233`，该模块不在 main 工作树内，也没有在本轮重新构建。
- PRD 性能目标尚未实测，因此本总结不作任何性能达标声明。
- 当前本地测试文件改动不应随报告产物自动提交；是否整理为正式测试提交由项目负责人决定。
