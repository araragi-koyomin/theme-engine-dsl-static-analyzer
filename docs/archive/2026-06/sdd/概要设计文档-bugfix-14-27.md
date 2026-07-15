# 概要设计文档 (HLD)：Bug 14-27 修复

> **版本**: v1.0  
> **日期**: 2026-07-13  
> **分支**: `fix/bugfix-14-27`  
> **状态**: 待审批

---

## 1. 文档目的与范围

本文档描述针对 `docs/bugfix-summary.md` 中记录的 13 个未修复 Bug（Bug 14-27）的修复设计。

### 1.1 修复范围

| Bug 编号 | 规则 ID | 类别 | 修复类型 |
|----------|---------|------|----------|
| Bug 14 | SEM-PERSIST-001 | 规则引擎 | 约束条件扩展 |
| Bug 15 | SEM-TYPE-001 | 类型推断 | 类型传播链修复 |
| Bug 16 | SEM-TYPE-001 | 类型推断 | # 前缀类型检查 |
| Bug 18 | SEM-TYPE-003 | 表达式语法 | Rule ID 重分类 |
| Bug 19 | SYN-EXPR-004 | 表达式语法 | Rule ID 重分类 |
| Bug 20 | SEM-TYPE-003 | 表达式语法 | Rule ID 重分类 |
| Bug 21 | SYN-EXPR-002 | 精度检查 | 非问题（PRD 合规） |
| Bug 22 | SEM-REF-001 | 函数引用 | Rule ID 重分类 |
| Bug 23 | SEM-REF-001 | 变量引用 | 非问题（策略变更） |
| Bug 24 | SEM-REF-003 | 变量引用 | 跨作用域检测 |
| Bug 25 | SEM-REF-001 | 元素属性引用 | Rule ID 重分类 |
| Bug 26 | SEM-CMD-004 | Command 规则 | 求值器函数实现 |
| Bug 27 | SEM-TYPE-002 | 类型推断 | 跨上下文返回类型查找 |

### 1.2 不在范围内

- Bug 1-10, 12-13, 17, 28：已修复
- Bug 29, 30：确认为非 Bug（分析器行为正确）
- IDEA 插件层（Plugin 层）代码修改
- ANTLR grammar 文件（`DslExpression.g4`, `DslRuleCondition.g4`）修改

---

## 2. 背景与策略决策

### 2.1 项目现状

- 当前分支: `fix/bugfix-14-27`，基于 `main` 创建
- CLI fat jar 版本: `v0.1.0`，构建命令 `./gradlew :feature:analysis:buildFatJar`
- 14 个测试 fixture（`complex/` 8 个 + `complex_expressions/` 6 个）
- 当前匹配率: 75.8% 完全匹配，86.3% 部分匹配，误报率 4.5%

### 2.2 策略决策

经与产品方确认，以下策略变更适用于本次修复：

#### 决策 1：取消前向引用检测

**声明**：本项目不报告任何"声明/定义之前引用"的错误，仅检查"引用变量在文件中是否定义"。本项目将变量部分简化为全部按全局变量处理，不对"声明"与"调用"的顺序进行判定。

**影响**：
- Bug 23 不再是 Bug（当前"无诊断"行为即正确行为）
- `VarRefAnalyzer.java` 中的 `isForwardReference()` 和 `buildForwardReferenceDiagnostic()` 方法需删除
- `ANSWER_KEY.md` 中前向引用相关条目需移除

#### 决策 2：不实现常量折叠（遵循 PRD）

**依据**：PRD.md 第 185 行明确标注"不做常量折叠（不对表达式求值），不做符号执行"。

**影响**：
- Bug 21（`5000000 + 5000000` 结果精度检查）标记为设计限制，非 Bug
- `ANSWER_KEY.md` 中 Bug 21 相关条目标记为"可选/不实现"

#### 决策 3：元素属性引用统一归类为 SEM-REF-001

`#elem.prop` 形式的引用（元素未声明时）从 SEM-REF-002 改为 SEM-REF-001。

#### 决策 4：未知函数归类为 SEM-REF-001

未在函数签名库中定义的函数名（如 `bogusFunc`）归类为 SEM-REF-001（未定义引用）。

---

## 3. 系统架构影响分析

