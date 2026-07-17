---
module_ids: [M4]
doc_kind: plan
status: active
created: 2026-07-16
---
# FIX005 二元/一元表达式操作数类型一致性校验 — PHASE 4 任务拆分

> 阶段：PHASE 4（任务拆分）
> 状态：待用户确认
> 依据：phase3-design.md（3 方法 + 2 接线 + 1 诊断）
> 关键修正：phase2-spec.md SPEC-2/3/5 根节点规则（入口仅 BINARY_EXPR/UNARY_EXPR 启动，根为其他 kind 不产诊断，避免与 `checkAttribute:107`/`checkVarExpressionBody:281` 顶层比对双产）

## 1. 任务列表

每个 task = 一个 TDD 循环（RED→GREEN→REFACTOR），完成后 commit。粒度 15-30 分钟。

| Task | 标题 | spec | AC | TS | 粒度 | 依赖 |
|---|---|---|---|---|---|---|
| T1 | 诊断构造 + BINARY 递归 + checkAttribute 接线 | SPEC-1,2,3(BINARY+根规则+LITERAL+FUNC),4(checkAttribute),5,6 | AC-1,2,8 | TS-1.1,1.2,1.8 | 25min | — |
| T2 | 嵌套 + 双操作数 + 一元 | SPEC-3(UNARY), SPEC-6(数量) | AC-3,4,5 | TS-1.3,1.4,1.5 | 15min | T1 |
| T3 | 回归保护 + string 上下文合法 + 去重 | SPEC-4(string 不调), SPEC-5, SPEC-3(LITERAL) | AC-6,7,9,10 | TS-1.6,1.7,1.9,1.10 | 20min | T2 |
| T4 | Var.expression 上下文 + 函数返回 string | SPEC-4(checkVarExpressionBody), SPEC-3(FUNC), SPEC-2 | AC-11,12 | TS-1.11,1.12 | 25min | T3 |
| T5 | null 保守 + 剧场测试改造 | SPEC-2(D2), AC-14 | AC-13,14 | TS-1.13,1.14 | 20min | T4 |
| T6 | 全量门禁 + golden 同步 | AC-15,16 | TS-1.15,1.16 | 30min | T5 |

## 2. 任务详情

### T1：诊断构造 + BINARY 递归 + checkAttribute 接线

- **RED**：在 `TypeAnalyzerTest` 加 `binaryExprRightStringOperandProducesSEM_TYPE_001`（`x="1+@a"`，@a=string 引用，期望 1×SEM-TYPE-001，位置=@a）、`binaryExprLeftStringOperandProducesSEM_TYPE_001`（`x="@a+1"`）。运行确认失败（当前 `inferBinaryExpr` 返回 number，无诊断）。
- **GREEN**：
  - 新增 `buildOperandTypeMismatchDiagnostic(operand, locationNode, hostDesc, context)`——复用 `VarRefAnalyzer.buildUndefinedFunctionDiagnostic:221-245` 位置模式（operand 位置 + (0,0) 回退 locationNode + 显式 `.line/.column/.endLine/.endColumn`）
  - 新增 `collectStringOperandsInNumberExpr(node, expectedType, engine, symbolTable, reported, hits)`——**根节点规则**：仅 BINARY_EXPR/UNARY_EXPR 启动；递归 children；LITERAL 跳过；FUNCTION_CALL 作为候选但不递归参数；命中=inferType 返回 DslStringType
  - 新增 `checkOperandTypesInNumberExpr(expr, expectedType, engine, symbolTable, context, locationNode, hostDesc, diagnostics)`——调 collect 后对每个 hit 调 build 诊断
  - 接线 `checkAttribute` 的 `if (expectedType instanceof DslNumberType)` 块（:115-123）调用新方法
- **REFACTOR**：复用现有 `RULE_TYPE_001`/`ERROR`/`resolveDocUrl`；不重复字面量
- **回归**：现有 `binaryExprInNumberContextNoViolation`（`#n+2`，:116）保持绿（根 BINARY_EXPR，children `#n`(number)不命中、`2`(LITERAL)跳过 → 0 诊断）

### T2：嵌套 + 双操作数 + 一元

- **RED**：加 `nestedBinaryStringOperandProducesSEM_TYPE_001`（`1+2+@a`，期望 1 条定位 @a）、`twoStringOperandsProduceTwoSEM_TYPE_001`（`@a+@b`，期望 2 条）、`unaryStringOperandProducesSEM_TYPE_001`（`-@a`，期望 1 条）
- **GREEN**：在 collect 递归加 `UNARY_EXPR` 分支（递归 children）；嵌套/双操作数由递归自然覆盖。一元 `ExpressionNode.unaryExpr("-", operand, ...)` 构造
- **回归**：T1 用例仍绿

### T3：回归保护 + string 上下文合法 + 去重

- **RED**（部分应直接绿，验证保护）：
  - `stringContextConcatNoViolation`（`textExp="'a'+@b"`，期望 0——string 上下文不接线）
  - `stringContextNumberInConcatNoViolation`（`textExp="'a'+#num"`，num 声明 number，期望 0）
  - `literalStringInBinaryOnlySEM_TYPE_003`（`x="1+'str'"`，期望 1×SEM-TYPE-003 + 0×SEM-TYPE-001，LITERAL 跳过）
  - `hashStringVarInBinaryProducesSEM_TYPE_001`（`x="1+#strVar"`，strVar 声明 string via `varDecl`，期望 1×SEM-TYPE-001——修正 `#`string 在 number 算术漏检）
