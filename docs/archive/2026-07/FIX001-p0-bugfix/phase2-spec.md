---
module_ids: [M3, M4, M5, M7, CLI]
phase: P0
doc_kind: spec
status: archived
created: 2026-07-14
---
# P0 Beta 闭环修复 — PHASE 2 规格定义

> 阶段：PHASE 2（规格定义）
> 状态：待用户确认
> 依据：`docs/specs/p0-bugfix/phase1-requirements.md`

## 契约总览

| SPEC # | 对应 P0 项 | 契约名 | 涉及接口/类 |
|---|---|---|---|
| SPEC-1 | P0-1 | DiagnosticProvider 模式感知分析 | `DiagnosticProvider`, `DiagnosticProviderImpl`, `BatchInspectionRunnerImpl` |
| SPEC-2 | P0-1a | rule_sources.json 规则分类修正 | `rule_sources.json` |
| SPEC-3 | P0-1b | ExpressionSyntaxChecker 不产出 SEM-* | `ExpressionSyntaxChecker` |
| SPEC-4 | P0-2 | FixActionRegistry 生产初始化 | `CliMain`, `FixActionRegistry` |
| SPEC-5 | P0-3 | TypeAnalyzer 按 config 过滤 | `AnalyzerRegistry`, `DiagnosticProviderImpl` |
| SPEC-6 | P0-3 | Quiet 输出过滤 | `ReportExporterImpl`, `BatchInspectionRunnerImpl` |
| SPEC-7 | P0-3 | Verbose 详细输出 | `CliMain` |
| SPEC-8 | P0-4 | 内部异常不吞 + 诊断产出 | `BatchInspectionRunnerImpl`, `FileDiagnosticResult` |
| SPEC-9 | P0-4 | 内部异常退出码=2 | `BatchInspectionResult`, `ExitCodeCalculator` |

---

## SPEC-1：DiagnosticProvider 模式感知分析

### 接口签名

```java
public interface DiagnosticProvider {
    List<Diagnostic> analyze(
        DslFileNode ast,
        RuleRepository ruleRepo,
        SymbolTableBuilder symbolTableBuilder,
        PipelineMode mode,
        InspectionConfig config
    );
}
```

### 输入参数

| 参数 | 类型 | 约束 |
|---|---|---|
| `ast` | `DslFileNode` | 非空；rootElement 可为 null（XML 解析失败时） |
| `ruleRepo` | `RuleRepository` | 非空；ConfigAwareRuleRepository 包装 |
| `symbolTableBuilder` | `SymbolTableBuilder` | 非空 |
| `mode` | `PipelineMode` | `FULL` / `SYNTAX_ONLY` / `SEMANTIC_ONLY` 之一 |
| `config` | `InspectionConfig` | 非空；`typeCheck` 字段控制 TypeAnalyzer |

### 输出保证

| 模式 | 产出内容 | 不产出 |
|---|---|---|
| `FULL` + `typeCheck=true` | M4 全部 analyzer + SyntaxChecker(SYN-001/003/004) + ExpressionSyntaxChecker(SYN-EXPR-*) | — |
| `FULL` + `typeCheck=false` | M4 analyzer 除 TypeAnalyzer + SyntaxChecker + ExpressionSyntaxChecker | SEM-TYPE-* |
| `SYNTAX_ONLY` | SyntaxChecker + ExpressionSyntaxChecker | 任何 SEM-* |
| `SEMANTIC_ONLY` + `typeCheck=true` | M4 analyzer 除 SyntaxErrorAnalyzer 外的全部（含 ConstraintAnalyzer） | SYN-* |
| `SEMANTIC_ONLY` + `typeCheck=false` | M4 analyzer 除 TypeAnalyzer + SyntaxErrorAnalyzer | SYN-*, SEM-TYPE-* |

> **注意**：`SyntaxErrorAnalyzer`（M4 中处理 `SYN-SAX-001` 的 analyzer）归属语义模式还是语法模式？此 analyzer 处理 XML 解析失败产生的 hasError 节点，属于语法层。`SEMANTIC_ONLY` 模式应跳过它。

