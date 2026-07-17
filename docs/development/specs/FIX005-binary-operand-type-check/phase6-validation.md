---
module_ids: [M4]
doc_kind: report
status: active
created: 2026-07-16
---
# FIX005 二元/一元表达式操作数类型一致性校验 — PHASE 6 一致性验证

> 阶段：PHASE 6（一致性验证）
> 状态：待用户确认
> 依据：phase2-spec.md（SPEC-1~6）vs 实现 `TypeAnalyzer.java` + `TypeAnalyzerTest.java`/`TypeInferenceEngineTest.java`

## 1. 验证范围

逐项核对 SPEC-1~6 契约、TS-1.1~1.16 测试场景、质量门禁指标，确认代码与 spec/design 一致。

## 2. 全量测试 + 门禁结果

| 项 | 命令 | 结果 |
|---|---|---|
| L1-L3 单元/golden | `gradle --no-daemon :feature:analysis:test` | ✅ 927 tests, 0 failed, 35 skipped（L4 跳过） |
| Core 隔离 | `:feature:analysis:checkCoreIntellijDependency` | ✅ PASSED (0 violations) |
| Fat jar 装配 | `:feature:analysis:buildFatJar` | ✅ SUCCESS |
| L4 子进程 E2E | `:feature:analysis:e2e` | ✅ SUCCESS |
| **全量门禁总和** | test + checkCoreIntellijDependency + buildFatJar + e2e | ✅ BUILD SUCCESSFUL |

## 3. spec/code 一致性核对

| SPEC | 契约 | 实现位置 | 一致性 |
|---|---|---|---|
| SPEC-1 入口 | `checkOperandTypesInNumberExpr(expr, expectedType, engine, symbolTable, context, locationNode, hostDesc, diagnostics)`；仅 number 上下文；不抛异常 | `TypeAnalyzer.checkOperandTypesInNumberExpr` | ✅ 签名/前置（`expectedType instanceof DslNumberType`）/null 安全返回一致 |
| SPEC-2 命中判据 | `inferred != null && inferred instanceof DslStringType` 命中；null/MixedType 跳过 | `collectStringOperandsInNumberExpr` 候选块 | ✅ 一致；D2 null/MixedType 保守跳过 |
| SPEC-3 递归边界 | 根节点规则（仅 BINARY/UNARY 启动）；LITERAL 跳过；FUNCTION_CALL 候选不递归参数；CONDITIONAL 不递归；ARRAY_ACCESS 递归 indexExpression | `checkOperandTypesInNumberExpr` 根检查 + `collectStringOperandsInNumberExpr` switch | ✅ 一致（根节点规则在入口，递归边界在 collect） |
| SPEC-4 上下文覆盖 | `checkAttribute` number 分支 + `checkVarExpressionBody` number 分支调用；string 不调 | `checkAttribute` if 块 + `checkVarExpressionBody` if 块（加去重条件） | ✅ 一致；`checkVarExpressionBody` 加 `exprType==null\|\|typeEquals(exprType,varType)` 条件避免与:281 双产（SPEC-5 修正） |
| SPEC-5 去重 | 与 `checkStringLiteralInNumExpr`(LITERAL)/`checkRefVarExpressionErrors`(string 上下文)/顶层比对不双产 | 跳过 LITERAL；number 上下文与 string 上下文 checkRefVar 不重叠；Var 顶层条件去重 | ✅ 一致（含 PHASE 5 T6 双产修正） |
| SPEC-6 诊断 | SEM-TYPE-001/ERROR/位置=operand（(0,0) 回退 locationNode）/消息模板/每节点 1 条 | `buildOperandTypeMismatchDiagnostic` | ✅ 一致；位置复用 `VarRefAnalyzer.buildUndefinedFunctionDiagnostic` 模式 |

## 4. 测试场景覆盖

| TS | AC | 场景 | 实际测试 | 结果 |
|---|---|---|---|---|
| TS-1.1 | AC-1 | `1+@a` 右操作数 string | `TypeAnalyzerTest.binaryExprRightStringOperandProducesSEM_TYPE_001` | ✅ |
| TS-1.2 | AC-2 | `@a+1` 左操作数 string | `binaryExprLeftStringOperandProducesSEM_TYPE_001` | ✅ |
| TS-1.3 | AC-3 | `1+2+@a` 嵌套 | `nestedBinaryStringOperandProducesSEM_TYPE_001` | ✅ |
| TS-1.4 | AC-4 | `@a+@b` 双 string | `twoStringOperandsProduceTwoSEM_TYPE_001` | ✅ |
| TS-1.5 | AC-5 | `-@a` 一元 | `unaryStringOperandProducesSEM_TYPE_001` | ✅ |
| TS-1.6 | AC-6 | `'a'+@b` string 拼接合法 | `stringContextConcatWithStringVarNoViolation` | ✅ |
| TS-1.7 | AC-7 | `'a'+#num` string 拼 number 合法 | `stringContextConcatWithNumberVarNoViolation` | ✅ |
| TS-1.8 | AC-8 | `#n+2` 回归 | `binaryExprInNumberContextNoViolation`（现有） | ✅ |
| TS-1.9 | AC-9 | `1+'str'` LITERAL 只 SEM-TYPE-003 | `literalStringInBinaryProducesOnlySEM_TYPE_003` | ✅ |
| TS-1.10 | AC-10 | `1+#strVar` #string 修正 | `hashStringVarInBinaryProducesSEM_TYPE_001` | ✅ |
| TS-1.11 | AC-11 | `<Var expression="1+@a" type="number"/>` | `varExpressionNumberContextStringOperandProducesSEM_TYPE_001` | ✅ |
| TS-1.12 | AC-12 | `strfn(1)+1` 函数返回 string | `functionReturningStringInArithmeticProducesSEM_TYPE_001` | ✅ |
| TS-1.13 | AC-13 | `1+#undef` null 保守 | `nullOperandSkipped` | ✅ |
| TS-1.14 | AC-14 | 剧场测试改造 | `TypeInferenceEngineTest.binaryExprReturnsExpectedContext` 补注释；负测试由 TS-1.1 覆盖 | ✅ |
| TS-1.15 | AC-15 | 全量门禁 | 见 §2 | ✅ |
| TS-1.16 | AC-16 | golden 同步 | **实际零 fixture 受影响**（修正避免双产后 `multi_element_expression_blast` line 10 回到 1 条，与原 golden 一致） | ✅（偏差见 §5） |

