# 文档管理体系重构计划

> 目标：对 docs/ 进行分类、打标、归档，使其可检索、有层次；建立未来的文档模板和规范，防止再次失控。
>
> 依据：`docs/knowlege_management.md` 三层记忆架构（热层/温层/冷层 + frontmatter + 归档）
>
> 范围：`docs/` 全目录（`docs/themes_engine_next/` 不处理）
>
> 本文件是计划，不含实施。

---

## 1. 现状诊断

### 1.1 文件审计（不含 themes_engine_next）

| 位置 | 文件数 | 问题 |
|---|---|---|
| `docs/` 根 | 12 | 核心文档(PRD/Architecture/TDD)与临时报告混编,无层次 |
| `docs/architecture/` | 10 | 结构清晰,但无 frontmatter |
| `docs/sdd/` | 2 | Bug 14-27 设计文档,Bug 已修,未归档 |
| `docs/specs/p0-bugfix/` | 6 | SDD phase 文档,位置合理但与 superpowers/ 割裂 |
| `docs/superpowers/plans/` | 9 | 实施计划,与 specs/ 分离,无交叉引用 |
| `docs/superpowers/specs/` | 8 | 设计文档,含 3 个疑似重复的架构重构版本 |
| `docs/e2e-baseline/` | 18 | 非 markdown 测试数据,已被 golden 框架替代 |
| `docs/e2e-results/` | 16 | 同上 |
| `docs/e2e-verify/` | 15 | 同上,命名不一致(.xml.json vs .json) |
| **合计** | **97** | |

### 1.2 三个根因（对标 knowlege_management.md）

| 根因 | 症状 | 本项目表现 |
|---|---|---|
| 没有 schema | 179 个文件仅 1 个有 frontmatter | grep 全仓才能找到"M3 什么情况" |
| 没有层次 | SDD 文档散落在 sdd/ + specs/ + superpowers/ 三个目录 | "P0 修复的 spec 在哪?" 无法一句话回答 |
| 没有生命周期 | Bug 14-27 设计文档仍在 sdd/,e2e 数据已被替代仍占 49 个文件 | 49 个废弃文件淹没活跃文档 |

### 1.3 Stale 核心文档（dev-summary §8 列出 15 处偏差）

| 文档 | 偏差数 | 典型偏差 |
|---|---|---|
| PRD.md | 多处 | §2.1.2 说 SAX 但实际是 StAX;CLI 参数名 --type-check vs --no-type-check |
| Architecture.md | 8处 | SAX→StAX;PSI Adapter 包不存在;Editor 完成度高估;版本号 0.0.1 vs 0.1.0 |
| TDD.md | 5处 | CLI 参数语义;Editor 后台/增量未实现;函数库目录语义 |
| CLI-Usage.md | 多处 | --syntax-only/--semantic-only/--no-type-check 语义与实现不一致 |
| architecture/M3 | 多处 | 写 SAX 但实际 StAX;SYN-001~007 编号但实际只产 SYN-001/003/004 |

---

## 2. 目标目录结构

