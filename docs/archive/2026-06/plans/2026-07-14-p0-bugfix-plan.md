# P0 Beta 闭环修复实施计划 + Prompt 模板

> **用途**：本文件供当前会话后续或其他团队成员/会话使用，按 SDD 流程逐项实施 P0 修复。每个 P0 项包含：现状证据、修复方案（含代码片段）、涉及文件、golden 测试影响、验证命令。配套的 implementer prompt 模板可直接用于分派 subagent。
>
> **前置条件**：E2E golden 测试框架已落地（`feature/e2e-golden-testing` 分支），L3（in-process）+ L4（fat jar 子进程）门禁全绿。P0 修复会改变诊断行为，golden 文件需同步更新。
>
> **分支**：建议从 `feature/e2e-golden-testing` 切出 `feature/p0-bugfix` 进行，避免与测试框架冲突。

---

## P0 总览

| # | 工作项 | 涉及文件 | golden 影响 |
|---|---|---|---|
| P0-1 | 接入 SyntaxChecker 到生产链 | `DiagnosticProviderImpl.java`, `BatchInspectionRunnerImpl.java` | SYN-001~007 新增诊断，部分 fixture golden 需更新 |
| P0-2 | FixActionRegistry 生产初始化 | `CliMain.java`, `FixActionRegistry.java` | JSON suggestedFixes 非空，但 golden 不校验此字段，无影响 |
| P0-3 | CLI 参数真实语义落实 | `CliMain.java`, `BatchInspectionRunnerImpl.java`, `AnalyzerRegistry.java`, `ReportExporterImpl.java` | `--syntax-only`/`--semantic-only`/`--no-type-check` 模式下诊断变化 |
| P0-4 | 内部异常退出语义收口 | `BatchInspectionRunnerImpl.java`, `BatchInspectionResult.java`, `ExitCodeCalculator.java` | 异常场景退出码 0→2 |

---

## P0-1：接入 SyntaxChecker 到生产链

### 现状证据

`DiagnosticProviderImpl.analyze()`（`feature/analysis/src/main/java/com/huawei/theme/analysis/core/semanticanalysis/DiagnosticProviderImpl.java:17-23`）只调用 `ExpressionSyntaxChecker`，未调用 `SyntaxChecker`：

```java
public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder symbolTableBuilder) {
    List<Diagnostic> diagnostics =
            new DiagnosticProviderImplInner(ast, ruleRepo, symbolTableBuilder).getDiagnostics();
    diagnostics.addAll(new ExpressionSyntaxChecker(ruleRepo).check(ast.getFilePath(), ast));
    return diagnostics;  // ← 缺少 SyntaxChecker.check() 调用
}
```

`BatchInspectionRunnerImpl.analyzeFile()`（`BatchInspectionRunnerImpl.java:119-126`）在 `SYNTAX_ONLY` 模式下跳过 `DiagnosticProvider`：

```java
if (mode != PipelineMode.SYNTAX_ONLY) {  // ← SYNTAX_ONLY 跳过了所有诊断
    diagnostics = diagnosticProvider.analyze(ast, ruleRepository, symbolTableBuilder);
}
```

**影响**：SYN-001（根元素错误）、SYN-002（嵌套约束）、SYN-003（未知标签）、SYN-004（未知属性）、SYN-005（必填缺失）、SYN-006（字面量类型）、SYN-007（枚举错误）在生产链不产出；`--syntax-only` 模式名存实亡。

### 修复方案

**步骤 1：在 `DiagnosticProviderImpl.analyze()` 中追加 `SyntaxChecker` 调用**

文件：`feature/analysis/src/main/java/com/huawei/theme/analysis/core/semanticanalysis/DiagnosticProviderImpl.java`

