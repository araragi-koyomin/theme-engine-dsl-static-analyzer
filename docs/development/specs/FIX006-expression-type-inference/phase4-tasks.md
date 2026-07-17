---
module_ids: [M4]
doc_kind: plan
status: active
created: 2026-07-16
---
# FIX006 表达式类型推断系统重设计 — PHASE 4 任务拆分

> 阶段：PHASE 4（任务拆分）
> 状态：待用户确认
> 依据：phase3-design.md（2 新类 + engine 重写 6 方法 + TypeAnalyzer 改造 + 删除 4 类）

## 1. 任务列表

每个 task = 一个 TDD 循环（RED→GREEN→REFACTOR），完成后 commit。粒度 15-30 分钟。

| Task | 标题 | spec | AC | 粒度 | 依赖 |
|---|---|---|---|---|---|
| T1 | 新增 DslUndefinedType/DslUnknownType | SPEC-1,2 | — | 10min | — |
| T2 | engine inferVariableRef/inferArrayAccess（#未声明→unknown） | SPEC-8 | AC-10 | 15min | T1 |
| T3 | engine inferBinaryExpr（操作数推导） | SPEC-5 | AC-1~4,6,7,8 | 25min | T2 |
| T4 | engine inferUnaryExpr + inferIfelseType 移入 + inferType CONDITIONAL | SPEC-6,M6 | AC-5,14 | 25min | T3 |
| T5 | engine inferFunctionCall（查所有签名） | SPEC-7 | AC-11,12,13 | 20min | T4 |
| T6 | TypeAnalyzer isTypeMatch + checkAttribute/checkVarExpressionBody 改造 + 删 FIX005 接线/SEM-TYPE-003 调用 | SPEC-11,12,M8,M9,M10 | AC-1,2,9,15 | 30min | T5 |
| T7 | 删除冗余方法体（FIX005/SEM-TYPE-003/双引擎/checkIfelseBranchTypes） | SPEC-13 | AC-15 | 20min | T6 |
| T8 | 测试重写（TypeInferenceEngineTest + TypeAnalyzerTest 适配 + 删 SEM-TYPE-003 测试） | AC-16 | AC-16 | 25min | T7 |
| T9 | 全量门禁 + golden 同步 | AC-17,18 | AC-17,18 | 30min | T8 |

## 2. 任务详情

### T1：新增 DslUndefinedType/DslUnknownType

- **RED**：`DslTypeTest` 加 `undefinedTypeGetName`/`unknownTypeGetName` 测试（断言 getName()="undefine"/"unknown"），确认编译失败（类不存在）
- **GREEN**：新建 `DslUndefinedType`/`DslUnknownType`（仿 DslNumberType）
- **spec**：SPEC-1, SPEC-2

### T2：engine inferVariableRef/inferArrayAccess（#未声明→unknown）

- **RED**：`TypeInferenceEngineTest` 加 `hashUndefinedVarReturnsUnknown`（`#undef` 未声明 → `unknown`，非 null），确认失败（当前返回 null）
- **GREEN**：改 `inferVariableRef`/`inferArrayAccess`，`#`前缀 lookup 失败 → `new DslUnknownType()`
- **spec**：SPEC-8

### T3：engine inferBinaryExpr（操作数推导）

- **RED**：`TypeInferenceEngineTest` 加：
  - `binaryNumberPlusNumberReturnsNumber`（`1+2`→number）
  - `binaryNumberPlusStringReturnsString`（`1+@a`→string，拼接）
  - `binaryStringPlusNumberReturnsString`（`@a+1`→string）
  - `binaryNumberMinusStringReturnsUndefine`（`1-'str'`→undefine）
  - `binaryWithUnknownReturnsUnknown`（`1+@undef`→unknown）
  - `binaryWithUndefineReturnsUndefine`（`1+undef`→undefine，需构造 undefine 操作数）
- **GREEN**：重写 `inferBinaryExpr`（优先级：undefine>unknown>+拼接/-*/%算术 string→undefine）
- **关键**：**删除** `binaryExprReturnsExpectedContext`（FIX005 改造的剧场测试，断言返回 expectedContext），新推导不再返回 expectedContext
- **spec**：SPEC-5

### T4：engine inferUnaryExpr + inferIfelseType 移入 + inferType CONDITIONAL

- **RED**：`TypeInferenceEngineTest` 加：
  - `unaryMinusNumberReturnsNumber`（`-1`→number）
  - `unaryMinusStringReturnsUndefine`（`-@a`→undefine）
  - `unaryMinusUnknownReturnsUnknown`（`-@undef`→unknown）
  - `ifelseConsistentBranchesReturnsType`（`ifelse(c,1,2)`→number）
  - `ifelseConflictingBranchesReturnsUnknown`（`ifelse(c,sin(0.5),'hello')`→unknown）
- **GREEN**：重写 `inferUnaryExpr`；从 TypeAnalyzer 移入 `inferIfelseType`（递归改用 engine.inferType）；`inferType` 加 `CONDITIONAL` case
- **spec**：SPEC-6, M6

### T5：engine inferFunctionCall（查所有签名）

- **RED**：`TypeInferenceEngineTest` 加：
  - `functionCallNumberReturnReturnsNumber`（`sin(0.5)`→number）
  - `functionCallStringReturnReturnsString`（`strfn(0.5)`→string，stub 需有 strfn）
  - `undefinedFunctionReturnsUnknown`（`bogusFunc(1)`→unknown）
- **GREEN**：改 `inferFunctionCall`：ifelse→inferIfelseType；其他收集 `getSignature(name,"number")`/`getSignature(name,"string")` returnType 去重（唯一→该类型，冲突/无→unknown）
- **spec**：SPEC-7

