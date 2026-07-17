---
module_ids: [M4]
doc_kind: spec
status: active
created: 2026-07-16
---
# FIX006 表达式类型推断系统重设计 — PHASE 2 规格定义

> 阶段：PHASE 2（规格定义）
> 状态：待用户确认
> 依据：phase1-requirements.md（4 类型 + 运算规则 + 期望检查 + D1-D10）

本文档定义接口契约与数据结构，不涉及算法/控制流细节（留 PHASE 3/5）。

## 1. 数据结构契约

### SPEC-1：`DslUndefinedType`（新增）

| 要素 | 契约 |
|---|---|
| 包 | `com.huawei.theme.analysis.core.shared.type` |
| 继承 | `extends DslType` |
| `getName()` | `"undefine"` |
| 语义 | 表达式类型推断失败/string 参与非`+`运算；期望检查中**报错** |

### SPEC-2：`DslUnknownType`（新增）

| 要素 | 契约 |
|---|---|
| 包 | 同上 |
| 继承 | `extends DslType` |
| `getName()` | `"unknown"` |
| 语义 | 不确定类型（未声明变量/未定义函数/ifelse 分支冲突）；期望检查中**保守放过** |

### SPEC-3：`DslMixedType` 处置

- 保留类（不删除，避免破坏其他引用），但新逻辑不再使用
- `inferIfelseType` 分支冲突改返 `DslUnknownType`（非 `DslMixedType`）
- 现有引用 `DslMixedType` 处（`checkVarExpressionBody` 等）随 D9 统一引擎一并清理

## 2. TypeInferenceEngine 契约（重写）

### SPEC-4：`inferType`（保留签名，D6）

```java
DslType inferType(ExpressionNode node, DslType expectedContext, SymbolTable symbolTable)
```
- 返回 `DslType` 实例（`DslNumberType`/`DslStringType`/`DslUndefinedType`/`DslUnknownType`/`DslArrayType`）
- `expectedContext` 参数保留签名，但二元/一元/函数推导**不依赖**它（纯操作数/签名推导）；仅兼容现有调用点，可后续移除

### SPEC-5：`inferBinaryExpr`（重写：操作数推导，不再返回 expectedContext）

输入：BINARY_EXPR（`operator`、`children=[left,right]`）。递归 `inferType(left)`、`inferType(right)` 得 `lt`、`rt`。推导规则（按优先级）：

| 优先级 | 条件 | 结果 |
|---|---|---|
| 1 | `lt` 或 `rt` 为 `undefine` | `undefine`（D2/D3） |
| 2 | `lt` 或 `rt` 为 `unknown` | `unknown`（D1/D3） |
| 3 | operator `+` 且都 `number` | `number` |
| 4 | operator `+` 且任一 `string` | `string`（拼接，对称） |
| 5 | operator `+` 且任一非标量（`DslArrayType`） | `undefine` |
| 6 | operator ∈ `{-,*,/,%}` 且都 `number` | `number` |
| 7 | operator ∈ `{-,*,/,%}` 且任一 `string` | `undefine` |
| 8 | 其他 | `undefine` |

> 后置：不返回 `expectedContext`；`null` 操作数视为 `undefine`（推导失败）

### SPEC-6：`inferUnaryExpr`（重写）

输入：UNARY_EXPR（`operator`、`children=[operand]`）。递归 `inferType(operand)` 得 `ot`。operator `-`：

| `ot` | 结果 |
|---|---|
| `number` | `number` |
| `string` | `undefine` |
| `unknown` | `unknown` |
| `undefine` | `undefine` |

### SPEC-7：`inferFunctionCall`（改：D4 查所有签名）

- ifelse 函数：调 `inferIfelseType`（分支类型一致→该类型；冲突→`unknown`）
- 其他函数：收集 `getSignature(name,"number")` 与 `getSignature(name,"string")` 的 returnType，去重：
  - 都无签名 → `unknown`（未定义函数，交 SEM-REF-001）
  - 唯一 returnType → 该类型
  - 多签名 returnType 冲突 → `unknown`
