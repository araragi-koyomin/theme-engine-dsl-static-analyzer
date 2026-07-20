---
module_ids: [CORE, E2E]
doc_kind: spec
status: active
created: 2026-07-17
---
# 质量门禁与工作流改革 — 设计规格（Lean Spec）

> **路径说明**：本规格刻意采用**单文件 lean spec**（非 `FIX00N/FEAT00N` 六阶段 SDD 目录），以 dogfood 本规格设计的"轻量路径"（见 §3.1 改动 canary：canary 证明改动"简单且有效"→跳过 PHASE 1-4）。这是对 `doc-management` 命名规范的**有意偏离**，原因是：本规格本身是对 SDD 过度工程化的改革（LL-011），用六阶段 SDD 套路来改革"别用六阶段 SDD"是 meta 反讽。偏离已在此声明。

## 1. 背景与诊断

三处同一病灶（证据见 `docs/development/reports/test-theater-audit-2026-07-15.md` + `lesson.md` LL-011/012/013 + 本次审计）：

| 层面 | 症状 | 证据 |
|---|---|---|
| 流程 | 6 阶段 SDD + 15 commit 产出**零效果**，同事 1 commit 同效 | LL-011（FIX006 废弃） |
| 测试 | 假绿测掩盖真 bug（`assertTrue(isEmpty())` 被当成"报 SEM-REF-001"） | test-theater 报告 C1（VarRef）、C2（TypeAnalyzer null 库→FIX003） |
| 门禁 | JaCoCo 0% 覆盖率报告一直存在，没人发现它报 0 | 本次审计 R1；`feature/analysis/build/reports/jacoco/test/jacocoTestReport.xml` LINE covered=0/6594 |

**诊断**：病根 = **缺"可证伪性"**。整套流程没有机制能告诉你"这个绿/这个流程/这个门禁"其实没在干活——全是"看起来对"，没有"能反证它在量"的通道。用户已确认此诊断。

## 2. 目标与非目标

**目标**：
- G1 给改动/测试/门禁三类产物各加一条"可证伪 canary"（能反证它在干活的机制）。
- G2 交付两个具体修复作为首批 dogfood：JaCoCo 覆盖率基础设施（实测从 0% → 真实数）、FIX004 测试剧场（15 CRITICAL 优先 bug-masking 项）。
- G3 把 3 条 canary 极简 codify 进 `AGENTS.md`/`SOP.md`。

**非目标**：
- 不重写 6 阶段 SDD（只插一个 PHASE 1.5 前置门 + 一节原则）。
- 不在测得真实覆盖率前把 0.80 硬门禁写进 `check`（避免把"测不出来"伪装成"达标"）。
- 不归档 `p0-bugfix`（与本改革无关）。

## 3. 设计：三条可证伪 canary

每条 canary 必须含**触发/运行/信号**三要素——规则本身可证伪（你能查它是否被执行），这是 §6 meta-canary 护栏的要求。

### 3.1 改动 canary（Change Delta）— LL-012 防护
- **触发**：任何改 analyzer 行为的 fix/feat。纯 doc 豁免；纯 refactor 豁免**且不走 SDD**（无行为变化，无需 spec）。
- **运行**：PHASE 2 前用 ≥1 真实 DSL 脚本跑 `java -jar dsl-analyzer.jar --format json <file>`，diff `main` vs 改后输出。新功能无 main 基线 → 改为构造"应触发新行为"的真实输入，确认触发。
- **信号**：先声明**预期 delta**（refactor/测试基建改动=预期空 diff；行为修复/新功能=预期非空 diff）。跑真实脚本，`actual ≠ expected` → 查：空 diff 撞上"声称行为修复" = 剧场 → 废弃/重定界（如 FIX006）；非空 diff 撞上"声称 refactor" = 副作用回归 → 查。`actual = expected` → canary 过，进 SDD。
- **同时是 LL-011 解药**：canary 若证明改动"简单且有效"（如一行参数改动产生**预期内**可见 diff），则跳过 PHASE 1-4 走轻量路径（本 spec 即此例）。

### 3.2 测试 canary（Negative Control）
- **触发**：任何声称"防 bug X"的测试。
- **运行**：注入 bug X（mutate SUT 重引入该 bug），跑该测试。
- **信号**：绿→红 = 真测试；仍绿 = 剧场 → 修断言或删测试。
- **应用**：FIX004 的 15 CRITICAL 逐个用此法治理；每个被修测试随附一次 bug-注入 run 作证据。

### 3.3 门禁 canary（Gate Liveness）
- **触发**：每个 CI 门禁（含新增 `jacocoTestCoverageVerification`）。
- **运行**：引入门禁时喂已知坏输入，确认门禁 trip（build fail / 非 0 退出 / 测试红）。文档化一次。
- **信号**：能 trip = 活门禁；不能 trip = 剧场。**不每次 CI 跑**（太慢），引入时一次 + 文档化。
- **应用**：JaCoCo `jacocoTestCoverageVerification` 引入时，注入一个 0% 覆盖类确认 verification 能 fail——这正是 JaCoCo 0% 失败本该有的护栏。