```
docs/
├── BACKLOG.md                              ← 热层：项目状态索引
├── PRD.md                                  ← 核心：产品需求（更新内容）
├── Architecture.md                         ← 核心：架构总览（更新内容）
├── TDD.md                                  ← 核心：技术设计（更新内容）
├── CLI-Usage.md                            ← 核心：CLI 用户指南（更新内容）
├── DSL-Rule-Spec.md                        ← 核心：DSL 规则规范
├── Editor.md                               ← 核心：编辑器插件指南
├── UX-Design.md                            ← 核心：UX 设计
├── prompt-templates.md                     ← 核心：可复用 prompt 模板
│
├── architecture/                           ← 温层：模块深度文档（保留,加 frontmatter）
│   ├── M0-ParserInfrastructure.md
│   ├── M1-FileIdentification.md
│   ├── M2-RuleLibrary.md
│   ├── M3-SyntaxAnalysis.md               ← 更新：SAX→StAX, SYN 编号修正
│   ├── M4-SemanticAnalysis.md             ← 更新：CLI 参数语义
│   ├── M5-QuickFix.md
│   ├── M6-UIInteraction.md
│   ├── M7-BatchInspection.md              ← 更新：CLI 参数语义
│   ├── M8-Navigation.md
│   └── PSI-Adapter.md
│
├── development/                            ← 温层：开发过程文档（新建,合并 sdd/ + specs/ + superpowers/）
│   ├── plans/                              ← 实施计划（from superpowers/plans/）
│   │   ├── 2026-07-14-e2e-golden-testing.md
│   │   ├── 2026-07-14-p0-bugfix-plan.md
│   │   └── ...（其余 7 个计划文件）
│   ├── specs/                              ← 设计规格 + SDD phase 文档
│   │   ├── p0-bugfix/                     ← SDD phase 1-6（from specs/p0-bugfix/）
│   │   │   ├── phase1-requirements.md
│   │   │   ├── phase2-spec.md
│   │   │   ├── phase3-design.md
│   │   │   ├── phase4-tasks.md
│   │   │   ├── phase6-validation.md
│   │   │   └── known-issues.md
│   │   ├── 2026-06-25-core-skeleton-design.md
│   │   ├── 2026-07-06-cli-pipeline-design.md
│   │   └── ...（其余设计文档,排除已归档的重复版本）
│   ├── reports/                            ← 开发报告
│   │   ├── bugfix-summary.md
│   │   ├── dev-summary-2026-07-14.md       ← from Theme_Engine_DSL_..._总结.md
│   │   └── variable-reference-implementation-report.md
│   └── sdd/                               ← 遗留 SDD 设计文档（from sdd/,待归档判定）
│       ├── bugfix-14-27-hld.md             ← from 概要设计文档-bugfix-14-27.md
│       └── bugfix-14-27-ddd.md             ← from 详细设计文档-bugfix-14-27.md
│
├── archive/                                ← 冷层：归档
│   └── 2026-06/
│       ├── specs/                           ← 被取代的设计文档
│       │   ├── 2026-06-17-architecture-refactor-design.md
│       │   └── 2026-06-17-architecture-restructuring-design.md
│       └── e2e-data/                        ← 废弃的 E2E 测试数据（from e2e-baseline/ + e2e-results/ + e2e-verify/）
│           └── (49 files, 已被 golden 框架替代)
│
├── knowledge/                              ← 知识管理
│   ├── knowledge_management.md             ← from knowlege_management.md（修正拼写）
│   ├── lessons-learned.md                  ← 教训模板（新建）
│   └── doc-templates.md                    ← 文档模板规范（新建）
│
└── themes_engine_next/                     ← 不处理
```

### 2.1 删除项

| 路径 | 文件数 | 理由 |
|---|---|---|
| `docs/e2e-baseline/` | 18 | 已被 `fixtures/*.expected.json` golden 框架替代 |
| `docs/e2e-results/` | 16 | 同上 |
| `docs/e2e-verify/` | 15 | 同上 |

> **决策**：用户确认"可以直接去掉"。不归档,直接删除。golden 框架（`src/test/resources/fixtures/*.expected.json` + `FixtureCoverageTest`）已完全替代。

### 2.2 归档项

| 源路径 | 目标路径 | 理由 |
|---|---|---|
| `superpowers/specs/2026-06-17-architecture-refactor-design.md` | `archive/2026-06/specs/` | 被 06-18 版本取代 |
| `superpowers/specs/2026-06-17-architecture-restructuring-design.md` | `archive/2026-06/specs/` | 同日同主题,被取代 |
| `sdd/概要设计文档-bugfix-14-27.md` | `archive/2026-06/sdd/bugfix-14-27-hld.md` | Bug 14-27 已修,设计文档已完成使命 |
| `sdd/详细设计文档-bugfix-14-27.md` | `archive/2026-06/sdd/bugfix-14-27-ddd.md` | 同上 |

