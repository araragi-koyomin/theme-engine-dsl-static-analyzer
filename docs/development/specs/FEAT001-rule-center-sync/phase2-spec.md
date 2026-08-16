---
module_ids: [M2, M4, M6, CLI]
phase: P1
doc_kind: spec
status: active
created: 2026-07-20
---

# FEAT001 规则中心仓同步 — PHASE 2 规格定义

## 1. 目的与适用边界

本规格定义规则中心仓、中心转换流水线与编辑器客户端之间的**数据和行为契约**。它不规定网络协议、鉴权方式、存储实现或 LLM 提示词。

规则检查的事实来源只有待检查 DSL 的 XML/JSON 文本和已安装规则包。规则包不得要求读取 `src` 所指文件、查询网络、探测文件是否存在、推断文件类型/大小/时长，或模拟引擎运行。`src` 等路径属性除非存在明确的文本结构规则，否则是普通 `string`；文档中关于资源的说明只能作为 `description` 保存。

## 2. 术语

| 术语 | 含义 |
|---|---|
| 源文档 | 上传并获批的 Markdown 规范文件。 |
| 候选 | 从源文档任意位置抽取的、尚未决定是否发布为诊断的事实或约束。 |
| 发布约束 | 已通过全部质量门禁、写入规则 JSON 并会产生静态诊断的候选。 |
| 规则包 | 某一版本的完整规则库快照；客户端只能整体切换，不能增量合并。 |
| 已安装版本 | 客户端已验证并可供分析器加载的规则包版本。 |
| 当前版本 | 已安装版本中被客户端选作分析输入的版本。 |

## 3. 规则包契约

### SP-01：完整规则包目录

每个可发布版本必须是一个独立、完整的目录（或等价压缩制品）。其中 `rules/` 和 `functions/` 必须保持与当前项目资源目录完全相同的分类和文件名，不重新发明规则分类：

```text
rule-package/
  manifest.json
  rules/
    elements/...                 # 与现有 rules/elements/** 一致
    global_vars.json             # 与现有 rules/global_vars.json 一致
    rule_sources.json            # 与现有 rules/rule_sources.json 一致
  functions/
    dsl_functions.json           # 与现有 functions/dsl_functions.json 一致
  source-markdown/
    elements/<现有元素分类>/<元素名>/...
    global_vars/...
    rule_sources/...
  verification/
    release-report.json
```

`rules/` 与 `functions/` 是分析器加载的唯一规则输入。`rule_sources.json` 不是“source mappings”目录：它是现有的 `ruleId → category/description/docUrl` 映射，诊断借此找到对应说明链接。

`source-markdown/` 是随版本保存的原始 Markdown 副本，用于编辑器展示完整说明和追溯某条规则来自哪一版规范；它不参与分析，也不会被当作规则执行。其一级目录与 `rules/` 的逻辑目标对齐：例如 `rules/elements/view/Image.json` 对应的源文档放在 `source-markdown/elements/view/Image/`。一份 Markdown 同时解释多个元素时，可在各目标目录保留其同一版本副本；该文件仍通过摘要和源文档 ID 表示为同一份源文档，不会被误认为多份规范。

`verification/` 用于可追溯和审计，不能被分析器解释为可执行规则。包内必须包含运行当前 IDEA 静态检查所需的全部规则类别；缺少任一必需类别的包不可安装。

### SP-02：清单 `manifest.json`

```json
{
  "schemaVersion": 1,
  "packageVersion": "2026.07.20.1",
  "channel": "approved",
  "createdAt": "2026-07-20T10:00:00Z",
  "contentSha256": "<rules、functions、source-markdown 与 verification 的规范化内容摘要>",
  "minimumAnalyzerVersion": "<可选版本下限>",
  "sourceDocumentRevisions": [
    { "documentId": "image", "revision": "r42", "sha256": "<markdown 摘要>" }
  ]
}
```

契约：

1. `schemaVersion`、`packageVersion`、`channel`、`createdAt`、`contentSha256` 必填。
2. 首版仅接受 `channel = "approved"` 的制品。
3. `packageVersion` 在一个中心仓中唯一且不可复用；同一版本的内容摘要必须恒定。
4. `contentSha256` 覆盖除该字段本身外的全部包内容，并由中心仓在客户端可获取的可信发布信息中再次提供。
5. `minimumAnalyzerVersion` 存在时，低于该版本的客户端必须拒绝切换并说明兼容性原因。