- 不依赖 `expectedContext`

### SPEC-8：`inferVariableRef`/`inferArrayAccess`（调整）

| 前缀 | lookup 结果 | 返回 |
|---|---|---|
| `@` | — | `string`（恒定，不看符号表） |
| `#` | 成功 | `decl.getType()`（数组访问取 baseType） |
| `#` | 失败（未声明） | **`unknown`**（非 `null`，交 SEM-REF-001） |

> 关键变更：`#`未声明从 `null` 改为 `unknown`（SPEC-8）

### SPEC-9：`inferLiteral`（不变）

- 数值字面量 → `number`；字符串字面量（`'...'`）→ `string`

### SPEC-10：`inferIfelseType`（调整）

- 递归推断各分支类型；全一致 → 该类型；任一冲突或含 `undefine` → `unknown`？或 `undefine`？
  > **建议默认**：分支含 `undefine` → `unknown`（保守，ifelse 分支错交由分支内表达式自身报）；分支类型不一致 → `unknown`

## 3. TypeAnalyzer 契约（重写期望检查）

### SPEC-11：`checkAttribute` 期望检查（统一 SEM-TYPE-001）

```
inferred = engine.inferType(expr, expectedType, symbolTable)
```

| 期望类型 | inferred 为 number | string | undefine | unknown | DslArrayType |
|---|---|---|---|---|---|
| `number` | ✅ | ❌SEM-TYPE-001 | ❌SEM-TYPE-001 | ✅ | ❌SEM-TYPE-001 |
| `string` | ❌SEM-TYPE-001 | ✅ | ❌SEM-TYPE-001 | ✅ | ❌SEM-TYPE-001 |

- 诊断：`SEM-TYPE-001`/`ERROR`/定位 attr 节点/消息"类型不匹配，期望{expected}实际{inferred}（属性 {name}）"
- **删除 SEM-TYPE-003 产出**：原 `checkStringLiteralInNumExpr` 的 SEM-TYPE-003 分支删除（新推导 `1+'str'`→string≠number→SEM-TYPE-001 自动覆盖）

### SPEC-12：`checkVarExpressionBody`（统一引擎 D9）

- 用 `engine.inferType`（非 `inferExpressionType`）推断 expression 类型
- 比对 `varType` vs `inferred`（4 类型规则，同 SPEC-11）
- Var.expression 含 string 操作数→推导 string/undefine→顶层比对报 SEM-TYPE-001（不再需 FIX005 的操作数校验 + 去重条件）
- 消除 FIX005 的 `inferExpressionType` vs `engine.inferType` 双产问题（统一引擎）

### SPEC-13：删除清单（FIX005 + SEM-TYPE-003 + 双引擎）

**TypeAnalyzer 删除**：
- FIX005：`checkOperandTypesInNumberExpr`、`collectStringOperandsInNumberExpr`、`buildOperandTypeMismatchDiagnostic` + 2 接线（`checkAttribute`/`checkVarExpressionBody` number 分支调用）
- SEM-TYPE-003：`checkStringLiteralInNumExpr`（003 产出）、`buildStringLiteralInNumDiagnostic`、`buildSimpleLiteralTypeMismatchDiagnostic`、`isSimpleLiteralExpression`、`RULE_TYPE_003` 常量
- 双引擎（D9）：`inferExpressionType`、`inferIfelseType`、`hasIfelseMixedBranches`（统一到 `engine.inferType`）

**TypeInferenceEngine 删除**：无（仅重写 `inferBinaryExpr`/`inferUnaryExpr`/`inferFunctionCall`/`inferVariableRef`/`inferArrayAccess` 内部实现）

