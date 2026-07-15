---
module_ids: [M3, M4, M5, M7, CLI]
phase: P0
doc_kind: plan
status: active
created: 2026-07-14
---
# P0 Beta 闭环修复 — PHASE 4 任务拆分

> 阶段：PHASE 4（任务拆分）
> 依据：`docs/specs/p0-bugfix/phase2-spec.md`（SPEC）+ `docs/specs/p0-bugfix/phase3-design.md`（设计）

## 任务列表

| Task | 标题 | SPEC | AC | 测试场景 | 依赖 | 估时 |
|---|---|---|---|---|---|---|
| T1 | rule_sources.json category 修正 | SPEC-2 | AC-9 | TS-2.1~2.3 | 无 | 10min |
| T2 | ExpressionSyntaxChecker 移除 SEM-TYPE-003 | SPEC-3 | AC-10 | TS-3.1~3.3 | 无 | 15min |
| T3 | VerboseCollector 新增类 | SPEC-7 | AC-6(部分) | TS-7.1(部分) | 无 | 20min |
| T4 | DiagnosticProvider 接口变更 + 模式分发 + SyntaxChecker 接线 | SPEC-1, SPEC-5 | AC-1~4 | TS-1.1~1.7, TS-5.1~5.4 | T2, T3 | 30min |
| T5 | BatchInspectionRunnerImpl 模式传递 + quiet 过滤 | SPEC-1, SPEC-6 | AC-2~5 | TS-6.1~6.3 | T4 | 25min |
| T6 | FixActionRegistry 生产初始化 + CliMain verbose 输出 | SPEC-4, SPEC-7 | AC-6, AC-7 | TS-4.1~4.2, TS-7.1~7.3 | T3, T5 | 25min |
| T7 | 内部异常不吞 + INTERNAL-*-ERROR 诊断 + 退出码=2 | SPEC-8, SPEC-9 | AC-8 | TS-8.1~8.3, TS-9.1~9.4 | 无 | 25min |
| T8 | Golden 框架扩展 — expectedFixes 精确匹配 | SPEC-4 可测试性 | AC-7 | — | 无 | 20min |
| T9 | Golden 文件更新（SyntaxChecker 接线后） | 全部 | AC-1 | — | T1~T8 | 30min |
| T10 | 模式专项 fixture + golden（syntax-only / semantic-only） | SPEC-1 | AC-2, AC-3 | — | T4, T5, T9 | 20min |
| T11 | CLI 参数 E2E 测试（verbose + quiet + suggestedFixes） | SPEC-4, SPEC-6, SPEC-7 | AC-5~7 | — | T6, T9 | 20min |
| T12 | 异常注入 E2E 测试（退出码=2） | SPEC-8, SPEC-9 | AC-8 | — | T7, T9 | 15min |
| T13 | 全量门禁验证 + 修复残余 | 全部 | AC-11 | — | T1~T12 | 15min |

## 依赖关系图

```
T1 (rule_sources) ──────────────────────────────────┐
T2 (ESC SEM-TYPE-003) ─────┐                        │
T3 (VerboseCollector) ──────┤                        │
                            ▼                        │
                     T4 (DiagnosticProvider)         │
                            │                        │
                            ▼                        │
                     T5 (Runner mode+quiet)          │
                            │                        │
                            ▼                        │
                     T6 (FixActionRegistry+verbose)  │
                            │                        │
T7 (Exception+exitCode) ────┼──────────────────────  │
                            │                        │
T8 (Golden expectedFixes) ──┼──────────────────────  │
                            │                        │
                            ▼                        ▼
                     T9 (Golden 文件更新) ◄─────────┘
                            │
                   ┌────────┼────────┐
                   ▼        ▼        ▼
              T10(mode) T11(E2E) T12(exc E2E)
                   │        │        │
                   └────────┼────────┘
                            ▼
                      T13 (全量门禁)
```

## 各任务详细说明

### T1: rule_sources.json category 修正

- **SPEC**: SPEC-2
- **AC**: AC-9
- **文件**: `feature/analysis/src/main/resources/rules/rule_sources.json`
- **变更**: SYN-EXPR-001~006/ANTLR 的 `category` 字段从 `"SEM"` 改为 `"SYN"`
- **RED**: 写测试 `RuleSourceCategoryTest`：从 classpath 加载 rule_sources.json，断言 SYN-EXPR-* category=="SYN"，SEM-TYPE-* category=="SEM"
- **GREEN**: 改 JSON 数据
- **REFACTOR**: 无

### T2: ExpressionSyntaxChecker 移除 SEM-TYPE-003

