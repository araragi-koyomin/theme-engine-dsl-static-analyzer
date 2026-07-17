# 质量门禁与工作流改革 — 实现计划（Phase A）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地可证伪 canary 框架，交付 JaCoCo 真实覆盖率（从 0% → 真实数），全程以"真跑 `dsl-analyzer.jar` 对真实 DSL 脚本 diff"为**主验证**。

**Architecture:** spike（commit `888682f`）已验证 `feature:core-tests` 纯子项目能让 JaCoCo 记录非零覆盖。本计划把 ~880 core 单测分批迁入 `feature:core-tests`，加 `jacocoTestCoverageVerification`（带门禁 canary），并把 3 条 canary 规则 codify 进 `AGENTS.md`/`SOP.md`。每步改动 = 先存 canary 基线 → 改 → 重建 jar → 跑 canary → diff（test-only/配置改动预期空 diff；行为改动预期非空）。

**Tech Stack:** Java 17, Gradle 8.2 (--no-daemon), JaCoCo 0.8.11, JUnit 5.9.3, bash。

**Phase B（本计划不展开）:** FIX004 测试剧场治理（15 CRITICAL + 34 HIGH），用 Phase A 建的 canary 方法逐条治，单独计划。

**关键约束（AGENTS.md）:** 所有 `./gradlew` 必须加 `--no-daemon` + 时限（构建 120s）；禁止 PowerShell 管道（用 Grep 工具搜内容）；`core/**` 禁止 `import com.intellij.*`（`checkCoreIntellijDependency` 强制）。

**Canary 语料（5 个真实 DSL 脚本，稳定 .xml 输入）:**
- `fixtures/complex/type_inference_edge_cases.xml`（type/ref/expression，14 err + 1 warn）
- `fixtures/e2e-pipeline/widget_multi_violation.xml`
- `fixtures/e2e-pipeline/wallpaper_constraint_enum.xml`
- `fixtures/e2e-pipeline/lockscreen_type_and_ref.xml`
- `fixtures/e2e-pipeline/clean/lockscreen_valid.xml`（clean 负例，0 诊断）

---

## 文件结构

| 文件 | 责任 | 动作 |
|---|---|---|
| `scripts/canary-real-run.sh` | 构建 fat jar + 真跑语料 + 输出聚合 JSON | Create |
| `feature/core-tests/build.gradle` | 纯子项目（无 intellij 插件），JaCoCo 记录 core 覆盖 | Modify（spike 已建，本计划硬化） |
| `build.gradle`（root） | `subprojects{}` intellij 排除 `:feature:core-tests` | 已含（spike），本计划不动 |
| `feature/analysis/build.gradle` | （可选）分析侧 jacoco 保留作对照 | Modify（仅注释说明） |
| `feature/analysis/src/test/java/.../core/**` → `feature/core-tests/src/test/java/.../core/**` | ~880 单测迁移 | git mv（分批） |
| `feature/analysis/src/test/resources/fixtures/**` | 测试 fixture | **不搬**（core-tests 通过 sourceSet 指向 analysis 资源目录共享） |
| `AGENTS.md` | 可证伪性原则节 | Modify |
| `docs/SOP.md` | PHASE 1.5 行 + frontmatter | Modify |
| `docs/knowledge/lessons-learned.md` | LL-014/015 | Modify |

**留 `:feature:analysis`（不迁）:**
- `plugin/editor/DslExpressionHighlightingLexerTest`（需 intellij 平台）
- `core/e2e/FatJarSubprocessE2ETest`（L4 子进程，需 fat jar）
- `core/e2e/` + `core/e2e/golden/` 的 L3 golden 套件（in-process CliMain，与 fixture/golden 强耦合，留 analysis）

---

## Task 1: Canary 工具 + 基线

**Files:**
- Create: `scripts/canary-real-run.sh`

- [ ] **Step 1: 写 canary 脚本**