```java
import com.huawei.theme.analysis.core.syntaxanalysis.SyntaxChecker;  // 新增 import

@Override
public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder symbolTableBuilder) {
    List<Diagnostic> diagnostics =
            new DiagnosticProviderImplInner(ast, ruleRepo, symbolTableBuilder).getDiagnostics();
    diagnostics.addAll(new SyntaxChecker(ruleRepo).check(ast.getFilePath(), ast));
    diagnostics.addAll(new ExpressionSyntaxChecker(ruleRepo).check(ast.getFilePath(), ast));
    return diagnostics;
}
```

**步骤 2：修复 `BatchInspectionRunnerImpl.analyzeFile()` 的 `SYNTAX_ONLY` 模式**

文件：`feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionRunnerImpl.java`

将 `SYNTAX_ONLY` 模式从"跳过诊断"改为"只跑语法诊断"：

```java
List<Diagnostic> diagnostics = List.of();
if (mode != PipelineMode.SEMANTIC_ONLY) {
    try {
        diagnostics = diagnosticProvider.analyze(ast, ruleRepository, symbolTableBuilder);
    } catch (Exception e) {
        diagnostics = List.of();
    }
}
```

> 逻辑变更：`SYNTAX_ONLY` → 跑 `DiagnosticProvider`（含 SyntaxChecker + ExpressionSyntaxChecker，不含 M4 Analyzer），因为 `DiagnosticProviderImplInner` 的 Analyzer 已在 FULL 模式跑。SYNTAX_ONLY 应跳过 M4 Analyzer 但保留 M3 结构语法。
>
> **进阶方案**（更精确）：在 `DiagnosticProviderImpl` 中增加 `analyzeSyntaxOnly(ast, ruleRepo)` 方法，只调 `SyntaxChecker` + `ExpressionSyntaxChecker`，不跑 `AnalyzerRegistry`。`SYNTAX_ONLY` 模式调此方法，`FULL` 模式调 `analyze()`。

### 验证命令

```bash
./gradlew --no-daemon :feature:analysis:test --tests "com.huawei.theme.analysis.core.e2e.GoldenDiagnosticMatchTest"
```

> 预期：部分 fixture 的 golden 会 FAIL（SYN-001~007 新诊断出现）。用 `GoldenDumper` 重新生成 golden 草稿，对照 `ANSWER_KEY.md` 复核后更新 `.expected.json`。

---

## P0-2：FixActionRegistry 生产初始化

### 现状证据

`FixActionRegistry.init(ruleRepository)` 仅在测试中调用。`CliMain.run()`（`feature/analysis/src/main/java/com/huawei/theme/analysis/core/cli/CliMain.java:109-116`）构造了 `QuickFixProviderImpl` 但未初始化 registry：

```java
BatchInspectionRunner runner = new BatchInspectionRunnerImpl(
        matcher, astProvider,
        new DiagnosticProviderImpl(),
        new QuickFixProviderImpl(),  // ← FixActionRegistry 未 init，fixActions 实际为空
        new SymbolTableBuilderImpl(),
        effectiveRepo,
        effectiveConfig
);
```

**影响**：CLI JSON 报告的 `suggestedFixes` 字段为空数组，违背 PRD §2.1.5。

### 修复方案

文件：`feature/analysis/src/main/java/com/huawei/theme/analysis/core/cli/CliMain.java`

在 `loadRuleRepository()` 之后、构造 `BatchInspectionRunnerImpl` 之前，调用 `FixActionRegistry.init()`：

```java
import com.huawei.theme.analysis.core.quickfix.FixActionRegistry;  // 新增 import

RuleRepository effectiveRepo = new ConfigAwareRuleRepository(ruleRepo, effectiveConfig);

FixActionRegistry.init(effectiveRepo);  // ← 新增：生产初始化

CliDslFileMatcher matcher = new CliDslFileMatcher(effectiveRepo);
```

### 验证命令

```bash
./gradlew --no-daemon :feature:analysis:test --tests "com.huawei.theme.analysis.core.e2e.GoldenDiagnosticMatchTest"
```

> 预期：无影响（golden 不校验 `suggestedFixes`）。但可手动验证 `java -jar dsl-analyzer.jar --format json <fixture>` 的 `suggestedFixes` 非空。

