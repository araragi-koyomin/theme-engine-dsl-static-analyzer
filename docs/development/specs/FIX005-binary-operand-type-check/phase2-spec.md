---
module_ids: [M4]
doc_kind: spec
status: active
created: 2026-07-16
---
# FIX005 二元/一元表达式操作数类型一致性校验 — PHASE 2 规格定义

> 阶段：PHASE 2（规格定义）
> 状态：待用户确认
> 依据：phase1-requirements.md（D1–D10 默认值 + D4/AC-10 偏差修正）

本文档定义方案 A 新增校验的**接口契约**，不涉及内部实现细节（算法/控制流留给 PHASE 3 设计与 PHASE 5 TDD 探索）。方法名 `checkOperandTypesInNumberExpr` 为逻辑名，最终命名在 PHASE 3 确定。

## 1. 顶层契约

### SPEC-1：操作数类型一致性校验入口

| 要素 | 契约 |
|---|---|
| 方法签名（逻辑） | `void checkOperandTypesInNumberExpr(ExpressionNode expr, DslType expectedType, TypeInferenceEngine engine, SymbolTable symbolTable, DslContext context, DslAstNode locationNode, List<Diagnostic> diagnostics)` |
| 输入·expr | 表达式根节点，非 null |
| 输入·expectedType | 属性/Var.expression 期望类型；**前置条件**：`expectedType instanceof DslNumberType`（仅 number 上下文调用） |
| 输入·engine | 已构造的 TypeInferenceEngine（复用 checkAttribute 的 engine 实例） |
| 输入·symbolTable | 符号表，可 null（null 时操作数类型推断会返回 null，保守不报） |
| 输入·locationNode | 宿主位置回退节点（元素或属性节点），用于诊断位置回退 |
| 输入·diagnostics | 诊断累加列表，方法向其追加 0..N 条 |
| 输出 | 无返回值；副作用为向 diagnostics 追加 SEM-TYPE-001 诊断 |
| 后置条件 | 不修改 expr/symbolTable/engine；不抛异常（所有内部异常吞掉，保守跳过） |
| 异常 | 不抛出；遇 null 子节点/空 children 安全返回 |

## 2. 业务规则

### SPEC-2：命中判据（D1 + D2）

**候选操作数** = 在 `BINARY_EXPR`/`UNARY_EXPR` 结构内递归遇到的非 LITERAL 操作数（递归边界见 SPEC-3）。**根节点本身不作为候选**——根节点整体类型错误由 `checkAttribute:107`/`checkVarExpressionBody:281` 顶层比对（整体 `inferType` 结果 vs expected）独占，避免双产（详见 SPEC-5）。

对每个候选操作数 `operand`：

1. 计算实际类型：`inferred = engine.inferType(operand, expectedType, symbolTable)`
2. **命中**（产 SEM-TYPE-001）当且仅当：`inferred != null && inferred instanceof DslStringType`
3. **跳过**（不报）当：`inferred == null` **或** `inferred instanceof DslMixedType`（D2 保守，避免对未知/混合类型误报）

> 说明：`@`前缀 VARIABLE_REF/ARRAY_ACCESS 的 `inferred` 恒为 `DslStringType`（`inferVariableRef/inferArrayAccess` 对 `@` 前缀固定返回 string）；`#`前缀引用 string 变量的 `inferred` = `decl.getType()` = string；返回 string 的 FUNCTION_CALL 的 `inferred` = 签名 returnType = string。三者皆命中。

### SPEC-3：递归边界

**根节点规则**：入口 `expr` 仅当其为 `BINARY_EXPR` 或 `UNARY_EXPR` 时启动递归；根为其他 kind（`VARIABLE_REF`/`LITERAL`/`FUNCTION_CALL`/`ARRAY_ACCESS`/`CONDITIONAL` 等）时**不产任何诊断**（根整体类型归顶层比对独占）。启动后按下表递归 BINARY/UNARY 的操作数：

| 节点 kind（作为操作数遇到时） | 处理 |
|---|---|
| `BINARY_EXPR` | 继续递归其 `children`（左右操作数） |
| `UNARY_EXPR` | 继续递归其 `children`（单操作数） |
| `LITERAL` | **跳过**（D3：LITERAL string 由 `checkStringLiteralInNumExpr` 独占产 SEM-TYPE-003，避免双产） |
| `VARIABLE_REF` | 作为候选操作数判类型（叶子） |
| `ARRAY_ACCESS` | 作为候选操作数判类型；并递归其 `indexExpression`（索引也是 number 算术上下文） |
| `FUNCTION_CALL` | 作为候选操作数判类型；**不递归进 `children`（参数）**——参数类型错误归 `checkFunctionCalls`（SEM-TYPE-002） |
| `CONDITIONAL`（ifelse） | **不作为候选、不递归**——ifelse 分支类型由 `checkIfelseBranchTypes` 独占 |

