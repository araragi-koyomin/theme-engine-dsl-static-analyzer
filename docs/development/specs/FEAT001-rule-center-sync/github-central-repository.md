---
module_ids: [M2, M4, M6]
phase: P1
doc_kind: architecture
status: active
created: 2026-07-20
---

# FEAT001 GitHub 中心仓首版接入与可迁移接口

## 1. 决策

首版以 **GitHub 私有仓库 + GitHub Actions + GitHub Release** 实现中心仓的文档、流水线和已审批规则包发布。

GitHub 不是 IDEA 插件的固定依赖。插件只认识公司规则中心的稳定发布契约；GitHub 是该契约的第一个后端。未来替换为公司 CodeHub、GitLab 或制品库时，不修改规则包格式、IDEA 同步流程或用户交互，只替换中心仓后端适配器。

## 2. 用一句话理解各自的职责

| 组件 | 它负责什么 | 它不负责什么 |
|---|---|---|
| GitHub 仓库 | 保存 MD、版本历史、PR 审批和发布配置。 | 直接向 IDEA 用户做鉴权。 |
| GitHub Actions | 将已审批 MD 转为候选/规则包，运行验证，生成反馈和发布资产。 | 让不通过验证的约束进入规则包。 |
| GitHub Release | 保存一个不可变版本的完整规则包 zip、manifest 和发布报告。 | 作为插件长期绑定的业务 API。 |
| 公司规则中心网关 | 以公司 SSO 鉴权，向插件提供“检查最新版本 / 下载指定版本”。 | 重新解释 MD 或规则 JSON。 |
| IDEA 插件 | 向网关查询、请求用户确认、校验并原子切换规则包。 | 访问 GitHub 仓库、持有 GitHub Token 或运行转换流水线。 |

```mermaid
flowchart LR
    Author[文档作者] --> PR[GitHub Pull Request]
    PR --> Validate[GitHub Actions: 转换与验证]
    Validate --> Feedback[Check / PR 反馈]
    Feedback --> Merge[审批并合并 main]
    Merge --> Publish[GitHub Actions: 完整包发布]
    Publish --> Release[GitHub Release assets]
    Release --> Gateway[公司规则中心网关]
    Gateway --> Idea[IDEA 插件]
```

## 3. GitHub 在完整流程中的作用

### 3.1 文档进入与作者反馈

文档作者通过 PR 提交 MD，而不是直接上传一个会立即影响用户的 JSON。PR 工作流执行：全文候选提取、已验证示例检索、condition 能力校验、正反例验证和 `DocumentConversionFeedback` 生成。

工作流把结果作为 GitHub Check 和 PR 评论/链接反馈给作者：

- 哪些说明或约束会发布；
- 哪些段落因外部资源语义、目标不明或不支持 grammar 而跳过；
- 哪些已解析候选正反例失败，要求作者按反馈返工；
- 每项均带原文行号与原因码。

这样，文档作者看到的是“这份修订将如何影响静态检查”，而不是等待插件用户反馈后才知道转换错误。

### 3.2 审批与发布

`main` 受分支保护：只有通过文档审批和 Actions 检查的 PR 可以合并。合并后发布工作流必须在合并提交上**重新**执行转换和验证，避免把 PR 的临时结果当作正式发布。

发布工作流只有在 release report 为 `passed` 或 `passed-with-exclusions` 时才创建正式 GitHub Release。发布资产固定为：

```text
rule-package.zip
manifest.json
release-report.json
```

资产名保持固定，版本由 Release tag 与 manifest 表达，避免客户端下载协议随版本拼接文件名。zip 内必须是 SP-01 规定的完整包结构，包含 `rules/`、`functions/`、`source-markdown/` 与 `verification/`。Release tag 使用 `rules-v<packageVersion>`；发布说明列出版本、变更摘要、跳过/validation-error 数量和最小分析器版本。