> `RULE_TYPE_001`/`RULE_TYPE_002`/`RULE_ARR_001`/`RULE_VAR_004` 保留；`RULE_TYPE_003` 删除

## 4. 测试场景清单（对应 AC-1~18）

| TS # | AC | 场景 | 输入 | 期望 |
|---|---|---|---|---|
| TS-2.1 | AC-1 | number 属性·`+`含 string | `x="1+@a"` | 推断 string≠number→SEM-TYPE-001 |
| TS-2.2 | AC-2 | number 属性·`+`string 在左 | `x="@a+1"` | string≠number→SEM-TYPE-001 |
| TS-2.3 | AC-3 | number 属性·嵌套 | `x="1+2+@a"` | string≠number→SEM-TYPE-001 |
| TS-2.4 | AC-4 | number 属性·`-`含 string 字面量 | `x="1-'str'"` | undefine≠number→SEM-TYPE-001 |
| TS-2.5 | AC-5 | number 属性·一元 string | `x="-@a"` | undefine≠number→SEM-TYPE-001 |
| TS-2.6 | AC-6 | number 属性·number+number | `x="1+2"` | number==number→不报 |
| TS-2.7 | AC-7 | string 属性·string 拼接 | `textExp="'a'+@b"` | string==string→不报 |
| TS-2.8 | AC-8 | string 属性·string+number 拼接 | `textExp="'a'+1"` | string==string→不报 |
| TS-2.9 | AC-9 | Var·简单字面量不匹配（原 SEM-TYPE-003） | `<Var type="string" expression="100+50"/>` | number≠string→SEM-TYPE-001（非 003） |
| TS-2.10 | AC-10 | number 属性·`#`未声明变量 | `x="1+#undef"` | unknown→放过（SEM-REF-001 独占） |
| TS-2.11 | AC-11 | number 属性·未定义函数 | `x="bogusFunc(1)+1"` | unknown→放过（SEM-REF-001 独占） |
| TS-2.12 | AC-12 | number 属性·number 函数 | `x="sin(0.5)+1"` | number==number→不报 |
| TS-2.13 | AC-13 | number 属性·string 函数参与算术 | `x="substr('x',1,2)+1"` | string≠number→SEM-TYPE-001 |
| TS-2.14 | AC-14 | number Var·ifelse 分支冲突 | `<Var type="number" expression="ifelse(#c==1, sin(0.5), 'hello')"/>` | unknown→放过 |
| TS-2.15 | AC-15 | SEM-TYPE-003 不再产出 | 原 SEM-TYPE-003 场景 | 产 SEM-TYPE-001，无 SEM-TYPE-003 |
| TS-2.16 | AC-16 | inferBinaryExpr 操作数推导 | `TypeInferenceEngineTest`：`1+@a`→string（非 expectedContext） | 断言推导结果=string |
| TS-2.17 | AC-17 | 全量门禁 | `gradle --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e` | BUILD SUCCESSFUL |
| TS-2.18 | AC-18 | golden 同步 | 受影响 fixture `.expected.json` | L3/L4 golden 匹配通过 |

## 5. 偏差/待确认

| # | 项 | 状态 |
|---|---|---|
| Q1 | SPEC-10 inferIfelseType 分支含 undefine → unknown 还是 undefine？ | 建议默认 unknown（保守，分支内表达式自身错已报；ifelse 整体不重复报） |
| Q2 | DslArrayType 在标量期望下视为 undefine（SPEC-11 已列报错） | 默认报 SEM-TYPE-001 |
| Q3 | `expectedContext` 参数最终是否移除 | PHASE 3 评估；当前保留签名 |

## 6. string 上下文细化规则（A 方案，待下次会话实现）

> 用户澄清（2026-07-16）：string 上下文里 `1+2`（纯字面量运算，可解释为字面量拼接）不报错；`sin(1)`（无法解释为字面量）报错，需大括号 `{sin(1)}`；`{1+2}` 大括号理解为嵌入数字表达式。