### SPEC-4：上下文覆盖（F7）

| 调用点 | 触发条件 |
|---|---|
| `checkAttribute`（元素属性） | `expectedType instanceof DslNumberType` 时，与 `checkStringLiteralInNumExpr`/`checkIfelseBranchTypes` 并列调用 |
| `checkVarExpressionBody`（Var.expression） | Var 的 `type` 为 number，或 auto 上下文解析为 number 时调用；string Var 不调用 |

> string 上下文（`expectedType instanceof DslStringType`）**不调用**新校验——保留"任何类型可参与 string 拼接"的既有语义（D2/AC-6/AC-7）。

## 3. 去重边界（D3 + D4 修正）

### SPEC-5：与现有检查不双产

| 现有检查 | 覆盖场景 | 与新校验关系 |
|---|---|---|
| `checkStringLiteralInNumExpr` | LITERAL string 在 number 算术 → SEM-TYPE-003 | 新校验跳过 LITERAL（SPEC-3），**不双产** |
| `checkRefVarExpressionErrors`·`buildHashPrefixOnStringVarDiagnostic` | `#`前缀引用 string 变量 → SEM-TYPE-001，但仅 `expectedType==null \|\| typeEquals(decl.type, expected)` 时（即 string 上下文或无期望） | number 上下文下不报；新校验仅在 number 上下文运行 → **上下文不重叠，不双产** |
| `checkRefVarExpressionErrors`·`buildVarRefTypeErrorDiagnostic` | `#`引用变量的 expression 类型与声明不一致 → SEM-TYPE-001 | 与新校验正交（前者看被引用 Var 的 expression 一致性，后者看当前表达式的操作数类型） |
| `checkAttribute:107` 顶层比对（`engine.inferType`） | 根整体 `inferType`≠expected → SEM-TYPE-001 | engine 对 BINARY_EXPR 返回 `expectedContext`（上下文决定），顶层**不报** BINARY 整体；新校验补 BINARY 内操作数，**不双产** |
| `checkVarExpressionBody:281` 顶层比对（`inferExpressionType`） | BINARY 整体 `inferExpressionType`（第一非 null 子节点类型）≠varType → SEM-TYPE-001 | `inferExpressionType` 对 BINARY 返回第一子节点类型，可能 string≠number 触发顶层报；接线加条件 `exprType==null \|\| typeEquals(exprType,varType)` 时才调新校验（仅顶层未报才补操作数），**避免双产**（PHASE 5 T6 修正） |

> 结论：新校验在 number 算术上下文覆盖所有非 LITERAL 的 string 操作数，与现有检查无重叠产出。

## 4. 诊断契约（D6 + D7 + D8）

### SPEC-6：SEM-TYPE-001（操作数类型不一致）诊断字段

| 字段 | 值 |
|---|---|
| `ruleId` | `SEM-TYPE-001`（复用 `RULE_TYPE_001`） |
| `severity` | `ERROR` |
| `message` | 模板：`类型不匹配，表达式含 string 类型操作数 {operand.text}，不能参与 number 算术运算（属性 {attrName}）`；Var.expression 场景为 `...（Var expression）` |
| `filePath` | `context.getFilePath()` |
| `astNode`（位置源） | string 操作数节点 `operand` 本身；**回退**：当 `operand.line==0 && operand.column==0` 时回退到 `locationNode` |
| `line/column/endLine/endColumn` | 取自上述 astNode 的源位置 |
| `ruleDocUrl` | `resolveDocUrl(context, RULE_TYPE_001)` |
| `suggestedFixes` | 空（本规则不提供自动修复，与现有 `buildTypeMismatchDiagnostic` 一致） |
| 数量 | 每个命中操作数节点 1 条；`@a+@b` 两个不同操作数节点 → 2 条；同一 AST 节点实例不重复报（按节点引用去重） |

## 5. 测试场景清单（对应 AC-1 ~ AC-16）