- **SPEC**: SPEC-3
- **AC**: AC-10
- **文件**: `ExpressionSyntaxChecker.java`
- **变更**: 移除 `:122-125` 的 `else if ("number".equals(expressionKind))` 分支产出 SEM-TYPE-003。该 parse 失败场景改由 `SYN-EXPR-ANTLR` 兜底（落入 else）
- **RED**: 写测试：对 `x="'hello'"`（number 属性含字符串）跑 ExpressionSyntaxChecker.check()，断言输出无 SEM-TYPE-003
- **GREEN**: 删除 SEM-TYPE-003 分支
- **REFACTOR**: 无

### T3: VerboseCollector 新增类

- **SPEC**: SPEC-7
- **AC**: AC-6（部分——只建类，不接线）
- **文件**: 新建 `feature/analysis/src/main/java/com/huawei/theme/analysis/core/semanticanalysis/VerboseCollector.java`
- **方法**: `recordStageTime`, `recordAstStats`, `recordSymbolStats`, `recordAnalyzerCount`, `recordTypeInference`, `render()`
- **RED**: 写测试 `VerboseCollectorTest`：record 各类数据，断言 `render()` 输出含 `[verbose]` 前缀的 5 类信息
- **GREEN**: 实现类
- **REFACTOR**: 无

### T4: DiagnosticProvider 接口变更 + 模式分发 + SyntaxChecker 接线

- **SPEC**: SPEC-1, SPEC-5
- **AC**: AC-1~4
- **文件**:
  - `DiagnosticProvider.java`（接口签名变更：加 mode+config+collector）
  - `DiagnosticProviderImpl.java`（按 mode 分发；FULL 接线 SyntaxChecker）
  - `DiagnosticProviderImplInner.java`（接受 config+mode；过滤 TypeAnalyzer/SyntaxErrorAnalyzer）
  - 现有测试（更新 analyze() 调用签名）
- **RED**: 写测试 `DiagnosticProviderModeTest`：
  - FULL 模式 + 有未知标签 fixture → 含 SYN-003
  - SYNTAX_ONLY 模式 → 输出仅 SYN-*，无 SEM-*
  - SEMANTIC_ONLY 模式 → 输出仅 SEM-*，无 SYN-*
  - FULL + typeCheck=false → 无 SEM-TYPE-*
- **GREEN**: 实现接口+分发+过滤+接线
- **REFACTOR**: 提取 analyzeSyntax/analyzeSemantic 私有方法

### T5: BatchInspectionRunnerImpl 模式传递 + quiet 过滤

- **SPEC**: SPEC-1, SPEC-6
- **AC**: AC-2~5
- **文件**: `BatchInspectionRunnerImpl.java`
- **变更**:
  - analyzeFile() 传 mode+config+collector 给 DiagnosticProvider.analyze()
  - SYNTAX_ONLY 不再跳过 DiagnosticProvider（改为传 SYNTAX_ONLY mode）
  - quiet=true 时过滤 WARNING/INFO 级诊断
- **RED**: 写测试：
  - SYNTAX_ONLY 模式跑 fixture → SEM-* count=0
  - quiet=true + fixture 含 3E/2W/1I → 仅 3E
- **GREEN**: 实现
- **REFACTOR**: quiet 过滤提为私有方法

### T6: FixActionRegistry 生产初始化 + CliMain verbose 输出

- **SPEC**: SPEC-4, SPEC-7
- **AC**: AC-6, AC-7
- **文件**: `CliMain.java`
- **变更**:
  - 在 `effectiveRepo` 之后调 `FixActionRegistry.init(effectiveRepo)`
  - verbose=true 时创建 VerboseCollector，传给 Runner，输出 render()
- **RED**: 写测试（CliMainE2ETest 扩展）：
  - `--format json` + fixture with SEM-ATTR-001 → JSON suggestedFixes 非空
  - `--verbose` + fixture → stdout 含 `[verbose]` 5 类信息
- **GREEN**: 实现
- **REFACTOR**: 无

### T7: 内部异常不吞 + INTERNAL-*-ERROR 诊断 + 退出码=2

- **SPEC**: SPEC-8, SPEC-9
- **AC**: AC-8
- **文件**:
  - `FileDiagnosticResult.java`（加 hasInternalError）
  - `BatchInspectionResult.java`（加 hasInternalErrors）
  - `BatchInspectionRunnerImpl.java`（异常不吞，产出 INTERNAL-*-ERROR）
  - `ExitCodeCalculator.java`（hasInternalErrors → 2）
