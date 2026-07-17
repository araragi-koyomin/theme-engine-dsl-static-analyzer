---
module_ids: [M4]
doc_kind: spec
status: active
created: 2026-07-16
---
# FIX006 表达式类型推断系统重设计 — PHASE 1 需求澄清

> 阶段：PHASE 1（需求澄清）
> 状态：待用户确认
> 分支：`feature/fix006-expression-type-inference`（待创建）
> 关系：**在表达式类型检查上取代 FIX005**（FIX005 方案 A "上下文决定论" → FIX006 "操作数推导论"）

## 1. 背景

FIX005 方案 A 采用"上下文决定论"：`TypeInferenceEngine.inferBinaryExpr`/`inferUnaryExpr` 无条件返回 `expectedContext`（PRD:190/TDD:259），丢弃操作数实际类型。这导致：
- 二元/一元表达式类型不按操作数推导，需补丁式 `checkOperandTypesInNumberExpr` 校验 number 算术中的 string 操作数
- SEM-TYPE-003（字面量类型错误）与 SEM-TYPE-001（复杂表达式类型不匹配）职责重叠
- 类型推断不自洽（`1+@a` 被判 number 而非 string）

用户要求重新设计为**操作数推导论**：`inferBinaryExpr`/`inferUnaryExpr` 按操作数实际类型推导，4 种结果（undefine/number/string/unknown），从根本解决类型推断一致性。

## 2. 目标

- 重写 `TypeInferenceEngine.inferBinaryExpr`/`inferUnaryExpr` 为操作数推导（不再返回 `expectedContext`）
- 引入 4 类型：`undefine`（推导失败）、`number`、`string`、`unknown`（不确定，保守放过）
- 删除 SEM-TYPE-003（统一归 SEM-TYPE-001）
- 取代 FIX005 方案 A（删除 `checkOperandTypesInNumberExpr` + 接线，新推导自动覆盖 `1+@a` 等场景）

## 3. 范围

### 包含

| 项 | 范围 |
|---|---|
| F1 | `inferBinaryExpr`/`inferUnaryExpr` 重写为操作数推导（4 类型） |
| F2 | 新增 `DslUndefinedType`/`DslUnknownType` 类型（或复用现有，PHASE 3 定） |
| F3 | `TypeAnalyzer` 期望检查重写（4 类型规则，统一 SEM-TYPE-001） |
| F4 | 删除 SEM-TYPE-003 相关：`checkStringLiteralInNumExpr` 的 003 产出、`buildStringLiteralInNumDiagnostic`、`buildSimpleLiteralTypeMismatchDiagnostic`、`isSimpleLiteralExpression` 分支 |
| F5 | 删除 FIX005 的 `checkOperandTypesInNumberExpr`/`collectStringOperandsInNumberExpr`/`buildOperandTypeMismatchDiagnostic` + 2 接线 |
| F6 | 测试重写（`TypeInferenceEngineTest`/`TypeAnalyzerTest`） |
| F7 | golden fixture 同步（类型推断行为变化） |

### 不包含

- `VarRefAnalyzer` SEM-REF-001（未声明变量/未定义函数归 unknown，SEM-REF-001 独占，不重复报 SEM-TYPE）
- 函数签名库/DSL 语法变更
- 数组越界（SEM-ARR-001）、数组缺 size（SEM-VAR-004）等非类型推断规则

## 4. 类型推断规则

### 4.1 类型来源（叶子节点）

| 节点 | 推断类型 |
|---|---|
| 数值字面量（`42`、`3.14`） | `number` |
| 字符串字面量（`'...'`） | `string` |
| `@`变量引用（`@a`） | `string` |
| `@`数组访问（`@arr[0]`） | `string` |
| `#`变量引用（`#a`） | 声明类型（`number`/`string`） |
| `#`数组访问（`#arr[0]`） | 数组 baseType |
| `#`未声明变量 | **`unknown`**（SEM-REF-001 独占报错） |
| 函数调用（签名存在） | 签名 returnType |
| 未定义函数 | **`unknown`**（SEM-REF-001 独占报错） |
| ifelse 分支类型一致 | 该类型 |
| ifelse 分支类型冲突 | `unknown` |