Create `scripts/canary-real-run.sh`:
```bash
#!/usr/bin/env bash
# 改动 canary: 构建 fat jar + 真跑真实 DSL 语料, 输出聚合诊断 JSON.
# 用法: bash scripts/canary-real-run.sh > canary.out
# 改动前/后各跑一次, diff 两个 .out. diff 空 = 改动对真实分析行为零影响.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
./gradlew --no-daemon :feature:analysis:buildFatJar --console=plain >/dev/null
JAR="feature/analysis/build/cli/dsl-analyzer.jar"
CORPUS=(
  "feature/analysis/src/test/resources/fixtures/complex/type_inference_edge_cases.xml"
  "feature/analysis/src/test/resources/fixtures/e2e-pipeline/widget_multi_violation.xml"
  "feature/analysis/src/test/resources/fixtures/e2e-pipeline/wallpaper_constraint_enum.xml"
  "feature/analysis/src/test/resources/fixtures/e2e-pipeline/lockscreen_type_and_ref.xml"
  "feature/analysis/src/test/resources/fixtures/e2e-pipeline/clean/lockscreen_valid.xml"
)
for f in "${CORPUS[@]}"; do
  echo "=== $f ==="
  java -jar "$JAR" --format json "$f"
done
```

- [ ] **Step 2: 跑 canary，存基线**

Run: `bash scripts/canary-real-run.sh > /tmp/canary-baseline.out`
Expected: 输出 5 段 `=== <file> ===` + JSON。`type_inference_edge_cases.xml` 段含 14 errors + 1 warning（ruleId 含 SEM-TYPE-001/002、SEM-REF-001、SEM-ARR-001、SYN-EXPR-001/002）；`lockscreen_valid.xml` 段 `"diagnostics": []`。

- [ ] **Step 3: 验证 canary 可重复（确定性）**

Run: `bash scripts/canary-real-run.sh > /tmp/canary-baseline2.out && diff /tmp/canary-baseline.out /tmp/canary-baseline2.out`
Expected: diff 空（同一 commit 两次跑输出一致）。若非空 → jar 构建非确定性导致输出漂移，先查（见 spec §8 风险），不可继续。

- [ ] **Step 4: 提交**

```bash
git add scripts/canary-real-run.sh
git commit -m "feat(quality-gates): 改动 canary 运行器 (真跑 jar 对真实 DSL 语料 diff)"
```

---

## Task 2: 硬化 `feature/core-tests/build.gradle`（资源共享 + 覆盖率范围）

spike 建的 `feature/core-tests/build.gradle` 只搬了一个自包含测试。本任务为后续批量迁移打基础：让 fixture 依赖测试能共享 analysis 资源目录、`classDirectories` 限 `core/**`。

**Files:**
- Modify: `feature/core-tests/build.gradle`

- [ ] **Step 1: 读当前 build.gradle**

Run: `cat feature/core-tests/build.gradle`（确认 spike 留下的内容，以下在它基础上改）。

- [ ] **Step 2: 改 sourceSet 共享 analysis 测试资源 + 限定 classDirectories**

确保 `feature/core-tests/build.gradle` 含如下（在 spike 基础上补 `sourceSets.test.resources` 与 `jacocoTestReport` 范围）：
```groovy
sourceSets {
    test {
        java.srcDirs 'src/test/java'
        // 共享 :feature:analysis 的测试资源(fixture/golden),避免搬迁/漂移
        resources.srcDirs 'src/test/resources', '../analysis/src/test/resources'
    }
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
    // 仅报告 core/** 覆盖(分母不含 plugin.*),使 % 有意义
    classDirectories.setFrom(fileTree("${project(':feature:analysis').layout.buildDirectory}/classes/java/main") {
        include 'com/huawei/theme/analysis/core/**'
    })
}
```
（若 spike 已写部分，合并去重；不要删 `apply plugin: 'jacoco'`/`java`/依赖。）

- [ ] **Step 3: 验证已迁的 DslTypeTest 仍绿 + 覆盖率非零**

Run: `./gradlew --no-daemon :feature:core-tests:test :feature:core-tests:jacocoTestReport --console=plain`
Expected: `:feature:core-tests:test` 3 tests pass；`jacocoTestReport` 生成；`feature/core-tests/build/reports/jacoco/test/jacocoTestReport.xml` report-level `LINE covered > 0`。

