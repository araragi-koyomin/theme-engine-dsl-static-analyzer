---
module_ids: [M2, M4, M6]
phase: P1
doc_kind: report
status: active
created: 2026-07-20
---

# FEAT001 PHASE 5 TDD 执行日志

## 1. 基线

基线提交为 `d05753110e1ff0726304f5a4d6c1117cd69ccc13`，分支为 `codex/dsl-rule-sync-discovery`。开始时工作区只有已确认的 FEAT001 文档和 `docs/BACKLOG.md` 更新。

### 1.1 环境与外部服务

| 项目 | 实际结果 |
|---|---|
| OS / Java | Windows 11 10.0 amd64；Temurin 17.0.19 |
| Gradle | 8.2，使用本机已缓存的官方发行版建立基线 |
| `gradlew` | `distributionUrl` 指向不存在的 `C:/Users/30991/Downloads/gradle-8.2-bin.zip`，退出 1；G02 必须以 GitHub-hosted runner canary 修复其不可移植性 |
| GitHub CLI | 2.94.0；已登录 `araragi-koyomin` |
| 产品仓 | `araragi-koyomin/theme-engine-dsl-static-analyzer`，公开，默认分支 `main` |
| GitHub Models catalog | HTTP 200，共 37 个模型 |
| GitHub Models inference | HTTP 200；请求 `openai/gpt-4.1`、`temperature=0`、`seed=42`、`json_schema`，实际模型 `gpt-4.1-2025-04-14`，响应 `{"status":"ok"}` |

### 1.2 测试与覆盖率

组合全量命令在 120 秒上限内未完成，因此不记通过或失败；按 Goal 约定拆分后结果如下：

| Task | tests | failures | errors | skipped | 结果 |
|---|---:|---:|---:|---:|---|
| `:feature:analysis:test` | 98 | 0 | 0 | 35 | 通过；35 项为在普通 test 中跳过的 L4 子进程用例 |
| `:feature:analysis:e2e` | 35 | 0 | 0 | 0 | 通过；L4 实际执行 35/35 |
| `:feature:core-tests:test` | 808 | 0 | 0 | 2 | 通过；2 项为基线既有禁用项，后续不得增加 |
| `:feature:lsp:test` | 77 | 0 | 0 | 0 | 通过 |

`checkCoreIntellijDependency` 实际信号为 `PASSED (0 violations)`。JaCoCo LINE 为 `4273 covered / 5180 total = 82.4903%`，`jacocoTestCoverageVerification` 通过。

### 1.3 五份真实 DSL canary

使用 Gradle 8.2 构建实际 fat jar 后，以 Git Bash 运行 `java -jar feature/analysis/build/cli/dsl-analyzer.jar --format json <file>`：

| 真实语料 | errors | warnings | 关键实际信号 |
|---|---:|---:|---|
| `type_inference_edge_cases.xml` | 14 | 1 | 包含 `SEM-TYPE-001`、`SEM-TYPE-002`、`SEM-REF-001`、`SEM-ARR-001`、`SYN-EXPR-*` |
| `widget_multi_violation.xml` | 7 | 1 | 包含 `SEM-REQ-001`、`SEM-TRIG-*`、`SEM-NEST-001`、`SEM-SCOPE-001` |
| `wallpaper_constraint_enum.xml` | 5 | 1 | 包含 `SEM-IMG-002`、`SEM-IMG-003`、`SEM-ENUM-001` |
| `lockscreen_type_and_ref.xml` | 8 | 0 | 包含 `SEM-REF-003`、`SEM-TYPE-*`、`SYN-EXPR-ANTLR` |
| `clean/lockscreen_valid.xml` | 0 | 0 | `diagnostics` 精确为空 |

默认内置规则路径的预期是：不相关诊断相对本基线保持零 delta。远端 v2 激活后的目标 ruleId 必须产生声明的非零 delta，回滚后消失。

## 2. 逐任务 RED / GREEN / REFACTOR

后续每个 C/G/I 任务在独立提交前追加：失败命令与断言、最小通过命令、重构后命令、测试 canary mutation 和 commit SHA。
