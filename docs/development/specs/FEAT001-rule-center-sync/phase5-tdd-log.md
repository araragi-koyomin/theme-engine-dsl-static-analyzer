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

### C01：中心仓 JSON 契约模型

| 阶段 | 命令 | 实际信号 |
|---|---|---|
| RED | Gradle 8.2 `--no-daemon :feature:core-tests:test --tests "com.huawei.theme.analysis.core.rulecenter.RuleCenterJsonCodecTest"` | 退出 1；`RuleCenterJsonCodec`、`RuleCandidate`、`ConstraintVerification`、`DocumentConversionFeedback` 等 16 个缺失符号，证明测试先于实现 |
| GREEN | 同一目标测试命令 | 退出 0；5 个契约场景全部通过 |
| REFACTOR | 同一命令追加 `--rerun-tasks` | 退出 0；20 个 Gradle task 实际执行，未依赖 up-to-date 假绿 |
| 测试 canary | 临时让 `SKIPPED` 接受空 `skipReason`，只跑 `rejectsSkippedCandidateWithoutReasonAndValidationErrorWithoutFailure` | 退出 1；精确在测试第 52 行失败；恢复校验后全部 5 项重新通过 |

实现仅包含共享 DTO、枚举和严格 Gson codec，不加载规则、不调用模型、不访问网络。任务提交信息为 `feat(C01): add strict rule center JSON contracts`；最终 SHA 在 Phase 6 提交矩阵记录。

### C02：condition 能力登记表

| 阶段 | 命令 | 实际信号 |
|---|---|---|
| RED | Gradle 8.2 `--no-daemon :feature:core-tests:test --tests "com.huawei.theme.analysis.core.rulecenter.ConditionCapabilityRegistryTest"` | 退出 1；registry、analysis、capability 和 rejection 共 16 个缺失符号 |
| GREEN | 同一目标测试命令 | 退出 0；5 组测试通过，基础 grammar、`containsExpression`、固定 children filter/where-size 被区分 |
| REFACTOR | 同一命令追加 `--rerun-tasks` | 退出 0；20 个 Gradle task 实际执行 |
| 测试 canary | 临时使 `containsExpression` 正则不匹配，运行其精确测试方法 | 退出 1；测试第 37 行失败；恢复后强制重跑全绿 |

拒绝信号覆盖 `.endsWith()`、`number()`、`c => ...count()`、`element.parent.children`、不完整内置调用和依赖 child attrs 的未登记 Lambda。任务提交信息为 `feat(C02): register supported condition capabilities`。

### C03：严格 condition 接受器

| 阶段 | 命令 | 实际信号 |
|---|---|---|
| RED | Gradle 8.2 `--no-daemon :feature:core-tests:test --tests "com.huawei.theme.analysis.core.rulecenter.StrictConditionAcceptorTest"` | 退出 1；acceptor、结果和状态共 13 个缺失符号 |
| GREEN | 同一目标测试命令 | 退出 0；5 组真实 ANTLR lexer/parser 接受与拒绝测试通过 |
| REFACTOR | 同一命令追加 `--rerun-tasks` | 退出 0；20 个 Gradle task 实际执行 |
| 测试 canary（初次） | 移除 EOF 检查，但尾随输入使用 lexer 不认识的 `trailing` | 测试仍绿，证明原测试由 lexer 错误兜底、没有反证 EOF 门禁；该输入被废弃 |
| 测试 canary（修正） | 尾随第二段合法 condition token，再移除 EOF 检查 | 退出 1；精确在第 56 行失败；恢复 EOF 检查后 5 组测试强制重跑通过 |

该任务把“语法被接受”与“业务计算结果为 false”分离，并拒绝尾随合法 token、残缺表达式、未登记方法及 DSL 函数库函数。任务提交信息为 `feat(C03): strictly accept complete rule conditions`。

### C04：正反例真实验证运行器

| 阶段 | 命令 | 实际信号 |
|---|---|---|
| RED | Gradle 8.2 `--no-daemon :feature:core-tests:test --tests "*ConstraintVerificationRunnerTest"` | 退出 1；运行器、请求和结果共 10 个缺失符号 |
| GREEN | 同一目标测试命令 | 退出 0；5 组真实 `AstBuilder` + `ConstraintAnalyzer` 场景通过 |
| REFACTOR | 同一命令追加 `--rerun-tasks` | 退出 0；20 个 Gradle task 全部实际执行 |
| 测试 canary | 临时反转“正例必须包含目标 ruleId”的生产判断，只运行 `passesOnlyWhenProductionAnalyzerHitsPositiveAndMissesNegative` | 退出 1；精确在测试第 29 行失败；恢复后强制重跑 5 组测试通过 |

