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

### G01：GitHub Models 候选抽取适配器

| 阶段 | 命令 | 实际信号 |
|---|---|---|
| RED | Gradle 8.2 `--no-daemon :feature:core-tests:test --tests "*GitHubModelsCandidateExtractionServiceTest"` | 退出 1；抽取 service/request/result、inference client/request/response 和异常共 20 个缺失符号 |
| GREEN | 同一目标测试命令 | 退出 0；5 组结构化请求、证据/行号、发布绕过、语义证据和身份/去重场景通过 |
| REFACTOR | 同一命令追加 `--rerun-tasks` | 退出 0；20 个 Gradle task 全部实际执行 |
| 测试 canary | 临时允许模型候选返回 `published`，只运行 `rejectsModelAttemptToBypassGateWithPublishedStatus` | 退出 1；精确在测试第 60 行失败；恢复后全组强制重跑通过 |
| 真实 inference smoke | 以 `gh auth token` 仅注入进程内存，POST GitHub Models `openai/gpt-4.1`，`temperature=0`、`seed=42`、`json_schema` | HTTP 成功；实际模型 `gpt-4.1-2025-04-14`；响应 `candidateCount=0`、`schemaValid=true`；token 未输出 |

生产 HTTP client 使用 GitHub Models 官方 inference endpoint、`models: read` 对应 Bearer token 与版本化 REST header。抽取结果记录请求/实际模型、prompt 版本、prompt/文档/原始响应 SHA-256；模型只能产生 `EXTRACTED` 候选，精确原文摘录和行号不匹配即拒绝。任务提交信息为 `feat(G01): extract candidates with GitHub Models`。

### G02：PR 文档转换、验证、修复与作者反馈 workflow

| 阶段 | 命令 | 实际信号 |
|---|---|---|
| RED | Gradle 8.2 `--no-daemon :feature:core-tests:test --tests "*RuleCenterValidationOrchestratorTest"` | 退出 1；`CandidateExtractionService`、批量请求/结果和 orchestrator 共 10 个缺失符号，证明 workflow 不是先写 shell 外壳后补测试 |
| GREEN | Gradle 8.2 `--no-daemon :feature:core-tests:test --tests "*RuleCenterValidationOrchestratorTest" --tests "*GitHubModelsConstraintRepairStrategyTest" --tests "*RuleCenterWorkflowContractTest"` | 退出 0；单/多文档、说明写入、同 ID 覆盖、外部资源排除、普通 string 防误约束、真实 fixture 修复、GitHub 权限与产物契约通过 |
| REFACTOR/回归 | `./gradlew.bat --no-daemon :feature:core-tests:test :feature:analysis:checkCoreIntellijDependency` | 退出 0；879 tests、0 failure、0 error、2 个既有 skipped；core IntelliJ dependency 0 violations |
| 测试 canary | 临时移除 `conditionUsesOnlyDeclaredLiteralValues` 门禁，只运行 `modelCannotTurnAnOrdinaryStringAttributeIntoAnUndeclaredValueConstraint` | 退出 1；模型生成的 `src == 'video.mp4'` 会被错误发布，测试精确在第 141 行变红；恢复后通过 |
| workflow 权限 canary | 临时把 `models: read` 改为 `models: none`，只运行 `validationWorkflowHasModelsPermissionAndProducesAuthorFeedback` | 退出 1；精确在 workflow contract 第 33 行变红；恢复后通过 |

真实链路使用临时 Image Markdown，并以 `gh auth token` 仅注入当前 Gradle 进程，运行 `./gradlew.bat --no-daemon ruleCenterValidateDocument`。首次运行因模型证据行号不准退出 1；加入“全文精确唯一摘录定位”后，模型省略 Markdown 反引号仍被严格拒绝；再加入“只在规范化文本唯一匹配时回填原始完整行”后越过证据门禁。随后完整包暴露当前内置库存在跨元素同 ID 历史规则；门禁改为仅 grandfather 未被本次修改的历史冲突，本次发布 ID 若参与冲突仍失败。

最终真实运行退出 0，实际模型为 GitHub Models `openai/gpt-4.1`，报告 `passed`：唯一候选覆盖 `SEM-IMG-002`，condition 为 `src`/`srcExp` 同时存在，真实正例观察到该 ruleId、反例未观察到；未生成文件格式、存在性、大小或时长约束。产物为 `candidates.json`、`feedback.json`、`release-report.json`、`audit.json`、`feedback-summary.md` 和完整临时规则包。token 未写文件、未输出。任务提交信息为 `feat(G02): validate rule documents in GitHub Actions`。

### G03：approved Release 发布门禁与三项固定资产

