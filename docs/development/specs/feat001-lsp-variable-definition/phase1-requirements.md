---
module_ids: [M8]
phase: P1
doc_kind: spec
status: active
created: 2026-07-17
---
# FEAT001 LSP 变量定义跳转 — PHASE 1 需求澄清

> 阶段：PHASE 1（需求澄清）
> 状态：待用户确认
> 分支：`lsp-server`

## 1. 背景

DSL 静态分析器提供两条编辑器集成线：

- **IntelliJ 插件**（`feature/clients/intellij` + `feature/analysis` 的 PSI 层）：已具备变量跳转能力（直接扫描 PSI，见 BACKLOG M6 条目「变量跳转/FindUsages/Rename 已有基础实现」），架构设计见 `docs/architecture/M8-Navigation.md`。
- **LSP / VS Code 插件**（`feature/lsp` + `feature/clients/vscode`）：当前已实现 `textDocument/completion`、`textDocument/hover`、`textDocument/semanticTokens/full`、`textDocument/codeAction`，但 **`textDocument/definition` 完全未实现**——`DslLanguageServer.initialize` 未声明 `definitionProvider`，`DslTextDocumentService` 无 `definition` 方法。

结果：VS Code 中对 DSL 表达式里的变量引用（`#varName` / `@varName`）按 F12 或 Ctrl+Click 无任何跳转反应，用户无法从引用跳到 `<Var name="varName" .../>` 声明处。

## 2. 目标

为 LSP 服务端实现 `textDocument/definition`，使 VS Code（及其它标准 LSP 客户端）支持：

> 光标位于表达式属性值中的变量引用 `#varName` 或 `@varName` 上 → 触发 Go to Definition（F12 / Ctrl+Click）→ 跳转到**当前文件内**该变量的 `<Var name="varName">` 声明处。

## 3. 范围

### 包含

| 项 | 范围 |
|---|---|
| F-1 | LSP 服务端新增 `DefinitionProvider`，输入 `ContextResolver.Context`（复用现有上下文解析）+ `DslFileNode` AST + 文档文本，输出零或一个 `Location` |
| F-2 | `DslTextDocumentService` 实现 `textDocument/definition(DefinitionParams)`，复用 `AstContextResolver`/`ContextResolver` 解析光标上下文 |
| F-3 | `DslLanguageServer.initialize` 声明 `definitionProvider = true` |
| F-4 | 触发条件：光标在**表达式属性值内**的 `#varName` / `@varName` token 上（由 `AstContextResolver` 解析为 `ExpressionNode.variableRef` / `arrayAccess`，`ctx.exprNode != null`） |
| F-5 | 跳转目标：当前文件 AST 中第一个 `category == "variable"` 且 `name` 属性 `rawValue == varName` 的元素，Range 精确到 **`name` 属性值的变量名文本**（非整个 Var 元素） |
| F-6 | 全局变量（`global_vars.json` 内置，如 `#system.time.hour`）文件内无定义 → 返回**空 Location 列表**（不报错、不跳转） |
| F-7 | 同名重复 Var 声明 → 返回**第一个**匹配（文档顺序最前） |
| F-8 | 作用域：**仅当前文件内**查找，不跨文件 |

### 不包含

- **定义 → 引用** 反向跳转：属 `textDocument/references` 语义，不在 definition 范畴
- **VarArray / Vars 数组容器声明**的跳转：`VarArray`/`Vars` 无 `name` 属性（数组名归属待 DSL 语义进一步澄清），本期不处理；`#arr[0]` 形式的数组访问仍跳到同名 `<Var name="arr">`（若存在）
- **跨文件跳转**：当前 LSP 为单文件语义，无 workspace 索引
- **IntelliJ 端**：已由 PSI 层独立实现，本 FEAT 不涉及
- **声明处反向跳引用、Rename 重构**：属 M8 Extension 层，后续 FEAT

## 4. 关键设计决策（已与用户确认）

> 以下 5 点为 PHASE 1 澄清结论，用户已认可默认方案：

