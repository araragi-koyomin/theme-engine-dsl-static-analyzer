---
module_ids: [M3, M4, M5, M7, CLI]
phase: P0
doc_kind: spec
status: active
created: 2026-07-14
---
# P0 Beta 闭环修复 — PHASE 1 需求澄清

> 阶段：PHASE 1（需求澄清）
> 状态：待用户确认
> 分支：`feature/p0-bugfix`

## 1. 背景

当前 main 分支的 CLI/分析管线存在 4 项 P0 级契约性缺陷：

1. **SyntaxChecker 未接入生产链**：`DiagnosticProviderImpl.analyze()` 只调 `ExpressionSyntaxChecker`，未调 `SyntaxChecker`，导致 SYN-001（根元素）/SYN-003（未知标签）/SYN-004（未知属性）在生产链不产出。
2. **FixActionRegistry 未生产初始化**：`CliMain.run()` 构造了 `QuickFixProviderImpl` 但未调 `FixActionRegistry.init()`，CLI JSON 报告的 `suggestedFixes` 为空数组，违背 PRD §2.1.5。
3. **CLI 参数语义未落实**：`--syntax-only`/`--semantic-only`/`--no-type-check`/`--quiet`/`--verbose` 均只解析存配置，未真正控制 Analyzer 行为/过滤/输出。
4. **内部异常被吞**：`BatchInspectionRunnerImpl.analyzeFile()` 对 AST/诊断/修复异常降级为空列表，`ExitCodeCalculator` 只看 errorCount，内部异常仍可能呈现为退出码 0。

这些缺陷导致 CLI 交付与 PRD/架构文档不一致，CI/CD 无法可靠依赖退出码与输出。

## 2. 目标

修复 4 项 P0 缺陷，使 CLI 的 6 种模式（FULL、--syntax-only、--semantic-only、--no-type-check、--quiet、--verbose）行为与 PRD §2.1 一致，内部异常不再被静默吞掉。

## 3. 范围

### 包含

| P0 项 | 范围 |
|---|---|
| P0-1 | SyntaxChecker 接入 DiagnosticProvider 生产链；`--syntax-only` 只跑 M3 层（SyntaxChecker + ExpressionSyntaxChecker） |
| P0-1a | **规则分类修正**：`rule_sources.json` 中 SYN-EXPR-001~006/ANTLR 的 category 从 `"SEM"` 改为 `"SYN"`（与 rule ID 前缀和代码实现一致——均为纯语法检查） |
| P0-1b | **SEM-TYPE-003 归属修正**：移除 `ExpressionSyntaxChecker`（M3）产出 SEM-TYPE-003 的分支（`ExpressionSyntaxChecker.java:122-125`），统一由 `TypeAnalyzer`（M4）产出。M3 不再产出任何 SEM-* 规则 |
| P0-2 | `FixActionRegistry.init(ruleRepo)` 在 CliMain 生产路径调用 |
| P0-3 | 5 个 CLI 参数真实语义落实（详见下方"模式矩阵"） |
| P0-4 | BatchInspectionRunnerImpl 不再吞异常；ExitCodeCalculator 返回 2 表示内部错误 |

### 不包含

- LSP 相关（独立并行线，不阻塞 main）
- Editor/Plugin 层（Quick Fix UI、ToolWindow、右键批量检查——P1 范畴）
- 规则/函数库热更新（P2 范畴）
- AnalyzerRegistry 的 static 全局状态重构（P2 范畴，当前仅在其上加过滤逻辑）

## 4. 模式矩阵（需求澄清结论）

| 模式 | M3 SyntaxChecker (SYN-001/003/004) | M3 ExpressionSyntaxChecker (SYN-EXPR-*) | M4 Analyzer (SEM-NEST/REQ/SCOPE/ENUM/REF/...) | M4 ConstraintAnalyzer (SEM-ATTR/IMG/PERSIST/...) | M4 TypeAnalyzer (SEM-TYPE-*) |
|---|---|---|---|---|---|
| **FULL（默认）** | ✓ | ✓ | ✓ | ✓ | ✓ |
| **--syntax-only** | ✓ | ✓ | ✗ | ✗ | ✗ |
| **--semantic-only** | ✗ | ✗ | ✓ | ✓ | ✗ |
| **--no-type-check**（在 FULL 基线上） | ✓ | ✓ | ✓ | ✓ | ✗ |

