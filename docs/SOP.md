---
module_ids: [CORE]
doc_kind: guide
status: active
created: 2026-07-15
---

# SOP: 开发全流程导航图

> 用途：Debug 定位（主要） + 新功能开发启动 + 代码库导航。
> 定位：`docs/SOP.md`。每次 debug 或启动新功能时先读此文件。

---

## 1. Debug 工作流

### 1.1 症状 → 定位

| 症状 | 第一步看哪里 | 关键文件 |
|---|---|---|
| CLI 输出错误/缺失诊断 | `CliMain.run()` → `BatchInspectionRunnerImpl.analyzeFile()` | `core/cli/CliMain.java`, `core/batchinspection/BatchInspectionRunnerImpl.java` |
| 诊断行列号错误 | `AstBuilder.findTagStart()` + `SourcePositionMapper` | `core/syntaxanalysis/AstBuilder.java` |
| 诊断 ruleId 不对 | `DiagnosticProviderImpl.analyze()` → `DiagnosticProviderImplInner.analyze()` → 具体 Analyzer | `core/semanticanalysis/DiagnosticProviderImpl.java`, `core/semanticanalysis/analyzers/` |
| --syntax-only 不工作 | `DiagnosticProvider.analyze()` 的 mode 分发 + `BatchInspectionRunnerImpl.analyzeFile()` | `DiagnosticProviderImpl.java:37-43` |
| --quiet/--verbose 不工作 | `BatchInspectionRunnerImpl.analyzeFile()` quiet 过滤 + `CliMain.run()` verbose 输出 | `BatchInspectionRunnerImpl.java:170-180`, `CliMain.java:125-130` |
| 退出码错误 | `ExitCodeCalculator.compute()` | `core/batchinspection/ExitCodeCalculator.java` |
| suggestedFixes 为空 | `CliMain.run()` 是否调了 `FixActionRegistry.init()` + `QuickFixProviderImpl.getFixActions()` | `CliMain.java:106`, `core/quickfix/FixActionRegistry.java` |
| fat jar 运行异常(但 in-process 正常) | L4 测试差异——StAX provider、classpath、manifest | `AstBuilder.java findTagStart`, `build.gradle buildFatJar` |
| Editor 诊断不显示 | `ThemeDslDiagnosticAnnotator` → Core AstBuilder + DiagnosticProviderImpl | `plugin/editor/ThemeDslDiagnosticAnnotator.java` |
| 规则不生效 | `rule_sources.json` + 元素规则 JSON + `JsonRuleLoader` | `src/main/resources/rules/`, `core/rulelibrary/JsonRuleLoader.java` |

### 1.2 测试定位

```
单元测试    → src/test/java/com/huawei/theme/analysis/core/<module>/
Golden 测试 → src/test/java/.../core/e2e/GoldenDiagnosticMatchTest.java
Golden 数据 → src/test/resources/fixtures/<category>/<name>.expected.json
L4 子进程   → src/test/java/.../core/e2e/FatJarSubprocessE2ETest.java
```

运行测试（详见 skill `gradle-build-test`）：
```bash
# 单个测试类
./gradlew --no-daemon :feature:analysis:test --tests "com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProviderModeTest"
# Golden 匹配
./gradlew --no-daemon :feature:analysis:test --tests "com.huawei.theme.analysis.core.e2e.GoldenDiagnosticMatchTest"
# L4 fat jar 子进程
./gradlew --no-daemon :feature:analysis:buildFatJar :feature:analysis:e2e
```

### 1.3 常见陷阱

常见陷阱和防护措施详见 `docs/knowledge/lessons-learned.md`（7-slot 模板，每条有可追溯来源锚点和可执行防护机制）。

---

## 2. 新功能开发

### 2.1 分支策略

```
main (稳定,全量门禁绿)
  ├── fix/<name> (缺陷修复分支,基于 main)
  ├── feature/<name> (功能开发分支,基于 main)
  │   └── PR → reviewer → merge to main
  └── feature/doc-restructure (文档专用分支)
```

### 2.2 SDD 流程导航

| PHASE | 做什么 | 产出放哪 | 验证方式 |
|---|---|---|---|
| 1 需求澄清 | 向用户提问直到无歧义 | `docs/development/specs/<phase>/phase1-requirements.md` | 用户确认 |
| 2 规格定义 | 定义接口契约(输入/输出/前后置/异常) | `docs/development/specs/<phase>/phase2-spec.md` | 用户确认 |
| 3 设计 | 类图/时序图/模块职责/可测试性 | `docs/development/specs/<phase>/phase3-design.md` | 用户确认 |
| 4 任务拆分 | 15-30min 粒度 TDD 任务 | `docs/development/specs/<phase>/phase4-tasks.md` | 用户确认 |
| 5 TDD 编码 | RED→GREEN→REFACTOR,每 task commit | 代码 + 测试 | `./gradlew --no-daemon :feature:analysis:test` |
| 6 一致性验证 | 逐项核对 spec→测试覆盖 | `docs/development/specs/<phase>/phase6-validation.md` | 全量门禁 + 用户确认 |