### 3.1 修改涉及的模块

```
core/
├── ruledsl/
│   └── DefaultRuleDslEvaluator.java     ← Bug 26: 实现 containsExpression 预处理
├── semanticanalysis/
│   └── analyzers/
│       ├── TypeAnalyzer.java            ← Bug 15, 16, 20, 22, 27
│       ├── LiteralTypeAnalyzer.java     ← Bug 18
│       ├── VarRefAnalyzer.java          ← Bug 22, 23(删除), 24, 25
│       └── ConstraintAnalyzer.java      ← (无修改，消费方)
├── expression/
│   └── TypeInferenceEngine.java         ← Bug 27: 跨上下文返回类型查找
├── syntaxanalysis/
│   └── ExpressionSyntaxChecker.java     ← Bug 18, 19
└── (resources)
    └── rules/elements/variable/
        └── Var.json                     ← Bug 14: 约束条件扩展
```

### 3.2 不受影响的模块

- M0 解析器基础设施（ANTLR grammar 不修改）
- M1 文件识别
- M2 规则库加载器（JsonRuleLoader 不修改）
- M3 语法分析 AstBuilder（不修改）
- M5 修复逻辑
- M7 批量检查与报告
- Plugin 层全部模块

### 3.3 数据流影响

本次修改集中在 M4 语义分析阶段和 M3 表达式语法检查阶段，不改变模块间接口和数据流。

```
M3 语法分析 → ExpressionSyntaxChecker (Bug 18,19 修改)
           ↓
M4 语义分析 → TypeAnalyzer (Bug 15,16,20,22,27 修改)
           → LiteralTypeAnalyzer (Bug 18 修改)
           → VarRefAnalyzer (Bug 22,23,24,25 修改)
           → ConstraintAnalyzer → DefaultRuleDslEvaluator (Bug 26 修改)
           → TypeInferenceEngine (Bug 27 修改)
```

---

## 4. 修改文件清单

### 4.1 Java 源文件

| 序号 | 文件路径 | Bug | 修改类型 |
|------|----------|-----|----------|
| 1 | `core/ruledsl/DefaultRuleDslEvaluator.java` | 26 | 新增 `containsExpression` 预处理 |
| 2 | `core/semanticanalysis/analyzers/TypeAnalyzer.java` | 15,16,20,22,27 | 多处修改 |
| 3 | `core/semanticanalysis/analyzers/LiteralTypeAnalyzer.java` | 18 | 扩展检查范围 |
| 4 | `core/semanticanalysis/analyzers/VarRefAnalyzer.java` | 22,23,24,25 | 删除+新增 |
| 5 | `core/expression/TypeInferenceEngine.java` | 27 | inferFunctionCall 修改 |
| 6 | `core/syntaxanalysis/ExpressionSyntaxChecker.java` | 18,19 | 条件分支修改 |

### 4.2 规则 JSON 文件

| 序号 | 文件路径 | Bug | 修改类型 |
|------|----------|-----|----------|
| 1 | `rules/elements/variable/Var.json` | 14 | 约束条件扩展 |

### 4.3 测试与文档文件

| 序号 | 文件路径 | 修改类型 |
|------|----------|----------|
| 1 | `fixtures/complex/ANSWER_KEY.md` | 移除前向引用条目，标记 Bug 21 设计限制 |
| 2 | `fixtures/complex_expressions/ANSWER_KEY.md` | 同上 |
| 3 | `docs/bugfix-summary.md` | 更新策略决策、修复状态、验证数据 |

---

## 5. 设计原则与约束

### 5.1 设计原则

1. **最小变更原则**：每个 Bug 的修复尽可能局部化，避免大范围重构
2. **不引入回归**：修复后所有已通过的 fixture 必须继续保持通过
3. **声明式优先**：能通过修改 JSON 约束实现的检测逻辑，不写 Java 代码（遵循 AGENTS.md 第 4.3 节）
4. **PRD 合规**：所有修改不违背 PRD 中明确的设计约束（如不做常量折叠）

### 5.2 约束

1. **Core-Plugin 隔离**：所有修改在 `core/` 包内，不引入 `com.intellij.*` 依赖
2. **代码风格**：遵循 AGENTS.md 第 4 节代码风格指南（4 空格缩进、左括号不换行、行宽 ≤120）
3. **日志规范**：使用 `LogUtil` 记录日志
4. **无注释原则**：除非用户要求，不添加代码注释