读 report 确认：
Run: `grep '<counter' feature/core-tests/build/reports/jacoco/test/jacocoTestReport.xml | tail -5`
Expected: 最后（report 级）`<counter type="LINE" ... covered="N" .../>` N>0。

- [ ] **Step 4: 改动 canary（预期空 diff — 配置改动）**

Run: `bash scripts/canary-real-run.sh > /tmp/canary-after.out && diff /tmp/canary-baseline.out /tmp/canary-after.out`
Expected: diff 空（build.gradle 改动不影响 analyzer 行为）。非空 → 查（配置改动不应碰诊断）。

- [ ] **Step 5: 提交**

```bash
git add feature/core-tests/build.gradle
git commit -m "build(quality-gates): core-tests 硬化(共享 analysis 测试资源 + classDirectories 限 core/**)"
```

---

## Task 3: 迁移批 1 — 小自包含包（core/shared, core/function, core/ruledsl, core/fileidentification）

**Files:**
- git mv: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/{shared,function,ruledsl,fileidentification}/**` → `feature/core-tests/src/test/java/com/huawei/theme/analysis/core/{...}/`

- [ ] **Step 1: 列出要迁的测试文件**

Run: `git ls-files 'feature/analysis/src/test/java/com/huawei/theme/analysis/core/shared/**' 'feature/analysis/src/test/java/com/huawei/theme/analysis/core/function/**' 'feature/analysis/src/test/java/com/huawei/theme/analysis/core/ruledsl/**' 'feature/analysis/src/test/java/com/huawei/theme/analysis/core/fileidentification/**'`
Expected: 列出这 4 个包下的所有 `*Test.java`（约 3+1+2+2=8 个，DslTypeTest 已迁不算）。

- [ ] **Step 2: 确认这些测试零 `import com.intellij`**

Run: 用 Grep 工具在上述文件搜 `^import com\.intellij` → 0 命中。若有命中 → 该文件留 `:feature:analysis`，不迁。

- [ ] **Step 3: git mv 批量迁移（保包路径）**

```bash
for pkg in shared function ruledsl fileidentification; do
  src="feature/analysis/src/test/java/com/huawei/theme/analysis/core/$pkg"
  dst="feature/core-tests/src/test/java/com/huawei/theme/analysis/core/$pkg"
  mkdir -p "$dst"
  git mv "$src" "$dst"
done
```

- [ ] **Step 4: 验证迁移后两侧测试绿**

Run: `./gradlew --no-daemon :feature:analysis:test :feature:core-tests:test --console=plain`
Expected: 两边 BUILD SUCCESSFUL。`:feature:analysis:test` 数下降（迁出的数），`:feature:core-tests:test` 数上升相等。若 analysis 编译断（某测试被未迁的依赖引用）→ 查依赖，必要时把被引用的辅助类一并迁。

- [ ] **Step 5: 验证覆盖率覆盖到新迁类**

Run: `./gradlew --no-daemon :feature:core-tests:jacocoTestReport --console=plain`
读 `feature/core-tests/build/reports/jacoco/test/jacocoTestReport.xml`：report-level `LINE covered` 应比 Task 2 后上升。

- [ ] **Step 6: 改动 canary（预期空 diff — 纯测试搬迁）**

Run: `bash scripts/canary-real-run.sh > /tmp/canary-after.out && diff /tmp/canary-baseline.out /tmp/canary-after.out`
Expected: diff 空（测试搬迁不改 analyzer 行为）。非空 → 红色警报：测试搬迁竟改了诊断，必有 main 被误改，立即 `git status`/`git diff` 查。

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "test(quality-gates): 迁 core/{shared,function,ruledsl,fileidentification} 单测到 core-tests"
```

---

## Task 4: 迁移批 2 — core/rulelibrary, core/syntaxanalysis, core/expression

**Files:** 同 Task 3 模式，包换成 `rulelibrary`、`syntaxanalysis`、`expression`。

- [ ] **Step 1: 列文件 + Grep 验证零 intellij import**（同 Task 3 Step 1-2，包换之）。
- [ ] **Step 2: git mv 迁移**（同 Task 3 Step 3，pkg 换成 `rulelibrary syntaxanalysis expression`）。
- [ ] **Step 3: 双侧测试绿** — Run: `./gradlew --no-daemon :feature:analysis:test :feature:core-tests:test --console=plain`（同 Task 3 Step 4）。
- [ ] **Step 4: 覆盖率上升** — Run `:feature:core-tests:jacocoTestReport`，读 report-level `LINE covered` 上升。
- [ ] **Step 5: 改动 canary（预期空 diff）** — `bash scripts/canary-real-run.sh > /tmp/canary-after.out && diff /tmp/canary-baseline.out /tmp/canary-after.out`，预期空。
- [ ] **Step 6: 提交** — `git commit -m "test(quality-gates): 迁 core/{rulelibrary,syntaxanalysis,expression} 单测到 core-tests"`

---

## Task 5: 迁移批 3 — core/quickfix, core/semanticanalysis

**Files:** 包 `quickfix`（含 `generators/`）、`semanticanalysis`（含 `analyzers/`、`model/`）。

- [ ] **Step 1: 列文件 + Grep 验证零 intellij import**（注意 `generators/`、`analyzers/`、`model/` 子目录）。
- [ ] **Step 2: git mv 迁移**（`pkg` 换成 `quickfix semanticanalysis`；保子目录结构）。
- [ ] **Step 3: 双侧测试绿** — `:feature:analysis:test :feature:core-tests:test`。M4 的 `VarRefAnalyzerTest`/`TypeAnalyzerTest` 等 FIX004 目标测试此时迁到 core-tests，**先原样迁、不改断言**（FIX004 留 Phase B）。若 `TypeAnalyzerTest` 的 stub 仍缺 `getAllSignatures()` 覆盖（3f95e4a 已补）→ 确认编译过。
- [ ] **Step 4: 覆盖率上升 + 改动 canary（预期空）** — 同上。
- [ ] **Step 5: 提交** — `git commit -m "test(quality-gates): 迁 core/{quickfix,semanticanalysis} 单测到 core-tests"`

---

## Task 6: 迁移批 4 — core/cli, core/batchinspection

**Files:** 包 `cli`、`batchinspection`（含 `model/`）。

- [ ] **Step 1-5: 同 Task 5 模式**（列文件 / Grep / git mv / 双侧绿 / canary 空 diff）。
- [ ] **Step 6: 提交** — `git commit -m "test(quality-gates): 迁 core/{cli,batchinspection} 单测到 core-tests"`

**注意:** `core/cli` 测试可能调 `CliMain.run`（in-process，非子进程）— 可迁。`FatJarSubprocessE2ETest`（`core/e2e/`）**不迁**（需 fat jar 子进程，留 analysis）。

---

## Task 7: 留守清单 + 最终 canary

**Files:** 无（验证 + 文档化）。

- [ ] **Step 1: 确认留守 `:feature:analysis` 的测试**

Run: `git ls-files 'feature/analysis/src/test/java/com/huawei/theme/analysis/**/*Test.java'`
Expected: 仅剩 `plugin/editor/DslExpressionHighlightingLexerTest` + `core/e2e/**`（FatJarSubprocessE2ETest + L3 golden 套件）。其余 core 单测应全在 `feature/core-tests/`。