### 4.2 二元运算

| 运算符 | number op number | 含 string（任一） | 含 unknown（无 undefine） | 含 undefine |
|---|---|---|---|---|
| `+`（拼接/加法） | `number` | `string`（拼接） | `unknown` | `undefine` |
| `-`/`*`/`/`/`%`（算术） | `number` | `undefine` | `unknown` | `undefine` |

> `+` 语义：number+number→number（加法）；任一 string→string（拼接，对称，number+string→string）
> `-`/`*`/`/`/`%` 语义：number op number→number；string 参与任一→undefine（string 不能算术）

### 4.3 一元运算

| operand | `-operand` |
|---|---|
| `number` | `number` |
| `string` | `undefine` |
| `unknown` | `unknown` |
| `undefine` | `undefine` |

### 4.4 传播优先级（运算含多类操作数）

- 含 `undefine` → `undefine`（确定错优先报）
- 含 `unknown`（无 `undefine`）→ `unknown`（保守放过）
- 否则按 4.2/4.3 运算规则

## 5. 期望检查（统一 SEM-TYPE-001）

| 期望类型 | 实际 number | 实际 string | 实际 undefine | 实际 unknown |
|---|---|---|---|---|
| `number` | ✅ 放过 | ❌ SEM-TYPE-001 | ❌ SEM-TYPE-001 | ✅ 放过 |
| `string` | ❌ SEM-TYPE-001 | ✅ 放过 | ❌ SEM-TYPE-001 | ✅ 放过 |

> `unknown` 保守放过（不确定类型不报，避免误报 + 与 SEM-REF-001 去重）
> `undefine` 报错（推导失败/string 参与算术）
> **删除 SEM-TYPE-003**：原字面量场景（`1+'str'`）新推导下→string≠number→SEM-TYPE-001 自动覆盖

## 6. 关键决策（建议默认，请确认或纠正）

| # | 决策点 | 建议默认 | 理由 |
|---|---|---|---|
| D1 | `unknown` 参与运算传播 | `unknown`（保守放过） | 避免对未声明/未定义/ifelse 冲突误报 |
| D2 | `undefine` 参与运算传播 | `undefine`（报错传播） | 确定错优先报 |
| D3 | `unknown` 与 `undefine` 同时 | `undefine` 优先（确定错优先报） | 不让未声明变量掩盖 string 算术错 |
| D4 | 函数调用类型推断 | 查所有签名取 returnType 去重：唯一→该类型；多签名冲突/无签名→`unknown` | 不依赖 expectedContext，纯操作数推导；未定义→unknown 交 SEM-REF-001 |
| D5 | 数组访问 index 类型校验 | 不校验 index 类型（归语法层 SEM-ARR-001 越界已有） | 聚焦操作数类型，index 越界归 SEM-ARR-001 |
| D6 | `expectedContext` 参数去留 | 保留 `inferType(node, expectedContext, symbolTable)` 签名（函数签名查找 fallback 用），但二元/一元推导不依赖它 | 最小改动调用点 |
| D7 | Var.expression auto 上下文 | type 缺省→number 期望（沿用现有） | 不变 |
| D8 | 期望检查诊断 | SEM-TYPE-001/ERROR/定位 attr 或 Var 节点/消息"类型不匹配，期望X实际Y（属性/Var expression）" | 复用现有 buildTypeMismatchDiagnostic 风格 |
| D9 | `inferExpressionType`（TypeAnalyzer 自有）去留 | 统一到 `engine.inferType`，删除 `inferExpressionType`/`inferIfelseType`（避免双推断引擎） | 消除 FIX005 发现的 inferExpressionType vs engine 差异 |
| D10 | verbose 类型推断链 | 记录"attr→推断类型→期望类型→是否匹配"，unknown/undefine 也要记录 | 调试可见 |

## 7. 影响范围