---

## 6. 分阶段实施计划

### Phase 0：环境准备与基线建立

| 步骤 | 内容 | 产出 |
|------|------|------|
| 0.1 | 构建 fat jar | `dsl-analyzer.jar` |
| 0.2 | 对 14 个 fixture 运行 E2E，导出 JSON | `docs/e2e-baseline/*.json` |
| 0.3 | 记录基线诊断数 | 基线对照表 |

### Phase 1：策略变更与文档更新

| 步骤 | 内容 | 文件 |
|------|------|------|
| 1.1 | 删除 `VarRefAnalyzer` 前向引用检测逻辑 | `VarRefAnalyzer.java` |
| 1.2 | 更新 ANSWER_KEY 移除前向引用条目 | 两个 `ANSWER_KEY.md` |
| 1.3 | 更新 ANSWER_KEY 标记 Bug 21 为设计限制 | 两个 `ANSWER_KEY.md` |
| 1.4 | 重建 jar，验证策略变更效果 | E2E 对比 |

### Phase 2：Rule ID 重分类修复（7 个 Bug）

| 步骤 | Bug | 内容 |
|------|-----|------|
| 2.1 | 25 | `VarRefAnalyzer`: 元素属性引用 SEM-REF-002 → SEM-REF-001 |
| 2.2 | 22 | `VarRefAnalyzer`: 未知函数 SEM-TYPE-001 → SEM-REF-001 |
| 2.3 | 26 | `DefaultRuleDslEvaluator`: 实现 `containsExpression()` 预处理 |
| 2.4 | 18 | `ExpressionSyntaxChecker` + `LiteralTypeAnalyzer`: 字符串字面量在数值属性中 → SEM-TYPE-003 |
| 2.5 | 19 | `ExpressionSyntaxChecker`: 未闭合/嵌套引号 → SYN-EXPR-004 |
| 2.6 | 20 | `TypeAnalyzer`: 简单字面量类型不匹配 → SEM-TYPE-003 |
| 2.7 | 27 | `TypeInferenceEngine`: 跨上下文函数返回类型查找 |
| 2.8 | — | 重建 jar，Phase 2 E2E 验证 |

### Phase 3：检测缺失修复（3 个 Bug）

| 步骤 | Bug | 内容 |
|------|-----|------|
| 3.1 | 14 | `Var.json`: SEM-PERSIST-001 约束扩展检查 expression 内容 |
| 3.2 | 16 | `TypeAnalyzer`: # 前缀引用 string 变量检测 |
| 3.3 | 24 | `VarRefAnalyzer`: 跨作用域重复变量名检测 |
| 3.4 | — | 重建 jar，Phase 3 E2E 验证 |

### Phase 4：类型传播修复（1 个 Bug）

| 步骤 | Bug | 内容 |
|------|-----|------|
| 4.1 | 15 | `TypeAnalyzer`: #var 引用传播 Var 表达式类型错误 |
| 4.2 | — | 重建 jar，Phase 4 E2E 验证 |

### Phase 5：全量验证与文档更新

| 步骤 | 内容 |
|------|------|
| 5.1 | 全量 14 fixture E2E + clean 文件验证 |
| 5.2 | 导出最终 JSON + Markdown 报告 |
| 5.3 | 更新 `bugfix-summary.md` 修复状态和验证数据 |
| 5.4 | 更新 ANSWER_KEY 最终版 |

---

## 7. 测试验证策略

### 7.1 测试方法

使用 `jar` 命令进行真实端到端测试，导出 JSON 和 Markdown 文件进行比对。

```bash
# 构建
./gradlew :feature:analysis:buildFatJar

# 单 fixture JSON 导出（精确比对）
java -jar feature/analysis/build/cli/dsl-analyzer.jar --format json --no-color \
  --output docs/e2e-results/<fixture>.json <fixture-path>

# 目录扫描 Markdown 报告
java -jar feature/analysis/build/cli/dsl-analyzer.jar --format markdown \
  --output docs/e2e-results/full-report.md <fixtures-dir>
```

### 7.2 验证检查点