### 2.3 迁移项

| 源路径 | 目标路径 | 操作 |
|---|---|---|
| `superpowers/plans/*.md`(9个) | `development/plans/` | git mv + 加 frontmatter |
| `superpowers/specs/*.md`(6个,排除2个归档) | `development/specs/` | git mv + 加 frontmatter |
| `specs/p0-bugfix/*.md`(6个) | `development/specs/p0-bugfix/` | git mv + 加 frontmatter |
| `sdd/*.md`(2个) | `archive/2026-06/sdd/` | git mv(重命名) |
| `Theme_Engine_DSL_..._总结.md` | `development/reports/dev-summary-2026-07-14.md` | git mv(重命名) + 加 frontmatter |
| `bugfix-summary.md` | `development/reports/` | git mv + 加 frontmatter |
| `variable-reference-implementation-report.md` | `development/reports/` | git mv + 加 frontmatter |
| `knowlege_management.md` | `knowledge/knowledge_management.md` | git mv(修正拼写) |
| `e2e-baseline/` + `e2e-results/` + `e2e-verify/` | 删除 | 已被 golden 替代 |

### 2.4 清理后目录

迁移后删除空目录：`sdd/`、`specs/`（空,因为 p0-bugfix/ 移到 development/）、`superpowers/`（空,因为内容移到 development/）。

---

## 3. Frontmatter 规范

### 3.1 Schema

```yaml
---
module_ids: [M3, M4]        # 关联模块，枚举：M0-M8, PSI, CLI, CORE, E2E（跨模块用 CORE）
phase: P0                  # 关联开发阶段，枚举：P0/P1/P2/P3（无关联则省略）
doc_kind: architecture     # 文档类型，枚举见下表
status: active             # 状态，枚举：active|stale|superseded|archived
created: 2026-07-14        # 创建日期
---
```

### 3.2 doc_kind 枚举

| 值 | 用途 | 示例 |
|---|---|---|
| `architecture` | 架构/模块设计文档 | Architecture.md, architecture/M3-SyntaxAnalysis.md |
| `spec` | 接口契约/规格定义 | development/specs/p0-bugfix/phase2-spec.md |
| `plan` | 实施计划/任务拆分 | development/plans/2026-07-14-p0-bugfix-plan.md |
| `report` | 开发报告/总结 | development/reports/dev-summary-2026-07-14.md |
| `guide` | 用户指南 | CLI-Usage.md, Editor.md |
| `decision` | 决策记录 | （未来使用） |
| `note` | 知识笔记/参考 | knowledge/knowledge_management.md |
| `template` | 文档模板 | knowledge/doc-templates.md |

### 3.3 各文档 Frontmatter 示例

```yaml
# PRD.md
---
module_ids: [CORE]
doc_kind: guide
status: stale
created: 2026-06-15
---
```

```yaml
# architecture/M3-SyntaxAnalysis.md
---
module_ids: [M3]
doc_kind: architecture
status: active
created: 2026-06-17
---
```

```yaml
# development/specs/p0-bugfix/phase2-spec.md
---
module_ids: [M3, M4, M5, M7, CLI]
phase: P0
doc_kind: spec
status: active
created: 2026-07-14
---
```

```yaml
# development/plans/2026-07-14-e2e-golden-testing.md
---
module_ids: [E2E]
phase: P0
doc_kind: plan
status: active
created: 2026-07-14
---
```

```yaml
# archive/2026-06/specs/2026-06-17-architecture-refactor-design.md
---
module_ids: [CORE]
doc_kind: spec
status: superseded
created: 2026-06-17
---
```

---

## 4. 热层：BACKLOG.md