---

## P0-3：CLI 参数真实语义落实

### 现状证据

| 参数 | 现状 | 问题 |
|---|---|---|
| `--syntax-only` | `BatchInspectionRunnerImpl:120` 跳过所有诊断 | 应只跑 M3 结构语法（P0-1 修复后自然解决） |
| `--semantic-only` | 仍跑 `TypeAnalyzer` | 应跳过类型推断 |
| `--no-type-check` | 存入 `InspectionConfig.typeCheck` 但不控制 `AnalyzerRegistry` | `TypeAnalyzer` 仍执行 |
| `--quiet` | 解析存配置 | 未过滤 WARNING/INFO 输出 |
| `--verbose` | 解析存配置 | 未输出 AST/耗时/类型链/符号表摘要 |

### 修复方案

**步骤 1：`--semantic-only` / `--no-type-check` 跳过 TypeAnalyzer**

文件：`feature/analysis/src/main/java/com/huawei/theme/analysis/core/semanticanalysis/AnalyzerRegistry.java`

在 `AnalyzerRegistry.getAnalyzers()` 或 `analyze()` 路径中，根据 `InspectionConfig.typeCheck` 标志过滤掉 `TypeAnalyzer`：

```java
public List<Analyzer> getAnalyzers(InspectionConfig config) {
    List<Analyzer> analyzers = new ArrayList<>(registeredAnalyzers);
    if (config != null && !config.isTypeCheck()) {
        analyzers.removeIf(a -> a instanceof TypeAnalyzer);
    }
    return analyzers;
}
```

> `DiagnosticProviderImpl.DiagnosticProviderImplInner.analyze()` 调用时需传入 `InspectionConfig`，在 `mode == SEMANTIC_ONLY` 时也移除 `TypeAnalyzer`。

**步骤 2：`--quiet` 过滤 WARNING/INFO**

文件：`feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/ReportExporterImpl.java`

在 `exportTerminal`/`exportJson`/`exportMarkdown` 中，当 `quiet=true` 时过滤掉 WARNING 和 INFO 级别的诊断：

```java
if (config.isQuiet()) {
    diagnostics = diagnostics.stream()
            .filter(d -> d.getSeverity() == DiagnosticSeverity.ERROR)
            .collect(Collectors.toList());
}
```

> 需要把 `quiet` 标志传入 `ReportExporterImpl`，或在 `BatchInspectionRunnerImpl.analyzeFile()` 返回前过滤。

**步骤 3：`--verbose` 输出分析细节**

文件：`feature/analysis/src/main/java/com/huawei/theme/analysis/core/cli/CliMain.java`

在 `exportReport()` 之后，当 `verbose=true` 时输出 AST 节点数、分析耗时、符号表摘要：

```java
if (config.isVerbose()) {
    System.out.println("AST nodes: " + countAstNodes(result));
    System.out.println("Analysis time: " + (endTime - startTime) + "ms");
    System.out.println("Symbols: " + symbolTableSummary);
}
```

### 验证命令

```bash
./gradlew --no-daemon :feature:analysis:test --tests "com.huawei.theme.analysis.core.cli.CliMainE2ETest"
./gradlew --no-daemon :feature:analysis:test --tests "com.huawei.theme.analysis.core.e2e.GoldenDiagnosticMatchTest"
```

> 预期：`--no-type-check` / `--semantic-only` 模式的诊断数量变化，golden 需更新。

---

## P0-4：内部异常退出语义收口

### 现状证据

`BatchInspectionRunnerImpl.analyzeFile()`（`BatchInspectionRunnerImpl.java:112-135`）对 AST/诊断/修复异常降级为空列表：

```java
try {
    ast = astProvider.getDslAst(filePath, content);
} catch (Exception e) {
    return FileDiagnosticResult.builder()
            .filePath(filePath).diagnostics(List.of()).fixActions(List.of()).build();  // ← 吞异常
}
```

