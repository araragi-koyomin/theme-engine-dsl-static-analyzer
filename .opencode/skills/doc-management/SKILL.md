---
name: doc-management
description: Use when creating, moving, archiving, or adding frontmatter to documentation files under docs/. Not for source code or build configuration.
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

## 命名规范

| 类型 | 格式 | 示例 |
|---|---|---|
| 模块文档 | `MX-Name.md` | `M3-SyntaxAnalysis.md` |
| 计划 | `YYYY-MM-DD-slug.md` | `2026-07-14-p0-bugfix-plan.md` |
| SDD Phase | `phaseN-title.md` | `phase2-spec.md` |
| 报告 | `slug.md` | `dev-summary-2026-07-14.md` |

## 自动化守护

```bash
bash scripts/check-frontmatter.sh      # 检查所有 .md 有 frontmatter
bash scripts/check-doc-dir-size.sh      # 活跃目录 >15 文件 warn, >25 error
```

## 文档模板

新建文档时参考 `docs/knowledge/doc-templates.md`。
