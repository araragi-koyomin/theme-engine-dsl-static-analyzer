---
module_ids: [CORE]
doc_kind: template
status: active
created: 2026-07-15
---

# 文档模板规范

> 新建文档时必须使用以下模板。frontmatter 必填,不可省略。

## Frontmatter Schema

```yaml
---
module_ids: [M3, M4]        # 关联模块，枚举：M0-M8, PSI, CLI, CORE, E2E
phase: P0                  # 关联开发阶段，枚举：P0/P1/P2/P3（无关联则省略）
doc_kind: architecture     # 文档类型，见下表
status: active             # 状态：active|stale|superseded|archived
created: 2026-07-15        # 创建日期
---
```

### doc_kind 枚举

| 值 | 用途 |
|---|---|
| `architecture` | 架构/模块设计文档 |
| `spec` | 接口契约/规格定义 |
| `plan` | 实施计划/任务拆分 |
| `report` | 开发报告/总结 |
| `guide` | 用户指南 |
| `decision` | 决策记录 |
| `note` | 知识笔记/参考 |
| `template` | 文档模板 |

## 模块文档模板

```markdown
---
module_ids: [MX]
doc_kind: architecture
status: active
created: YYYY-MM-DD
---
# MX 模块名 - 架构设计

## 1. 模块职责
## 2. 三层划分（Core/Extension/Optional）
## 3. 核心组件
## 4. 模块依赖
## 5. CLI 相关
## 6. 设计要点
```

## SDD Phase 文档模板

```markdown
---
module_ids: [...]
phase: PX
doc_kind: spec|plan|report
status: active
created: YYYY-MM-DD
---
# PX 修复 — PHASE N 标题

## 契约/任务/验证内容...
```

## Lessons Learned 模板

```markdown
---
doc_kind: note
status: active
created: YYYY-MM-DD
---
### LL-XXX: 标题
- 坑：
- 根因：
- 触发条件：
- 修复：
- 防护：
- 来源锚点：
```

## 命名规范

| 类型 | 格式 | 示例 |
|---|---|---|
| 模块文档 | `MX-Name.md` | `M3-SyntaxAnalysis.md` |
| 计划 | `YYYY-MM-DD-slug.md` | `2026-07-14-p0-bugfix-plan.md` |
| SDD Phase | `phaseN-title.md` | `phase2-spec.md` |
| 报告 | `slug.md` | `dev-summary-2026-07-14.md` |
| 归档 | 保留原名,放 `archive/YYYY-MM/` | — |

## 归档规则

- Bug 修好了 → 归档
- 讨论收敛了 → 归档
- 计划执行完了 → 归档
- 研究结论落地了 → 归档
- **归档 ≠ 删除**。历史有价值,但不应出现在活跃目录。
- 归档目录结构镜像源目录：`specs/` → `archive/YYYY-MM/specs/`