验证只把 fixture 文本交给当前静态分析链，不读取 `src` 等属性所指向的资源。XML 语法错误或当前脚本解析器不支持的内容得到 `FIXTURE_PARSE_ERROR`；condition 已被严格接受后，正例漏报和反例误报分别得到 `POSITIVE_FIXTURE_MISSED` 与 `NEGATIVE_FIXTURE_HIT`，不会伪装成 skipped。任务提交信息为 `feat(C04): verify constraints with production analyzer`。

### C05：已验证约束示例目录

| 阶段 | 命令 | 实际信号 |
|---|---|---|
| RED | Gradle 8.2 `--no-daemon :feature:core-tests:test --tests "*VerifiedConstraintExampleCatalogTest"` | 退出 1；示例、查询、关系、证据范围和目录共 49 个缺失符号 |
| GREEN | 同一目标测试命令 | 退出 0；5 组验证记录、资源语义、相关性、能力和数量限制场景通过 |
| REFACTOR | 同一命令追加 `--rerun-tasks` | 退出 0；20 个 Gradle task 全部实际执行 |
| 测试 canary | 临时绕过 `hasPassingVerification`，只运行 `excludesExampleWithoutPassingVerificationRecord` | 退出 1；精确在测试第 53 行失败；恢复后强制重跑全组通过 |

目录最多返回 3 条同关系、同 target kind 且 condition 能力覆盖查询要求的示例；相同元素和属性只影响排序，不把示例业务语义复制到新目标。缺验证记录、验证字段不一致、未登记 condition 和 `EXTERNAL_RESOURCE` 证据全部不建索引。任务提交信息为 `feat(C05): catalog only verified constraint examples`。

### C06：候选发布分流

| 阶段 | 命令 | 实际信号 |
|---|---|---|
| RED | Gradle 8.2 `--no-daemon :feature:core-tests:test --tests "*CandidatePublicationRouterTest"` | 退出 1；router、request、decision 共 13 个缺失符号 |
| GREEN | 同一目标测试命令 | 退出 0；5 组直接跳过、验证错误、待验证、已验证和 description 场景通过 |
| REFACTOR | 同一命令追加 `--rerun-tasks` | 退出 0；20 个 Gradle task 全部实际执行 |
| 测试 canary | 临时把 fixture 失败从 `VALIDATION_ERROR` 改成 `SKIPPED`，只运行 `fixtureFailureIsValidationErrorAndNeverSkipped` | 退出 1；精确在测试第 59 行失败；恢复后全组强制重跑通过 |

分流器只产生候选状态和稳定原因码，不生成 IDE 诊断。约束候选的外部资源语义、目标不明、未登记 grammar 与证据冲突直接 skipped；parser 已接受后的 fixture 失败保留 `validationFailure`。description 候选不把资源说明误当约束，可继续作为文档内容处理。任务提交信息为 `feat(C06): route candidate publication outcomes`。

### C07：两轮受控修复循环

| 阶段 | 命令 | 实际信号 |
|---|---|---|
| RED | Gradle 8.2 `--no-daemon :feature:core-tests:test --tests "*ConstraintRepairLoopTest"` | 退出 1；repair loop、strategy、context、proposal、request、outcome 共 17 个缺失符号 |
| GREEN | 同一目标测试命令 | 退出 0；4 组首轮修复、两轮耗尽、第三轮禁止和不可变字段保护场景通过 |
| REFACTOR | 同一命令追加 `--rerun-tasks` | 退出 0；20 个 Gradle task 全部实际执行 |
| 测试 canary | 临时把 `MAX_REPAIR_ATTEMPTS` 从 2 改成 3，只运行 `neverCallsRepairStrategyAThirdTime` | 退出 1；第三次调用精确触发测试第 76 行的 `AssertionError`；恢复后全组强制重跑通过 |

每轮修复上下文携带最多 3 条 C05 已验证示例，但每个 proposal 仍由 C04 真实验证器复验。原文证据指纹、目标指纹、目标元素、证据候选 ID 与规则 ID 均不可变；首次或第二次修复成功为 `VERIFIED`，两次失败或越权修改为 `VALIDATION_ERROR + REWORK_REQUIRED`。任务提交信息为 `feat(C07): bound constraint repair loop`。

### C08：完整规则包与发布报告组装

| 阶段 | 命令 | 实际信号 |
|---|---|---|
| RED | Gradle 8.2 `--no-daemon :feature:core-tests:test --tests "*RulePackageAssemblerTest"` | 退出 1；assembler、request/result、source artifact 和 report status 共 15 个缺失符号 |
| GREEN | 同一目标测试命令 | 退出 0；5 组完整快照、排除项、缺文件、坏 JSON、伪造验证记录场景通过 |
| REFACTOR | 同一命令追加 `--rerun-tasks` | 退出 0；20 个 Gradle task 全部实际执行 |
| 门禁/测试 canary | 临时从必需路径删除 `rules/rule_sources.json`，只运行 `missingRequiredRuleCategoryMakesReportFailed` | 退出 1；精确在测试第 112 行失败；恢复后全组强制重跑通过 |