## 4. 位置

- `AGENTS.md`：质量门禁表后加一节"可证伪性原则（Anti-Theater Canaries）"，3 条规则各 2-3 行 + 触发/运行/信号。
- `SOP.md`：§2.2 SDD 流程导航表插一行 `PHASE 1.5 改动 canary`（PHASE 2 前置）；§1 Debug 加 canary 运行法。**顺手补 `SOP.md` frontmatter**（审计 R8）。
- `feature/analysis/build.gradle`（或 `feature/core-tests/build.gradle`）：新增 `jacocoTestCoverageVerification` + 阈值（见 §5.1）。
- `docs/knowledge/lessons-learned.md`：新增 LL-014（JaCoCo 0% = 门禁剧场）、LL-015（可证伪性原则）。

## 5. dogfood 执行顺序

### 5.1 JaCoCo 全量迁移（交付 G2-a）
1. 按 spike caveats（commit `888682f` 已验证）把 ~880 core 测试搬到 `feature:core-tests`：保留根 `build.gradle:59` 的 intellij 插件排除；`jacocoTestReport.classDirectories` 限 `core/**`；fixture 依赖的测试逐个评估（搬资源 or 留 `:feature:analysis`）；L4 `FatJarSubprocessE2ETest` + `buildFatJar` 留 `:feature:analysis`。
2. 加 `jacocoTestCoverageVerification`：实测 real core LINE = 82.51% >= 0.80 → 阈值 = **0.80**（AGENTS.md 质量门禁），wired into `check`（commit 4423ac2）。门禁 canary 已验证可 trip（minimum=1.0 → FAIL；0.80 → PASS）。0.80 而非 0.825：留 2.5% 缓冲防 JaCoCo 微小波动假 fail，避免门禁因 0.01% 抖动变剧场。
3. **门禁 canary**：写一个 canary 测试注入 0% 覆盖类，确认 `jacocoTestCoverageVerification` fail——证明门禁活着（§3.3）。

### 5.2 FIX004 治理（交付 G2-b）
1. 优先 bug-masking 项：C1（`VarRefAnalyzerTest` `@` 掩盖 SEM-REF-001，但 FIX002 已改 `@` 跳过，此测试断言可能已过时——先跑 canary 确认现状）、C2（`TypeAnalyzer` null 函数库静默吞 SEM-TYPE-*，联动 FIX003）。
2. 每个被修/被删测试**带测试 canary**：注入它防的 bug，确认 fail（§3.2）。
3. 其余 13 CRITICAL + 34 HIGH 按 test-theater 报告分级处理；本 spec 不展开逐条方案（避免 lean spec 变重）。

### 5.3 框架 codification（G3）
- 写 `AGENTS.md` 可证伪性节 + `SOP.md` 插入 + LL-014/015。与 §5.1/§5.2 并行。

## 6. meta-canary 护栏

每条 canary 规则必须含"运行命令 + 通过/失败信号"。**无具体命令/信号的规则 = 剧场，不许入文。** 这是防"改革自己变剧场"的护栏（meta-canary）。本规格每条规则已给出运行命令与信号，故入文。

## 7. 验收标准（可测试）

- AC1 `feature:core-tests` 的 `jacocoTestReport` LINE covered > 0 且为合理真实数（非 0 非 100% 伪值）。
- AC2 `jacocoTestCoverageVerification` 在注入 0% 覆盖类时 build fail（门禁 canary trip）——证据为一次 canary run 输出。
- AC3 FIX004 C1/C2 被修测试，附 bug-注入 run 证据证明测试由绿变红（测试 canary trip）。
- AC4 `AGENTS.md` 含"可证伪性原则"节，3 条规则各含触发/运行/信号；`SOP.md` 含 PHASE 1.5 行 + frontmatter。
- AC5 全量 E2E 门禁仍绿：`./gradlew --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e :feature:lsp:test :feature:core-tests:test`。

## 8. 风险

| 风险 | 缓解 |
|---|---|
| 改革自己变剧场（meta 反讽） | §6 meta-canary：规则必须含运行命令+信号，否则不许入文 |
| 全量迁移 ~880 测试引入回归 | 按 spike 已验证模式分批搬，每批跑 `:feature:analysis:test` + `:feature:core-tests:test`；fixture 依赖逐个评估 |
| 0.80 硬门禁破坏构建 | 真实数测得前不写死 0.80；先用回归保护阈值 |
| FIX004 逐条治理再变重流程 | 本 spec 不展开 FIX004 逐条方案，留给执行阶段按 canary 逐条决策 |

## 9. 来源锚点
- LL-011/012/013：`C:\Users\30991\Desktop\tmp\lesson.md`（用户本机，待迁入 `docs/knowledge/lessons-learned.md`）
- 审计 R1（JaCoCo 0%）、R2（测试剧场）：本次审计报告
- spike 验证：commit `888682f` 于 `fix/quality-gates`
