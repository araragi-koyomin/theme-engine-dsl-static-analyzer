---
name: doc-management
description: Use when creating, moving, archiving, or adding frontmatter to documentation files under docs/, or when updating BACKLOG.md to reflect new work items, bugs, branches, or module status changes. Not for source code or build configuration.
---

# Document Management

## 三层记忆架构

| 层 | 位置 | 内容 | 规则 |
|---|---|---|---|
| **热层** | `docs/BACKLOG.md` | 模块状态(M0-M8) + 阶段状态(P0-P3) + 文档待办 | 只放活跃项,完成后移除 |
| **温层** | `docs/architecture/` + `docs/development/` | 模块深度文档 + SDD 文档 + 开发报告 | 活跃文档,有 frontmatter |
| **冷层** | `docs/archive/YYYY-MM/` | 已完成的计划/规格/设计文档 | 归档≠删除,镜像源目录结构 |

## Frontmatter Schema

所有 `docs/**/*.md` 文件（`archive/` 和 `themes_engine_next/` 豁免）必须有 YAML frontmatter：

```yaml
---
module_ids: [M3, M4]        # 关联模块：M0-M8, PSI, CLI, CORE, E2E
phase: P0                  # 关联阶段：P0/P1/P2/P3（无关联则省略）
doc_kind: architecture     # architecture|spec|plan|report|guide|decision|note|template
status: active             # active|stale|superseded|archived
created: 2026-07-15
---
```

## 归档规则

- 计划执行完 → `docs/archive/YYYY-MM/`
- 讨论收敛 → 归档
- Bug 修好 → 归档
- 设计被采纳并实现 → 归档（活文档如 Architecture.md 保留）
- **归档 ≠ 删除**

## BACKLOG 维护规则

BACKLOG.md 是项目状态热层索引。以下事件**必须同步更新 BACKLOG**，不可遗漏：

| 触发事件 | 更新动作 |
|---|---|
| 新建 `FIX00N`/`FEAT00N` spec 目录 | 缺陷修复追踪/开发阶段表加条目（编号、名称、status=in-progress、分支、入口链接） |
| 发现新 bug/缺陷（即使尚未开始修） | 缺陷修复追踪表加条目（status=pending），标注来源（审计报告/用户报告/代码 review） |
| 创建开发分支 | 活跃分支表加条目 |
| 模块状态变化（done→in-progress 等） | 模块状态表更新 status + evidence |
| FIX/FEAT 完成并 merge to main | 条目 status 改 done → spec 目录归档 → 条目移除（热层只放活跃项） |
| SDD 阶段切换（PHASE 1→2→…→6） | 条目 status 标注当前 PHASE（如 `in-progress (PHASE 5)`） |

> **session 启动检查**：每次开发 session 开始时，先读 BACKLOG.md 了解当前项目状态（模块/阶段/活跃分支/缺陷追踪），再开始工作。

## 命名规范

| 类型 | 格式 | 示例 |
|---|---|---|
| 模块文档 | `MX-Name.md` | `M3-SyntaxAnalysis.md` |
| 计划 | `YYYY-MM-DD-slug.md` | `2026-07-14-p0-bugfix-plan.md` |
| SDD 规格目录 | `FIX00N-slug`（缺陷修复）/ `FEAT00N-slug`（功能开发） | `FIX002-undefined-str-ref`、`FEAT003-custom-rule` |
| SDD Phase | `phaseN-title.md`（置于规格目录内） | `phase2-spec.md` |
| 报告 | `slug.md` | `dev-summary-2026-07-14.md` |

> **SDD 规格目录编号规则**：`FIX`/`FEAT` 后接三位顺序号（001, 002, …），全仓库递增、不回收。既有的 `p0-bugfix` 目录视为 FIX001（早于本约定）。目录名即 SDD 一组六阶段工作的命名空间，其下放 `phase1-requirements.md` … `phase6-validation.md`。

## 自动化守护

```bash
bash scripts/check-frontmatter.sh      # 检查所有 .md 有 frontmatter
bash scripts/check-doc-dir-size.sh      # 活跃目录 >15 文件 warn, >25 error
```

## 文档模板

新建文档时参考 `docs/knowledge/doc-templates.md`。
