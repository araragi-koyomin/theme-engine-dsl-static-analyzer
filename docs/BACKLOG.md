---
module_ids: [CORE]
doc_kind: note
status: active
created: 2026-07-15
---

# DSL 静态分析器 — 项目状态索引

> 热层索引。只放活跃项,完成后移除。聚合文件永久保留。

## 模块状态

| 模块 | 名称 | 状态 | 文档 |
|---|---|---|---|
| M0 | 解析器基础设施 | done | [→](architecture/M0-ParserInfrastructure.md) |
| M1 | 文件识别 | done | [→](architecture/M1-FileIdentification.md) |
| M2 | 规则库 | done | [→](architecture/M2-RuleLibrary.md) |
| M3 | 语法分析 | in-progress | [→](architecture/M3-SyntaxAnalysis.md) |
| M4 | 语义分析 | done | [→](architecture/M4-SemanticAnalysis.md) |
| M5 | 修复逻辑 | in-progress | [→](architecture/M5-QuickFix.md) |
| M6 | UI交互 | in-progress | [→](architecture/M6-UIInteraction.md) |
| M7 | 批量检查 | in-progress | [→](architecture/M7-BatchInspection.md) |
| M8 | 导航与重构 | pending | [→](architecture/M8-Navigation.md) |
| PSI | PSI Adapter | pending | [→](architecture/PSI-Adapter.md) |

## 开发阶段

| 阶段 | 名称 | 状态 | 入口 |
|---|---|---|---|
| P0 | Beta 闭环修复 | done | [→](development/specs/p0-bugfix/) |
| P1 | Editor 交互交付 | planned | — |
| P2 | 扩展性与一致性 | planned | — |
| P3 | 锦上添花 | planned | — |

## 文档待办 (stale docs)

> All core docs updated to reflect P0 changes. No stale docs remaining.

## 活跃分支

| 分支 | 描述 | PR |
|---|---|---|
| feature/doc-restructure | 文档管理体系重构 | 待创建 |

## E2E 测试门禁

```bash
./gradlew --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e
```
全绿方可合并。详见 [AGENTS.md](../AGENTS.md) E2E 分层测试章节。