- [ ] **Step 2: 全量门禁绿**

Run: `./gradlew --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e :feature:lsp:test :feature:core-tests:test :feature:core-tests:jacocoTestReport --console=plain`
Expected: 全绿。测试总数 ≈ 990+（analysis 留 ~100 + core-tests ~880 + lsp 75）。

- [ ] **Step 3: 改动 canary（预期空 diff — 全程纯测试搬迁）**

Run: `bash scripts/canary-real-run.sh > /tmp/canary-after.out && diff /tmp/canary-baseline.out /tmp/canary-after.out`
Expected: diff 空。这是整个迁移的**总 canary**：证明把 880 测试搬走没改 analyzer 一行行为。

- [ ] **Step 4: 读最终真实覆盖率**

Run: `grep '<counter' feature/core-tests/build/reports/jacoco/test/jacocoTestReport.xml | tail -5`
记录 report-level `LINE covered=X missed=Y`，算 `X/(X+Y)` = 真实 core 覆盖率。**把这个数写进 Task 8 阈值**。

- [ ] **Step 5: 提交（若有留守清理）**

若 Step 1 发现误迁/漏迁，修正后 `git commit -m "test(quality-gates): 迁移收尾(留守清单核对)"`。

---

## Task 8: `jacocoTestCoverageVerification` + 门禁 canary