- **RED**: 写测试：
  - 损坏 XML fixture → FileDiagnosticResult.hasInternalError=true
  - ThrowingAnalyzer 注入 → INTERNAL-ANALYZER-ERROR 诊断
  - ExitCodeCalculator: hasInternalErrors=true → 2
- **GREEN**: 实现
- **REFACTOR**: 无

### T8: Golden 框架扩展 — expectedFixes 精确匹配

- **SPEC**: SPEC-4 可测试性设计
- **AC**: AC-7
- **文件**:
  - `ExpectedDiagnostic.java`（加 expectedFixes: List<String>）
  - `GoldenMatcher.java`（matches 后加 fix 校验：expectedFixes != null 时断言 actual.suggestedFixes 集合相等）
  - `GoldenExpectationParser.java`（解析 expectedFixes）
- **RED**: 写测试 `GoldenMatcherExpectedFixesTest`：
  - expectedFixes 与 actual.suggestedFixes 匹配 → pass
  - expectedFixes 与 actual 不匹配 → fail（FP/FN diff）
  - expectedFixes=null → 跳过 fix 校验（向后兼容）
- **GREEN**: 实现
- **REFACTOR**: 无

### T9: Golden 文件更新（SyntaxChecker 接线后）

- **SPEC**: 全部
- **AC**: AC-1
- **文件**: `feature/analysis/src/test/resources/fixtures/**/*.expected.json` + `dsl/*.expected.json`
- **步骤**:
  1. 写临时 GoldenDumpRunner，跑 `CliMain.run("--format","json",fixture)`（FixActionRegistry.init 已在 T6 接线）
  2. 用 GoldenDumper 重新生成所有 golden 草稿
  3. 对照 ANSWER_KEY.md 复核：新增 SYN-001/003/004 诊断
  4. 对有 fix 的诊断添加 expectedFixes 字段（精确文本）
  5. 删除临时 runner
  6. 跑 L3 GoldenDiagnosticMatchTest 验证全绿
- **依赖**: T1~T8 全部完成

### T10: 模式专项 fixture + golden

- **SPEC**: SPEC-1
- **AC**: AC-2, AC-3
- **文件**: 新增 `fixtures/mode/` 目录
- **步骤**:
  1. 创建 `syntax_only_test.xml`（含未知标签+嵌套违规+必填缺失+表达式错误）
  2. 创建 `semantic_only_test.xml`（含嵌套违规+类型错误+变量引用错误，无语法错误）
  3. 用 dumper 生成 golden（SYNTAX_ONLY 模式仅 SYN-*，SEMANTIC_ONLY 仅 SEM-*）
  4. 跑 L3 验证
- **依赖**: T4, T5, T9

### T11: CLI 参数 E2E 测试（verbose + quiet + suggestedFixes）

- **SPEC**: SPEC-4, SPEC-6, SPEC-7
- **AC**: AC-5~7
- **文件**: `CliMainE2ETest.java`（扩展）
- **测试**:
  - `--quiet` + fixture → stdout 仅 ERROR 级
  - `--verbose` + fixture → stdout 含 [verbose] 5 类
  - `--format json` + fixture → JSON suggestedFixes 非空且内容正确
- **依赖**: T6, T9

### T12: 异常注入 E2E 测试（退出码=2）

- **SPEC**: SPEC-8, SPEC-9
- **AC**: AC-8
- **文件**: 新增 `fixtures/exception/broken_xml.xml` + `CliMainE2ETest` 扩展
- **测试**:
  - 损坏 XML → 退出码=2
  - 正常 fixture → 退出码=0 或 1
- **依赖**: T7, T9

### T13: 全量门禁验证 + 修复残余

- **SPEC**: 全部
- **AC**: AC-11
- **命令**: `./gradlew --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e`
- **步骤**: 跑全量门禁，修复任何残余失败，确认 BUILD SUCCESSFUL
- **依赖**: T1~T12 全部完成

---

## 优先级与并行度

| 优先级 | 任务 | 可并行? |
|---|---|---|
| P1 | T1, T2, T3, T7, T8 | ✅ 五者无依赖，可并行 |
| P2 | T4 | ❌ 依赖 T2+T3 |
| P3 | T5, T9 | T5 依赖 T4；T9 依赖 T1~T8 |
| P4 | T6, T10, T11, T12 | 部分可并行 |
| P5 | T13 | ❌ 依赖全部 |

---

> **阶段切换**：PHASE 4 完成。请用户确认 task 列表，确认后进入 PHASE 5（TDD 编码实现）。