| 检查点 | 通过标准 |
|--------|----------|
| 基线对照 | 修复前 14 fixture 诊断数与 bugfix-summary 一致 |
| 无回归 | 已全匹配的 fixture（constraint_edge_cases, operator_precedence）保持全匹配 |
| Clean 文件 | lockscreen_valid.xml 保持 0 诊断 |
| 逐 Bug 验证 | 每个 Bug 的 Expected vs Actual 匹配 |
| 最终统计 | 误报率 ≤ 4.5%，完全匹配率 ≥ 80% |

### 7.3 逐 Bug 验证矩阵

| Bug | Fixture | 期望行 | 期望 Rule ID | 验证方法 |
|-----|---------|--------|-------------|----------|
| 14 | deep_nesting_violations.xml | 9 | SEM-PERSIST-001 | JSON 导出 grep |
| 15 | type_inference_edge_cases.xml | 18 | SEM-TYPE-001 | JSON 导出 grep |
| 16 | multi_element_expression_blast.xml | 62 | SEM-TYPE-001 | JSON 导出 grep |
| 18a | deep_nesting_violations.xml | 13 | SEM-TYPE-003 | JSON 导出 grep |
| 18b | multi_element_expression_blast.xml | 42 | SEM-TYPE-003 | JSON 导出 grep |
| 19a | expression_syntax_errors.xml | 6 | SYN-EXPR-004 | JSON 导出 grep |
| 19b | expression_syntax_errors.xml | 26 | SYN-EXPR-004 | JSON 导出 grep |
| 20a | variable_lifecycle_errors.xml | 13 | SEM-TYPE-003 | JSON 导出 grep |
| 20b | type_inference_edge_cases.xml | 12 | SEM-TYPE-003 | JSON 导出 grep |
| 22 | expression_syntax_errors.xml | 7 | SEM-REF-001 | JSON 导出 grep |
| 24 | variable_lifecycle_errors.xml | 27 | SEM-REF-003 | JSON 导出 grep |
| 25a | array_index_edge_cases.xml | 15 | SEM-REF-001 | JSON 导出 grep |
| 25b | multi_element_expression_blast.xml | 69 | SEM-REF-001 | JSON 导出 grep |
| 26 | trigger_command_combos.xml | 23 | SEM-CMD-004 | JSON 导出 grep |
| 27 | chained_function_hell.xml | 7 | SEM-TYPE-002 | JSON 导出 grep |

---

## 8. 风险分析

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| Bug 14 正则表达式边界匹配误报 | 中 | 使用 `([^a-zA-Z0-9_].*\|$)` 边界检查防止 `#hourly` 误匹配 `#hour` |
| Bug 18/19 修改 SYN-EXPR-ANTLR 流程影响其他 fixture | 中 | 每个 Phase 后全量 E2E 验证 |
| Bug 27 TypeInferenceEngine 跨上下文查找影响其他函数推断 | 中 | 验证所有含函数调用的 fixture |
| Bug 15 类型传播产生重复诊断 | 低 | 在 `checkSingleVarExprError` 中添加去重检查 |
| Bug 26 containsExpression 预处理正则不匹配 | 低 | 使用与 `ExpressionParser.hasExpressionSyntax` 一致的逻辑 |

---

## 9. 交付物清单

| 交付物 | 路径 | 说明 |
|--------|------|------|
| SDD 概要设计文档 | `docs/sdd/概要设计文档-bugfix-14-27.md` | 本文档 |
| SDD 详细设计文档 | `docs/sdd/详细设计文档-bugfix-14-27.md` | 逐 Bug 详细设计 |
| E2E 基线报告 | `docs/e2e-baseline/` | 修复前基线 JSON |
| E2E 最终报告 | `docs/e2e-results/` | 修复后 JSON + Markdown |
| 更新的 bugfix-summary | `docs/bugfix-summary.md` | 最终验证数据 |
| 更新的 ANSWER_KEY | `fixtures/complex/ANSWER_KEY.md` 等 | 更新预期诊断 |

---

## 10. 审批记录

| 角色 | 审批人 | 日期 | 结果 |
|------|--------|------|------|
| 设计者 | AI Agent | 2026-07-13 | — |
| 审批者 | 用户 | 2026-07-13 | 待审批 |