### 后置条件

- `SYNTAX_ONLY` 输出的所有 Diagnostic 的 ruleId 以 `SYN-` 前缀开头
- `SEMANTIC_ONLY` 输出的所有 Diagnostic 的 ruleId 以 `SEM-` 前缀开头
- `FULL` 输出无前缀限制

### 异常

- `ruleRepo` 为 null → 抛 `NullPointerException`
- AST rootElement 为 null 或 hasError → 返回空列表（XML 格式错误由上层处理）

### 测试场景

| 场景 | 输入 | 期望输出 |
|---|---|---|
| TS-1.1 | FULL 模式 + 有未知标签的 fixture | 含 SYN-003 诊断 |
| TS-1.2 | FULL 模式 + 有未知属性的 fixture | 含 SYN-004 诊断 |
| TS-1.3 | SYNTAX_ONLY 模式 + 有 SEM-NEST-001 违规的 fixture | 不含 SEM-NEST-001 |
| TS-1.4 | SYNTAX_ONLY 模式 + 有 SYN-003 的 fixture | 含 SYN-003 |
| TS-1.5 | SEMANTIC_ONLY 模式 + 有 SYN-003 的 fixture | 不含 SYN-003 |
| TS-1.6 | SEMANTIC_ONLY 模式 + 有 SEM-NEST-001 的 fixture | 含 SEM-NEST-001 |
| TS-1.7 | SEMANTIC_ONLY + typeCheck=false + 有 SEM-TYPE-001 的 fixture | 不含 SEM-TYPE-001 |

---

## SPEC-2：rule_sources.json 规则分类修正

### 数据契约

`feature/analysis/src/main/resources/rules/rule_sources.json` 中以下条目的 `category` 字段从 `"SEM"` 改为 `"SYN"`：

| ruleId | 原 category | 修正后 |
|---|---|---|
| SYN-EXPR-001 | SEM | **SYN** |
| SYN-EXPR-002 | SEM | **SYN** |
| SYN-EXPR-003 | SEM | **SYN** |
| SYN-EXPR-004 | SEM | **SYN** |
| SYN-EXPR-005 | SEM | **SYN** |
| SYN-EXPR-006 | SEM | **SYN** |
| SYN-EXPR-ANTLR | SEM | **SYN** |

### 后置条件

- `RuleRepository.getRuleSource("SYN-EXPR-001").getCategory()` 返回 `"SYN"`
- 其余规则（SYN-001/003/004、SEM-*）的 category 不变

### 测试场景

| 场景 | 输入 | 期望 |
|---|---|---|
| TS-2.1 | 从 classpath 加载 rule_sources.json | SYN-EXPR-001~006/ANTLR 的 category 全部为 "SYN" |
| TS-2.2 | 从 classpath 加载 rule_sources.json | SYN-001/003/004 的 category 仍为 "SYN" |
| TS-2.3 | 从 classpath 加载 rule_sources.json | SEM-TYPE-001/002/003 的 category 仍为 "SEM" |

---

## SPEC-3：ExpressionSyntaxChecker 不产出 SEM-*

### 接口契约

```java
public class ExpressionSyntaxChecker {
    public List<Diagnostic> check(String filePath, DslFileNode fileNode);
}
```

### 后置条件

- `check()` 返回的所有 Diagnostic 的 ruleId **仅**为以下之一：
  - `SYN-EXPR-001`, `SYN-EXPR-002`, `SYN-EXPR-003`, `SYN-EXPR-004`, `SYN-EXPR-005`, `SYN-EXPR-006`, `SYN-EXPR-ANTLR`
- **不产出** `SEM-TYPE-003`（原 `ExpressionSyntaxChecker.java:122-125` 分支移除）

### 变更说明