**Files:**
- Modify: `feature/core-tests/build.gradle`

- [ ] **Step 1: 读真实覆盖率（来自 Task 7 Step 4）**

设 `REAL_COVERAGE` = 上一步算得的 `X/(X+Y)`（小数，如 0.42）。

- [ ] **Step 2: 写 `jacocoTestCoverageVerification`（阈值=真实数，作回归保护）**

在 `feature/core-tests/build.gradle` 加：
```groovy
jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = 'LINE'
                value   = 'COVEREDRATIO'
                // 阈值=当前真实覆盖率(回归保护:覆盖率下降则 build fail).
                // 真实数 >= 0.80 后,改为 0.80 落实 AGENTS.md 质量门禁(human 决策).
                minimum = ${REAL_COVERAGE}
            }
        }
    }
}
check.dependsOn jacocoTestCoverageVerification
```
（把 `${REAL_COVERAGE}` 换成 Step 1 的实际小数，如 `0.42`。）

- [ ] **Step 3: 门禁 canary — 证明 verification 能 trip**

临时把 `minimum` 调到一个不可能的高值（如 `1.0`）：
Run: `./gradlew --no-daemon :feature:core-tests:jacocoTestCoverageVerification --console=plain`
Expected: **BUILD FAILED**（覆盖率 < 100%）。这证明门禁能 fail（不是剧场）。若 BUILD SUCCESSFUL → 门禁是死的，查 `check.dependsOn` 与 rule 语法。
然后**改回** `minimum = ${REAL_COVERAGE}`，重跑 → BUILD SUCCESSFUL。

- [ ] **Step 4: 改动 canary（预期空 diff — 配置改动）**

Run: `bash scripts/canary-real-run.sh > /tmp/canary-after.out && diff /tmp/canary-baseline.out /tmp/canary-after.out`
Expected: diff 空。

- [ ] **Step 5: 把真实覆盖率 + 0.80 gap 记录给 human**

在提交信息里写明：`真实 core LINE 覆盖率 = X%`，`0.80 gap = (0.80 - X)`，`未把 0.80 写死进 check（待真实数达标后由 human 决定）`。

- [ ] **Step 6: 提交**

```bash
git add feature/core-tests/build.gradle
git commit -m "build(quality-gates): jacocoTestCoverageVerification (阈值=真实覆盖率 X%, 回归保护) + 门禁 canary 验证可 trip"
```

---

## Task 9: Codify — `AGENTS.md` 可证伪性节

**Files:**
- Modify: `AGENTS.md`（在"质量门禁"表后加一节）

- [ ] **Step 1: 读 AGENTS.md 定位插入点**

Run: Grep `质量门禁` in `AGENTS.md` → 找到"### 质量门禁"表，在其后、"### 阶段切换规则"前插入新节。

- [ ] **Step 2: 插入"可证伪性原则"节**

在"### 质量门禁"表后插入：
```markdown
### 可证伪性原则（Anti-Theater Canaries）

每个产物（改动/测试/门禁）必须有"能反证它在干活"的机制。无反证通道 = 剧场（0% 覆盖率报告、假绿测、零效果 SDD 都是此病）。三条 canary：

| canary | 触发 | 运行 | 信号 |
|---|---|---|---|
| **改动 canary** | 任何改 analyzer 行为的 fix/feat（纯 doc/纯 refactor 豁免且不走 SDD） | PHASE 2 前用 ≥1 真实 DSL 脚本跑 `java -jar dsl-analyzer.jar --format json <f>`，diff main vs 改后；新功能无 main 基线→构造应触发新行为的真实输入确认触发 | 先声明预期 delta（refactor/测试基建=预期空；行为修复/新功能=预期非空）；`actual≠expected`→查（空 diff 撞"声称行为修复"=剧场→废弃；非空撞"声称 refactor"=回归→查副作用）；`actual=expected`→进 SDD |
| **测试 canary** | 任何声称"防 bug X"的测试 | 注入 bug X（mutate SUT 重引入），跑该测试 | 绿→红=真测试；仍绿=剧场→修或删 |
| **门禁 canary** | 每个 CI 门禁（含 `jacocoTestCoverageVerification`） | 引入门禁时喂已知坏输入，确认门禁 trip（build fail/非 0 退出/测试红）；文档化一次 | 能 trip=活门禁；不能 trip=剧场。不每次 CI 跑，引入时一次+文档化 |

**meta-canary**：每条规则必须含"运行命令+信号"。无具体命令/信号的规则=剧场，不许入文。
```

