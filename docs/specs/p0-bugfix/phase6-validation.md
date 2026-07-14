# P0 Beta 闭环修复 — PHASE 6 一致性验证

> 阶段：PHASE 6（一致性验证）
> 依据：`docs/specs/p0-bugfix/phase2-spec.md`（SPEC）+ `docs/specs/p0-bugfix/phase3-design.md`（设计）

## 1. 全量测试结果

| 指标 | 结果 |
|---|---|
| Test suites | 84 |
| Tests | 948 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 35（L4 FatJarSubprocessE2ETest 在 `test` task 中 Assumption 跳过；`e2e` task 中实跑 33/33 绿） |
| Core 隔离 | checkCoreIntellijDependency: PASSED (0 violations) |
| Fat jar 装配 | buildFatJar: SUCCESS |
| L4 子进程 E2E | e2e: 33/33 PASS |
| 全量门禁 | BUILD SUCCESSFUL (29s) |

**编译告警**: 0 warnings。有 2 条 deprecation NOTE（`注: 某些输入文件使用或覆盖了已过时的 API`），属信息提示非告警。

**行覆盖率**: 项目未配置 JaCoCo/coverage 插件，无法自动测量行覆盖率。**此为门禁基础设施缺口**，建议后续补充。

---

## 2. SPEC → 测试覆盖逐项核对

### SPEC-1: DiagnosticProvider 模式感知分析

| 契约要素 | 测试 | 状态 |
|---|---|---|
| FULL 模式产出 SYN-001/003/004 | GoldenDiagnosticMatchTest（5 fixture golden 含 SYN-004） | ✅ |
| SYNTAX_ONLY 仅产出 SYN-* | ModeGoldenTest.syntax_only_test（断言无 SEM-*） | ✅ |
| SEMANTIC_ONLY 仅产出 SEM-* | ModeGoldenTest.semantic_only_test（断言无 SYN-*） | ✅ |
| FULL + typeCheck=false 排除 SEM-TYPE-* | DiagnosticProviderModeTest（TS-5.1） | ✅ |
| DiagnosticProvider 6-arg 签名 | DiagnosticProviderModeTest + 8 个现有测试更新 | ✅ |
| BatchInspectionRunnerImpl 传 mode+config | BatchInspectionRunnerImplTest + ModeTest | ✅ |

**覆盖完整。**

### SPEC-2: rule_sources.json category 修正

| 契约要素 | 测试 | 状态 |
|---|---|---|
| SYN-EXPR-* category == "SYN" | RuleSourceCategoryTest（7 条断言） | ✅ |
| SYN-001/003/004 category 不变 | RuleSourceCategoryTest（2 条断言） | ✅ |
| SEM-TYPE-* category 不变 | RuleSourceCategoryTest（1 条断言） | ✅ |

**覆盖完整。**

### SPEC-3: ExpressionSyntaxChecker 不产出 SEM-*

| 契约要素 | 测试 | 状态 |
|---|---|---|
| ESC.check() 输出仅含 SYN-EXPR-* | ExpressionSyntaxCheckerNoSemTest | ✅ |
| SEM-TYPE-003 分支已移除 | ExpressionSyntaxCheckerNoSemTest（断言无 SEM-TYPE-003） | ✅ |
| FULL 模式仍捕获 SEM-TYPE-003（由 TypeAnalyzer） | DiagnosticProviderModeTest（FULL + typeCheck=true 含 SEM-TYPE） | ✅ |

**覆盖完整。**

### SPEC-4: FixActionRegistry 生产初始化

| 契约要素 | 测试 | 状态 |
|---|---|---|
| CliMain.run() 调 FixActionRegistry.init() | CliMainE2ETest.jsonOutput_suggestedFixes_nonEmpty | ✅ |
| JSON suggestedFixes 非空 | CliMainE2ETest.jsonOutput_suggestedFixes_nonEmpty | ✅ |
| Golden expectedFixes 精确匹配 | GoldenMatcherExpectedFixesTest（7 tests） | ✅ 框架就绪 |
| **Golden 文件实际写入 expectedFixes** | **无——现有 golden 文件均无 expectedFixes 字段** | **❌ 缺口 GAP-1** |

### SPEC-5: TypeAnalyzer 按 config 过滤

| 契约要素 | 测试 | 状态 |
|---|---|---|
| typeCheck=false 排除 TypeAnalyzer | DiagnosticProviderModeTest（TS-5.1~5.2） | ✅ |
| SEMANTIC_ONLY 排除 TypeAnalyzer + SyntaxErrorAnalyzer | DiagnosticProviderModeTest（TS-5.3~5.4） | ✅ |
| SYNTAX_ONLY 不跑任何 M4 analyzer | DiagnosticProviderModeTest + ModeGoldenTest | ✅ |

**覆盖完整。**

### SPEC-6: Quiet 输出过滤

