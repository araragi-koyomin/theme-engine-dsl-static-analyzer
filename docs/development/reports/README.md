---
module_ids: [CORE]
doc_kind: guide
status: active
created: 2026-07-15
---

# 开发报告索引与生命周期

本目录只保存仍会驱动决策或未完成工作的报告。报告是某个基准版本上的证据快照，不承担缺陷状态机职责；
所有未完成缺陷必须进入 `docs/BACKLOG.md`，完成证据进入对应 FIX/FEAT 的 PHASE 6 文档。

## 活跃报告

| 报告 | 基准 / 用途 | 当前关联工作 | 下一次状态变化 |
|---|---|---|---|
| [测试剧场审计](test-theater-audit-2026-07-15.md) | 2026-07-15 测试断言质量快照 | FIX003、FIX004 | FIX003/004 全部完成后移入 `docs/archive/YYYY-MM/reports/` |
| [Showcase 风险审计](showcase-risk-audit-2026-07-15.md) | `main@3857eb7` 的演示风险、规则能力与来源一致性 | FIX005、FIX006 | Showcase 结束且风险项迁入规格后归档 |

## 2026-07 已归档

| 报告 | 归档原因 | 位置 |
|---|---|---|
| Bug Fix Summary | 记录的未修复 Bug 数为 0，属于历史验证快照 | [归档](../../archive/2026-07/reports/bugfix-summary.md) |
| 2026-07-14 开发总结 | 基准为 `main@e9e9bcd`，已被 BACKLOG 和后续审计取代 | [归档](../../archive/2026-07/reports/dev-summary-2026-07-14.md) |
| 变量引用实现报告 | 实现已交付，剩余限制已转入 BACKLOG/Showcase 风险说明 | [归档](../../archive/2026-07/reports/variable-reference-implementation-report.md) |
| FIX001 / P0 规格与验证 | P0 已完成并合入，规格生命周期结束 | [归档](../../archive/2026-07/FIX001-p0-bugfix/) |

## 状态维护规则

1. 新报告创建时必须写明基准提交、调查范围和关联 BACKLOG 编号。
2. 报告发现新缺陷时，同一变更内登记 BACKLOG；报告正文不作为唯一状态来源。
3. 后续修复只在报告开头增加“当前状态”回写，不改写原始发现和原始统计。
4. 关联缺陷全部关闭、结论被新报告取代或项目阶段结束时，将报告状态改为 `archived` 并移入冷层。
5. 每次 Showcase、版本冻结或 SDD PHASE 6 前，复核本索引、BACKLOG 和实际分支是否一致。