GitHub Release 适合作为版本制品仓库：Release API 支持按版本管理 Release 与上传/下载 release assets；而 Actions artifact 是工作流运行的产物，适合报告、日志和同一流水线内传递，不作为 IDEA 的长期发布通道。[GitHub Releases API](https://docs.github.com/en/rest/releases/releases?apiVersion=latest) [GitHub Actions artifacts](https://docs.github.com/en/actions/concepts/workflows-and-actions/workflow-artifacts?apiVersion=2022-11-28)

### 3.3 给 IDEA 分发

IDEA 不直接调用 GitHub。网关读取 approved GitHub Release 资产，对插件提供：

```text
GET /v1/dsl-rules/latest?analyzerVersion=<version>
GET /v1/dsl-rules/{packageVersion}/download
```

网关执行公司 SSO、仓库访问控制、GitHub API 访问、Release/资产筛选和版本元数据映射。IDEA 仍执行自己的摘要、manifest、规则 JSON 和加载校验，不能仅相信网关或 GitHub 的“发布成功”状态。

GitHub 私有 Release/资产读取需要仓库 `Contents: read` 权限。该权限应由网关持有的 GitHub App 安装令牌使用，不应嵌入 IDEA 插件或要求每位插件用户配置 PAT。[GitHub Release assets API](https://docs.github.com/en/rest/releases/assets?apiVersion=2022-11-28) [GitHub Apps API](https://docs.github.com/en/rest/apps/apps?apiVersion=2022-11-28)

## 4. 推荐的仓库布局与工作流

```text
company/dsl-rule-center/
  docs/                         # 已审批 MD 源文档
    elements/view/Image.md
  templates/                    # 受控提取模板与能力登记
  verified-examples/            # 已通过验证的示例索引
  fixtures/                     # 规则正例/反例
  .github/workflows/
    validate-document.yml       # PR 工作流
    publish-rule-package.yml    # main 合并后的发布工作流
```

`validate-document.yml` 只由 base 为受保护 `main` 的 `pull_request_target` 触发，事件过滤与 job 条件会分别校验一次；它绝不直接执行 PR head 中的代码。工作流从 `main` 的 base SHA 检出可信 Gradle/Java 到 `trusted/`，从 fork/head SHA 仅稀疏检出 `rule-center/docs` 到 `proposal/`，并把后者作为不可信数据交给可信程序。head 检出显式设置 `persist-credentials: false` 与 `allow-unsafe-pr-checkout: true`；该 opt-in 只允许数据检出，不允许从 `proposal/` 运行脚本、Gradle wrapper、Action 或可执行文件。文档真实路径还必须位于显式 `RULE_CENTER_DOCUMENT_ROOT` 下。`pull-requests: write` 只授予需要回写作者反馈的 PR job，手动验证 job 不继承该权限。工作流只处理受影响 MD，但对每份受影响文档保留全文扫描能力，并发布转换报告和作者反馈。

`publish-rule-package.yml` 由受保护 `main` 的合并提交触发：若已有 approved Release，先把全部合格 tag 交给分析器侧 Java 版本比较器，按与客户端完全相同的“任意精度数字点分、缺失尾段补零”语义选择最高不可变 Release，而不是依赖发布时间、32 位整数或 shell 的 `sort -V`。因此 `1.0` 与 `1.0.0` 被视为等价，`2147483648.0` 仍按真实数值大于 `3.0`，新版本必须严格大于现有最高版本。流水线随后下载并验证该版本的 `rule-package.zip`，把其中的完整 `rules/`、`functions/`、`source-markdown/` 与 manifest 作为上一版基线；首版才使用仓库内置规则并转换全部中心文档。本次只对新增/修改 Markdown 调用模型，验证通过的变化覆盖到完整基线，未修改的规则与源文档原样保留。随后工作流再次运行最终门禁、组装完整包、创建 GitHub Release、上传三个固定资产。正式发布环境使用 GitHub Environment `dsl-rule-production` 的人工审批或等价公司审批门禁。

仓库必须启用 GitHub Immutable Releases。发布工作流在模型推理前通过官方 `/repos/{owner}/{repo}/immutable-releases` 接口预检设置，发布后再次核对 Release 的 `immutable=true`。该预检需要 Administration: read，因此 `dsl-rule-production` environment 保存专用的 `RULE_CENTER_ADMIN_TOKEN`；它不进入插件、不进入 PR 校验 job，也不得输出到日志。普通插件用户和文档作者无需配置该令牌。[GitHub Immutable Releases](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/establish-provenance-and-integrity/prevent-release-changes) [仓库不可变发布 API](https://docs.github.com/en/rest/repos/repos?apiVersion=2026-03-10#check-if-immutable-releases-are-enabled-for-a-repository)

## 5. 稳定接口：防止 IDEA 绑定 GitHub

### 5.1 IDEA 看到的接口

IDEA 只依赖已有设计中的 `ReleaseCatalog` 语义：

```java
interface ReleaseCatalog {
    LatestRelease findLatest(String currentVersion, String analyzerVersion);
    DownloadedArtifact download(String packageVersion);
    ReleaseMetadata findVersion(String packageVersion);
}
```

`LatestRelease` 至少含：`packageVersion`、`createdAt`、`contentSha256`、`minimumAnalyzerVersion`、`channel`、变更摘要和下载定位。接口不得暴露 GitHub 的 owner、repo、tag、assetId、URL 或 Token。

### 5.2 中心仓后端接口

网关后端使用另一层接口隔离宿主平台：

```java
interface RuleReleaseBackend {
    List<PublishedRelease> listPublishedReleases();
    ReleaseArtifact openArtifact(String packageVersion, String assetName);
}
```

GitHub 实现 `GitHubReleaseBackend`：

| GitHub 概念 | 后端统一概念 |
|---|---|
| 正式 GitHub Release | `PublishedRelease` |
| `rules-v<version>` tag | `packageVersion` |
| Release assets | `ReleaseArtifact` |
| Release draft/pre-release | 不可发布状态 |
| Release body / asset metadata | 变更摘要与发布元数据 |

网关仅把同时满足以下条件的 Release 映射为 `approved`：`immutable=true`、非 draft、非 pre-release、资产列表恰好由 `rule-package.zip`、`manifest.json`、`release-report.json` 三项组成且名称不重复、三项 GitHub asset SHA-256 可验证、manifest 摘要一致、release report 是允许发布状态、最低分析器版本兼容。额外资产和重复资产会在客户端发现阶段直接拒绝，而不只是依赖发布 workflow 的上传命令。

## 6. 从 GitHub 迁到 CodeHub 的边界

替换后端时，以下内容必须保持不变：

| 不变项 | 原因 |
|---|---|
| `rule-package` 目录与 `manifest.json` schema | IDEA 安装器与验证器依赖它。 |
| `ReleaseCatalog` 请求/响应语义 | IDEA 不应知道中心仓类型。 |
| approved、摘要、兼容性、完整包与回滚规则 | 安全与用户体验不能因迁移降低。 |
| 作者反馈字段与原因码 | 上传人不应因平台迁移失去可行动反馈。 |

| GitHub 首版实现 | CodeHub 等替换实现 |
|---|---|
| Pull Request + GitHub Check | CodeHub Merge Request + 检查/评论 |
| GitHub Actions | CodeHub CI 或公司流水线 |
| GitHub Release assets | CodeHub Release 或公司制品库资产 |
| GitHub App 安装令牌 | CodeHub 服务账号/应用凭据 |
| `GitHubReleaseBackend` | `CodeHubReleaseBackend` |

迁移步骤是：实现新的 `RuleReleaseBackend`、用同一组 C10/C11 contract fixture 运行测试、并在网关配置中切换后端。IDEA 插件、已安装规则包和用户设置不需要迁移或重新配置。

## 7. 安全、完整性与运维要求

1. GitHub 仓库必须为私有，`main` 开启保护和必需检查。
2. 仓库或组织必须启用 Immutable Releases；生产 environment 只额外保存预检所需的 Administration: read 令牌。
3. 只有网关的 GitHub App 有 Release 读取权限；Actions 发布身份只拥有创建 Release 所需的最小权限。
4. 插件下载后必须再次校验网关返回摘要、包内 `manifest.contentSha256`、目录完整性和规则可加载性。
5. 不使用 GitHub Actions artifact 作为正式下载地址；只使用已发布 Release 的固定资产。
6. 网关缓存最新 Release 时必须保留 packageVersion 与摘要，不得把“更晚创建但不兼容”的 Release 告知旧插件。
7. GitHub API 故障、权限过期或资产缺失时，网关返回检查失败；IDEA 保持当前规则版本。

## 8. 开发与测试路径

开发阶段先实现 `GitHubReleaseBackend` 的 contract fixture 和 `TestReleaseCatalog`，不要求真实 GitHub 仓库已存在。随后建立一个隔离 GitHub 测试仓库：用 test Release 资产验证 Actions 发布、网关映射、IDEA 手动检查、摘要篡改拒绝和回滚。

生产仓库启用后，再把网关 backend 配置从 test GitHub 仓库切到正式 GitHub 仓库；IDEA 仍访问同一个公司网关地址。C10 实现 GitHub Release 映射，C11 保护插件不读取任何 GitHub 特有字段。

## 9. 资料

- [GitHub Release 和 Release asset REST API](https://docs.github.com/en/rest/releases?apiVersion=2026-03-10)
- [GitHub Actions workflow artifacts](https://docs.github.com/en/actions/concepts/workflows-and-actions/workflow-artifacts?apiVersion=2022-11-28)
- [GitHub App 安装令牌与权限](https://docs.github.com/en/rest/apps/apps?apiVersion=2022-11-28)