1. **跳转方向**：仅「引用 → 定义」（F12 / Ctrl+Click）。反向（定义→引用）属 references，不做。
2. **目标精度**：光标落到 `name` 属性值里的变量名文本上，而非 Var 元素的 `<` 处（更精确，符合 VS Code 跳转后高亮变量名的直觉）。
3. **全局变量**：文件内无定义 → 返回**空**（`null` 或空 List），不报错。
4. **同名重复声明**：返回**第一个**匹配（文档顺序最前，语义上层级最近）。
5. **作用域**：**单文件**内查找，不跨文件。

## 5. 约束

- 仅用现有依赖（lsp4j 0.21.2、JUnit5、GSON、Lombok），**无新引入依赖**
- 复用现有基础设施：`AstContextResolver`（AST 精确上下文）、`ContextResolver`（文本兜底）、`PositionMapper`（核心坐标→LSP 位置）、`AnalysisService.parse`（取 AST）、`RuleRepository.getElementRule`（识别 variable category）
- 遵循 `.opencode/skills/java-code-style/SKILL.md`：final 类、私有构造、不可变返回、无 public 静态方法滥用
- LSP 坐标约定：核心 AST 节点 `line` 1-based / `column` 0-based，经 `PositionMapper.toPosition(line, column)` 转 LSP 0-based line / 0-based char
- 不修改 `DslAstNode`/`DslElementNode`/`DslAttributeValueNode` 等核心数据结构（位置信息已具备）
- 全量门禁必须全绿：`./gradlew --no-daemon :feature:lsp:test :feature:analysis:test`

## 6. 验收标准（每条可测试）

| AC # | 验收标准 | 测试方式 |
|---|---|---|
| AC-1 | `DslLanguageServer.initialize` 返回的 `ServerCapabilities.definitionProvider` 为 `true` | 单元测试：构造 server，调 initialize，断言 capabilities.getDefinitionProvider().getLeft() == true |
| AC-2 | 光标在 `#varName` 引用上（已声明的 Var）→ `definition` 返回一个 Location，uri 为当前文件，Range 起始位于 Var 的 `name` 属性值变量名文本处 | 单元测试：DefinitionProviderTest，构造 AST + Context，断言 Location 的 Range 与 name 属性值区间一致 |
| AC-3 | 光标在 `@varName` 引用上（字符串变量引用）→ 与 `#varName` 行为一致，跳转到同名 Var 声明 | 单元测试：同 AC-2，前缀换 `@` |
| AC-4 | 光标在 `#unknown` 上（无对应 Var 声明）→ `definition` 返回空（null 或空列表），不抛异常 | 单元测试：未声明变量引用 → assertNull / assertTrue(list.isEmpty()) |
| AC-5 | 全局变量引用（如 `#system.time.hour`，ruleRepository.getGlobalVar 命中）→ 返回空（文件内无定义） | 单元测试：全局变量引用 → 空 |
| AC-6 | 同名 Var 声明出现两次 → 返回**第一个**（文档顺序最前）的 Location | 单元测试：两个同名 Var，断言返回的 Range == 第一个 Var 的 name 值区间 |
| AC-7 | 光标不在表达式 token 上（如在标签名、属性名、文本内容处）→ `definition` 返回空，不抛异常 | 单元测试：各类非引用上下文 → 空 |
| AC-8 | `DslTextDocumentService.definition` 在文档未打开（documents.get 返回 null）时返回空，不抛异常 | 单元测试：未 open 的 uri → 空 |
| AC-9 | 数组访问 `#arr[0]` 引用 → 跳转到 `<Var name="arr">`（若存在） | 单元测试：ARRAY_ACCESS 的 exprNode → 跳到 name="arr" |
| AC-10 | 全量门禁全绿 | `./gradlew --no-daemon :feature:lsp:test :feature:analysis:test` BUILD SUCCESSFUL |

---

> **阶段切换**：PHASE 1 完成。请用户确认以上需求文档，确认后进入 PHASE 2（规格定义）。