> **关键决策（已确认）**：
> 1. `--syntax-only` 仅跑 M3 层，不含 M4 语义 analyzer。PRD 把"嵌套错误"归为语法，但代码实现为 SEM-NEST-001（M4 语义，需规则库 allowedParents）。以代码实际职责分离为准，嵌套归 `--semantic-only`。
> 2. SYN-EXPR-001~006/ANTLR 的 rule ID 前缀为 `SYN-EXPR`，实际检查均为纯语法（AST 结构/字面量词法/字符模式/parser 错误），`rule_sources.json` 中 category 从 `"SEM"` 修正为 `"SYN"`。
> 3. SEM-TYPE-003 原被 ExpressionSyntaxChecker（M3）和 TypeAnalyzer（M4）双来源产出。移除 M3 的产出分支，统一归 M4 TypeAnalyzer。M3 不再产出任何 SEM-* 规则。

### 输出控制

| 参数 | 行为 |
|---|---|
| `--quiet` | JSON/Terminal/Markdown 输出仅含 ERROR 级诊断，WARNING/INFO 被过滤 |
| `--verbose` | 额外输出 5 项（详见下方） |

### `--verbose` 输出内容（已确认）

| # | 输出项 | 格式示例 |
|---|---|---|
| E | **类型推断链**（必须） | 每个表达式属性：`attr x="ifelse(...)" → inferred: number, expected: number, match: OK` |
| A | **管线阶段耗时** | `[verbose] AST build: 5ms, semantic analysis: 12ms, type inference: 3ms` |
| B | **AST 统计** | `[verbose] AST: 24 elements, 87 attributes, 15 expressions` |
| C | **符号表摘要** | `[verbose] Symbols: 8 globals, 3 user vars, 0 duplicates` |
| D | **每 analyzer 诊断计数** | `[verbose] Diagnostics: SyntaxErrorAnalyzer=2, TypeAnalyzer=5, ConstraintAnalyzer=3, ...` |

## 5. 约束

- Core 层无 `com.intellij` import（编译期 `checkCoreIntellijDependency` 强制）
- 无新引入依赖（仅用已有 JUnit5 + GSON + Lombok）
- 不修改 golden schema，但需更新 golden 内容以反映修正后的行为（P0-1 会使部分 fixture 新增 SYN 诊断）
- 每项修复后全量门禁必须全绿：`./gradlew --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e`

## 6. 验收标准（每条可测试）

| AC # | 验收标准 | 测试方式 |
|---|---|---|
| AC-1 | FULL 模式产出 SYN-001/003/004 诊断（SyntaxChecker 接入） | L3 golden：现有 fixture golden 新增 SYN 条目，GoldenDiagnosticMatchTest 通过 |
| AC-2 | `--syntax-only` 模式只产出 SYN-* 前缀诊断（SYN-001/003/004 + SYN-EXPR-001~006/ANTLR），不产出任何 SEM-* 诊断 | 新增 mode 专项 fixture + golden：expectedDiagnostics 仅含 SYN-*，SEM-* count=0 |
| AC-3 | `--semantic-only` 模式不产出 SYN-* 前缀诊断，产出 SEM-* 但不含 SEM-TYPE-* | 新增 mode 专项 fixture + golden：expectedDiagnostics 仅含 SEM-*（非 TYPE），SYN-* count=0 |
| AC-4 | `--no-type-check` 模式不产出 SEM-TYPE-* 诊断，其余诊断不变 | golden 对比：同一 fixture 的 SEM-TYPE-* count 从 N→0，其余不变 |
| AC-5 | `--quiet` 模式 JSON/Terminal/Markdown 输出仅含 ERROR 级诊断 | CliMainE2ETest：`--quiet` 模式 stdout 无 warning/info 级别诊断 |
| AC-6 | `--verbose` 模式输出含类型推断链 + 耗时 + AST 统计 + 符号表摘要 + 每 analyzer 计数 | CliMainE2ETest：`--verbose` 模式 stdout 含 `[verbose]` 前缀的 5 类信息 |
| AC-7 | CLI JSON 报告的 suggestedFixes 字段非空（FixActionRegistry 初始化） | CliMainE2ETest：`--format json` 输出至少一条诊断的 suggestedFixes 数组非空 |
| AC-8 | AST 构建/诊断/修复发生内部异常时退出码=2 | 新增异常注入 fixture + CliMainE2ETest：退出码断言=2 |
| AC-9 | `rule_sources.json` 中 SYN-EXPR-* 的 category 为 `"SYN"`（非 `"SEM"`） | 单元测试：解析 rule_sources.json，断言 SYN-EXPR-* category=="SYN" |
| AC-10 | ExpressionSyntaxChecker（M3）不产出任何 SEM-* 规则（SEM-TYPE-003 分支已移除） | 单元测试：对含字符串字面量的 number 属性跑 ExpressionSyntaxChecker，断言无 SEM-TYPE-003 |
| AC-11 | 全量门禁全绿 | `./gradlew --no-daemon clean test buildFatJar e2e checkCoreIntellijDependency` BUILD SUCCESSFUL |

---

> **阶段切换**：PHASE 1 完成。请用户确认以上需求文档，确认后进入 PHASE 2（规格定义）。