原代码在 `isStringExpr && parseFailed && expressionKind=="number"` 时产出 `SEM-TYPE-003`。此分支移除后，该场景的 parse 失败改由 `SYN-EXPR-ANTLR` 兜底（落入 `else` 分支），或由 `TypeAnalyzer` 的 `checkStringLiteralInNumExpr` 在 M4 层捕获。

### 测试场景

| 场景 | 输入 | 期望 |
|---|---|---|
| TS-3.1 | 属性 x="'hello'"（期望 number, 值为字符串字面量）跑 ExpressionSyntaxChecker | 不含 SEM-TYPE-003；含 SYN-EXPR-ANTLR 或空 |
| TS-3.2 | 属性 x="'hello'" 跑 FULL 模式 DiagnosticProvider | 含 SEM-TYPE-003（由 TypeAnalyzer 产出） |
| TS-3.3 | 任意 fixture 跑 ExpressionSyntaxChecker | 输出仅含 SYN-EXPR-* ruleId |

---

## SPEC-4：FixActionRegistry 生产初始化

### 接口契约

`CliMain.run()` 在构造 `BatchInspectionRunnerImpl` 之前，必须调用：

```java
FixActionRegistry.init(effectiveRepo);
```

### 前置条件

- `effectiveRepo` 已完成 `ConfigAwareRuleRepository` 包装
- `FixActionRegistry.init()` 尚未被调用（`initialized == false`），或已调用但幂等（`init` 内部有 `if (initialized) return` 保护）

### 后置条件

- `FixActionRegistry.getGenerator(ruleId)` 对已注册的 ruleId 返回非空 Optional
- CLI `--format json` 输出中，至少一条诊断的 `suggestedFixes` 数组非空

### 异常

- `FixActionRegistry.init(null)` → 抛 NPE（init 内部 new generators 时访问 ruleRepo）

### 测试场景

| 场景 | 输入 | 期望 |
|---|---|---|
| TS-4.1 | CliMain.run("--format","json",fixture_with_SEM_ATTR_001) | JSON 输出中 SEM-ATTR-001 诊断的 suggestedFixes 非空 |
| TS-4.2 | CliMain.run("--format","json",clean_fixture) | 无诊断时 suggestedFixes 为空数组（无诊断可修） |

---

## SPEC-5：TypeAnalyzer 按 config 过滤

### 接口契约

`DiagnosticProviderImpl` 内部调用 analyzer 时，根据 `config.typeCheck` 和 `mode` 过滤 analyzer 列表：

```java
List<DslAnalyzer> analyzers = AnalyzerRegistry.getAnalyzers();
if (mode == SYNTAX_ONLY) {
    analyzers = List.of();  // SYNTAX_ONLY 不跑任何 M4 analyzer
} else if (!config.isTypeCheck() || mode == SEMANTIC_ONLY) {
    // SEMANTIC_ONLY 或 no-type-check 时移除 TypeAnalyzer
    analyzers = analyzers.filter(a -> !(a instanceof TypeAnalyzer));
}
// SEMANTIC_ONLY 还需移除 SyntaxErrorAnalyzer（处理 SYN-SAX-001 的 M4 analyzer）
if (mode == SEMANTIC_ONLY) {
    analyzers = analyzers.filter(a -> !(a instanceof SyntaxErrorAnalyzer));
}
```

> **设计决策**：不在 `AnalyzerRegistry` 加 `getAnalyzers(config)` 方法（避免给 static 全局类加 config 依赖，属 P2 重构范畴），而在 `DiagnosticProviderImpl` 内部过滤。`AnalyzerRegistry.getAnalyzers()` 签名不变。

### 后置条件

- `SYNTAX_ONLY`：不跑任何 M4 analyzer
- `SEMANTIC_ONLY`：跑 M4 analyzer 除 TypeAnalyzer 和 SyntaxErrorAnalyzer
- `FULL` + `typeCheck=false`：跑 M4 analyzer 除 TypeAnalyzer
- `FULL` + `typeCheck=true`：跑全部 M4 analyzer

### 测试场景