- **GREEN**：确认 string 上下文不调新校验（SPEC-4 由接线点 `instanceof DslNumberType` 保证）；LITERAL 跳过；`#strVar` inferType=string 命中
- **回归**：现有 `hashStringVarInNumberAttrProducesSEM_TYPE_001`（`x="#s"` 根为 VARIABLE_REF，:92）保持 size==1（根节点规则：根非 BINARY/UNARY 不启动新校验，仅顶层报）

### T4：Var.expression 上下文 + 函数返回 string

- **RED**：
  - `varExpressionNumberContextStringOperandProducesSEM_TYPE_001`（`<Var expression="1+@a" type="number"/>`，期望 1×SEM-TYPE-001）。注：`inferExpressionType(:382)` 对 BINARY_EXPR 返回第一个非 null 子节点类型，`1`→number==varType→顶层:281 不报；新校验报 `@a`→size==1
  - `functionReturningStringInArithmeticProducesSEM_TYPE_001`（`x="strfn(1)+1"`，期望 1×SEM-TYPE-001 定位 strfn 节点）。**需在 `stubLibrary()` 加 `strfn` 签名**：`expressionKind="string"`, `returnType=DslStringType`, params=`[(number)]`（避免 `checkFunctionCalls` SEM-TYPE-002 干扰：strfn 在 number 上下文无签名→fallback string 签名→参数 1(number) 匹配→不报 002；新校验 inferType(strfn)=string→命中 001）
- **GREEN**：接线 `checkVarExpressionBody` 的 `if (varType instanceof DslNumberType)` 块（:293-296）；确认 FUNCTION_CALL 候选命中 + 不递归参数
- **REFACTOR**：stubLibrary 的 strfn 签名与 sin 并列

### T5：null 保守 + 剧场测试改造

- **RED**：
  - `nullOperandSkipped`（`x="1+#undef"`，#undef 未声明，期望 0×SEM-TYPE-001——`inferVariableRef` 对 `#`前缀 lookup 失败返回 null，D2 跳过。注：SEM-REF-001 由 VarRefAnalyzer 独立产出，不在 TypeAnalyzerTest 范围）
  - 改造 `TypeInferenceEngineTest.binaryExprReturnsExpectedContext`（:100）：保留"上下文决定结果类型"契约断言（spec 设计），补注释说明"操作数类型一致性校验由 TypeAnalyzer 层负责"，并新增 `@strPlusNumberInTypeAnalyzerProducesSEM_TYPE_001` 负测试（验证 TypeAnalyzer 报 SEM-TYPE-001）
- **GREEN**：确认 null 跳过；剧场测试改造不破坏现有断言
- **REFACTOR**：剧场测试注释清晰标注"上下文决定论是 spec 设计，非 bug；操作数校验在 TypeAnalyzer 层"

### T6：全量门禁 + golden 同步

- **RED**：跑 `gradle --no-daemon :feature:analysis:test`，定位受影响 golden fixture（grep fixtures 中 `@`/`#string` 出现在 number 算术属性的 `.xml`，其 `.expected.json` 缺新 SEM-TYPE-001）
- **GREEN**：更新受影响 `.expected.json`（新增 SEM-TYPE-001 条目，含 line/column）
- **门禁**：`gradle --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e` → BUILD SUCCESSFUL
- **golden 工具**：可用 `GoldenDumper` 生成草稿后人工复核（仅本次新增的 SEM-TYPE-001 条目）

## 3. 依赖关系

```
T1 (BINARY+接线) ──┬──> T2 (嵌套+一元) ──┬──> T3 (回归+去重) ──> T4 (Var+函数) ──> T5 (null+剧场) ──> T6 (门禁+golden)
                   │                       │
                   └── AC-8 回归保护        └── AC-6/7 string 合法
```

T1→T2→T3→T4→T5→T6 严格顺序（每 task 在前 task 绿基础上增量）。T6 必须最后（依赖全部实现完成）。

## 4. 优先级与风险

| 风险 | 缓解 |
|---|---|
| golden fixture 波及面未知（T6） | T6 先 grep fixtures 定位，再用 GoldenDumper 生成草稿；若波及 >5 个 fixture，回 PHASE 1 评估范围 |
| `strfn` stub 签名与 `checkFunctionCalls` 交互（T4） | T4 RED 先验证 strfn 参数匹配不产 SEM-TYPE-002，隔离 001/002 |
| `inferExpressionType` 对 BINARY_EXPR"第一子节点"推断与 engine"上下文决定"不一致（已确认） | TS-1.11 用 `1+@a`（@a 在后）使顶层不报，隔离新校验；不改动 `inferExpressionType`（超 FIX005 范围） |
| 剧场测试改造破坏现有断言（T5） | 保留 `binaryExprReturnsExpectedContext` 原断言（spec 契约），仅补注释+负测试，不删除断言 |

## 5. Commit 策略

每 task 一个 commit，消息格式：`fix(FIX005): T<n> <短描述>`。T6 含 golden 更新单独 commit。不使用 `--amend`/`--force-push`。

---

> **阶段切换**：PHASE 4 完成。6 个 task 严格顺序，每 task 一个 TDD 循环。请用户确认任务列表后进入 PHASE 5（TDD 编码实现）。
