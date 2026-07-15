---
module_ids: [CORE]
doc_kind: note
status: active
created: 2026-07-15
---

# DSL 静态分析器 — 项目状态索引

> 热层索引。只放活跃项,完成后移除。

## 模块状态

| 模块 | 名称 | 状态 | 证据 | 文档 |
|---|---|---|---|---|
| M0 | 解析器基础设施 | done | ANTLR4 grammar + ExpressionParser + RuleDslEvaluator + 函数签名库 | [→](architecture/M0-ParserInfrastructure.md) |
| M1 | 文件识别 | done | `.xml` + 根标签双重识别 | [→](architecture/M1-FileIdentification.md) |
| M2 | 规则库 | done | JsonRuleLoader + DefaultRuleRepository + 77 个规则 JSON | [→](architecture/M2-RuleLibrary.md) |
| M3 | 语法分析 | in-progress | StAX AstBuilder + SyntaxChecker 接线(P0) + ExpressionSyntaxChecker SEM-TYPE-003 移除(P0) | [→](architecture/M3-SyntaxAnalysis.md) |
| M4 | 语义分析 | in-progress | 9 Analyzer + DiagnosticProvider 模式分发(P0) + FIX002 done(`@`跳过+编译断裂已修, PR#88) + FIX003 pending(TypeAnalyzer null函数库) | [→](architecture/M4-SemanticAnalysis.md) |
| M5 | 修复逻辑 | in-progress | 6 类 generator + FixActionRegistry 生产初始化(P0) + CLI suggestedFixes 非空(P0) | [→](architecture/M5-QuickFix.md) |
| M6 | UI交互 | in-progress | 标签/属性/枚举补全 + 实时诊断 + 变量跳转/FindUsages/Rename。待补: Quick Fix UI, ToolWindow | [→](architecture/M6-UIInteraction.md) |
| M7 | 批量检查 | in-progress | CLI 全链路 + 三格式报告 + --quiet/--verbose 语义落实(P0) + 异常退出码 2(P0) | [→](architecture/M7-BatchInspection.md) |
| M8 | 导航与重构 | pending | 未开始。Ctrl+Click/FindUsages/Rename 已有基础实现(直接扫描 PSI) | [→](architecture/M8-Navigation.md) |
| PSI | PSI Adapter | pending | 目标设计。当前为 ThemeDslDiagnosticAnnotator 直接映射,无独立 PSI Adapter 包 | [→](architecture/PSI-Adapter.md) |

## 开发阶段

| 阶段 | 名称 | 状态 | 证据 | 入口 |
|---|---|---|---|---|
| P0 | Beta 闭环修复 | done | PR #86 合入 main。SDD PHASE 1-6 完整；测试数量是历史规模信息，不作为无漏检证明 | [归档](archive/2026-07/FIX001-p0-bugfix/) |
| P1 | Editor 交互交付 | planned | 函数库接线(RuleRepositoryService) + Quick Fix UI(IntentionAction) + ToolWindow 诊断面板 + 右键批量检查 + 报告导出 | — |
| P2 | 扩展性与一致性 | planned | 规则/函数热更新 + Analyzer 实例级注册(ServiceLoader) + 统一诊断契约 + 作用域感知符号解析 + 规则数据质量工具 + 文档对齐 + 性能 benchmark | — |
| P3 | 锦上添花 | planned | 自定义报告模板 + 定时自动检查 + 规则编辑器 UI + Plugin Verifier | — |

## 缺陷修复追踪

| 编号 | 名称 | 状态 | 分支 | 入口 |
|---|---|---|---|---|
| FIX003 | TypeAnalyzer null 函数库静默吞 SEM-TYPE-* | pending | — | [审计 C2](development/reports/test-theater-audit-2026-07-15.md) |
| FIX004 | 测试剧场治理（FIX002 已关闭 C1/C15 与相关 P6，剩余项需重新基线） | pending | — | [审计报告](development/reports/test-theater-audit-2026-07-15.md) |
| FIX005 | 内置 RuleConstraint 超出 Rule DSL 能力并静默漏检 | pending | — | [Showcase 风险审计](development/reports/showcase-risk-audit-2026-07-15.md#fix005内置规则条件超出执行器能力) |
| FIX006 | Rule ID、来源描述与实际 constraint 语义错位 | pending | — | [Showcase 风险审计](development/reports/showcase-risk-audit-2026-07-15.md#fix006rule-id-与规则来源语义错位) |

## 遗留技术债

| 原编号 | 问题 | 状态 | Showcase 相关性 | 入口 |
|---|---|---|---|---|
| M-1 | per-analyzer 内部异常在 `--quiet` 下不可见 | pending | 中：现场不用 quiet；流水线需保证内部失败可观察 | [P0 known issues](archive/2026-07/FIX001-p0-bugfix/known-issues.md#m-1-per-analyzer-internal-analyzer-error-是-warning-级) |
| M-3 | JaCoCo 与 IntelliJ 插桩冲突，覆盖率为 0 | pending | 中：不可宣称达到 80% 覆盖率 | [P0 known issues](archive/2026-07/FIX001-p0-bugfix/known-issues.md#m-3-jacoco-覆盖率-0) |
| M-4 | mode fixture 名称与实际 FULL 模式验证不一致 | pending | 低：命名债，不影响现场行为 | [P0 known issues](archive/2026-07/FIX001-p0-bugfix/known-issues.md#m-4-mode-fixture-golden-在-full-模式下验证) |
| M-6 | 目录扫描遇到不可读文件时静默跳过 | pending | 中：流水线完整性问题，现场不要演示权限异常 | [P0 known issues](archive/2026-07/FIX001-p0-bugfix/known-issues.md#m-6-runonfile-抛异常-vs-runondirectory-静默跳过) |

## 活跃分支

| 分支 | 描述 | 状态 |
|---|---|---|
| `codex/showcase-preparation` | Showcase 样例、风险审计、备战材料与文档生命周期整理 | 待评审 |

## E2E 测试门禁

**CI 门禁(必须全绿)**:
```bash
./gradlew --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e :feature:lsp:test
```
包含 L4 fat jar 子进程测试(33/33 绿)。

**本地快速开发(不含 L4)**:
```bash
./gradlew --no-daemon :feature:analysis:test
```
仅跑 L1-L3 单元/golden 测试,L4 fat jar 子进程测试被 Assumption 跳过(需 `:feature:analysis:e2e` 单独触发)。