中心仓的具体鉴权、签名和传输协议由公司基础设施决定；无论采用何种协议，客户端在安装前都必须同时核对“从受信任发布端取得的版本/摘要”和包内清单，二者不一致即失败。

### SP-03：规则 JSON 兼容性

`rules/` 中的元素、全局变量和 `rule_sources.json`，以及 `functions/dsl_functions.json`，必须满足当前分析器既有 JSON schema。未知必填字段、缺失必需规则类别、重复规则标识、无法解析的 JSON 或不兼容的 schema 都是安装拒绝条件。

描述性文本必须写入现有 `description` 字段或 `source-markdown/`；它本身不产生任何诊断。仅 `constraints` 中、且已被验证的条件才可产生诊断。

## 4. 全文候选与发布质量契约

### SP-04：候选记录 `RuleCandidate`

中心流水线扫描每份源文档的所有章节。`RuleCandidate` 不是运行时规则 JSON，也不会随意改变 IDE 诊断；它是一张中心仓的“提取工作单”，记录模型或规则程序从哪段原文提出了什么改动、该改动是否能被验证、以及最终是否发布。每个候选至少保存：

```json
{
  "candidateId": "cand-...",
  "documentId": "image",
  "documentRevision": "r42",
  "sourceEvidence": {
    "sectionPath": ["参数说明", "src"],
    "location": { "startLine": 51, "endLine": 53 },
    "excerpt": "原文摘录"
  },
  "target": {
    "kind": "elementAttribute",
    "element": "Image",
    "attribute": "src"
  },
  "proposedKind": "description | constraint | skipped",
  "proposedChange": {
    "field": "description | attrTypes.<name>.description | constraints[]",
    "value": "待写入的文本或完整 RuleConstraint 对象"
  },
  "status": "extracted | skipped | validating | repairing | validation-error | verified | published",
  "skipReason": "OUT_OF_STATIC_SCOPE | UNSUPPORTED_CONDITION_GRAMMAR | UNRESOLVED_TARGET | EVIDENCE_CONFLICT | null",
  "validationFailure": "POSITIVE_FIXTURE_MISSED | NEGATIVE_FIXTURE_HIT | FIXTURE_PARSE_ERROR | null"
}
```

字段含义如下：

| 字段 | 要回答的问题 | 示例 |
|---|---|---|
| `candidateId` | 如何在审批和报告中唯一指代这次提取？ | `cand-img-src-001` |
| `sourceEvidence` | 它来自 Markdown 的哪一段？ | “参数说明 / src，第 51–53 行，原文摘录” |
| `target` | 它想修改规则库的哪个对象？ | `Image` 的 `src` 属性 |
| `proposedKind` / `proposedChange` | 提议加说明、加约束，还是直接跳过？ | 为 `attrTypes.src.description` 写入说明 |
| `status` / `skipReason` / `validationFailure` | 质量门禁走到了哪一步，为什么没有发布？ | `skipped`：涉及资源时长；或 `validation-error`：正例未命中 ruleId |

`sourceEvidence` 必须保留原文位置和摘录；候选不能仅保存模型结论。候选扫描不因章节名、章节缺失或文档顺序而跳过文本。

`target.kind` 的合法值仅限当前分析器能够表达的对象，例如 `element`、`elementAttribute`、`parentChildRelation`、`globalVariable`、`functionSignature`、`ruleSource`。无法定位目标时，候选立即标为 `skipped`，不能发布，也不进入人工审核队列。

### SP-05：约束发布的四道硬门

一个 `proposedKind = "constraint"` 的候选只有同时满足下列条件，才可进入规则包：

1. **目标明确**：目标能映射为一个已有或同包定义的 DSL 元素、属性、父子关系、变量或函数。
2. **纯文本可判定**：违规与否只依赖 XML/JSON AST 中的标签、属性、值和结构；不得依赖任何外部资源、路径语义、引擎执行结果或运行环境。
3. **可执行验证**：条件能被当前规则 condition 能力完整接受（grammar 或已登记的 evaluator 内置能力），并以该候选的正例得到指定 `ruleId`，以反例不得到该 `ruleId`。
4. **证据无冲突**：它与同一文档证据、当前已发布包和包内示例无冲突；冲突必须显式标为 `skipped`。