| 契约要素 | 测试 | 状态 |
|---|---|---|
| quiet=true 过滤 WARNING/INFO | CliMainE2ETest.quietMode_jsonOutput_containsOnlyErrorSeverity | ✅ |
| quiet=true 时 warnings count=0 | CliMainE2ETest（JSON summary warnings=0） | ✅ |

**覆盖完整。**

### SPEC-7: Verbose 详细输出

| 契约要素 | 测试 | 状态 |
|---|---|---|
| verbose 输出含 [verbose] 前缀 | CliMainE2ETest.verboseMode_output_containsVerboseLines | ✅ |
| A: 管线阶段耗时 | 手动验证：`[verbose] AST build: 54ms` | ✅ |
| **B: AST 统计** | **手动验证：`AST: 0 elements, 0 attributes, 0 expressions`** | **❌ 缺口 GAP-2** |
| **C: 符号表摘要** | **手动验证：`Symbols: 0 globals, 0 user vars, 0 duplicates`** | **❌ 缺口 GAP-3** |
| D: 每 analyzer 诊断计数 | 手动验证：`Diagnostics: ConstraintAnalyzer=6, ...` | ✅ |
| **E: 类型推断链** | **手动验证：`Type inference: (none)`——`recordTypeInference()` 从未在生产代码调用** | **❌ 缺口 GAP-4** |
| **测试验证 5 类内容非空/正确** | **无——verboseMode 测试只验 [verbose] 前缀存在** | **❌ 缺口 GAP-5** |

### SPEC-8: 内部异常不吞 + INTERNAL-*-ERROR 诊断

| 契约要素 | 测试 | 状态 |
|---|---|---|
| AST 异常 → INTERNAL-AST-ERROR | ExceptionHandlingTest（mock AstProvider throws） | ✅ 单元 |
| 诊断异常 → INTERNAL-ANALYZER-ERROR | ExceptionHandlingTest（ThrowingAnalyzer 注入） | ✅ 单元 |
| FileDiagnosticResult.hasInternalError | ExceptionHandlingTest（断言字段） | ✅ |
| **真实管线可达性** | **AstBuilder.getDslAst() 内部 catch 所有异常 → INTERNAL-AST-ERROR 在真实 CLI 不可达** | **⚠️ 偏差 DEV-1** |

### SPEC-9: 内部异常退出码=2

| 契约要素 | 测试 | 状态 |
|---|---|---|
| hasInternalErrors=true → exit code 2 | ExceptionHandlingTest（ExitCodeCalculator 断言） | ✅ |
| 无内部异常 → exit code 0/1 | CliMainE2ETest（现有退出码测试） | ✅ |
| 文件不存在 → exit code 2 | CliMainE2ETest.nonexistentPath_returnsTwo | ✅ |
| 规则加载失败 → exit code 2 | CliMainE2ETest.malformedRuleDirJson_returnsTwo | ✅ |

**覆盖完整。**（注：exit code 2 的真实触发路径是 CliMain 参数/加载错误，不是 INTERNAL-*-ERROR——后者需 mock 注入。这是设计与实现的偏差 DEV-1。）

---

## 3. 设计 → 代码一致性检查

| 设计要素 | 代码实现 | 状态 |
|---|---|---|
| DiagnosticProvider 6-arg 接口 | `DiagnosticProvider.java` — 6-arg ✅ | ✅ 一致 |
| DiagnosticProviderImpl 模式分发 | `analyzeSyntax()` + `analyzeSemantic()` 私有方法 ✅ | ✅ 一致 |
| DiagnosticProviderImplInner 过滤 | 接受 config+mode，过滤 TypeAnalyzer/SyntaxErrorAnalyzer ✅ | ✅ 一致 |
| ExpressionSyntaxChecker 移除 SEM-TYPE-003 | 分支已删除，落入 SYN-EXPR-ANTLR ✅ | ✅ 一致 |
| BatchInspectionRunnerImpl quiet 过滤 | analyzeFile() 中 stream filter ✅ | ✅ 一致 |
| BatchInspectionRunnerImpl 异常不吞 | 3 个 catch 产 INTERNAL-*-ERROR ✅ | ✅ 一致 |
| VerboseCollector 5 record + render | 全部实现 ✅ | ✅ 一致 |
| CliMain FixActionRegistry.init | `FixActionRegistry.init(effectiveRepo)` 在 line 106 ✅ | ✅ 一致 |
| CliMain verbose 输出 | `collector.render()` 在 exportReport 后 ✅ | ✅ 一致 |
| ExitCodeCalculator hasInternalErrors→2 | `compute()` 先检查 ✅ | ✅ 一致 |
| 7-arg 便捷构造器（向后兼容） | 委托到 8-arg with null ✅ | ✅ 一致（设计未提及但合理） |

### 偏差说明