| 场景 | 输入 | 期望 |
|---|---|---|
| TS-5.1 | FULL + typeCheck=false + 有 SEM-TYPE-001 的 fixture | 不含 SEM-TYPE-001 |
| TS-5.2 | FULL + typeCheck=true + 有 SEM-TYPE-001 的 fixture | 含 SEM-TYPE-001 |
| TS-5.3 | SEMANTIC_ONLY + 有 SEM-TYPE-001 的 fixture | 不含 SEM-TYPE-001 |
| TS-5.4 | SEMANTIC_ONLY + 有 SEM-NEST-001 的 fixture | 含 SEM-NEST-001 |

---

## SPEC-6：Quiet 输出过滤

### 接口契约

当 `config.quiet == true` 时，`BatchInspectionRunnerImpl.analyzeFile()` 返回的 `FileDiagnosticResult.diagnostics` 仅含 `ERROR` 级别诊断（`WARNING` 和 `INFO` 被过滤）。

### 前置条件

- `config.quiet == true`
- 诊断列表已产出（过滤在返回前进行）

### 后置条件

- `FileDiagnosticResult.diagnostics` 中所有 Diagnostic 的 `severity == ERROR`
- `BatchInspectionResult.warningCount == 0` 且 `infoCount == 0`（quiet 模式下）
- `errorCount` 仅统计 ERROR 级诊断

### 异常

- 无额外异常；quiet 不影响退出码逻辑（退出码仍看 errorCount）

### 测试场景

| 场景 | 输入 | 期望 |
|---|---|---|
| TS-6.1 | --quiet + fixture 含 3E/2W/1I | stdout 仅含 3 条 ERROR 诊断 |
| TS-6.2 | --quiet + fixture 含 0E/2W | stdout 无诊断；退出码 0 |
| TS-6.3 | --quiet --format json | JSON diagnostics 数组仅含 severity=="error" |

---

## SPEC-7：Verbose 详细输出

### 接口契约

当 `config.verbose == true` 时，`CliMain.run()` 在报告输出后，向 stdout 追加 5 类信息，每行以 `[verbose]` 前缀：

```
[verbose] AST build: Xms, semantic analysis: Yms, type inference: Zms
[verbose] AST: N elements, M attributes, K expressions
[verbose] Symbols: G globals, U user vars, D duplicates
[verbose] Diagnostics: SyntaxErrorAnalyzer=N1, TypeAnalyzer=N2, ConstraintAnalyzer=N3, ...
[verbose] Type inference: attr <tagName>.<attrName>="<rawValue>" → inferred: <type>, expected: <type>, match: OK/MISMATCH
```

### 输入

- `config.verbose == true`
- 分析过程中收集的计时/统计/推断数据

### 输出保证

- 5 类信息各至少一行（即使某类数据为 0）
- 类型推断链（E）对每个 supportsExpression=true 且含表达式语法的属性输出一行
- 耗时（A）以毫秒为单位
- 每行以 `[verbose]` 前缀，便于 grep

### 前置条件

- DiagnosticProvider 在分析过程中需收集计时和统计信息（通过回调或返回值附加）

### 测试场景

| 场景 | 输入 | 期望 |
|---|---|---|
| TS-7.1 | --verbose + 任意 fixture | stdout 含 `[verbose]` 前缀的 5 类信息 |
| TS-7.2 | --verbose + 含表达式属性的 fixture | stdout 含 `Type inference:` 行 |
| TS-7.3 | --verbose + clean fixture（0 诊断） | stdout 仍含 5 类信息（计数为 0） |

---

## SPEC-8：内部异常不吞 + 诊断产出

### 接口契约

`BatchInspectionRunnerImpl.analyzeFile()` 不再用 `catch (Exception e) { return empty; }` 吞异常。改为：

1. AST 构建异常 → 产出 `INTERNAL-AST-ERROR` 诊断（ERROR 级）+ `FileDiagnosticResult.hasInternalError = true`
2. 诊断分析异常 → 产出 `INTERNAL-ANALYZER-ERROR` 诊断 + `hasInternalError = true`
3. 修复生成异常 → 跳过 fixActions（不影响诊断列表）+ `hasInternalError = true`