任一门失败时，候选不得写入 `constraints`。其中“外部资源语义”“不支持的 condition grammar”“无法定位目标”三类内容必须直接 `skipped`：不尝试转换、不自动修复、不进入人工审核队列。可安全表达的参数说明和功能说明可作为 `description` 发布；其余失败项也以 `skipped` 留下可追溯反馈。

### SP-05a：已验证约束示例库

中心流水线必须从已发布且拥有通过 `ConstraintVerification` 的约束中构建只读示例库。每个示例至少包含：目标类型、适用元素/属性、condition、真实正反例、规则 ID、所用 condition 能力及来源证据。候选提取可按目标和约束类型检索这些示例，作为受控 few-shot 示例或结构模板，以降低生成不支持 condition 的概率。

condition 能力必须显式登记，而不能由模型猜测。目前登记的 evaluator 内置能力包括 `containsExpression(element.attrs['attr'])`，以及固定形态的 `element.children.where(c -> c.tagName == 'Tag').size() <op> <number>` / `filter` 计数表达式；它们会在进入基础 grammar 前被预处理。它们不等价于支持任意函数、任意 Lambda 或任意对象方法调用。

DSL 表达式函数库 `functions/dsl_functions.json` 中的 `sin`、`max`、`int`、`strContains` 等函数服务于被检查的 DSL 表达式的签名/类型分析，不会自动成为规则 `condition` 的可调用函数。示例库只提供“如何表达当前分析器已经会检查的结构”的参考，不是自动放行名单：新候选仍必须独立通过 SP-05 与 SP-06。不得把示例中资源路径、枚举值或业务语义机械复制到不相干目标。

### SP-05b：源文档转换反馈 `DocumentConversionFeedback`

每份已处理源文档修订必须生成并回传给上传人的反馈：

```json
{
  "documentId": "image",
  "documentRevision": "r42",
  "conversionStatus": "PUBLISHED | PUBLISHED_WITH_SKIPS | PUBLISHED_WITH_ERRORS | NO_APPLICABLE_CHANGE | RELEASE_FAILED",
  "releaseVersion": "2026.07.20.1",
  "summary": { "published": 3, "descriptionOnly": 4, "skipped": 2 },
  "items": [
    {
      "sourceEvidence": { "startLine": 51, "endLine": 53, "excerpt": "原文摘录" },
      "outcome": "PUBLISHED | DESCRIPTION_ONLY | SKIPPED | VALIDATION_ERROR",
      "reasonCode": "OUT_OF_STATIC_SCOPE | UNSUPPORTED_CONDITION_GRAMMAR | UNRESOLVED_TARGET | EVIDENCE_CONFLICT | POSITIVE_FIXTURE_MISSED | NEGATIVE_FIXTURE_HIT | FIXTURE_PARSE_ERROR | null",
      "authorAction": "NONE | OPTIONAL_REWRITE | REWORK_REQUIRED"
    }
  ]
}
```

反馈通过中心仓的上传页面、站内通知或公司既有消息渠道送达；具体通道不属于本仓库实现。`OPTIONAL_REWRITE` 必须给出可行动说明：例如“补充明确的标签和属性名”或“改写为仅依赖 XML/JSON 结构的断言”。`OUT_OF_STATIC_SCOPE` 仅说明该项不会成为静态诊断，不要求作者为了静态检查而重写资源能力说明。

### SP-06：约束验证记录 `ConstraintVerification`

每个已发布 `constraints[]` 条目必须有一条验证记录：

```json
{
  "ruleId": "SEM-IMG-002",
  "condition": "element.attrs['src'] != null AND element.attrs['srcExp'] != null",
  "parserAccepted": true,
  "positiveFixture": "fixtures/SEM-IMG-002/positive.xml",
  "negativeFixture": "fixtures/SEM-IMG-002/negative.xml",
  "positiveObservedRuleIds": ["SEM-IMG-002"],
  "negativeObservedRuleIds": [],
  "evidenceCandidateIds": ["cand-..."],
  "status": "passed"
}
```

契约：