| 项 | 影响 |
|---|---|
| FIX005 | 标记 **superseded**（表达式部分被取代）；`checkOperandTypesInNumberExpr`/`collectStringOperandsInNumberExpr`/`buildOperandTypeMismatchDiagnostic` + 2 接线删除 |
| `TypeInferenceEngine` | `inferBinaryExpr`/`inferUnaryExpr` 重写；`inferFunctionCall` 改查所有签名（D4） |
| `TypeAnalyzer` | 期望检查重写（4 类型规则）；删除 `inferExpressionType`/`inferIfelseType`（D9 统一）；删除 SEM-TYPE-003 相关方法；删除 FIX005 接线 |
| `TypeInferenceEngineTest` | `binaryExprReturnsExpectedContext` 重写（不再返回 expectedContext，改为操作数推导断言） |
| `TypeAnalyzerTest` | FIX005 测试位置/消息调整（新推导顶层报，定位 attr 非 @a）；SEM-TYPE-003 测试删除；新增 4 类型推导测试 |
| golden fixture | 类型推断行为变化，`multi_element_expression_blast` 等 fixture 的 `.expected.json` 大量更新（T6 评估） |
| spec 文档 | PRD:190/TDD:259/M4-SemanticAnalysis.md "上下文决定论" 描述需更新为"操作数推导论"；DSL-Rule-Spec.md SEM-TYPE-003 条目删除/合并 |

## 8. 约束

- Core 层无 `com.intellij` import（`checkCoreIntellijDependency` 强制）
- 不引入新依赖
- 全量门禁全绿：`gradle --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e`

## 9. 验收标准（每条可测试）

| AC # | 验收标准 | 测试方式 |
|---|---|---|
| AC-1 | `1+@a`（number 属性）→ 整体 string ≠ number → SEM-TYPE-001 | 单测 |
| AC-2 | `@a+1` → string（拼接）≠ number → SEM-TYPE-001 | 单测 |
| AC-3 | `1+2+@a` 嵌套 → string ≠ number → SEM-TYPE-001 | 单测 |
| AC-4 | `1-'str'`（- 算术含 string）→ undefine ≠ number → SEM-TYPE-001 | 单测 |
| AC-5 | `-@a`（一元 string）→ undefine ≠ number → SEM-TYPE-001 | 单测 |
| AC-6 | `1+2`（number+number）→ number == number → 不报 | 单测 |
| AC-7 | `'a'+@b`（string 属性，拼接）→ string == string → 不报 | 单测 |
| AC-8 | `'a'+1`（string 属性，string+number 拼接）→ string == string → 不报 | 单测 |
| AC-9 | `<Var type="string" expression="100+50"/>` → number ≠ string → SEM-TYPE-001（原 SEM-TYPE-003 删除） | 单测 |
| AC-10 | `1+#undef`（#undef 未声明）→ unknown，放过（SEM-REF-001 独占） | 单测 |
| AC-11 | `bogusFunc(1)+1`（未定义函数）→ unknown，放过（SEM-REF-001 独占） | 单测 |
| AC-12 | `sin(0.5)+1`（number 函数）→ number == number → 不报 | 单测 |
| AC-13 | `substr('x',1,2)+1`（number 属性，string 函数）→ string ≠ number → SEM-TYPE-001 | 单测 |
| AC-14 | `ifelse(#c==1, sin(0.5), 'hello')`（number Var，分支冲突）→ unknown → 放过 | 单测 |
| AC-15 | SEM-TYPE-003 不再产出（删除） | 单测：原 SEM-TYPE-003 场景现产 SEM-TYPE-001 |
| AC-16 | `inferBinaryExpr` 不再返回 expectedContext（操作数推导） | TypeInferenceEngineTest 重写 |
| AC-17 | 全量门禁全绿 | `gradle --no-daemon clean test buildFatJar e2e checkCoreIntellijDependency` |
| AC-18 | golden fixture 同步 | 受影响 `.expected.json` 更新，L3/L4 golden 匹配通过 |

---

> **阶段切换**：PHASE 1 完成。请用户确认需求文档与 10 项关键决策（D1–D10）的建议默认值（尤其 D3 优先级、D4 函数签名查找、D9 删除 inferExpressionType 统一引擎），确认后进入 PHASE 2（规格定义）。
