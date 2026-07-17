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
| P0 | Beta 闭环修复 | done | PR #86 合入 main。949 tests 全绿。SDD PHASE 1-6 完整文档。SyntaxChecker 接线 + CLI 参数语义 + 异常退出码 + FixActionRegistry init | [→](development/specs/p0-bugfix/) |
| P1 | Editor 交互交付 | planned | 函数库接线(RuleRepositoryService) + Quick Fix UI(IntentionAction) + ToolWindow 诊断面板 + 右键批量检查 + 报告导出 | — |
| P2 | 扩展性与一致性 | planned | 规则/函数热更新 + Analyzer 实例级注册(ServiceLoader) + 统一诊断契约 + 作用域感知符号解析 + 规则数据质量工具 + 文档对齐 + 性能 benchmark | — |
| P3 | 锦上添花 | planned | 自定义报告模板 + 定时自动检查 + 规则编辑器 UI + Plugin Verifier | — |

## 缺陷修复追踪

| 编号 | 名称 | 状态 | 分支 | 入口 |
|---|---|---|---|---|
| FIX001 | P0 Beta 闭环修复 | done | — | [→](development/specs/p0-bugfix/) |
| FIX003 | TypeAnalyzer null 函数库静默吞 SEM-TYPE-* | pending | — | [审计 C2](development/reports/test-theater-audit-2026-07-15.md) |
| FIX004 | 测试剧场治理（15 CRITICAL + 34 HIGH） | pending | — | [审计报告](development/reports/test-theater-audit-2026-07-15.md) |

## 功能开发追踪

| 编号 | 名称 | 状态 | 分支 | 入口 |
|---|---|---|---|---|
| FEAT001 | LSP 变量定义跳转（`#var`/`@var` → `<Var name>`） | in-progress (PHASE 6) | `lsp-server` | [→](development/specs/feat001-lsp-variable-definition/) |

## 活跃分支

| 分支 | 描述 | PR |
|---|---|---|
| feature/doc-restructure | 文档管理体系重构(三层记忆 + frontmatter + stale 更新) | 待创建 |

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
