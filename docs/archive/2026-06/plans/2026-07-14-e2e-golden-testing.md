---
module_ids: [CORE]
doc_kind: plan
status: archived
created: 2026-07-14
---
# E2E Golden Testing 自动化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把"手动 `java -jar` + 人眼比对 DSL"的端到端验证转化为机器可判定的自动化 golden 匹配测试，覆盖 L3（in-process 严格 golden）与 L4（真实 fat jar 子进程），为 CI/CD 门禁提供可阻断退出码。

**Architecture:** 三层结构——Golden 数据层（每 fixture 一个 `.expected.json`，与 CLI `--format json` 输出同构）→ GoldenMatcher 引擎（ruleId+severity+count 严格、行号 ±2 近似，L3/L4 共用）→ 两个测试层（L3 in-process 走 `./gradlew test`，L4 真实子进程走 `./gradlew e2e` 依赖 `buildFatJar`）。现有 26 个松散 `CliMainE2ETest` 保留作 smoke 层不动。

**Tech Stack:** Java 17, JUnit 5 (Jupiter 5.9.3, 已有依赖), GSON 2.9.0 (已有依赖), Gradle 8.2, gradle-intellij-plugin 1.13.3。无新依赖引入。

> **CRITICAL — Gradle 命令约束（必读）**
> - 所有 `./gradlew` 命令**必须**加 `--no-daemon` 参数，否则 Daemon 不退出导致进程卡死。
> - 所有 Bash 命令**必须**设置 timeout：普通测试/编译设 30000ms；buildFatJar/e2e 设 120000ms。
> - 禁止在 Bash 命令中使用 PowerShell 管道过滤（`| Select-String` 等）。
> - 示例：`./gradlew --no-daemon :feature:analysis:test --tests "com.huawei.theme.analysis.core.e2e.golden.GoldenMatcherTest"`
> - 详见 `AGENTS.md` 的 "Bash 命令约束 / Gradle Daemon 约束" 章节。

---

## 关键背景：CLI JSON 实际输出格式

`JsonReportSerializer` 实际输出（单文件）：
```json
{
  "file": "path/to/theme.xml",
  "diagnostics": [
    {
      "severity": "error",
      "line": 15,
      "col": 3,
      "ruleId": "SEM-REF-001",
      "message": "引用未定义变量 #steps_value",
      "suggestedFixes": ["声明Var name=\"steps_value\""]
    }
  ],
  "summary": { "errors": 1, "warnings": 0, "info": 0 }
}
```
多文件输出额外包一层 `{"files":[...], "summary":{...,"totalFiles":N,"skippedFiles":N}}`。

**注意**：实际输出**无 `ruleDocUrl` 字段**（与 PRD §2.1.5 描述有偏差）。Golden schema 以实际输出为准。

## 文件结构

| 路径 | 职责 |
|---|---|
| `src/test/java/com/huawei/theme/analysis/core/e2e/golden/GoldenExpectation.java` | Golden 期望数据模型（fixture+exitCode+counts+entries+mustNotTrigger） |
| `src/test/java/com/huawei/theme/analysis/core/e2e/golden/ExpectedDiagnostic.java` | 单条期望诊断（ruleId+severity+approxLine+tolerance） |
| `src/test/java/com/huawei/theme/analysis/core/e2e/golden/MustNotTriggerEntry.java` | mustNotTrigger 条目（approxLine+reason） |
| `src/test/java/com/huawei/theme/analysis/core/e2e/golden/MatchResult.java` | 匹配结果（pass/fail + diff 详情） |
| `src/test/java/com/huawei/theme/analysis/core/e2e/golden/GoldenExpectationParser.java` | 从 .expected.json 解析为 GoldenExpectation（用 GSON） |
| `src/test/java/com/huawei/theme/analysis/core/e2e/golden/GoldenMatcher.java` | 匹配引擎：actual diagnostics vs GoldenExpectation → MatchResult |
| `src/test/java/com/huawei/theme/analysis/core/e2e/golden/ActualDiagnostic.java` | 实际诊断的轻量 DTO（从 JSON 反序列化） |
| `src/test/java/com/huawei/theme/analysis/core/e2e/GoldenDiagnosticMatchTest.java` | L3：in-process 参数化 golden 测试 |
| `src/test/java/com/huawei/theme/analysis/core/e2e/FatJarSubprocessE2ETest.java` | L4：真实 `java -jar` 子进程 golden 测试 |
| `src/test/java/com/huawei/theme/analysis/core/e2e/FixtureCoverageTest.java` | 元测试：每个 .xml 必须有 .expected.json |
| `src/test/java/com/huawei/theme/analysis/core/e2e/GoldenDumper.java` | 工具：dump 当前 CLI JSON 输出为 golden 草稿 |
| `src/test/resources/fixtures/**/<name>.expected.json` | Golden 文件，与 fixture .xml 同目录 |
| `feature/analysis/build.gradle` | 新增 `e2e` Gradle task |
| `AGENTS.md` | 追加门禁命令说明 |

## Golden JSON Schema

每个 `fixture.expected.json`：
```json
{
  "fixture": "complex/deep_nesting_violations.xml",
  "expectedExitCode": 1,
  "expectedCounts": { "errors": 16, "warnings": 1, "info": 0 },
  "expectedDiagnostics": [
    {
      "ruleId": "SEM-ATTR-001",
      "severity": "error",
      "approxLine": 5,
      "lineTolerance": 2,
      "description": "alpha=300 exceeds 255"
    }
  ],
  "mustNotTrigger": [
    { "approxLine": 3, "reason": "alpha=0 boundary valid" }
  ]
}
```
- **Clean fixture（负例）**：`expectedDiagnostics: []`、`expectedExitCode: 0`、`expectedCounts: {"errors":0,"warnings":0,"info":0}`，任何实际诊断即 FP 失败。
- **mustNotTrigger**：指定行附近（±2）不得有任何诊断；用于保护边界值/合法元素不被误报。

## 匹配算法（GoldenMatcher）

对每个 fixture 依次校验，任一不过即整体 FAIL：

