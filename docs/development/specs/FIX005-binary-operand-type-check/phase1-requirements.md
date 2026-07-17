---
module_ids: [M4]
doc_kind: spec
status: active
created: 2026-07-16
---
# FIX005 二元/一元表达式操作数类型一致性校验 — PHASE 1 需求澄清

> 阶段：PHASE 1（需求澄清）
> 状态：待用户确认
> 分支：`feature/fix005-binary-operand-type-check`（待创建）

## 1. 背景

`TypeInferenceEngine.inferBinaryExpr`（`TypeInferenceEngine.java:104-111`）与 `inferUnaryExpr`（`:113-118`）采用**上下文决定论**：无条件返回 `expectedContext`，递归推断子节点但**丢弃返回值**。该设计在 spec 中有明文依据（`TDD.md:259`、`PRD.md:190`、`M4-SemanticAnalysis.md:120`："BinaryExpression → 上下文决定：number→数值运算，string+→拼接"）。

但该设计漏掉了**操作数类型一致性校验**，导致 string 操作数混入 number 算术表达式时静默通过：

| 表达式（number 属性 x 上） | `@a` 语义 | 当前行为 | 应有行为 |
|---|---|---|---|
| `123+456+@a` | `@a` = string 引用 | 推断为 number，无诊断 | string 参与算术加法 → SEM-TYPE-001 |
| `@a+1` | 同上 | 无诊断 | SEM-TYPE-001 |
| `-@a`（一元取负） | 同上 | 无诊断 | SEM-TYPE-001 |

**三道防线全漏检**（`TypeAnalyzer.checkAttribute` 链路）：
1. `engine.inferType` → `inferBinaryExpr` 恒返回 `expectedContext=number` → `checkAttribute:107` `typeEquals(number,number)=true` → 不产 SEM-TYPE-001。
2. `checkStringLiteralInNumExpr` 只查 `LITERAL` 节点；`@a` 是 `VARIABLE_REF`，抓不到。
3. `checkRefVarExpressionErrors` 只查 `#`前缀引用 string 变量；`@a` 是 `@`前缀（合法 string 访问），不在其范围。

**测试剧场固化**：`TypeInferenceEngineTest.binaryExprReturnsExpectedContext`（`:100-106`）用 `1+2` 在 string 上下文断言返回 string，测试名直陈"返回 expectedContext"实现细节；`TypeAnalyzerTest.binaryExprInNumberContextNoViolation`（`:116`）仅用 `#n+2`（number+number）合法用例，**全仓无 `@str+number` 负测试**，无法区分"合法推断"与"漏检 bug"。

## 2. 目标

采用**方案 A（最小侵入）**：保留"上下文决定结果类型"的既有 spec 约定（`inferBinaryExpr`/`inferUnaryExpr` 仍返回 `expectedContext`），在 `TypeAnalyzer` 层补**操作数类型一致性校验**——number 算术上下文的二元/一元表达式中，任一操作数实际推断为 `DslStringType` 时产出 SEM-TYPE-001，使 string 操作数混入 number 算术不再静默通过。

## 3. 范围

### 包含

| 项 | 范围 |
|---|---|
| F1 | number 上下文二元表达式（`+ - * / %`）中任一操作数实推断为 string → SEM-TYPE-001 |
| F2 | number 上下文一元表达式（`-` 取负等）操作数实推断为 string → SEM-TYPE-001 |
| F3 | 嵌套递归校验（`1+(2+@a)`、`-#n+@a` 等所有层级） |
| F4 | string 操作数来源覆盖：`@`前缀变量引用/数组访问、返回 string 的函数调用 |
| F5 | 诊断定位在 string 操作数节点位置（非二元表达式根位置） |
| F6 | 与现有 `checkStringLiteralInNumExpr`（LITERAL→SEM-TYPE-003）、`checkRefVarExpressionErrors`（`#`string→SEM-TYPE-001）去重，不重复产出 |
| F7 | Var.expression（auto/number 上下文）同样校验 |
| F8 | 删除/改造剧场测试 `binaryExprReturnsExpectedContext` 与补 `@str+number` 负测试 |

### 不包含

- string 上下文 `+` 拼接的操作数校验（保留"任何类型可拼接"语义，`'a'+#num`、`'a'+@b` 合法不报）
- `-*/%` 在 string 上下文的合法性（超出本 fix 范围，`-*/%` 在 string 上下文仍按既有行为）
- `inferBinaryExpr` 改为"操作数推导结果类型"（方案 B，改动大，留待后续）
- FIX003（null 函数库吞 SEM-TYPE-*，独立追踪）
- SEM-TYPE-003 spec 表缺失（独立追踪）

## 4. 关键决策（建议默认值，请用户确认或纠正）