### 数据结构变更

`FileDiagnosticResult` 新增字段：

```java
@Data @Builder
public class FileDiagnosticResult {
    String filePath;
    List<Diagnostic> diagnostics;
    List<FixAction> fixActions;
    boolean hasInternalError;  // 新增
}
```

### 后置条件

- 内部异常不产生空结果，而是产出 `INTERNAL-*-ERROR` 诊断
- `FileDiagnosticResult.hasInternalError` 在发生内部异常时为 `true`
- 诊断列表非空（至少含 INTERNAL-*-ERROR 诊断）

### 测试场景

| 场景 | 输入 | 期望 |
|---|---|---|
| TS-8.1 | 损坏的 XML（SAX 抛异常的 fixture） | 产出 INTERNAL-AST-ERROR 诊断 |
| TS-8.2 | 正常 fixture | hasInternalError=false |
| TS-8.3 | ThrowingAnalyzer 注入的 fixture | 产出 INTERNAL-ANALYZER-ERROR 诊断 |

---

## SPEC-9：内部异常退出码=2

### 接口契约

```java
public final class ExitCodeCalculator {
    public static int compute(BatchInspectionResult result);
}
```

### 数据结构变更

`BatchInspectionResult` 新增字段：

```java
@Data @Builder
public class BatchInspectionResult {
    int totalFiles;
    int skippedFiles;
    int errorCount;
    int warningCount;
    int infoCount;
    List<FileDiagnosticResult> fileResults;
    boolean hasInternalErrors;  // 新增：任一 FileDiagnosticResult.hasInternalError=true 时为 true
}
```

### 输入/输出

| 输入条件 | 退出码 |
|---|---|
| `hasInternalErrors == true` | **2** |
| `hasInternalErrors == false` 且 `errorCount > 0` | 1 |
| `hasInternalErrors == false` 且 `errorCount == 0` | 0 |

### 后置条件

- 内部异常的退出码为 2（优先于 errorCount 判断）
- 无内部异常时，退出码逻辑不变

### 测试场景

| 场景 | 输入 | 期望退出码 |
|---|---|---|
| TS-9.1 | 损坏 XML fixture | 2 |
| TS-9.2 | 有 ERROR 诊断但无内部异常的 fixture | 1 |
| TS-9.3 | clean fixture（0 诊断 0 异常） | 0 |
| TS-9.4 | 有 ERROR 诊断且有内部异常的 fixture | 2（内部异常优先） |

---

## 验收测试清单汇总

| AC（PHASE 1） | 对应 SPEC | 测试场景 |
|---|---|---|
| AC-1 | SPEC-1 | TS-1.1, TS-1.2 |
| AC-2 | SPEC-1 + SPEC-3 | TS-1.3, TS-1.4, TS-3.3 |
| AC-3 | SPEC-1 + SPEC-5 | TS-1.5, TS-1.6, TS-5.3, TS-5.4 |
| AC-4 | SPEC-5 | TS-5.1, TS-5.2 |
| AC-5 | SPEC-6 | TS-6.1, TS-6.2, TS-6.3 |
| AC-6 | SPEC-7 | TS-7.1, TS-7.2, TS-7.3 |
| AC-7 | SPEC-4 | TS-4.1, TS-4.2 |
| AC-8 | SPEC-8 + SPEC-9 | TS-8.1, TS-8.2, TS-8.3, TS-9.1~9.4 |
| AC-9 | SPEC-2 | TS-2.1, TS-2.2, TS-2.3 |
| AC-10 | SPEC-3 | TS-3.1, TS-3.2, TS-3.3 |
| AC-11 | 全部 | 全量门禁 |

---

> **阶段切换**：PHASE 2 完成。请用户确认以上规格契约，确认后进入 PHASE 3（设计）。