- [ ] **Step 3: 验证 frontmatter 不受影响**（AGENTS.md 无 frontmatter 要求，跳过）。

- [ ] **Step 4: 提交**

```bash
git add AGENTS.md
git commit -m "docs(quality-gates): AGENTS.md 加'可证伪性原则'节(3 canary + meta-canary)"
```

---

## Task 10: Codify — `SOP.md` PHASE 1.5 + frontmatter

**Files:**
- Modify: `docs/SOP.md`

- [ ] **Step 1: 给 SOP.md 补 frontmatter（审计 R8）**

在文件**最顶**（`# SOP:` 之前）插入：
```yaml
---
module_ids: [CORE]
doc_kind: guide
status: active
created: 2026-07-17
---
```

- [ ] **Step 2: §2.2 SDD 流程表插 PHASE 1.5 行**

Run: Grep `2.2 SDD 流程导航` in `docs/SOP.md` → 找到流程表，在"1 需求澄清"行后插一行：
```markdown
| 1.5 改动 canary | 用真实 DSL 脚本跑 jar diff main vs 改后，声明预期 delta，actual≠expected→查 | — | canary 输出 diff |
```

- [ ] **Step 3: §1 Debug 加 canary 运行法**

在 §1.3"常见陷阱"后或 §1.2 测试定位后，加一小节：
```markdown
### 1.4 改动 canary（防质量剧场）

改动前/后各跑一次，diff 输出验证改动有（或如预期无）实效：
\`\`\`bash
bash scripts/canary-real-run.sh > /tmp/canary-before.out
# ...改动 + 重建...
bash scripts/canary-real-run.sh > /tmp/canary-after.out
diff /tmp/canary-before.out /tmp/canary-after.out
\`\`\`
纯 refactor/测试基建→预期空 diff；行为修复→预期非空 diff。详见 AGENTS.md"可证伪性原则"。
```

- [ ] **Step 4: 验证 frontmatter 脚本过**

Run: `bash scripts/check-frontmatter.sh docs/SOP.md`（若脚本支持单文件）或 `bash scripts/check-frontmatter.sh`（全量）→ SOP.md 不再报缺失。

- [ ] **Step 5: 提交**

```bash
git add docs/SOP.md
git commit -m "docs(quality-gates): SOP 加 PHASE 1.5 改动 canary + §1.4 运行法 + 补 frontmatter(R8)"
```

---

## Task 11: Codify — `lessons-learned.md` LL-014/015

**Files:**
- Modify: `docs/knowledge/lessons-learned.md`（追加 LL-014、LL-015；并把 `C:\Users\30991\Desktop\tmp\lesson.md` 的 LL-011/012/013 迁入）

- [ ] **Step 1: 追加 LL-014（JaCoCo 0% = 门禁剧场）**

按文件既有 7-slot 模板（状态/坑/根因/触发条件/修复/防护/来源锚点）追加：
- 状态：validated
- 坑：JaCoCo `jacocoTestReport` 一直报 LINE covered=0/6594，但 `test.exec` 非空、880 测试在跑。被当门禁存在数月，无人发现它报 0。
- 根因：gradle-intellij 插件强制 test JVM 用 `PathClassLoader`，JaCoCo load-time agent 无法记录经该 classloader 加载的项目类；又无门禁 canary，故 0% 报告长期"绿"。
- 触发条件：IntelliJ 插件模块套 JaCoCo 但无"门禁能 trip"的 canary。
- 修复：迁 core 单测到无 intellij 插件的 `feature:core-tests`（默认 classloader）→ JaCoCo 记录非零；加 `jacocoTestCoverageVerification` + 门禁 canary。
- 防护：每个 CI 门禁引入时必须喂已知坏输入证明能 trip（门禁 canary）。
- 来源锚点：审计 R1 + commit（Task 8）。