| # | 决策点 | 建议默认 | 理由 |
|---|---|---|---|
| D1 | 触发判据 | 操作数 `inferType` 实际返回 `DslStringType`（按 name 比对） | 复用现有类型体系，不引入新类型 |
| D2 | null/MixedType 操作数 | 保守不报（返回 null 或 `DslMixedType` 时跳过该操作数） | 避免对未知/混合类型误报 |
| D3 | 与 LITERAL string 去重 | 新校验**跳过 LITERAL 节点**，LITERAL string 仍由 `checkStringLiteralInNumExpr` 产 SEM-TYPE-003 | 二者正交，不双产；保留 SEM-TYPE-003 既有语义 |
| D4 | 与 `#`string 关系（**PHASE 2 前修正**） | 经核查 `checkSingleVarExprError` 的 `#`string 分支：`#`前缀引用 string 变量仅在 `expectedType==null \|\| typeEquals(decl.type, expectedType)` 成立时报（即 string 上下文或无期望）。number 上下文下 `typeEquals(string, number)=false`→**不报（漏检）**。故新校验在 number 上下文覆盖 `#`string 操作数，与 `checkRefVarExpressionErrors`（string 上下文独占）**上下文不重叠，不存在双产** | 修正 PHASE 1 初判；方案 A 价值扩大：覆盖 number 算术中 `#`string 漏检 |
| D5 | 函数调用返回 string | 纳入校验（`substr(...)+1` 在 number 上下文 → SEM-TYPE-001，定位在函数调用节点） | string 返回值参与算术同样语义错误 |
| D6 | 诊断数量 | 每个 string 操作数节点报 1 条；`@a+@b` 报 2 条（位置不同） | 精确逐点定位 |
| D7 | 诊断位置 | string 操作数节点的 line/column | 便于 LSP/IDE 定位 |
| D8 | 消息格式 | 复用 SEM-TYPE-001，消息体现"操作数类型不一致"：如 `类型不匹配，表达式含 string 类型操作数 @a，不能参与 number 算术运算` | 与现有 SEM-TYPE-001 同规则不同场景 |
| D9 | 校验落点 | TypeAnalyzer 层（新增 `checkBinaryOperandTypes` 递归校验），不改 TypeInferenceEngine 推断结果 | 保持 engine/analyzer 分层；engine 只推断，analyzer 产诊断 |
| D10 | verbose 记录 | 操作数校验命中时，verbose 推断链已有记录（inferred=number,expected=number,match=OK），不额外改 verbose 协议 | 最小改动 |

## 5. 约束

- Core 层无 `com.intellij` import（`checkCoreIntellijDependency` 强制）
- 不引入新依赖
- 不改 `TypeInferenceEngine.inferBinaryExpr`/`inferUnaryExpr` 的返回值（保留上下文决定论）
- 现有合法用例必须保持不报：`#n+2`（number+number）、`'a'+@b`（string 上下文拼接）、`'a'+#num`（string 上下文拼接）
- 受策略变更影响的 golden fixture（`@str` 混入 number 算术的 fixture）需同步更新 `.expected.json`
- 每项修复后全量门禁全绿：`gradle --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e`

## 6. 验收标准（每条可测试）

| AC # | 验收标准 | 测试方式 |
|---|---|---|
| AC-1 | number 属性 `x="1+@a"`（`@a` 为 string 引用）产 SEM-TYPE-001，位置在 `@a` 节点 | TypeAnalyzerTest 单测 |
| AC-2 | number 属性 `x="@a+1"` 产 SEM-TYPE-001（右操作数为 string 同样报） | 单测 |
| AC-3 | number 属性 `x="1+2+@a"` 嵌套二元式产 SEM-TYPE-001，定位在 `@a` | 单测 |
| AC-4 | number 属性 `x="@a+@b"` 两个 string 操作数产 2 条 SEM-TYPE-001（各定位其操作数） | 单测 |
| AC-5 | number 属性 `x="-@a"` 一元取负产 SEM-TYPE-001 | 单测 |
| AC-6 | string 属性 `textExp="'a'+@b"` 拼接**不报**（合法） | 单测 |
| AC-7 | string 属性 `textExp="'a'+#num"` 拼接**不报**（合法，number 参与拼接） | 单测 |
| AC-8 | number 属性 `x="#n+2"`（`#n` 为 number 变量）**不报**（回归保护，现有 `binaryExprInNumberContextNoViolation` 保持绿） | 现有单测 |
| AC-9 | number 属性 `x="1+'str'"` 字面量 string 产 SEM-TYPE-003（由 `checkStringLiteralInNumExpr`），新校验**不额外**产 SEM-TYPE-001（去重） | 单测 |
| AC-10 | number 属性 `x="1+#strVar"`（`#`前缀引用 string 变量，strVar 声明为 string）由**新校验**产 SEM-TYPE-001（`checkRefVarExpressionErrors` 在 number 上下文因 `typeEquals` 失败不报——此为方案 A 修正的漏检） | 单测 |
| AC-11 | `<Var expression="1+@a" type="number"/>` 产 SEM-TYPE-001 | 单测 |
| AC-12 | number 属性 `x="substr('x',1,2)+1"`（函数返回 string 参与算术）产 SEM-TYPE-001，定位在函数调用节点 | 单测 |
| AC-13 | 操作数推断返回 null 或 `DslMixedType` 时**不报**（保守） | 单测 |
| AC-14 | 剧场测试 `TypeInferenceEngineTest.binaryExprReturnsExpectedContext` 改造为行为契约测试（断言推断结果类型正确性，而非"返回 expectedContext"实现细节）；或明确标注其为"上下文决定论"契约测试并补充注释 | 单测改造 |
| AC-15 | 全量门禁全绿（含 L4 E2E） | `gradle --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e` BUILD SUCCESSFUL |
| AC-16 | 受影响 golden fixture 的 `.expected.json` 同步更新，L3/L4 golden 匹配通过 | GoldenDiagnosticMatchTest + E2E |

## 7. 偏差修正记录

| 日期 | 修正项 | 修正前 | 修正后 | 触发 |
|---|---|---|---|---|
| 2026-07-16 | D4 / AC-10 | D4 假设 `#`string 在 number 上下文由 `checkRefVarExpressionErrors` 报，新校验需去重 | 经核查 `checkSingleVarExprError`，`#`string 在 number 上下文不报（漏检）；新校验覆盖之，与 `checkRefVarExpressionErrors` 上下文不重叠，无需去重 | PHASE 2 规格定义前核查 `checkSingleVarExprError:522-526` 实际逻辑 |

---

> **阶段切换**：PHASE 1 完成（含 D4/AC-10 偏差修正）。用户已确认 D1–D10 默认值。进入 PHASE 2（规格定义）。
