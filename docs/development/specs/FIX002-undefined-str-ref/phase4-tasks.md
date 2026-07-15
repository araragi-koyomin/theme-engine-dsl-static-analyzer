---
module_ids: [M4]
phase: P0
doc_kind: spec
status: active
created: 2026-07-15
---
# FIX002 — PHASE 4 任务拆分

> 阶段：PHASE 4（任务拆分）
> 状态：待用户确认
> 每个 task 对应一个 TDD 循环，粒度 5-25min。

## 任务列表

### Task 1: FIX-A 解除编译阻断 + docUrl 一致性

| 项 | 内容 |
|---|---|
| spec | C3-D1（重复 refText）, C3-D2（docUrl 查错 rule）, C3-T1, C3-T3 |
| 类型 | TDD（RED=编译失败, GREEN=修复编译） |
| 依赖 | 无（最先执行，解除一切阻断） |
| 粒度 | ~15min |

**RED**：`./gradlew --no-daemon :feature:analysis:compileJava` → BUILD FAILED（line 357 重复 `refText`）。编译修通后 `elementPropertyRefDocUrlFromRuleSource` 失败（docUrl=null，因 line 342 用 RULE_REF_002 查 SEM-REF-001 source 查不到）。

**GREEN**：
1. `buildUndefinedElementRefDiagnostic` 拆 line 343 的 `refText` 为 `highlightText`（prefix+varName，用于 endColumn 宽度），保留 line 357 的为 `messageRef`（prefix+elementName，用于消息）。line 355 改用 `highlightText.length()`。
2. line 342 `RULE_REF_002` → `RULE_REF_001`（docUrl 与 ruleId 一致）。

**REFACTOR**：无。

**验证**：
- `compileJava` BUILD SUCCESSFUL
- `./gradlew --no-daemon :feature:analysis:test --tests "*.VarRefAnalyzerTest.elementPropertyRefWithUndefinedElementProducesSEM_REF_001"` 绿
- `./gradlew --no-daemon :feature:analysis:test --tests "*.VarRefAnalyzerTest.elementPropertyRefDocUrlFromRuleSource"` 绿

**commit**：`fix(FIX002): 解除 buildUndefinedElementRefDiagnostic 编译阻断 + docUrl/ruleId 一致性`

---

### Task 2: C3-T2 endColumn 非零宽特征化断言

| 项 | 内容 |
|---|---|
| spec | C3-T2（endColumn = column + len(highlightText)） |
| 类型 | 特征化测试（补充覆盖，实现已正确，非 RED→GREEN） |
| 依赖 | Task 1 |
| 粒度 | ~5min |

**操作**：给 `elementPropertyRefWithUndefinedElementProducesSEM_REF_001`（VarRefAnalyzerTest:309）补 `endColumn` 断言。

`#unlocker.move_x` 在 column=3 → endColumn = 3 + len("#unlocker.move_x") = 3 + 16 = 19。

**验证**：`./gradlew --no-daemon :feature:analysis:test --tests "*.VarRefAnalyzerTest.elementPropertyRefWithUndefinedElementProducesSEM_REF_001"` 绿。

**commit**：`test(FIX002): 补 endColumn 非零宽断言（C3-T2 特征化）`

---

### Task 3: FIX-B+C+D 移除 `@` 跳过 + 修正断言 + fixture Var 声明

| 项 | 内容 |
|---|---|
| spec | C1(BR-2/BR-3/BR-5), C2(T1/T2), C1-T6, C3-T4, C4 |
| 类型 | TDD（RED=测试失败, GREEN=删 @ 跳过 + fixture） |
| 依赖 | Task 1 |
| 粒度 | ~25min |

**RED**：
1. 改 `VarRefAnalyzerTest.undefinedStringRefProducesSEM_REF_001`（line 66-73）：断言从 `assertTrue(diagnostics.isEmpty())` 改为期望 1 条 SEM-REF-001（ruleId, severity=ERROR, message=`引用未定义变量 @str`, line=15, column=3, endColumn=3+len("@str")=7, suggestedFix=`声明 Var name="str"`）。
2. 新增 `VarRefAnalyzerTest.undefinedStringElementPropertyRef`：`@missingElem.currentTime`（用 `repoWithTemplates()`，elementNames 为空）→ 期望 1 条 SEM-REF-001（message=`引用未定义元素属性 @missingElem`, suggestedFix=`声明带 name="missingElem" 的元素`）。
3. 运行 → 两测试失败（`@` 被 line 83-85 跳过，diagnostics 为空）。

**GREEN**：
1. 删除 `VarRefAnalyzer.collectUndefinedReferences` line 83-85（`@` 跳过分支）。
2. 7 个 fixture 加 8 处 `<Var name="xxx" type="string"/>`（constraint_edge_cases 2 处）：
   - wallpaper_constraint_enum: `dynamic_bg`
   - lockscreen_nesting_var: `dynamic_overlay`
   - charging_skin_cmd_nest: `charge_icon`
   - deep_nesting_violations: `deep_dynamic`
   - constraint_edge_cases: `dynamic_src` + `only_srcexp`
   - lockscreen_multi_error: `weather_icon`
   - wallpaper_invalid_enum: `icon`
3. 运行 → 两测试绿 + golden 绿（fixture Var 使 `@xxx` 合法，counts 不变）。