## 5. 偏差说明（已回填对应 PHASE）

| # | 偏差 | 原因 | 回填 | 处理 |
|---|---|---|---|---|
| 1 | PHASE 1 D4/AC-10：`#`string 在 number 上下文被 `checkRefVarExpressionErrors` 漏报（非"已报需去重"） | 核查 `checkSingleVarExprError:522-526` 的 `typeEquals` 条件 | phase1 §7 | 新校验覆盖 `#`string 在 number 算术 |
| 2 | PHASE 2 SPEC-2/3/5：根节点若为 VARIABLE_REF/LITERAL 会与顶层比对双产 | `hashStringVarInNumberAttrProducesSEM_TYPE_001`(`x="#s"`) 已由顶层报 size==1 | phase2 §7 | 加 SPEC-3 根节点规则（仅 BINARY/UNARY 启动） |
| 3 | PHASE 2 SPEC-5：`checkVarExpressionBody` 用 `inferExpressionType`（非 engine.inferType），对 BINARY 返回第一子节点，与:281 双产 | T6 golden 失败：`multi_element_expression_blast` line 10 `#str_value+10` 双产 | phase2 §7 | `checkVarExpressionBody` 接线加条件 `exprType==null\|\|typeEquals(exprType,varType)` |
| 4 | AC-16：预期"新增 SEM-TYPE-001 条目"，实际零 fixture 受影响 | 修正避免双产 + 现有 fixture 的 number 算术操作数均为 number/LITERAL/#string-在-Var-首位 | 本文档 §4 TS-1.16 | golden 无需更新；方案 A 对现有 fixture 零影响，只对未覆盖场景（如 `Image x="1+@a"`）生效 |

## 6. 覆盖率

- jacoco 报告：`feature/analysis/build/reports/jacoco/test/`（html + xml）
- 新增 3 方法（`checkOperandTypesInNumberExpr`/`collectStringOperandsInNumberExpr`/`buildOperandTypeMismatchDiagnostic`）由 T1-T5 单测直接覆盖主路径：命中、null 跳过、LITERAL 跳过、FUNCTION_CALL 候选不递归参数、位置回退
- 未直接单测的边界分支：
  - `collectStringOperandsInNumberExpr` 的 `ARRAY_ACCESS` indexExpression 递归（AC 未列数组索引含 string 场景）
  - `buildOperandTypeMismatchDiagnostic` 的 `(0,0)` 位置回退（测试均用有效位置 operand）
- TypeAnalyzer 整体 900+ 行，新增 79 行；主路径覆盖，整体行覆盖率维持 >80% 质量门禁

## 7. 质量门禁达成

| 指标 | 要求 | 实际 |
|---|---|---|
| spec 条目测试覆盖率 | 100%（每条 spec 有对应测试） | ✅ SPEC-1~6 均有 TS 覆盖 |
| 单元测试通过率 | 100% | ✅ 927 tests, 0 failed |
| 代码行覆盖率 | > 80% | ✅ 新增主路径覆盖，整体维持（见 §6） |
| 编译告警 | 0 | ✅ checkCoreIntellijDependency 0 violations；javac 仅 deprecation 备注（既有，非本次引入） |
| spec/design/code 一致性 | 无未说明偏差 | ✅ 4 项偏差均已回填对应 PHASE 文档 |

## 8. 一致性验证结论

- ✅ 代码实现与 phase2-spec（SPEC-1~6）、phase3-design（3 方法 + 2 接线 + 1 诊断）一致
- ✅ 16 条验收标准（AC-1~16）全部达成（AC-16 实际零 fixture，偏差已说明）
- ✅ 全量门禁全绿（L1-L4 + Core 隔离 + fat jar）
- ✅ 4 项 PHASE 间偏差均按 SDD 回填修正（不在代码中绕过）
- ✅ 方案 A 对现有 fixture 零影响，只对未覆盖场景（`number 算术含 string 操作数`）新增诊断

---

> **阶段切换**：PHASE 6 完成。FIX005 SDD 六阶段全部完成，全量门禁全绿。请用户确认验收。如需合入 main，按 AGENTS.md 规定需先调用 reviewer agent 代码审查。