`ExitCodeCalculator` 只看 ERROR 数量，内部异常仍可能呈现为成功（退出码 0）。

### 修复方案

**步骤 1：`BatchInspectionResult` 增加异常标记**

文件：`feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/model/BatchInspectionResult.java`

```java
@Data @Builder
public class BatchInspectionResult {
    int totalFiles;
    int skippedFiles;
    int errorCount;
    int warningCount;
    int infoCount;
    List<FileDiagnosticResult> fileResults;
    boolean hasInternalErrors;  // ← 新增
    List<String> internalErrorMessages;  // ← 新增
}
```

**步骤 2：`BatchInspectionRunnerImpl` 记录内部异常而非吞掉**

文件：`feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/BatchInspectionRunnerImpl.java`

```java
private FileDiagnosticResult analyzeFile(String filePath, String content) {
    // ...
    DslFileNode ast;
    try {
        ast = astProvider.getDslAst(filePath, content);
    } catch (Exception e) {
        return FileDiagnosticResult.builder()
                .filePath(filePath)
                .diagnostics(List.of(Diagnostic.builder()
                        .severity(DiagnosticSeverity.ERROR)
                        .ruleId("INTERNAL-AST-ERROR")
                        .message("AST build failed: " + e.getMessage())
                        .filePath(filePath)
                        .line(0).column(0)
                        .build()))
                .fixActions(List.of())
                .hasInternalError(true)
                .build();
    }
    // 同理处理 diagnosticProvider 和 quickFixProvider 异常
}
```

**步骤 3：`ExitCodeCalculator` 返回 2 当有内部异常**

文件：`feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/ExitCodeCalculator.java`

```java
public static int compute(BatchInspectionResult result) {
    if (result.isHasInternalErrors()) {
        return 2;
    }
    return result.getErrorCount() > 0 ? 1 : 0;
}
```

### 验证命令

```bash
./gradlew --no-daemon :feature:analysis:test --tests "com.huawei.theme.analysis.core.batchinspection.*"
./gradlew --no-daemon :feature:analysis:test --tests "com.huawei.theme.analysis.core.cli.*"
```

> 预期：异常 fixture 退出码 0→2，需新增异常注入 fixture + golden 验证退出码。

---

## P0 实施顺序建议

1. **P0-1 先行**：接入 SyntaxChecker 后，跑 L3 golden，观察哪些 fixture 出现新 SYN 诊断。用 GoldenDumper 重新生成 golden，对照 ANSWER_KEY.md 复核。
2. **P0-2 次之**：FixActionRegistry init，无 golden 影响，但需手验 JSON suggestedFixes 非空。
3. **P0-3 依序**：先 `--no-type-check`（AnalyzerRegistry 过滤），再 `--quiet`（ReportExporter 过滤），再 `--verbose`（CliMain 输出），最后 `--syntax-only`/`--semantic-only`（依赖 P0-1 的 DiagnosticProvider 重构）。
4. **P0-4 收尾**：异常语义收口，新增异常 fixture + golden。

每项修复后跑全量门禁：
```bash
./gradlew --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e
```

---

## Implementer Prompt 模板

以下模板可直接用于 `Task` 工具分派 subagent 实施各项 P0 修复。使用时将 `[P0-N]` 替换为对应项。

### 通用 Implementer Prompt