**REFACTOR**：无。

**验证**：
- `./gradlew --no-daemon :feature:analysis:test --tests "*.VarRefAnalyzerTest"` 全绿
- `./gradlew --no-daemon :feature:analysis:test --tests "*.GoldenDiagnosticMatchTest"` 全绿
- `./gradlew --no-daemon :feature:analysis:test --tests "*.SemanticAnalysisIntegrationTest"` 全绿

**commit**：`fix(FIX002): 移除 @ 前缀跳过，string 变量引用参与存在性检测 + fixture Var 声明`

> FIX-B 与 FIX-D 必须同提交：删 `@` 跳过后若不声明 Var，golden 因新增 SEM-REF-001 而红。

---

### Task 4: FIX-E C5 LSP `@` 回归测试

| 项 | 内容 |
|---|---|
| spec | C5-T1 |
| 类型 | 回归保护（Task 3 后 `@` 已正确处理，非 RED→GREEN） |
| 依赖 | Task 3 |
| 粒度 | ~10min |

**操作**：`AnalysisServiceTest` 新增 `undefinedStringRefNonZeroWidthRange`（与现有 `undefinedVariableDiagnosticHasNonZeroWidthRange` 对称）：
- 输入：`<Lockscreen><Text name="t" textExp="@undefinedStr"/></Lockscreen>`
- 断言：core 诊断非空 + 含 SEM-REF-001 + LSP range 非零宽（endCharacter > startCharacter）

**验证**：`./gradlew --no-daemon :feature:lsp:test --tests "*.AnalysisServiceTest.undefinedStringRefNonZeroWidthRange"` 绿。

**commit**：`test(FIX002): LSP 补 @ 未定义引用回归测试（C5）`

---

### Task 5: 全量门禁验证

| 项 | 内容 |
|---|---|
| spec | AC-1~AC-8 全量 |
| 类型 | 验证（非编码） |
| 依赖 | Task 1-4 |
| 粒度 | ~10min |

**操作**：
```bash
./gradlew --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e
./gradlew --no-daemon :feature:lsp:test
```

**验证 AC 对照**：
| AC | 验证方式 |
|---|---|
| AC-1 | compileJava SUCCESSFUL（Task 1） |
| AC-2 | `undefinedStringRefProducesSEM_REF_001` 绿（Task 3） |
| AC-3 | `definedVarReferenceNoViolation` 绿（既有，`@` 路径现走同一检测） |
| AC-4 | `videoCurrentTimeStringRefWithExistingElementNoViolation` 绿（既有） |
| AC-5 | golden `deep_nesting_violations` 不含 `@ishour12` 的 SEM-REF-001 |
| AC-6 | 全量门禁 BUILD SUCCESSFUL |
| AC-7 | `undefinedStringElementPropertyRef` 绿（Task 3） |
| AC-8 | `undefinedStringRefNonZeroWidthRange` 绿（Task 4） |

**commit**：无（验证步骤）。

---

## 依赖图

```
Task 1 (FIX-A 编译)
  ├── Task 2 (C3-T2 endColumn)
  └── Task 3 (FIX-B+C+D @跳过+断言+fixture)
        └── Task 4 (FIX-E LSP 回归)
              └── Task 5 (全量门禁)
```

## spec 条目覆盖核对

| spec 条目 | Task | 测试场景 |
|---|---|---|
| C3-D1 | Task 1 | 编译通过 |
| C3-D2 | Task 1 | elementPropertyRefDocUrlFromRuleSource |
| C3-T1 | Task 1 | elementPropertyRefWithUndefinedElementProducesSEM_REF_001 |
| C3-T2 | Task 2 | endColumn 断言 |
| C3-T3 | Task 1 | elementPropertyRefDocUrlFromRuleSource |
| C1 BR-2 | Task 3 | undefinedStringRefProducesSEM_REF_001 (改) |
| C1 BR-3 | Task 3 | undefinedStringElementPropertyRef (新) |
| C1 BR-5 | Task 3 | undefinedNumericRefProducesSEM_REF_001 (既有回归) |
| C2-T1/T2 | Task 3 | undefinedStringRefProducesSEM_REF_001 (改, 含 endColumn) |
| C1-T6/C3-T4 | Task 3 | undefinedStringElementPropertyRef (新) |
| C4 | Task 3 | 7 fixture GoldenDiagnosticMatchTest |
| C5-T1 | Task 4 | undefinedStringRefNonZeroWidthRange (新) |

> **隐式覆盖说明**（review 指出 C1-T7/T8/C2-T3/C1-T3/C1-T5 无显式 `@` 变体）：这些场景仅有 `#` 前缀测试（binaryExprReportsBothUndefinedRefs / arrayAccessReportsUndefinedArrayVar / diagnosticHasDocUrlFromRuleSource / definedVarReferenceNoViolation / videoCurrentTimeStringRefWithExistingElementNoViolation），由 C1 BR-5（"`#`/`@` 走同一检测路径，无分支差异"）隐式覆盖。FIX002 范围内不新增 `@` 变体；补 `@` 变体纳入 FIX004 测试剧场治理。

---

> **阶段切换**：PHASE 4 完成。请用户确认任务列表（5 task, 4 commit, 依赖图, spec 覆盖核对），确认后进入 PHASE 5（TDD 编码）。