### T6：TypeAnalyzer 改造期望检查 + 删接线

- **RED**：`TypeAnalyzerTest` 加：
  - `numberAttrWithStringOperandProducesSEM_TYPE_001`（`x="1+@a"`→SEM-TYPE-001，定位 attr，消息"期望number实际string"）—— 此时因 FIX005 接线双产 size==2，期望 size==1 失败
  - `varStringWithNumberExprProducesSEM_TYPE_001`（`<Var type="string" expression="100+50"/>`→SEM-TYPE-001，原 SEM-TYPE-003）
  - `stringAttrConcatNoViolation`（`textExp="'a'+@b"`→不报）
- **GREEN**：
  - 新增 `isTypeMatch(expected, inferred)`（4 类型规则）
  - `checkAttribute`：`engine.inferType` + `isTypeMatch`（替代 typeEquals 比对）；**删 FIX005 接线**（checkOperandTypesInNumberExpr 调用）；**删 SEM-TYPE-003 调用**（checkStringLiteralInNumExpr 003 产出）
  - `checkVarExpressionBody`：用 `engine.inferType`（替代 inferExpressionType）+ `isTypeMatch`；**删 FIX005 接线 + 去重条件**；**删 isSimpleLiteralExpression 分支**（SEM-TYPE-003）
- **spec**：SPEC-11, SPEC-12, M8, M9, M10

### T7：删除冗余方法体

- **RED**：编译验证（T6 后以下方法无引用，删除后编译应通过）
- **GREEN**：删除 TypeAnalyzer：
  - FIX005：`checkOperandTypesInNumberExpr`/`collectStringOperandsInNumberExpr`/`buildOperandTypeMismatchDiagnostic`
  - SEM-TYPE-003：`checkStringLiteralInNumExpr`（若仅 003 职责）/`buildStringLiteralInNumDiagnostic`/`buildSimpleLiteralTypeMismatchDiagnostic`/`isSimpleLiteralExpression`/`RULE_TYPE_003`
  - 双引擎：`inferExpressionType`/`inferIfelseType`（已移入 engine）/`hasIfelseMixedBranches`
  - `checkIfelseBranchTypes`（ifelse 由 engine 统一）
- **验证**：`compileJava` + `compileTestJava` 通过
- **spec**：SPEC-13

### T8：测试重写

- **RED**：跑 `TypeAnalyzerTest`/`TypeInferenceEngineTest`，定位因新推导失败的现有测试
- **GREEN**：
  - `TypeInferenceEngineTest.binaryExprReturnsExpectedContext` → 重写为操作数推导断言（T3 已删，确认）
  - FIX005 测试（`binaryExprRightStringOperandProducesSEM_TYPE_001` 等）→ 调整期望：定位 attr（非 @a 操作数）、消息"期望number实际string"（非"含string操作数"）
  - SEM-TYPE-003 测试（`literalStringInBinaryProducesOnlySEM_TYPE_003` 等）→ 改期望 SEM-TYPE-001 或删除
  - `hashStringVarInNumberAttrProducesSEM_TYPE_001`（`x="#s"` 根 VARIABLE_REF）→ 新推导 #s=string≠number→SEM-TYPE-001，保持
- **spec**：AC-16

### T9：全量门禁 + golden 同步

- **RED**：跑 `:feature:analysis:test`，定位受影响 golden fixture（类型推断行为变化，`multi_element_expression_blast` 等）
- **GREEN**：更新受影响 `.expected.json`（SEM-TYPE-003→SEM-TYPE-001 转换、新增/移除诊断）
- **门禁**：`gradle --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e` → BUILD SUCCESSFUL
- **spec**：AC-17, AC-18

## 3. 依赖关系

```
T1 (类型类) ──> T2 (VarRef/ArrayAccess) ──> T3 (BinaryExpr) ──> T4 (UnaryExpr+ifelse+CONDITIONAL) ──> T5 (FunctionCall)
                                                                                                          │
T6 (Analyzer改造+删接线) <─────────────────────────────────────────────────────────────────────────────┘
T7 (删方法体) ──> T8 (测试重写) ──> T9 (门禁+golden)
```

T1→T2→T3→T4→T5（engine 自底向上）→T6（analyzer 依赖 engine）→T7→T8→T9。T9 必须最后。

## 4. 风险与缓解

| 风险 | 缓解 |
|---|---|
| golden fixture 波及面大（类型推断行为变化） | T9 先 grep 定位受影响 fixture；若 >10 个回 PHASE 1 评估 |
| `checkStringLiteralInNumExpr` 兼有非 003 职责 | T7 删除前确认其职责（grep 调用），若兼有其他职责只删 003 分支 |
| 现有 SEM-TYPE-003 测试数量未知 | T8 先 grep SEM-TYPE-003 测试，统一调整 |
| `inferFunctionCall` 查所有签名依赖 stub `getSignatures` | T5 用 `getSignature(name,"number"/"string")` 收集（不依赖 `getSignatures(name)`，兼容现有 stub） |
| T6 中间双产状态（FIX005 接线未删） | T6 RED 即暴露双产，GREEN 同步删接线 |

## 5. Commit 策略

每 task 一个 commit，消息：`fix(FIX006): T<n> <短描述>`。T9 含 golden 单独 commit。

---

> **阶段切换**：PHASE 4 完成。9 个 task 顺序，engine 自底向上→analyzer 改造→删除→测试→门禁。请用户确认后进入 PHASE 5（TDD 编码）。