```
Task tool (general-purpose):
  description: "Implement [P0-N]: [task name]"
  prompt: |
    You are implementing P0-N: [task name] for a Java/Gradle project at
    C:\Users\30991\theme-engine-dsl-static-analyzer. Branch: feature/p0-bugfix
    (create from feature/e2e-golden-testing).

    ## CRITICAL — Gradle/Bash Command Rules (MUST FOLLOW)

    1. 所有 ./gradlew 命令必须加 --no-daemon 参数。
    2. 所有 Bash 命令必须设置 timeout：测试 60000ms，打包 120000ms。
    3. 禁止 PowerShell 管道过滤。详见 AGENTS.md。

    ## Plan File

    Read the FULL specification from:
    docs/superpowers/plans/2026-07-14-p0-bugfix-plan.md
    Implement the [P0-N] section.

    ## Your Job

    1. Implement the fix per the plan (follow TDD: write/update test first, verify, implement, verify)
    2. Run the affected tests: ./gradlew --no-daemon :feature:analysis:test --tests "..."
    3. If golden tests FAIL (expected — behavior changed), use GoldenDumper to regenerate golden drafts:
       - Write a temporary GoldenDumpRunner test (see Task 5 in e2e-golden-testing plan)
       - Run it, then update .expected.json files to match new correct behavior
       - Delete the temp runner
       - Re-run L3: ./gradlew --no-daemon :feature:analysis:test --tests "...GoldenDiagnosticMatchTest"
    4. Run full gate: ./gradlew --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e
    5. Commit with message: "fix(p0-N): [description]"
    6. Self-review and report

    ## Context

    - E2E golden test framework is in place (L3 in-process + L4 fat-jar subprocess)
    - Golden files capture EXPECTED behavior; when P0 fixes change behavior, update golden
    - GoldenDumper at src/test/java/.../e2e/GoldenDumper.java generates golden drafts
    - ANSWER_KEY.md files in fixtures/complex/ and fixtures/complex_expressions/ are the curated source of truth
    - Code conventions: 4-space indent, no comments, Lombok, JUnit 5, GSON

    ## Report Format

    - Status: DONE | DONE_WITH_CONCERNS | BLOCKED
    - What you implemented
    - Test results (before/after golden update)
    - Golden files updated (list)
    - Files changed
    - Git commit SHA
    - Concerns
```

### P0-1 专用 Prompt 补充

```
    ## P0-1 Specifics

    - Modify DiagnosticProviderImpl.java:21 — add SyntaxChecker.check() call
    - Modify BatchInspectionRunnerImpl.java:120 — fix SYNTAX_ONLY to run syntax diagnostics
    - Read SyntaxChecker.java to understand its check() method signature
    - After fix, SYN-001~007 diagnostics will appear for fixtures with structural errors
    - Re-generate golden for affected fixtures using GoldenDumper
    - Cross-check against ANSWER_KEY.md (complex/ and complex_expressions/)
```

### P0-3 专用 Prompt 补充

```
    ## P0-3 Specifics

    - Read AnalyzerRegistry.java to understand how analyzers are registered/filtered
    - Read InspectionConfig.java for typeCheck/quiet/verbose flags
    - Modify AnalyzerRegistry to filter TypeAnalyzer when typeCheck=false
    - Modify ReportExporterImpl to filter WARNING/INFO when quiet=true
    - Modify CliMain.exportReport() to output verbose details when verbose=true
    - Each sub-fix is independent; can be committed separately
```

---

## Golden 文件更新流程（P0 修复后通用）

当 P0 修复改变了诊断行为（新增/移除/位置变化），golden 文件需同步更新：

1. **重新生成草稿**：临时启用 `GoldenDumpRunner`（见 e2e-golden-testing plan Task 5），跑 `./gradlew --no-daemon :feature:analysis:test --tests "...GoldenDumpRunner"` 生成所有 `.expected.json` 草稿
2. **对照 ANSWER_KEY.md**：打开 `fixtures/complex/ANSWER_KEY.md` 和 `fixtures/complex_expressions/ANSWER_KEY.md`，逐条核对 ruleId/severity/count
3. **添加 mustNotTrigger**：为 ANSWER_KEY.md 中"Valid Elements"/"Boundary"小节的合法元素添加 mustNotTrigger 条目
4. **删除临时 runner**：删除 `GoldenDumpRunner.java`
5. **验证 L3**：`./gradlew --no-daemon :feature:analysis:test --tests "...GoldenDiagnosticMatchTest"`
6. **验证 L4**：`./gradlew --no-daemon :feature:analysis:buildFatJar :feature:analysis:e2e`
7. **全量门禁**：`./gradlew --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e`