组装器复制完整 `rules/`、`functions/`，按现有分类写入 `source-markdown/`，并生成 manifest 与 `verification/release-report.json`。摘要按相对路径和文件字节确定性计算，报告中的摘要字段以空值规范化以消除循环引用。安全排除项得到 `passed-with-exclusions`；结构、JSON/schema 或新发布 condition 验证错误得到 `failed`。任务提交信息为 `feat(C08): assemble complete verified rule packages`。

### C09：源文档作者反馈

| 阶段 | 命令 | 实际信号 |
|---|---|---|
| RED | Gradle 8.2 `--no-daemon :feature:core-tests:test --tests "*DocumentFeedbackServiceTest"` | 退出 1；publisher/service/request 与新增反馈字段共 10 个缺失符号 |
| GREEN | 同一目标测试命令 | 退出 0；4 组全结果反馈、资源跳过、无变更/发布失败和行号校验场景通过 |
| REFACTOR | 同一命令追加 `--rerun-tasks` | 退出 0；20 个 Gradle task 全部实际执行 |
| 测试 canary | 临时允许源证据 `startLine = 0`，只运行 `rejectsFeedbackItemWithoutValidSourceLine` | 退出 1；精确在测试第 99 行失败；恢复后全组强制重跑通过 |

服务通过 `DocumentFeedbackPublisher` 抽象发布，不绑定 GitHub 或公司消息系统。每项反馈保留原文行号和摘录；外部资源静态范围跳过对应 `AuthorAction.NONE`，验证错误对应 `REWORK_REQUIRED`，沿用旧规则通过 `previousRuleRetained` 明示。任务提交信息为 `feat(C09): publish actionable document feedback`。

### C10：GitHub Release 生产后端契约

| 阶段 | 命令 | 实际信号 |
|---|---|---|
| RED | Gradle 8.2 `--no-daemon :feature:core-tests:test --tests "*GitHubReleaseBackendTest"` | 退出 1；GitHub descriptor/asset/backend/evaluation 等共 31 个缺失符号 |
| GREEN | 同一目标测试命令 | 退出 0；5 组 approved、draft/pre-release、报告/资产、版本/摘要/兼容性及资产摘要场景通过 |
| REFACTOR | 同一命令追加 `--rerun-tasks` | 退出 0；20 个 Gradle task 全部实际执行 |
| 测试 canary | 临时绕过 draft/pre-release 拒绝，只运行 `rejectsDraftAndPrerelease` | 退出 1；精确在测试第 148 行失败；恢复后全组强制重跑通过 |

后端固定要求 `rule-package.zip`、`manifest.json`、`release-report.json`，并使用 GitHub REST Release asset 官方 `digest = sha256:...` 字段校验下载制品摘要。只有非 draft、非 pre-release、tag/manifest/report 一致、报告可发布且分析器兼容的 Release 才映射为 `GitHubApprovedRelease`。任务提交信息为 `feat(C10): gate approved GitHub rule releases`。

### C11：稳定 ReleaseCatalog 网关

| 阶段 | 命令 | 实际信号 |
|---|---|---|
| RED | Gradle 8.2 `--no-daemon :feature:core-tests:test --tests "*ReleaseCatalogContractTest"` | 退出 1；catalog、stable metadata/artifact、GitHub adapter/source 和 fixture backend 共 22 个缺失符号 |
| GREEN | 同一目标测试命令 | 退出 0；4 组双后端同构、draft 隐藏、下载篡改和传输字段隔离场景通过 |
| REFACTOR | 同一命令追加 `--rerun-tasks` | 退出 0；20 个 Gradle task 全部实际执行 |
| 测试 canary | 临时向插件可见的 `LatestRelease` 加入 `githubUrl`，只运行 `stablePluginContractContainsNoGitHubTransportFields` | 退出 1；精确在测试第 68 行失败；恢复后全组强制重跑通过 |

稳定接口仅暴露 `findLatest`、`findVersion`、`download(packageVersion)`；元数据不含 URL、token、仓库名或 GitHub 类型。GitHub adapter 与 `FixtureReleaseCatalog` 返回相同 `LatestRelease`/`ReleaseMetadata`/`RulePackageArtifact` 语义，并在交付字节前再次校验资产 SHA-256。任务提交信息为 `feat(C11): isolate release catalog gateway`。