```markdown
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
| P0 | Beta 闭环修复 | in-progress | [→](development/specs/p0-bugfix/) |
| P1 | Editor 交互交付 | planned | — |
| P2 | 扩展性与一致性 | planned | — |
| P3 | 锦上添花 | planned | — |

## 文档待办 (stale docs)

| 文档 | 偏差 | 状态 | 说明 |
|---|---|---|---|
| PRD.md | 多处 | **stale** | SAX→StAX;CLI 参数名;FileType 范围 |
| Architecture.md | 8处 | **stale** | PSI Adapter 包不存在;Editor 完成度;版本号 |
| TDD.md | 5处 | **stale** | CLI 参数语义;Editor 后台/增量 |
| CLI-Usage.md | 多处 | **stale** | --syntax-only/--semantic-only 语义 |
| architecture/M3 | 多处 | **stale** | SAX→StAX;SYN 编号 |

## 活跃分支

| 分支 | 描述 | PR |
|---|---|---|
| feature/p0-bugfix | P0 Beta 闭环修复 | 待创建 |
```

---

## 5. Stale 核心文档更新计划

### 5.1 PRD.md 更新项

| § | 偏差 | 修正 |
|---|---|---|
| §2.1.2 | "SAX解析XML" | 改为"StAX XMLStreamReader" |
| §2.1.2 | `--type-check` | 改为`--no-type-check` |
| §3.4 | "XML结构解析使用JDK SAX" | 改为"JDK StAX (XMLStreamReader)" |
| §2.1.1 | `--syntax-only` 描述 | 更新：只跑 M3(SyntaxChecker+ExpressionSyntaxChecker) |
| §2.1.1 | `--semantic-only` 描述 | 更新：只跑 M4(不含 TypeAnalyzer) |
| §2.1.5 | suggestedFixes | 补充：FixActionRegistry 生产初始化已接 |
| §5 | 版本号 0.0.1 vs 0.1.0 | 统一为 0.1.0 |

### 5.2 Architecture.md 更新项

| § | 偏差 | 修正 |
|---|---|---|
| §2 | "SAX" 全部 | 改为 "StAX" |
| §2 | `plugin/psiadapter/` 等包 | 改为实际包 `plugin/editor/` |
| §2 | "DslPsiBridge" | 标注为目标设计,非当前实现 |
| §5 | "SAXParser" | 改为 "XMLStreamReader" |
| §5 | SYN-001~007 | 改为 SYN-001/003/004(实际产出) |
| §10 | "待创建/待重构"标签 | 更新为实际状态 |
| §10 | Development-Plan.md | 标注不存在 |

### 5.3 TDD.md 更新项

| § | 偏差 | 修正 |
|---|---|---|
| CLI 参数 | `--type-check` | 改为 `--no-type-check` |
| Editor | DumbService/增量/Dispatcher | 标注为未实现(目标设计) |
| 函数库 | `--rule-dir` 语义 | 补充：只控制规则,不控制函数库 |

### 5.4 CLI-Usage.md 更新项

| § | 偏差 | 修正 |
|---|---|---|
| `--syntax-only` | "只做语法检查" | 补充：只跑 M3,不跑 M4 |
| `--semantic-only` | "只做语义检查" | 补充：只跑 M4(不含 TypeAnalyzer) |
| `--no-type-check` | — | 补充：禁用 TypeAnalyzer |
| `--quiet` | — | 补充：过滤 WARNING/INFO |
| `--verbose` | — | 补充：5 类输出(AST统计+符号表+耗时+analyzer计数+类型推断链) |

### 5.5 architecture/M3 更新项

| § | 偏差 | 修正 |
|---|---|---|
| §1 | "JDK SAX" | 改为 "JDK StAX (XMLStreamReader)" |
| §3.2 | "SAXParser" | 改为 "XMLStreamReader" |
| §3.4 | SYN-001~007 表 | 改为 SYN-001/003/004(实际),其余标注为 M4 SEM-* |
| §5.2 | `--syntax-only` | 更新：只跑 SyntaxChecker + ExpressionSyntaxChecker |

---

## 6. 文档模板规范（新建 knowledge/doc-templates.md）

### 6.1 模块文档模板

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

### 6.2 SDD Phase 文档模板

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

### 6.3 Lessons Learned 模板

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

---

## 7. 命名规范