### 2.3 合入流程

1. 全量门禁全绿: `./gradlew --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e :feature:lsp:test`
2. 调 reviewer agent 审查
3. 审查通过 → push → 创建 PR
4. 用户确认 → merge to main
5. 独立的 fix/feat 分支采用 **squash merge**，保证 main commit 干净（1 个 fix/feat = 1 个 main commit）。squash 后将 SDD spec 目录归档至 `docs/archive/YYYY-MM/`，BACKLOG 热层条目移除

---

## 3. 代码库导航

### 3.1 模块 → 代码位置

| 模块 | 包路径 | 核心类 |
|---|---|---|
| M0 解析器 | `core/expression/` + `core/ruledsl/` + `core/function/` | ExpressionParser, DefaultRuleDslEvaluator, JsonFunctionSignatureLoader |
| M1 文件识别 | `core/fileidentification/` | DslFileIdentifier |
| M2 规则库 | `core/rulelibrary/` | JsonRuleLoader, DefaultRuleRepository |
| M3 语法分析 | `core/syntaxanalysis/` | AstBuilder (StAX), SyntaxChecker, ExpressionSyntaxChecker |
| M4 语义分析 | `core/semanticanalysis/` | DiagnosticProviderImpl, AnalyzerRegistry, TypeAnalyzer |
| M5 修复 | `core/quickfix/` | FixActionRegistry, QuickFixProviderImpl, generators/ |
| M7 批量检查 | `core/batchinspection/` | BatchInspectionRunnerImpl, ReportExporterImpl, ExitCodeCalculator |
| CLI | `core/cli/` | CliMain, CliConfig, InspectionConfig |
| Plugin/Editor | `plugin/editor/` | ThemeDslDiagnosticAnnotator, ThemeDsl*CompletionContributor |
| LSP | `feature/lsp/` | DslLanguageServer, DslTextDocumentService |
| IntelliJ LSP Client | `feature/clients/intellij/` | DslLspServerService, ThemeDslLspAnnotator |
| VS Code Client | `feature/clients/vscode/` | extension.ts |

### 3.2 CLI 数据流（主要）

```
CliMain.run(args)
  → CliConfig.fromArgs (参数解析)
  → JsonRuleLoader.loadFromClasspath (规则加载)
  → FixActionRegistry.init(ruleRepo) (修复注册)
  → BatchInspectionRunnerImpl
    → DslFileIdentifier (M1: .xml + 根标签)
    → AstBuilder.getDslAst (M3: StAX → DslFileNode AST)
    → DiagnosticProviderImpl.analyze(ast, ruleRepo, stb, mode, config, collector)
      → DiagnosticProviderImplInner (M4: 9 analyzers, filtered by mode+config)
      → SyntaxChecker.check (M3: SYN-001/003/004)
      → ExpressionSyntaxChecker.check (M3: SYN-EXPR-*)
    → QuickFixProviderImpl.getFixActions (M5: fix generators)
  → ReportExporterImpl (JSON/Terminal/Markdown)
  → ExitCodeCalculator.compute (0/1/2)
```

### 3.3 关键配置文件

| 文件 | 用途 |
|---|---|
| `feature/analysis/build.gradle` | 构建、测试、fat jar、e2e task、JaCoCo |
| `src/main/resources/rules/rule_sources.json` | 规则 ID → 分类/文档 URL 映射 |
| `src/main/resources/rules/elements/*.json` | 元素规则（属性/约束/枚举） |
| `src/main/resources/functions/dsl_functions.json` | 函数签名库 |
| `src/main/resources/META-INF/plugin.xml` | IntelliJ 插件配置 |
| `.gitignore` | 含 docs/superpowers, docs/sdd, docs/e2e-* 等豁免 |

### 3.4 文档导航

| 要找什么 | 去哪里 |
|---|---|
| 项目当前状态 | `docs/BACKLOG.md` |
| 模块设计 | `docs/architecture/MX-*.md` |
| SDD 文档 | `docs/development/specs/<phase>/` |
| 开发报告 | `docs/development/reports/` |
| 教训记录 | `docs/knowledge/lessons-learned.md` |
| 文档模板 | `docs/knowledge/doc-templates.md` |
| 知识管理理论 | `docs/knowledge/knowledge_management.md` |
| 历史文档 | `docs/archive/YYYY-MM/` |
| DSL 规则原始文档 | `docs/themes_engine_next/raw_markdown/` (不修改) |