| 偏差 | 描述 | 严重度 | 处理建议 |
|---|---|---|---|
| **DEV-1** | 设计时序图 3.3 暗示"AST 异常 → INTERNAL-AST-ERROR → exit 2"，但 AstBuilder 内部 catch 所有异常建 error node，真实管线中 INTERNAL-AST-ERROR 不可达。malformed XML 优雅处理为 SYN-SAX-001 → exit 1。 | 低 | 设计文档补充说明：INTERNAL-*-ERROR 用于意外异常（bug），非 malformed XML。malformed XML 是已知错误，优雅处理为 SYN-SAX-001。 |
| **DEV-2** | 设计未指定谁调 `recordAstStats()` / `recordSymbolStats()` / `recordTypeInference()`。实现中这 3 个方法从未在生产代码调用。 | **高** | 回 PHASE 3 补设计 → PHASE 5 补实现（Runner 记录 AST 统计，DiagnosticProviderImplInner 记录符号表，TypeAnalyzer 记录类型推断链）。 |
| **DEV-3** | 设计未提及 7-arg 便捷构造器。代码为向后兼容添加。 | 低 | 合理偏差，无需修正。 |

---

## 4. 缺口汇总

| Gap # | 描述 | 影响 SPEC | 严重度 | 修复方式 |
|---|---|---|---|---|
| **GAP-1** | Golden 文件无 expectedFixes 字段——GoldenMatcher 的 fix 精确匹配框架就绪但无实际 golden 数据 | SPEC-4 | 中 | T9: 用 GoldenDumper 重新生成 golden 草稿，对有 fix 的诊断添加 expectedFixes |
| **GAP-2** | `recordAstStats()` 从未在生产代码调用——verbose 输出 AST 统计恒为 0 | SPEC-7 (B) | **高** | 在 Runner.analyzeFile() 中 AST 构建后遍历 AST 统计元素/属性/表达式数，调 `collector.recordAstStats()` |
| **GAP-3** | `recordSymbolStats()` 从未在生产代码调用——verbose 输出符号表统计恒为 0 | SPEC-7 (C) | **高** | 在 DiagnosticProviderImplInner 中 `buildGlobal()` 后统计全局变量/用户 Var/重复声明数，调 `collector.recordSymbolStats()` |
| **GAP-4** | `recordTypeInference()` 从未在生产代码调用——verbose 输出类型推断链恒为 (none) | SPEC-7 (E) | **高** | 在 TypeAnalyzer 的 `checkAttribute()` / `checkVarExpressionBody()` 中，对每个表达式属性记录推断类型→期望类型→匹配结果，调 `collector.recordTypeInference()` |
| **GAP-5** | verbose E2E 测试只验 `[verbose]` 前缀存在，不验 5 类内容非空/正确 | SPEC-7 | **高** | 强化 `verboseMode_output_containsVerboseLines`：断言输出含 "AST:" 后非全 0、含 "Symbols:" 后非全 0、含 "Type inference:" |
| **GAP-6** | 项目无 JaCoCo/coverage 插件——无法测量行覆盖率 | 质量门禁 | 中 | 在 build.gradle 添加 `jacoco` 插件，`test { finalizedBy jacocoTestReport }` |

---

## 5. 质量门禁达成情况

| 指标 | 要求 | 实际 | 达成 |
|---|---|---|---|
| spec 条目测试覆盖率 | 100% | 9/9 SPEC 有对应测试；**SPEC-7 的 B/C/E 三项无有效测试**（GAP-2/3/4/5） | ❌ **未达成** |
| 单元测试通过率 | 100% | 948 tests, 0 failures | ✅ |
| 代码行覆盖率 | > 80% | 无法测量（无 JaCoCo） | ⚠️ **无法判定** |
| 编译告警 | 0 | 0 warnings（2 deprecation NOTE 非告警） | ✅ |
| spec/design/code 一致性 | 无未说明偏差 | 3 处偏差（DEV-1/2/3），DEV-2 未说明 | ❌ **DEV-2 未达成** |

---

## 6. 结论

PHASE 6 发现 **6 个缺口** 和 **3 处设计偏差**，其中 **4 个高严重度缺口**（GAP-2/3/4/5）集中在 SPEC-7 verbose 输出——3/5 的 verbose 方法未接线，且测试太弱未暴露此问题。

**门禁未通过**——spec 条目测试覆盖率未达 100%（SPEC-7 B/C/E 无有效测试），spec/design/code 一致性有未说明偏差（DEV-2）。

**修复计划**：回 PHASE 3 补设计（指定谁调 recordAstStats/recordSymbolStats/recordTypeInference）→ PHASE 5 补实现 + 强化测试 → T9 补 golden expectedFixes → 重新 PHASE 6 验证。

---

> **阶段切换**：PHASE 6 首轮验证完成，门禁未通过。需修复 GAP-1~6 + DEV-2 后重新验证。请用户确认修复计划。