| TS # | 对应 AC | 场景 | 输入 | 期望 |
|---|---|---|---|---|
| TS-1.1 | AC-1 | number 二元·右操作数 string | `x="1+@a"`，`@a`=string 引用 | 1×SEM-TYPE-001，位置=`@a` |
| TS-1.2 | AC-2 | number 二元·左操作数 string | `x="@a+1"` | 1×SEM-TYPE-001，位置=`@a` |
| TS-1.3 | AC-3 | number 嵌套二元·深层 string | `x="1+2+@a"` | 1×SEM-TYPE-001，位置=`@a` |
| TS-1.4 | AC-4 | number 二元·双 string 操作数 | `x="@a+@b"` | 2×SEM-TYPE-001，各定位其操作数 |
| TS-1.5 | AC-5 | number 一元·string 操作数 | `x="-@a"` | 1×SEM-TYPE-001，位置=`@a` |
| TS-1.6 | AC-6 | string 上下文·拼接合法 | `textExp="'a'+@b"` | 0 诊断 |
| TS-1.7 | AC-7 | string 上下文·number 参与拼接合法 | `textExp="'a'+#num"`（num 声明 number） | 0 诊断 |
| TS-1.8 | AC-8 | number·回归保护 | `x="#n+2"`（n 声明 number） | 0 诊断（现有 `binaryExprInNumberContextNoViolation` 保持绿） |
| TS-1.9 | AC-9 | number·LITERAL string 去重 | `x="1+'str'"` | 1×SEM-TYPE-003（由 `checkStringLiteralInNumExpr`），0×SEM-TYPE-001（新校验跳过 LITERAL） |
| TS-1.10 | AC-10（修正） | number·`#`string 变量漏检修正 | `x="1+#strVar"`（strVar 声明 string） | 1×SEM-TYPE-001（新校验产；`checkRefVarExpressionErrors` 在 number 上下文不报） |
| TS-1.11 | AC-11 | Var.expression·number 上下文 | `<Var expression="1+@a" type="number"/>` | 1×SEM-TYPE-001 |
| TS-1.12 | AC-12 | number·函数返回 string 参与算术 | `x="substr('x',1,2)+1"` | 1×SEM-TYPE-001，位置=`substr(...)` 节点 |
| TS-1.13 | AC-13 | number·null 操作数保守（D2） | `x="1+#undef"`（`#undef` 未声明，`inferVariableRef` 对 `#`前缀 lookup 失败返回 null） | 0×SEM-TYPE-001（D2 跳过 null；SEM-REF-001 由 VarRefAnalyzer 独立产出，不在 TypeAnalyzer 范围） |
| TS-1.14 | AC-14 | 剧场测试改造 | `TypeInferenceEngineTest.binaryExprReturnsExpectedContext` | 保留"上下文决定结果类型"契约断言 + 补注释说明操作数校验归 TypeAnalyzer 层 + 新增 `@str+number` 负测试验证 TypeAnalyzer 报 SEM-TYPE-001 |
| TS-1.15 | AC-15 | 全量门禁 | `gradle --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e` | BUILD SUCCESSFUL |
| TS-1.16 | AC-16 | golden 同步 | 受影响 fixture 的 `.expected.json` | 新增 SEM-TYPE-001 条目，L3/L4 golden 匹配通过 |

## 7. 偏差修正记录

| 日期 | 修正项 | 修正前 | 修正后 | 触发 |
|---|---|---|---|---|
| 2026-07-16 | SPEC-2 / SPEC-3 / SPEC-5 | SPEC-3 将 VARIABLE_REF/LITERAL/FUNCTION_CALL 等描述为"自身作为候选"，未排除根节点；SPEC-5 未列顶层比对 | 加 SPEC-3 根节点规则：入口仅 `BINARY_EXPR`/`UNARY_EXPR` 启动，根为其他 kind 不产诊断；SPEC-2 明确根节点不作为候选；SPEC-5 加 `checkAttribute:107`/`checkVarExpressionBody:281` 顶层比对不双产行 | PHASE 4 前核查 `hashStringVarInNumberAttrProducesSEM_TYPE_001`（`x="#s"` 根为 VARIABLE_REF）已由顶层比对报 size==1，若新校验把根当候选会变 size==2 双产 |
| 2026-07-16 | SPEC-5 顶层比对行 | 原将 `checkAttribute:107`/`checkVarExpressionBody:281` 合并为一行称"不双产"，未区分二者推断引擎差异 | `checkAttribute` 用 `engine.inferType`（BINARY 返回 `expectedContext`，顶层不报 BINARY）；`checkVarExpressionBody` 用 `inferExpressionType`（BINARY 返回第一非 null 子节点类型，可能 string≠number 触发顶层报）。后者与新校验对 BINARY 内 string 操作数双产。修正：`checkVarExpressionBody` 接线加条件 `exprType==null\|\|typeEquals(exprType,varType)` 时才调新校验 | PHASE 5 T6 golden 失败：`multi_element_expression_blast` line 10 `#str_value+10`（#str_value=string 在 BINARY 首位）顶层报 + 新校验报 → 双产 |

---

> **阶段切换**：PHASE 2 完成（含 SPEC-2/3/5 根节点规则修正）。进入 PHASE 3（设计）→ PHASE 4（任务拆分）。