1. **退出码**：`actualExitCode == expectedExitCode`，否则 FAIL("exit code: expected X got Y")
2. **计数**：实际 errors/warnings/info 总数 == expectedCounts，否则 FAIL("error count: expected X got Y")
3. **期望诊断逐条匹配（贪心 + 消费）**：
   - 把 actual 按 (ruleId, severity) 分组
   - 对每个 expectedDiagnostic entry：在对应 actual 组中找一条 `|actualLine - approxLine| <= lineTolerance` 且尚未被消费的，标记为"已匹配"；找不到则该 entry = FN
   - 所有 entry 匹配完后，actual 中剩余未消费的诊断 = FP
   - FN > 0 或 FP > 0 → FAIL，diff 列出每条缺漏/多余
4. **mustNotTrigger**：对每个 entry，actual 中不得有诊断的 line 落在 `[approxLine - 2, approxLine + 2]`，否则 FAIL("mustNotTrigger violated at line X")

---

## Task 1: Golden 期望数据模型

**Files:**
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/golden/ExpectedDiagnostic.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/golden/MustNotTriggerEntry.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/golden/GoldenExpectation.java`

- [ ] **Step 1: 写 ExpectedDiagnostic 数据类**

```java
package com.huawei.theme.analysis.core.e2e.golden;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpectedDiagnostic {
    private String ruleId;
    private String severity;
    private int approxLine;
    private int lineTolerance;
    private String description;
}
```

- [ ] **Step 2: 写 MustNotTriggerEntry 数据类**

```java
package com.huawei.theme.analysis.core.e2e.golden;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MustNotTriggerEntry {
    private int approxLine;
    private String reason;
}
```

- [ ] **Step 3: 写 GoldenExpectation 顶层模型**

```java
package com.huawei.theme.analysis.core.e2e.golden;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoldenExpectation {
    private String fixture;
    private int expectedExitCode;
    private ExpectedCounts expectedCounts;
    private List<ExpectedDiagnostic> expectedDiagnostics;
    private List<MustNotTriggerEntry> mustNotTrigger;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExpectedCounts {
        private int errors;
        private int warnings;
        private int info;
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew :feature:analysis:compileTestJava`
Expected: 编译成功（Lombok 已在 build.gradle 配置 annotationProcessor）。

- [ ] **Step 5: Commit**

```bash
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/golden/ExpectedDiagnostic.java feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/golden/MustNotTriggerEntry.java feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/golden/GoldenExpectation.java
git commit -m "feat(e2e): add golden expectation data model for fixture matching"
```

---

## Task 2: ActualDiagnostic DTO + GoldenExpectationParser

**Files:**
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/golden/ActualDiagnostic.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/golden/GoldenExpectationParser.java`
- Test: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/golden/GoldenExpectationParserTest.java`

- [ ] **Step 1: 写 ActualDiagnostic DTO（CLI JSON 输出反序列化目标）**

```java
package com.huawei.theme.analysis.core.e2e.golden;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ActualDiagnostic {
    private String severity;
    private int line;
    private int col;
    private String ruleId;
    private String message;
    private List<String> suggestedFixes;
}
```

- [ ] **Step 2: 写失败测试 GoldenExpectationParserTest**

```java
package com.huawei.theme.analysis.core.e2e.golden;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class GoldenExpectationParserTest {

    @TempDir
    Path tempDir;

    private Path writeGolden(String content) throws Exception {
        Path p = tempDir.resolve("sample.expected.json");
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void parse_validGolden_populatesAllFields() throws Exception {
        String json = """
                {
                  "fixture": "complex/deep_nesting_violations.xml",
                  "expectedExitCode": 1,
                  "expectedCounts": { "errors": 2, "warnings": 1, "info": 0 },
                  "expectedDiagnostics": [
                    { "ruleId": "SEM-ATTR-001", "severity": "error", "approxLine": 5, "lineTolerance": 2, "description": "alpha=300" },
                    { "ruleId": "SEM-ENUM-001", "severity": "error", "approxLine": 8, "lineTolerance": 2 }
                  ],
                  "mustNotTrigger": [
                    { "approxLine": 3, "reason": "boundary valid" }
                  ]
                }
                """;
        Path goldenFile = writeGolden(json);
        GoldenExpectationParser parser = new GoldenExpectationParser();

        GoldenExpectation exp = parser.parse(goldenFile);

        assertEquals("complex/deep_nesting_violations.xml", exp.getFixture());
        assertEquals(1, exp.getExpectedExitCode());
        assertEquals(2, exp.getExpectedCounts().getErrors());
        assertEquals(1, exp.getExpectedCounts().getWarnings());
        assertEquals(0, exp.getExpectedCounts().getInfo());
        assertEquals(2, exp.getExpectedDiagnostics().size());
        assertEquals("SEM-ATTR-001", exp.getExpectedDiagnostics().get(0).getRuleId());
        assertEquals("error", exp.getExpectedDiagnostics().get(0).getSeverity());
        assertEquals(5, exp.getExpectedDiagnostics().get(0).getApproxLine());
        assertEquals(2, exp.getExpectedDiagnostics().get(0).getLineTolerance());
        assertEquals(1, exp.getMustNotTrigger().size());
        assertEquals(3, exp.getMustNotTrigger().get(0).getApproxLine());
    }

    @Test
    void parse_cleanFixture_emptyDiagnostics() throws Exception {
        String json = """
                {
                  "fixture": "clean/lockscreen_valid.xml",
                  "expectedExitCode": 0,
                  "expectedCounts": { "errors": 0, "warnings": 0, "info": 0 },
                  "expectedDiagnostics": [],
                  "mustNotTrigger": []
                }
                """;
        Path goldenFile = writeGolden(json);
        GoldenExpectationParser parser = new GoldenExpectationParser();

        GoldenExpectation exp = parser.parse(goldenFile);

        assertEquals(0, exp.getExpectedExitCode());
        assertTrue(exp.getExpectedDiagnostics().isEmpty());
        assertTrue(exp.getMustNotTrigger().isEmpty());
    }

    @Test
    void parse_missingMustNotTrigger_defaultsToEmptyList() throws Exception {
        String json = """
                {
                  "fixture": "x.xml",
                  "expectedExitCode": 1,
                  "expectedCounts": { "errors": 1, "warnings": 0, "info": 0 },
                  "expectedDiagnostics": [
                    { "ruleId": "SEM-REF-001", "severity": "error", "approxLine": 5, "lineTolerance": 2 }
                  ]
                }
                """;
        Path goldenFile = writeGolden(json);
        GoldenExpectationParser parser = new GoldenExpectationParser();

        GoldenExpectation exp = parser.parse(goldenFile);

        assertNotNull(exp.getMustNotTrigger());
        assertTrue(exp.getMustNotTrigger().isEmpty());
    }

    @Test
    void parse_missingLineTolerance_defaultsToTwo() throws Exception {
        String json = """
                {
                  "fixture": "x.xml",
                  "expectedExitCode": 1,
                  "expectedCounts": { "errors": 1, "warnings": 0, "info": 0 },
                  "expectedDiagnostics": [
                    { "ruleId": "SEM-REF-001", "severity": "error", "approxLine": 5 }
                  ]
                }
                """;
        Path goldenFile = writeGolden(json);
        GoldenExpectationParser parser = new GoldenExpectationParser();

        GoldenExpectation exp = parser.parse(goldenFile);

        assertEquals(2, exp.getExpectedDiagnostics().get(0).getLineTolerance());
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.e2e.golden.GoldenExpectationParserTest"`
Expected: FAIL（GoldenExpectationParser 不存在，编译失败）。

- [ ] **Step 4: 写 GoldenExpectationParser 实现**

```java
package com.huawei.theme.analysis.core.e2e.golden;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class GoldenExpectationParser {

    private final Gson gson;

    public GoldenExpectationParser() {
        this.gson = new GsonBuilder().create();
    }

    public GoldenExpectation parse(Path goldenFile) {
        try (Reader reader = new InputStreamReader(Files.newInputStream(goldenFile), StandardCharsets.UTF_8)) {
            GoldenExpectation exp = gson.fromJson(reader, GoldenExpectation.class);
            if (exp == null) {
                throw new IllegalStateException("Empty or invalid golden file: " + goldenFile);
            }
            applyDefaults(exp);
            return exp;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read golden file: " + goldenFile, e);
        }
    }

    private void applyDefaults(GoldenExpectation exp) {
        if (exp.getExpectedDiagnostics() == null) {
            exp.setExpectedDiagnostics(java.util.Collections.emptyList());
        }
        for (ExpectedDiagnostic d : exp.getExpectedDiagnostics()) {
            if (d.getLineTolerance() == 0) {
                d.setLineTolerance(2);
            }
        }
        if (exp.getMustNotTrigger() == null) {
            exp.setMustNotTrigger(java.util.Collections.emptyList());
        }
        if (exp.getExpectedCounts() == null) {
            exp.setExpectedCounts(GoldenExpectation.ExpectedCounts.builder().build());
        }
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.e2e.golden.GoldenExpectationParserTest"`
Expected: PASS（4 tests）。

- [ ] **Step 6: Commit**

```bash
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/golden/ActualDiagnostic.java feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/golden/GoldenExpectationParser.java feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/golden/GoldenExpectationParserTest.java
git commit -m "feat(e2e): add golden expectation parser with default tolerance"
```

---

## Task 3: MatchResult + GoldenMatcher 引擎

**Files:**
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/golden/MatchResult.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/golden/GoldenMatcher.java`
- Test: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/golden/GoldenMatcherTest.java`

- [ ] **Step 1: 写 MatchResult 数据类**

```java
package com.huawei.theme.analysis.core.e2e.golden;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class MatchResult {
    private final boolean passed;
    private final List<String> diffs = new ArrayList<>();

    public MatchResult(boolean passed) {
        this.passed = passed;
    }

    public static MatchResult pass() {
        return new MatchResult(true);
    }

    public static MatchResult fail(String reason) {
        MatchResult r = new MatchResult(false);
        r.diffs.add(reason);
        return r;
    }

    public MatchResult addDiff(String diff) {
        this.diffs.add(diff);
        return this;
    }

    public String renderDiffs() {
        return String.join("\n", diffs);
    }
}
```

- [ ] **Step 2: 写失败测试 GoldenMatcherTest**

```java
package com.huawei.theme.analysis.core.e2e.golden;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoldenMatcherTest {

    private ActualDiagnostic diag(String ruleId, String severity, int line) {
        ActualDiagnostic d = new ActualDiagnostic();
        d.setRuleId(ruleId);
        d.setSeverity(severity);
        d.setLine(line);
        return d;
    }

    private ExpectedDiagnostic exp(String ruleId, String severity, int line) {
        return ExpectedDiagnostic.builder()
                .ruleId(ruleId).severity(severity).approxLine(line).lineTolerance(2).build();
    }

    @Test
    void match_allCorrect_passes() {
        GoldenExpectation ge = GoldenExpectation.builder()
                .expectedExitCode(1)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder().errors(2).warnings(0).info(0).build())
                .expectedDiagnostics(List.of(exp("SEM-ATTR-001", "error", 5), exp("SEM-ENUM-001", "error", 8)))
                .mustNotTrigger(List.of())
                .build();
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "error", 5), diag("SEM-ENUM-001", "error", 8));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertTrue(result.isPassed(), result.renderDiffs());
    }

    @Test
    void match_lineWithinTolerance_passes() {
        GoldenExpectation ge = GoldenExpectation.builder()
                .expectedExitCode(1)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder().errors(1).warnings(0).info(0).build())
                .expectedDiagnostics(List.of(exp("SEM-ATTR-001", "error", 5)))
                .mustNotTrigger(List.of())
                .build();
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "error", 7));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertTrue(result.isPassed(), result.renderDiffs());
    }

    @Test
    void match_lineBeyondTolerance_failsAsFalseNegative() {
        GoldenExpectation ge = GoldenExpectation.builder()
                .expectedExitCode(1)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder().errors(1).warnings(0).info(0).build())
                .expectedDiagnostics(List.of(exp("SEM-ATTR-001", "error", 5)))
                .mustNotTrigger(List.of())
                .build();
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "error", 20));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertFalse(result.isPassed());
        assertTrue(result.renderDiffs().contains("FN") || result.renderDiffs().contains("missing"));
    }

    @Test
    void match_unexpectedActual_failsAsFalsePositive() {
        GoldenExpectation ge = GoldenExpectation.builder()
                .expectedExitCode(0)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder().errors(0).warnings(0).info(0).build())
                .expectedDiagnostics(List.of())
                .mustNotTrigger(List.of())
                .build();
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "error", 5));

        MatchResult result = new GoldenMatcher().match(actuals, 0, ge);

        assertFalse(result.isPassed());
        assertTrue(result.renderDiffs().contains("FP") || result.renderDiffs().contains("unexpected"));
    }

    @Test
    void match_exitCodeMismatch_fails() {
        GoldenExpectation ge = GoldenExpectation.builder()
                .expectedExitCode(0)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder().errors(0).warnings(0).info(0).build())
                .expectedDiagnostics(List.of())
                .mustNotTrigger(List.of())
                .build();

        MatchResult result = new GoldenMatcher().match(List.of(), 1, ge);

        assertFalse(result.isPassed());
        assertTrue(result.renderDiffs().toLowerCase().contains("exit code"));
    }

    @Test
    void match_countMismatch_fails() {
        GoldenExpectation ge = GoldenExpectation.builder()
                .expectedExitCode(1)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder().errors(3).warnings(0).info(0).build())
                .expectedDiagnostics(List.of(exp("SEM-ATTR-001", "error", 5)))
                .mustNotTrigger(List.of())
                .build();
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "error", 5));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertFalse(result.isPassed());
        assertTrue(result.renderDiffs().toLowerCase().contains("error count"));
    }

    @Test
    void match_mustNotTriggerViolated_fails() {
        GoldenExpectation ge = GoldenExpectation.builder()
                .expectedExitCode(0)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder().errors(0).warnings(0).info(0).build())
                .expectedDiagnostics(List.of())
                .mustNotTrigger(List.of(MustNotTriggerEntry.builder().approxLine(3).reason("boundary").build()))
                .build();
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "warning", 4));

        MatchResult result = new GoldenMatcher().match(actuals, 0, ge);

        assertFalse(result.isPassed());
        assertTrue(result.renderDiffs().toLowerCase().contains("mustnottrigger"));
    }

    @Test
    void match_severityMismatch_failsAsFnAndFp() {
        GoldenExpectation ge = GoldenExpectation.builder()
                .expectedExitCode(1)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder().errors(1).warnings(1).info(0).build())
                .expectedDiagnostics(List.of(exp("SEM-ATTR-001", "error", 5)))
                .mustNotTrigger(List.of())
                .build();
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ATTR-001", "warning", 5));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertFalse(result.isPassed());
    }

    @Test
    void match_multipleSameRuleIdAtDifferentLines_eachMatchedIndividually() {
        GoldenExpectation ge = GoldenExpectation.builder()
                .expectedExitCode(1)
                .expectedCounts(GoldenExpectation.ExpectedCounts.builder().errors(2).warnings(0).info(0).build())
                .expectedDiagnostics(List.of(exp("SEM-ENUM-001", "error", 8), exp("SEM-ENUM-001", "error", 11)))
                .mustNotTrigger(List.of())
                .build();
        List<ActualDiagnostic> actuals = List.of(diag("SEM-ENUM-001", "error", 8), diag("SEM-ENUM-001", "error", 11));

        MatchResult result = new GoldenMatcher().match(actuals, 1, ge);

        assertTrue(result.isPassed(), result.renderDiffs());
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.e2e.golden.GoldenMatcherTest"`
Expected: FAIL（GoldenMatcher 不存在）。

- [ ] **Step 4: 写 GoldenMatcher 实现**

```java
package com.huawei.theme.analysis.core.e2e.golden;

import java.util.ArrayList;
import java.util.List;

public class GoldenMatcher {

    private static final int MUST_NOT_TRIGGER_TOLERANCE = 2;

    public MatchResult match(List<ActualDiagnostic> actuals, int actualExitCode, GoldenExpectation expectation) {
        MatchResult result = new MatchResult(true);

        checkExitCode(actualExitCode, expectation, result);
        checkCounts(actuals, expectation, result);
        checkExpectedDiagnostics(actuals, expectation, result);
        checkMustNotTrigger(actuals, expectation, result);

        if (!result.getDiffs().isEmpty()) {
            return result;
        }
        return MatchResult.pass();
    }

    private void checkExitCode(int actual, GoldenExpectation exp, MatchResult result) {
        if (actual != exp.getExpectedExitCode()) {
            result.addDiff(String.format("exit code: expected %d got %d", exp.getExpectedExitCode(), actual));
        }
    }

    private void checkCounts(List<ActualDiagnostic> actuals, GoldenExpectation exp, MatchResult result) {
        int actualErrors = countBySeverity(actuals, "error");
        int actualWarnings = countBySeverity(actuals, "warning");
        int actualInfos = countBySeverity(actuals, "info");
        GoldenExpectation.ExpectedCounts expected = exp.getExpectedCounts();
        if (actualErrors != expected.getErrors()) {
            result.addDiff(String.format("error count: expected %d got %d", expected.getErrors(), actualErrors));
        }
        if (actualWarnings != expected.getWarnings()) {
            result.addDiff(String.format("warning count: expected %d got %d", expected.getWarnings(), actualWarnings));
        }
        if (actualInfos != expected.getInfo()) {
            result.addDiff(String.format("info count: expected %d got %d", expected.getInfo(), actualInfos));
        }
    }

    private void checkExpectedDiagnostics(List<ActualDiagnostic> actuals, GoldenExpectation exp, MatchResult result) {
        List<ActualDiagnostic> remaining = new ArrayList<>(actuals);
        for (ExpectedDiagnostic ed : exp.getExpectedDiagnostics()) {
            ActualDiagnostic matched = findAndRemove(remaining, ed);
            if (matched == null) {
                result.addDiff(String.format("FN: missing %s/%s near line %d (tolerance %d)",
                        ed.getRuleId(), ed.getSeverity(), ed.getApproxLine(), ed.getLineTolerance()));
            }
        }
        for (ActualDiagnostic leftover : remaining) {
            result.addDiff(String.format("FP: unexpected %s/%s at line %d col %d: %s",
                    leftover.getRuleId(), leftover.getSeverity(), leftover.getLine(), leftover.getCol(),
                    leftover.getMessage()));
        }
    }

    private ActualDiagnostic findAndRemove(List<ActualDiagnostic> pool, ExpectedDiagnostic ed) {
        for (int i = 0; i < pool.size(); i++) {
            ActualDiagnostic d = pool.get(i);
            if (matches(d, ed)) {
                pool.remove(i);
                return d;
            }
        }
        return null;
    }

    private boolean matches(ActualDiagnostic d, ExpectedDiagnostic ed) {
        if (!d.getRuleId().equals(ed.getRuleId())) {
            return false;
        }
        if (!d.getSeverity().equals(ed.getSeverity())) {
            return false;
        }
        return Math.abs(d.getLine() - ed.getApproxLine()) <= ed.getLineTolerance();
    }

    private void checkMustNotTrigger(List<ActualDiagnostic> actuals, GoldenExpectation exp, MatchResult result) {
        for (MustNotTriggerEntry entry : exp.getMustNotTrigger()) {
            for (ActualDiagnostic d : actuals) {
                if (Math.abs(d.getLine() - entry.getApproxLine()) <= MUST_NOT_TRIGGER_TOLERANCE) {
                    result.addDiff(String.format("mustNotTrigger violated at line %d: %s (reason: %s)",
                            d.getLine(), d.getRuleId(), entry.getReason()));
                    break;
                }
            }
        }
    }

    private int countBySeverity(List<ActualDiagnostic> actuals, String severity) {
        return (int) actuals.stream().filter(d -> severity.equals(d.getSeverity())).count();
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.e2e.golden.GoldenMatcherTest"`
Expected: PASS（9 tests）。

- [ ] **Step 6: Commit**

```bash
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/golden/MatchResult.java feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/golden/GoldenMatcher.java feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/golden/GoldenMatcherTest.java
git commit -m "feat(e2e): add golden matcher engine with FP/FN/line-tolerance matching"
```

---

## Task 4: GoldenDumper 工具（dump 当前 CLI 输出为 golden 草稿）

**Files:**
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/GoldenDumper.java`

**说明**：GoldenDumper 是测试工具类，提供静态方法把当前 CLI 在某 fixture 上的 JSON 输出 dump 成 `.expected.json` 草稿。供 Task 5 生成 golden 文件用，之后人工对照 `ANSWER_KEY.md` 复核。

- [ ] **Step 1: 写 GoldenDumper**

```java
package com.huawei.theme.analysis.core.e2e;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.huawei.theme.analysis.core.cli.CliMain;
import com.huawei.theme.analysis.core.e2e.golden.ActualDiagnostic;

/**
 * Dumps current CLI JSON output as a .expected.json draft for a fixture.
 * Usage: run as test to regenerate golden drafts, then human-review against ANSWER_KEY.md.
 */
public class GoldenDumper {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void dumpFixture(Path fixtureXml, Path outputGolden) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        int exitCode;
        try {
            exitCode = CliMain.run(new String[]{"--format", "json", "--no-color", fixtureXml.toString()});
        } finally {
            System.setOut(original);
        }
        String json = out.toString(StandardCharsets.UTF_8);
        ActualDiagnostic[] diags = extractDiagnostics(json);
        int errors = countSeverity(diags, "error");
        int warnings = countSeverity(diags, "warning");
        int infos = countSeverity(diags, "info");

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"fixture\": \"").append(fixtureXml.getFileName()).append("\",\n");
        sb.append("  \"expectedExitCode\": ").append(exitCode).append(",\n");
        sb.append("  \"expectedCounts\": { \"errors\": ").append(errors)
                .append(", \"warnings\": ").append(warnings)
                .append(", \"info\": ").append(infos).append(" },\n");
        sb.append("  \"expectedDiagnostics\": [\n");
        for (int i = 0; i < diags.length; i++) {
            ActualDiagnostic d = diags[i];
            sb.append("    { \"ruleId\": \"").append(d.getRuleId())
                    .append("\", \"severity\": \"").append(d.getSeverity())
                    .append("\", \"approxLine\": ").append(d.getLine())
                    .append(", \"lineTolerance\": 2 }");
            if (i < diags.length - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");
        sb.append("  \"mustNotTrigger\": []\n");
        sb.append("}\n");
        Files.writeString(outputGolden, sb.toString(), StandardCharsets.UTF_8);
    }

    private static ActualDiagnostic[] extractDiagnostics(String json) {
        JsonElement root = JsonParser.parseString(json);
        JsonElement diagsElement;
        if (root.isJsonObject() && root.getAsJsonObject().has("files")) {
            diagsElement = root.getAsJsonObject().getAsJsonArray("files").get(0).getAsJsonObject().get("diagnostics");
        } else {
            diagsElement = root.getAsJsonObject().get("diagnostics");
        }
        return GSON.fromJson(diagsElement, ActualDiagnostic[].class);
    }

    private static int countSeverity(ActualDiagnostic[] diags, String severity) {
        int c = 0;
        for (ActualDiagnostic d : diags) {
            if (severity.equals(d.getSeverity())) c++;
        }
        return c;
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :feature:analysis:compileTestJava`
Expected: 编译成功。

- [ ] **Step 3: Commit**

```bash
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/GoldenDumper.java
git commit -m "feat(e2e): add GoldenDumper utility to bootstrap golden files from current behavior"
```

---

## Task 5: 为所有 fixture 生成 .expected.json

**Files:**
- Create: `feature/analysis/src/test/resources/fixtures/**/*.expected.json`（共约 28 个）

**说明**：这一步用 GoldenDumper 生成草稿，再对照 `ANSWER_KEY.md`（complex/ 与 complex_expressions/ 已有详细答案表）与 dev-summary §7.3（e2e-pipeline 实跑结果表）人工复核。clean fixture 的 golden 为空诊断列表。

- [ ] **Step 1: 写一个临时 dump 测试生成所有草稿**

创建临时测试 `GoldenDumpRunner.java`（生成完即删）：

```java
package com.huawei.theme.analysis.core.e2e;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

@Disabled("Run manually to regenerate golden drafts: remove @Disabled, run, then re-add and delete this class")
class GoldenDumpRunner {
    @Test
    void dumpAllFixtures() throws Exception {
        Path fixturesRoot = Path.of("src/test/resources/fixtures");
        try (Stream<Path> walk = Files.walk(fixturesRoot)) {
            walk.filter(p -> p.toString().endsWith(".xml"))
                    .forEach(p -> {
                        try {
                            Path golden = Path.of(p.toString().replace(".xml", ".expected.json"));
                            GoldenDumper.dumpFixture(p, golden);
                            System.out.println("Dumped: " + golden);
                        } catch (Exception e) {
                            System.err.println("Failed: " + p + " " + e.getMessage());
                        }
                    });
        }
    }
}
```

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.e2e.GoldenDumpRunner"`
Expected: 在每个 .xml 旁生成 .expected.json 草稿。

- [ ] **Step 2: 对照 ANSWER_KEY.md 复核 complex/ 8 个 golden**

对 `complex/` 下每个 `.expected.json`，打开 `complex/ANSWER_KEY.md` 对应小节，逐条核对：
- ruleId 与 severity 是否与答案表一致
- approxLine 是否与"Approx Line"一致
- expectedCounts 是否与答案表条数一致
- 把 ANSWER_KEY.md 中"Valid Elements (no violations expected)"的合法元素行加入 `mustNotTrigger`，保护不误报

重点核对项（来自 ANSWER_KEY.md）：
- `deep_nesting_violations.xml`：16 errors / 1 warning
- `type_inference_edge_cases.xml`：13 errors
- `constraint_edge_cases.xml`：5 errors / 1 warning
- `variable_lifecycle_errors.xml`：注意已移除"前向引用"项（策略变更），dup_name 第三处可能未报告
- `trigger_command_combos.xml`：6 errors
- `scope_nesting_boundaries.xml`：6 errors（StereoView 3D 边界可能不触发，按实际）
- `expression_syntax_errors.xml`：9 errors / 2 warnings
- `enum_boundary_tests.xml`：按实际（Button scope 违规多项）

- [ ] **Step 3: 复核 complex_expressions/ 6 个 golden**

对照 `complex_expressions/ANSWER_KEY.md`：
- `chained_function_hell.xml`：4 errors
- `string_expression_errors.xml`：7 errors
- `precision_boundary_tests.xml`：0 errors / 5-6 warnings（注意"已移除：不做常量折叠"项）
- `array_index_edge_cases.xml`：1 error
- `operator_precedence_tests.xml`：2 errors
- `multi_element_expression_blast.xml`：16 errors / 1 warning（注意"已移除：前向引用"项）

- [ ] **Step 4: 复核 e2e-pipeline/ 7 个 golden**

对照 dev-summary §7.3 实跑结果：
- `clean/lockscreen_valid.xml`：0E/0W（expectedDiagnostics=[], expectedExitCode=0）
- `wallpaper_constraint_enum.xml`：5E/1W
- `lockscreen_type_and_ref.xml`：8E
- `charging_skin_cmd_nest.xml`：8E
- `widget_multi_violation.xml`：7E
- `lockscreen_nesting_var.xml`：按实跑
- `nondsl/` 下非 DSL 文件：expectedExitCode=0, expectedDiagnostics=[]（应被跳过）

- [ ] **Step 5: 复核 batch-inspection/ 与 dsl/ golden**

- `batch-inspection/clean/lockscreen_valid.xml`：0E/0W
- `batch-inspection/widget_missing_required.xml`、`wallpaper_invalid_enum.xml`、`lockscreen_multi_error.xml`：按实跑
- `batch-inspection/nested/` 下 2 个 DSL + 1 非 DSL
- `dsl/` 下 5 个：`valid_lockscreen.xml`、`valid_widget.xml` 应为 0 诊断；`error_quotes.xml`、`error_unclosed.xml` 应有 XML 错误；`regular_config.xml` 非 DSL 应跳过

- [ ] **Step 6: 删除临时 GoldenDumpRunner**

删除 `GoldenDumpRunner.java`（已生成 golden，不再需要）。

- [ ] **Step 7: 编译验证 golden 文件可被 parser 读取**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.e2e.golden.GoldenExpectationParserTest"`
Expected: PASS（parser 单元测试仍绿）。

- [ ] **Step 8: Commit**

```bash
git add feature/analysis/src/test/resources/fixtures/
git commit -m "test(e2e): add golden expectation files for all fixtures (28 files)"
```

---

## Task 6: FixtureCoverageTest 元测试

**Files:**
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/FixtureCoverageTest.java`

**目的**：扫描 `fixtures/` 下所有 `.xml`，断言每个都有同名 `.expected.json`。防止后续新增 fixture 时漏配 golden。

- [ ] **Step 1: 写失败测试 FixtureCoverageTest**

```java
package com.huawei.theme.analysis.core.e2e;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixtureCoverageTest {

    private static final Path FIXTURES_ROOT = Path.of("src/test/resources/fixtures");

    @Test
    void everyXmlFixture_hasMatchingGoldenFile() throws Exception {
        List<String> missing = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(FIXTURES_ROOT)) {
            walk.filter(p -> p.toString().endsWith(".xml"))
                    .forEach(p -> {
                        Path golden = Path.of(p.toString().replace(".xml", ".expected.json"));
                        if (!Files.exists(golden)) {
                            missing.add(p.toString());
                        }
                    });
        }
        assertTrue(missing.isEmpty(),
                "Following fixtures lack a .expected.json golden file:\n" + String.join("\n", missing));
    }

    @Test
    void everyGoldenFile_hasMatchingXmlFixture() throws Exception {
        List<String> orphans = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(FIXTURES_ROOT)) {
            walk.filter(p -> p.toString().endsWith(".expected.json"))
                    .forEach(p -> {
                        Path xml = Path.of(p.toString().replace(".expected.json", ".xml"));
                        if (!Files.exists(xml)) {
                            orphans.add(p.toString());
                        }
                    });
        }
        assertTrue(orphans.isEmpty(),
                "Following golden files have no matching .xml fixture:\n" + String.join("\n", orphans));
    }
}
```

- [ ] **Step 2: 运行测试验证通过**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.e2e.FixtureCoverageTest"`
Expected: PASS（2 tests，前提是 Task 5 已为所有 fixture 配齐 golden）。

- [ ] **Step 3: Commit**

```bash
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/FixtureCoverageTest.java
git commit -m "test(e2e): add fixture coverage meta-test enforcing golden pairing"
```

---

## Task 7: L3 GoldenDiagnosticMatchTest（in-process 严格 golden）

**Files:**
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/GoldenDiagnosticMatchTest.java`

**目的**：参数化测试，对每个 `.expected.json` 跑 `CliMain.run("--format","json","--no-color", fixturePath)` in-process，解析 JSON 输出，用 GoldenMatcher 校验。

- [ ] **Step 1: 写 GoldenDiagnosticMatchTest**

```java
package com.huawei.theme.analysis.core.e2e;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.huawei.theme.analysis.core.cli.CliMain;
import com.huawei.theme.analysis.core.e2e.golden.ActualDiagnostic;
import com.huawei.theme.analysis.core.e2e.golden.GoldenExpectation;
import com.huawei.theme.analysis.core.e2e.golden.GoldenExpectationParser;
import com.huawei.theme.analysis.core.e2e.golden.GoldenMatcher;
import com.huawei.theme.analysis.core.e2e.golden.MatchResult;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GoldenDiagnosticMatchTest {

    private static final Path FIXTURES_ROOT = Path.of("src/test/resources/fixtures");
    private static final Gson GSON = new Gson();

    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream capturedOut;

    @BeforeEach
    void captureStdout() {
        originalOut = System.out;
        originalErr = System.err;
        capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    static Stream<Arguments> goldenFixtures() throws Exception {
        List<Arguments> args = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(FIXTURES_ROOT)) {
            walk.filter(p -> p.toString().endsWith(".expected.json"))
                    .forEach(p -> args.add(Arguments.of(p, Path.of(p.toString().replace(".expected.json", ".xml")))));
        }
        return args.stream();
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("goldenFixtures")
    void cliOutput_matchesGoldenExpectation(Path goldenFile, Path fixtureXml) throws Exception {
        GoldenExpectationParser parser = new GoldenExpectationParser();
        GoldenExpectation expectation = parser.parse(goldenFile);
        GoldenMatcher matcher = new GoldenMatcher();

        capturedOut.reset();
        int exitCode = CliMain.run(new String[]{"--format", "json", "--no-color", fixtureXml.toString()});
        String json = capturedOut.toString(StandardCharsets.UTF_8);

        ActualDiagnostic[] diags = extractDiagnostics(json);
        List<ActualDiagnostic> diagList = java.util.Arrays.asList(diags);

        MatchResult result = matcher.match(diagList, exitCode, expectation);

        assertTrue(result.isPassed(),
                "Golden mismatch for " + fixtureXml + ":\n" + result.renderDiffs()
                        + "\n--- Actual JSON output ---\n" + json);
    }

    private ActualDiagnostic[] extractDiagnostics(String json) {
        JsonElement root = JsonParser.parseString(json);
        JsonElement diagsElement;
        if (root.isJsonObject() && root.getAsJsonObject().has("files")) {
            diagsElement = root.getAsJsonObject().getAsJsonArray("files").get(0).getAsJsonObject().get("diagnostics");
        } else {
            diagsElement = root.getAsJsonObject().get("diagnostics");
        }
        if (diagsElement == null || diagsElement.isJsonNull()) {
            return new ActualDiagnostic[0];
        }
        return GSON.fromJson(diagsElement, ActualDiagnostic[].class);
    }
}
```

- [ ] **Step 2: 运行测试**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.e2e.GoldenDiagnosticMatchTest"`
Expected: PASS（每个 golden fixture 一个用例，约 28 个）。若有失败，说明 golden 文件与当前行为不符，回到 Task 5 复核该 fixture 的 golden。

- [ ] **Step 3: Commit**

```bash
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/GoldenDiagnosticMatchTest.java
git commit -m "test(e2e): add L3 in-process golden diagnostic match test (parameterized)"
```

---

## Task 8: L4 FatJarSubprocessE2ETest + Gradle e2e task

**Files:**
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/FatJarSubprocessE2ETest.java`
- Modify: `feature/analysis/build.gradle`

**目的**：真实 `java -jar` 子进程跑 fat jar，校验打包/manifest/classpath 资源装配，复用 GoldenMatcher。

- [ ] **Step 1: 写 FatJarSubprocessE2ETest**

```java
package com.huawei.theme.analysis.core.e2e;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.huawei.theme.analysis.core.e2e.golden.ActualDiagnostic;
import com.huawei.theme.analysis.core.e2e.golden.GoldenExpectation;
import com.huawei.theme.analysis.core.e2e.golden.GoldenExpectationParser;
import com.huawei.theme.analysis.core.e2e.golden.GoldenMatcher;
import com.huawei.theme.analysis.core.e2e.golden.MatchResult;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FatJarSubprocessE2ETest {

    private static final Path FIXTURES_ROOT = Path.of("src/test/resources/fixtures");
    private static final Gson GSON = new Gson();
    private static String fatJarPath;

    @BeforeAll
    static void requireFatJar() {
        fatJarPath = System.getProperty("fatJar.path");
        Assumptions.assumeTrue(fatJarPath != null,
                "Fat jar E2E skipped: set -DfatJar.path (run via ./gradlew e2e)");
        Assumptions.assumeTrue(Files.exists(Path.of(fatJarPath)),
                "Fat jar not found at " + fatJarPath + " (run ./gradlew buildFatJar first)");
    }

    static Stream<Arguments> goldenFixtures() throws Exception {
        List<Arguments> args = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(FIXTURES_ROOT)) {
            walk.filter(p -> p.toString().endsWith(".expected.json"))
                    .forEach(p -> args.add(Arguments.of(p, Path.of(p.toString().replace(".expected.json", ".xml")))));
        }
        return args.stream();
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("goldenFixtures")
    void fatJarOutput_matchesGoldenExpectation(Path goldenFile, Path fixtureXml) throws Exception {
        GoldenExpectationParser parser = new GoldenExpectationParser();
        GoldenExpectation expectation = parser.parse(goldenFile);
        GoldenMatcher matcher = new GoldenMatcher();

        ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar", fatJarPath,
                "--format", "json", "--no-color", fixtureXml.toString());
        pb.redirectErrorStream(false);
        Process proc = pb.start();
        boolean finished = proc.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            throw new IllegalStateException("CLI subprocess timed out for " + fixtureXml);
        }
        int exitCode = proc.exitValue();
        String stdout = readAll(proc);
        String stderr = readAllError(proc);

        ActualDiagnostic[] diags = extractDiagnostics(stdout);
        List<ActualDiagnostic> diagList = java.util.Arrays.asList(diags);

        MatchResult result = matcher.match(diagList, exitCode, expectation);

        assertTrue(result.isPassed(),
                "Fat-jar golden mismatch for " + fixtureXml + ":\n" + result.renderDiffs()
                        + "\n--- stdout ---\n" + stdout
                        + "\n--- stderr ---\n" + stderr);
    }

    private String readAll(Process proc) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private String readAllError(Process proc) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private ActualDiagnostic[] extractDiagnostics(String json) {
        JsonElement root = JsonParser.parseString(json);
        JsonElement diagsElement;
        if (root.isJsonObject() && root.getAsJsonObject().has("files")) {
            diagsElement = root.getAsJsonObject().getAsJsonArray("files").get(0).getAsJsonObject().get("diagnostics");
        } else {
            diagsElement = root.getAsJsonObject().get("diagnostics");
        }
        if (diagsElement == null || diagsElement.isJsonNull()) {
            return new ActualDiagnostic[0];
        }
        return GSON.fromJson(diagsElement, ActualDiagnostic[].class);
    }
}
```

- [ ] **Step 2: 修改 build.gradle 增加 e2e task**

在 `build.gradle` 末尾（`buildFatJar` task 之后）追加：

```groovy
task e2e(type: Test) {
    group = 'verification'
    description = 'Fat-jar subprocess E2E with golden matching (L4)'

    dependsOn buildFatJar
    systemProperty 'fatJar.path', layout.buildDirectory.file('cli/dsl-analyzer.jar').get().asFile.absolutePath

    include '**/FatJarSubprocessE2ETest*'
    exclude '**/GoldenDiagnosticMatchTest*'
    exclude '**/GoldenMatcherTest*'
    exclude '**/GoldenExpectationParserTest*'
    exclude '**/FixtureCoverageTest*'

    useJUnitPlatform()
    shouldRunAfter test

    reports {
        junitXml.outputLocation = layout.buildDirectory.dir('test-results/e2e')
        html.outputLocation = layout.buildDirectory.dir('reports/tests/e2e')
    }
}
```

- [ ] **Step 3: 验证 e2e task 可配置**

Run: `./gradlew :feature:analysis:tasks --group verification`
Expected: `e2e` task 出现在 verification 组。

- [ ] **Step 4: 运行 e2e（需先 buildFatJar）**

Run: `./gradlew :feature:analysis:buildFatJar :feature:analysis:e2e`
Expected: PASS（每个 golden fixture 一个子进程用例）。若失败，检查 jar 路径与 golden 文件。

- [ ] **Step 5: 运行默认 test 确认 FatJar 测试被跳过**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.e2e.FatJarSubprocessE2ETest"`
Expected: 该测试被 Assumption 跳过（无 fatJar.path 系统属性），不计失败。

- [ ] **Step 6: Commit**

```bash
git add feature/analysis/src/test/java/com/huawei/theme/analysis/core/e2e/FatJarSubprocessE2ETest.java feature/analysis/build.gradle
git commit -m "feat(e2e): add L4 fat-jar subprocess E2E test and gradle e2e task"
```

---

## Task 9: AGENTS.md 门禁命令 + 全量验证

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1: 在 AGENTS.md 末尾追加测试门禁章节**

在 `AGENTS.md` 的 `## 测试命令` 章节后追加：

```markdown
## E2E 测试与 CI 门禁

### 分层测试

| 层 | 命令 | 用途 | 门禁 |
|---|---|---|---|
| L1-L3 单元/管线/In-Process Golden | `./gradlew :feature:analysis:test` | 777+ 单测 + L3 golden 匹配 | 本地/CI 阻断 |
| Core 隔离检查 | `./gradlew :feature:analysis:checkCoreIntellijDependency` | core 无 com.intellij import | CI 阻断 |
| Fat jar 装配 | `./gradlew :feature:analysis:buildFatJar` | 打包 core+GSON+ANTLR fat jar | CI 阻断 |
| L4 真实子进程 E2E | `./gradlew :feature:analysis:e2e` | `java -jar` 子进程 + golden 匹配 | CI 阻断 |

### CI 门禁总和命令

```bash
./gradlew clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e
```

全绿方可合并。本地快速开发可只跑 `./gradlew :feature:analysis:test`（不含 fat jar 子进程）。

### Golden 文件维护

- 每个 `fixtures/**/*.xml` 必须有同名 `.expected.json`（由 `FixtureCoverageTest` 强制）
- 新增 fixture：同时写 `.xml` 与 `.expected.json`
- 策略变更导致诊断变化：同步更新对应 `.expected.json`，commit message 说明变更原因
- golden 匹配策略：ruleId+severity+count 严格，行号 ±2 近似（`lineTolerance` 默认 2）
```

- [ ] **Step 2: 运行全量门禁验证**

Run: `./gradlew clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e`
Expected: 全绿。记录测试总数与 e2e 用例数。

- [ ] **Step 3: Commit**

```bash
git add AGENTS.md
git commit -m "docs: add E2E golden test gate commands to AGENTS.md"
```

---

## 自审清单（实施完成后）

- [ ] 所有 golden fixture 与 ANSWER_KEY.md / 实跑结果一致
- [ ] L3 `./gradlew test` 全绿（含现有 777 + 新增 golden/parser/matcher）
- [ ] L4 `./gradlew e2e` 全绿（fat jar 子进程）
- [ ] `FixtureCoverageTest` 强制 fixture-golden 配对
- [ ] AGENTS.md 门禁命令可复制粘贴执行
- [ ] 无新引入依赖（仅用已有 JUnit5 + GSON + Lombok）