| 类型 | 格式 | 示例 |
|---|---|---|
| 模块文档 | `MX-Name.md` | `M3-SyntaxAnalysis.md` |
| 计划 | `YYYY-MM-DD-slug.md` | `2026-07-14-p0-bugfix-plan.md` |
| SDD Phase | `phaseN-title.md` | `phase2-spec.md` |
| 报告 | `slug.md` | `dev-summary-2026-07-14.md` |
| 归档 | 保留原名,放 `archive/YYYY-MM/` | — |

---

## 8. 自动化守护

### 8.1 Frontmatter 检查脚本

```bash
# scripts/check-frontmatter.sh
# 扫描 docs/**/*.md,断言每个文件有 frontmatter(archive/ 除外)
# warn: 缺 frontmatter; error: 无 frontmatter 且非 archive/
```

### 8.2 目录卫生检查

```bash
# scripts/check-doc-dir-size.sh
# docs/ 下每个活跃目录 > 15 个 .md → warn
# > 25 个 → error(需归档或拆分)
# archive/ 豁免
```

### 8.3 Stale 文档追踪

BACKLOG.md 的"文档待办"节追踪 stale 文档。更新后从待办移除,frontmatter status 改为 active。

---

## 9. 实施步骤（按顺序）

| 步骤 | 操作 | 文件数 |
|---|---|---|
| 1 | 创建新目录结构(development/, archive/, knowledge/) | — |
| 2 | 删除 e2e-baseline/ + e2e-results/ + e2e-verify/ | -49 |
| 3 | 归档 superseded 文档到 archive/2026-06/ | 4 |
| 4 | 迁移 superpowers/plans/ → development/plans/ | 9 |
| 5 | 迁移 superpowers/specs/ → development/specs/ | 6 |
| 6 | 迁移 specs/p0-bugfix/ → development/specs/p0-bugfix/ | 6 |
| 7 | 迁移 sdd/ → archive/2026-06/sdd/ | 2 |
| 8 | 迁移根级报告 → development/reports/ | 3 |
| 9 | 迁移 knowlege_management.md → knowledge/knowledge_management.md | 1 |
| 10 | 删除空目录(sdd/, specs/, superpowers/) | — |
| 11 | 为所有 .md 文件加 frontmatter | ~50 |
| 12 | 创建 BACKLOG.md | 1 |
| 13 | 创建 knowledge/lessons-learned.md(模板) | 1 |
| 14 | 创建 knowledge/doc-templates.md | 1 |
| 15 | 更新 PRD.md 内容(§5.1) | 1 |
| 16 | 更新 Architecture.md 内容(§5.2) | 1 |
| 17 | 更新 TDD.md 内容(§5.3) | 1 |
| 18 | 更新 CLI-Usage.md 内容(§5.4) | 1 |
| 19 | 更新 architecture/M3-SyntaxAnalysis.md 内容(§5.5) | 1 |
| 20 | 创建 check-frontmatter.sh + check-doc-dir-size.sh | 2 |
| 21 | 更新 AGENTS.md 文档管理章节 | 1 |
| 22 | 提交 | — |

---

## 10. 重构后文件计数预估

| 位置 | 文件数 | 变化 |
|---|---|---|
| `docs/` 根 | 10 | -2(报告移走) |
| `docs/architecture/` | 10 | 不变 |
| `docs/development/plans/` | 9 | +9(从 superpowers/) |
| `docs/development/specs/` | 12 | +6(superpowers/specs/) +6(specs/p0-bugfix/) |
| `docs/development/reports/` | 3 | +3(从根级) |
| `docs/archive/` | 6 | +4(归档)+2(sdd/) |
| `docs/knowledge/` | 3 | +1(迁移)+2(新建) |
| `docs/themes_engine_next/` | 82 | 不变 |
| **合计** | **135** | -49(e2e 删除) -44(迁移重组) |

> 活跃文档(不含 archive + themes_engine_next)从 97 → 47,信噪比大幅提升。