- [ ] **Step 2: 追加 LL-015（可证伪性原则）**

- 状态：validated
- 坑：流程层（FIX006 6 阶段 SDD 零效果）、测试层（假绿测掩盖真 bug）、门禁层（0% 覆盖率"绿"）三处同病。
- 根因：缺"可证伪性"——无机制反证"这个绿/流程/门禁"其实没在干活。
- 触发条件：任何产物加"通过"信号却不加"能 fail"的反证通道。
- 修复：给改动/测试/门禁各加一条 canary（见 AGENTS.md"可证伪性原则"）。
- 防护：meta-canary——规则必须含运行命令+信号，否则不许入文。
- 来源锚点：本计划 + `lesson.md` LL-011/012/013。

- [ ] **Step 3: 迁入 LL-011/012/013（来自 `C:\Users\30971\Desktop\tmp\lesson.md`）**

把 `lesson.md` 的 LL-011/012/013 三条按 7-slot 模板格式追加入 `lessons-learned.md`（内容已有，只调格式对齐既有条目）。`lesson.md` 本机文件不入仓。

- [ ] **Step 4: 提交**

```bash
git add docs/knowledge/lessons-learned.md
git commit -m "docs(quality-gates): 追加 LL-014(JaCoCo 0%门禁剧场)/LL-015(可证伪性) + 迁入 LL-011~013"
```

---

## Task 12: 最终门禁 + canary 总验

- [ ] **Step 1: 全量门禁**

Run: `./gradlew --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e :feature:lsp:test :feature:core-tests:test :feature:core-tests:jacocoTestReport :feature:core-tests:jacocoTestCoverageVerification --console=plain`
Expected: 全绿（`jacocoTestCoverageVerification` 用 Task 8 阈值通过）。

- [ ] **Step 2: 总改动 canary（整个 Phase A 对真实行为的总影响）**

Run: `bash scripts/canary-real-run.sh > /tmp/canary-final.out && diff /tmp/canary-baseline.out /tmp/canary-final.out`
Expected: diff 空。Phase A（测试搬迁 + 覆盖率基建 + 文档）对 analyzer 真实诊断**零影响**——这是整个改革"没改坏东西"的总证据。

- [ ] **Step 3: 把真实覆盖率 + canary 结果汇报给 human**

报告：`core 真实 LINE 覆盖率 = X%`、`0.80 gap`、`改动 canary 全程空 diff（ analyzer 行为未改）`、`门禁 canary 已验证可 trip`。请 human 决定 0.80 何时写死进 `check`。

- [ ] **Step 4: 不 push、不建 PR（等 human 决定 + 可选 reviewer agent）**

Phase A 完成。FIX004（Phase B）单独计划，用本 Phase 建的 canary 跑测试 canary。

---

## Self-Review（写计划后自查）

1. **Spec 覆盖**: spec §3.1 改动 canary → Task 1（工具）+ 每 Task 的 canary 步；§3.2 测试 canary → Phase B（本计划 Task 11 LL-015 记录方法，FIX004 用之）；§3.3 门禁 canary → Task 8 Step 3；§4 位置 → Task 9/10；§5.1 JaCoCo 迁移 → Task 2-7；§5.1 门禁 canary → Task 8；§5.3 codify → Task 9-11；§7 AC1 → Task 7 Step 4/Task 2 Step 3；AC2 → Task 8 Step 3；AC4 → Task 9-10；AC5 → Task 12 Step 1。**缺口**: spec §5.2 FIX004 → 本计划明确划入 Phase B（独立计划），非缺口。
2. **占位扫描**: `${REAL_COVERAGE}` 是 Task 7 Step 4 实测填入的变量，已在 Task 8 Step 1 说明换算，非占位。
3. **类型一致**: `feature:core-tests` 路径、`scripts/canary-real-run.sh`、`jacocoTestCoverageVerification` 跨任务命名一致。

无 inline 修正需要。