| 阶段 | 命令 | 实际信号 |
|---|---|---|
| RED | `./gradlew.bat --no-daemon :feature:core-tests:test --tests "*RulePackagePublicationPreparerTest" --tests "*RuleCenterPublishWorkflowContractTest"` | 退出 1；publication preparer/result 共 4 个缺失符号，publish workflow 文件不存在 |
| GREEN | `./gradlew.bat --no-daemon :feature:core-tests:test --tests "*RulePackagePublicationPreparerTest" --tests "*RulePackageZipExtractorTest" --tests "*RuleCenterPublishWorkflowContractTest" --tests "*RuleCenterValidationOrchestratorTest"` | 退出 0；三项资产、确定性 zip、failed/digest 门禁、zip-slip、上一版基线与 workflow 顺序共 14 个场景通过 |
| REFACTOR/回归 | `./gradlew.bat --no-daemon :feature:core-tests:test :feature:analysis:checkCoreIntellijDependency` | 退出 0；887 tests、0 failure、0 error、2 个既有 skipped；core IntelliJ dependency 0 violations |
| 发布门禁 canary | 临时把 publishable status 判断改为“status 非空”，只运行 `failedReportTripsGateBeforeAnyReleaseAssetIsCreated` | 退出 1；测试第 94 行发现 `failed` 报告不再抛错且生成了资产；恢复后全组通过 |
| YAML/本地发布 smoke | PyYAML 读取两个 workflow；随后对 G02 的真实通过包依次运行 `ruleCenterPrepareRelease` 与 `ruleCenterExtractBaseline` | 两个 YAML 均可解析；发布门禁输出 `release_gate=passed` 与 tag `rules-v2026.07.20.9001`；安全恢复门禁输出 `baseline_gate=passed` |

发布工作流只在受保护 `main` 的规则 Markdown 更新或显式手动触发时运行，并绑定 `dsl-rule-production` environment。已有正式 Release 时，它先下载固定名 `rule-package.zip`，执行 zip-slip/条目数/解压大小保护及 manifest/report/content digest 复验，再把完整 rules、functions、source-markdown 与 revisions 作为增量转换基线；首版才回退仓库内置规则。main 合并提交上的模型提取和真实验证全部重跑，`RulePackagePublicationPreparer` 再次核对 `passed` / `passed-with-exclusions`、schema、完整性和双摘要，之后才允许 `gh release create` 上传 `rule-package.zip`、`manifest.json`、`release-report.json`。任务提交信息为 `feat(G03): publish approved GitHub rule releases`。

### Review A 修复 1：原文证据边界与候选身份

| 阶段 | 命令 | 实际信号 |
|---|---|---|
| RED | `./gradlew.bat --no-daemon :feature:core-tests:test --tests "*RuleCenterValidationOrchestratorTest"` | 新增“模型谎报静态范围”和“无关证据虚构语义”后退出 1；两个场景均被错误发布 |
| GREEN | 同一目标测试命令 | 退出 0；外部资源事实、非逐字描述、非规范性证据与未在证据中出现的 condition 字面量均不可发布 |
| RED | `./gradlew.bat --no-daemon :feature:core-tests:test --tests "*GitHubModelsCandidateExtractionServiceTest" --tests "*RuleCenterValidationOrchestratorTest.duplicateCandidateIdsAcrossDocumentsAbortTheBatchBeforeApplication"` | 退出 1；响应 schema 暴露四类 P1 不可应用目标，解析器接受该目标，跨文档同 candidateId 未终止批次 |
| GREEN/REFACTOR | `./gradlew.bat --no-daemon :feature:core-tests:test --tests "*GitHubModelsCandidateExtractionServiceTest" --tests "*RuleCenterValidationOrchestratorTest"` | 退出 0；schema 与运行时均只接受 element/elementAttribute，candidateId 在整个批次唯一且在写 staged-rules 前检查 |
| 测试 canary | 临时移除外部语义判定中的 `!externalSemantics`，只运行 `lyingStaticTextFlagCannotConvertExternalFileDurationOrExistenceSemantics` | 退出 1；“文件必须存在且时长不超过 30 秒”被模型谎报为静态后错误进入验证，测试精确变红；恢复后目标测试通过 |

这组修复不依赖模型自报的 `staticTextOnly` 或 `evidenceConflict` 得出安全结论。确定性门禁以原文逐字证据、规范性措辞、condition 所引用属性/字面量和外部资源事实关键词交叉判定；未通过时只形成 skipped 反馈，不写入规则 JSON。P1 模型输出目标同步收窄为当前应用器真实支持的元素及元素属性，避免“schema 声称支持、发布阶段才静默丢失”。

### Review A 修复 2：完整包清单与生产加载复验

| 阶段 | 命令 | 实际信号 |
|---|---|---|
| RED | `./gradlew.bat --no-daemon :feature:core-tests:test --tests "*RulePackageAssemblerTest" --tests "*RulePackagePublicationPreparerTest" --tests "*RulePackageZipExtractorTest"` | 退出 1；缺少 inventory 契约，空元素目录、基线文件丢失、生产加载失败和反斜杠路径攻击均无对应实现 |
| GREEN/REFACTOR | 同一命令并追加 `--tests "*RuleCenterValidationOrchestratorTest"` | 退出 0；完整性、真实加载、发布前 inventory 复验、跨平台 zip 路径保护及编排基线传递全部通过 |
| 门禁 canary | 临时移除 publication preparer 的 manifest/实际 inventory 比对，只运行 `manifestInventoryTripsGateEvenWhenAnAttackerRecomputesBothContentDigests` | 退出 1；删除唯一元素规则并重算 manifest/report 双摘要后会错误产出 Release，测试精确变红；恢复门禁后通过 |

manifest 现在保存排序后的 `rules/` 与 `functions/` 文件清单。编排器在任何模型改写前记录上一版完整清单，组装结果必须是该清单的超集且至少包含一个元素规则；随后使用生产 `JsonRuleLoader` 和 `JsonFunctionSignatureLoader` 重载最终目录。发布准备阶段重新扫描清单并与 manifest 精确比对，因此“只剩少量合法 JSON 后重算摘要”的截断包不能发布。ZIP 基线恢复还会在所有操作系统上拒绝反斜杠、绝对路径和盘符路径。