- `parserAccepted` 必须来自真实条件解析器，不能由字符串匹配或 LLM 声称替代。
- 正例必须包含该 `ruleId`；反例不得包含该 `ruleId`。若同一脚本触发其他规则，可以存在于 `ObservedRuleIds` 中，但不得影响对目标 ruleId 的断言。
- parser 拒绝 condition 时，候选属于 `UNSUPPORTED_CONDITION_GRAMMAR`，立即 `skipped`。parser 已接受、但正例/反例运行失败时，候选必须标为 `validation-error` 并进入受控修复循环；不得直接标为 skipped。
- 修复循环每次必须保留同一份原文证据和目标，只允许修改候选 condition 或 fixture；每次修改后重新运行真实 parser、正例和反例。最多两次修复尝试。两次仍失败时，候选保留为 `validation-error`，不得写入 `constraints`，并向上传人发出 `REWORK_REQUIRED` 反馈；这不自动阻断其他已验证改动组成完整包。
- 若失败候选意图修改一条已发布约束，新包继续保留上一版已验证约束，并把“源文档修订尚未反映”写入报告。发布报告必须列出所有 skipped、validation-error 和“沿用上一版约束”的候选及原因，并将相同信息写入 `DocumentConversionFeedback`，防止“未发布”被静默丢失。

### SP-07：发布报告 `release-report.json`

发布报告必须记录包版本、清单摘要、参与源文档修订、全部候选计数、按状态分类的候选列表、全部 `ConstraintVerification`、JSON schema 校验结果和包完整性校验结果。

报告状态只能是：

| 状态 | 含义 | 能否进入 approved |
|---|---|---|
| `passed` | 所有候选均已发布或安全地转为 description。 | 可以。 |
| `passed-with-exclusions` | 存在 skipped、validation-error 或沿用上一版的候选，但最终写入包的每条约束都已通过验证。 | 可以；须随发布记录公开排除项。 |
| `failed` | 包结构、摘要、JSON/schema、函数库、最终约束验证或发布报告本身失败。 | 不可以。 |

只有 `passed` 或 `passed-with-exclusions` 的包可被中心仓标记为 `approved`；候选失败绝不能以“让它通过”为目的修改原文证据或放松正反例断言。

## 5. 中心仓与客户端同步契约

### SP-08：只读发布目录接口

客户端只依赖下列逻辑操作；实际可映射为 HTTP、公司制品库 SDK 或其他受控通道：

| 操作 | 输入 | 成功输出 | 保证 |
|---|---|---|---|
| 查询最新版本 | 当前版本（可空）、IDEA 插件分析器版本 | `LatestRelease` | 只返回 approved 包；不返回不可兼容或未通过发布报告的包。 |
| 下载规则包 | `packageVersion` | 不透明完整制品字节流及其发布摘要 | 制品对应唯一版本，摘要可供客户端校验。 |
| 查询指定版本 | `packageVersion` | `ReleaseMetadata` | 用于历史版本恢复；仅返回客户端允许安装的 approved 包。 |

`LatestRelease` 至少包含 `packageVersion`、`createdAt`、`contentSha256`、`minimumAnalyzerVersion`、变更摘要和下载定位信息。通知文案只可基于此元数据，例如“DSL 规则更新：2026.07.20.1”，不得暗示更新已经生效。

### SP-09：IntelliJ IDEA 插件更新发现与状态机

首版不要求中心仓直接向 IDE 进程推送消息。IDEA 插件采用**拉取检查**：启动后检查一次；IDE 持续打开时每 24 小时检查一次；并提供“检查 DSL 规则更新”手动操作（设置页与规则更新通知中均可进入）。中心仓日后即使提供推送，推送也只能作为触发一次相同查询的提示，不能绕过查询、用户确认和完整性校验。

用户拒绝某个版本后，插件记录该版本为“已忽略”：在该 IDEA 会话内不再重复弹窗；下次定时检查只有发现更高版本才再次提醒。用户可随时点击“检查 DSL 规则更新”，该操作会重新显示当前最新 approved 版本，即使它曾被忽略。

```text
CURRENT(v) --发现 approved 新版--> UPDATE_AVAILABLE(w)
UPDATE_AVAILABLE(w) --用户拒绝/关闭--> CURRENT(v)
UPDATE_AVAILABLE(w) --用户确认--> DOWNLOADING(w)
DOWNLOADING(w) --下载/摘要/解包失败--> CURRENT(v)
DOWNLOADING(w) --成功--> VALIDATING(w)
VALIDATING(w) --任一校验失败--> CURRENT(v)
VALIDATING(w) --成功--> STAGED(w)
STAGED(w) --原子切换成功--> CURRENT(w)
STAGED(w) --切换或加载失败--> CURRENT(v)
CURRENT(w) --用户选择回滚 v--> CURRENT(v)
```

契约：