### SPEC-14：string 上下文 number 表达式规则（细化 SPEC-11）

| 表达式形式 | 处理 |
|---|---|
| number **字面量**（`1`、`123`） | 放过（字面量拼接） |
| **纯字面量运算**（`1+2`、`-1`，BINARY/UNARY 全 LITERAL，无函数/变量） | 放过（字面量拼接理解，非数字运算） |
| number **函数调用**（`sin(1)`） | **报 SEM-TYPE-001**（需 `{sin(1)}`） |
| number **变量引用**（`#num`） | 放过（按字面量推断，可直接取值拼接） |
| `{expr}` **大括号包裹**（BRACED） | 放过（嵌入数字表达式，合法） |
| string 字面量/`@var` | 放过（string 拼接） |

> 判据：string 上下文 number 表达式，若含 **number 函数调用**（如 `sin(1)`，需运算）→ 报错；其他（LITERAL/纯字面量运算 `1+2`/`#num` 变量/BRACED）→ 放过。判据简化为"含 number 函数调用即报错"。实现需递归扫描 string 上下文表达式是否含 number FUNCTION_CALL。

### SPEC-15：BRACED 节点（A 方案）

- `ExpressionKind` 加 `BRACED`
- `DslExpressionVisitorAdapter`：`visitStringTerm`（grammar :20 `'{' numericExpression '}'`）/`visitPrimaryExpr`（:43 `'{' expression '}'`）构建 BRACED 节点（children=[内部 expr]）
- `inferType` BRACED case：返回内部 expr 类型
- `isTypeMatch`（string 上下文 + number）：exprNode 是 LITERAL 或 BRACED 或纯字面量运算 → 放过

### SPEC-16：实现范围（A 方案，下次会话）

1. `ExpressionKind.BRACED`
2. `DslExpressionVisitorAdapter`：braced 构建 BRACED
3. `inferType` BRACED case
4. 重新引入 `isSimpleLiteralExpression`（T7 删除，恢复）
5. `isTypeMatch` string+number：LITERAL/BRACED/纯字面量运算放过
6. `checkAttribute`/`checkVarExpressionBody` string 上下文：传 exprNode 给 isTypeMatch
7. T8 重做（string 上下文测试：`1+2` 不报/`sin(1)` 报/`{sin(1)}` 不报）
8. T9 golden 同步（9 fixture + braced 场景）
9. PHASE 6 验证

### 待确认（A 方案）
- `#num`（number 变量）在 string 上下文 **放过**（用户确认 2026-07-16：按字面量推断，可直接取值拼接）
- `@var`（string 变量）合法（string 拼接，不报）
- 仅 number **函数调用**（`sin(1)`）在 string 上下文报错（需大括号 `{sin(1)}`）

## 7. 偏差修正记录

| 日期 | 修正项 | 修正前 | 修正后 | 触发 |
|---|---|---|---|---|
| 2026-07-16 | SPEC-11 string 上下文 | 原"期望 string 实际 number → 报错"简单规则 | 用户澄清：string 上下文 number 字面量/纯字面量运算放过，number 函数/变量报错，`{expr}` braced 合法。需 SPEC-14/15/16 细化 + BRACED + isSimpleLiteralExpression 恢复 | PHASE 5 T9 golden 失败 + 用户澄清 string 上下文语义 |
| 2026-07-16 | T1-T8 核心完成 | — | engine 4 类型重写 + TypeAnalyzer 改造 + 删除冗余 + 单测全绿（TypeInferenceEngineTest/TypeAnalyzerTest）已完成；A 方案 string 上下文细化 + T9 golden + PHASE 6 待下次 | 分阶段决策 |

---

> **阶段切换**：PHASE 2 完成（含 A 方案 string 上下文细化 spec SPEC-14/15/16）。T1-T8 核心已实现（单测绿），A 方案 + T9 golden + PHASE 6 待下次会话。