1. 只有用户确认，才允许从 `UPDATE_AVAILABLE` 进入下载和切换；启动、定时或手动检查最新版本都不改变当前规则。
2. 下载文件必须先置于 staging 区；只有摘要、清单、JSON schema、完整性、版本兼容性和发布报告均通过，才允许切换。
3. 切换必须是原子的：客户端在任一时刻只向分析器暴露一个完整包。失败后当前版本不变。
4. 成功切换后，至少保留上一个已安装版本作为回滚目标。回滚也必须经过同样的包完整性和可加载性校验。
5. 任何失败必须向用户提供失败阶段和可行动原因；不得将未验证包标记为已安装或当前版本。

### SP-10：IDEA 编辑器加载契约

首版只要求 IntelliJ IDEA 插件从一个“当前规则包定位”读取规则。未安装远端包时，该定位指向随发行物内置的基线完整包；已切换远端包时，IDEA 中的静态检查和文档展示必须使用同一已验证版本。

插件可以在下一次分析会话或明确的规则库重载后生效，但必须向用户明确显示已生效版本。不得出现“编辑器文档使用新包、诊断仍使用旧包”或反向分裂。LSP、VS Code 和 CLI 的同步在后续功能中按同一原则接入，不是本规格的交付范围。

## 6. 异常与拒绝策略

| 情况 | 契约结果 |
|---|---|
| 中心仓不可达或超时 | 保持当前版本；提示检查失败，不产生更新弹窗。 |
| 用户未确认 | 保持当前版本，不下载、不切换。 |
| 摘要、清单或包结构不一致 | 拒绝安装，删除/隔离 staging 制品，保持当前版本。 |
| 某个候选 condition 不能解析 | 直接 `skipped`；反馈上传人，其他通过项可继续组成 `passed-with-exclusions` 包。 |
| 某个候选的 condition 已解析，但正反例验证不通过 | 标为 `validation-error` 并最多修复两次；耗尽后向上传人发出 `REWORK_REQUIRED`，并可沿用上一版安全约束。 |
| 最终包中任一已写入约束不能解析，或包结构/摘要/schema/函数库校验失败 | 中心仓拒绝发布；客户端不应看到该版本。 |
| 包不兼容当前分析器 | 不提供更新或显示“需升级分析器”，不得切换。 |
| 新包加载或原子切换失败 | 自动恢复原当前版本，并报告失败阶段。 |
| 文档语义无法在静态文本范围内证实 | 直接 `skipped`；原始 MD 仍可随包保留，绝不生成诊断。 |

## 7. 验收测试清单与 PHASE 1 可追溯性

| Spec | 对应验收标准 | 最少测试场景 |
|---|---|---|
| SP-04、SP-05 | AC-01 | 同一约束分别置于功能概述、参数说明、约束章节；均留下候选与原文证据。 |
| SP-03、SP-05 | AC-02 | `src` 的大小/时长/格式描述只改变 description；规则包不存在由该描述推导的值检查。 |
| SP-05、SP-06、SP-07 | AC-03、AC-04 | 有效共存约束的正反例通过；不可解析 condition 和外部资源语义候选均不能发布。 |
| SP-01、SP-02、SP-08、SP-09 | AC-05、AC-07 | 仅 approved 完整包可被发现；用户确认后才下载、校验和切换。 |
| SP-09、SP-10 | AC-06 | 下载、摘要、schema、加载、切换各阶段失败均仍用旧包；用户可回滚；IDEA 的静态检查和文档展示指向同一版本。 |
| SP-05b、SP-07 | AC-08 | 上传包含已发布、说明类和跳过项的 MD；上传人收到带原文位置、原因和可选重写建议的反馈。 |

## 8. 需要在 PHASE 3 落定的实现选择

- 包的实际制品格式、安装位置、保留版本数量与原子目录切换方式。
- 中心仓的实际 API、鉴权和签名/信任根；它们必须实现 SP-08 的逻辑契约。首版更新发现采用 SP-09 的启动检查、24 小时轮询和手动检查。
- 候选抽取中 LLM、规则模板及人工审批的协作方式；无论实现如何，必须满足 SP-04 至 SP-07。
- 条件 grammar 的扩展或严格 lint 的模块边界，以及旧包中已有不可解析条件的迁移策略。
- IDEA 插件内规则包安装位置、保留版本数量与文档展示如何读取 `source-markdown/`；LSP、CLI 与 VS Code 是后续接入目标，不在首版范围内。
