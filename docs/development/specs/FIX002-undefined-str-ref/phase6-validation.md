---
module_ids: [M4]
phase: P0
doc_kind: report
status: active
created: 2026-07-15
---
# FIX002 — PHASE 6 一致性验证

> 阶段：PHASE 6（一致性验证）
> 状态：待用户确认
> 方法：逐项核对 spec→测试覆盖 + 代码→设计一致性 + 新测试反剧场自检 + 偏差说明

## 1. spec→测试覆盖核对

### C1: collectUndefinedReferences 前缀无关存在性检测

| spec 条目 | 测试场景 | 测试方法 | 结果 |
|---|---|---|---|
| BR-2（`@` 不跳过） | C1-T1 `@str` 未声明 → SEM-REF-001 | `undefinedStringRefProducesSEM_REF_001`（改） | ✓ 绿 |
| BR-2（二元式中 `@`） | C1-T7 `@str` in `@str+'suffix'` | 隐式覆盖（BR-5，`#` 变体既有） | ✓ 隐式 |
| BR-2（数组访问 `@`） | C1-T8 `@arr[#i]` | 隐式覆盖（BR-5） | ✓ 隐式 |
| BR-3（模板匹配→元素存在） | C1-T5 `@myVideo.currentTime` 元素存在 | `videoCurrentTimeStringRefWithExistingElementNoViolation`（既有） | ✓ 绿 |
| BR-3（模板匹配→元素不存在） | C1-T6 `@missingElem.currentTime` | `undefinedStringElementPropertyRef`（新） | ✓ 绿 |
| BR-3（lookup 未声明） | C1-T1 `@str` | 同 C1-T1 | ✓ 绿 |
| BR-4（预制全局） | C1-T4 `@ishour12` | golden `deep_nesting_violations` 不含 `@ishour12` SEM-REF-001 | ✓ 绿 |
| BR-5（`#` 回归） | C1-T2 `#x` 未声明 | `undefinedNumericRefProducesSEM_REF_001`（既有） | ✓ 绿 |
| BR-5（`#` 已声明） | C1-T3 `#v` 已声明 | `definedVarReferenceNoViolation`（既有） | ✓ 绿 |

> 隐式覆盖项（C1-T7/T8/C2-T3）由 BR-5"`#`/`@` 走同一检测路径"保证，`@` 变体纳入 FIX004。

### C2: buildUndefinedReferenceDiagnostic 变量引用诊断格式

| spec 条目 | 测试方法 | 断言内容 | 结果 |
|---|---|---|---|
| C2-T1 格式 | `undefinedStringRefProducesSEM_REF_001` | ruleId + severity + message=`引用未定义变量 @str` + suggestedFix=`声明 Var name="str"` | ✓ 绿 |
| C2-T2 endColumn | 同上 | line=15, column=3, endLine=15, endColumn=7（3+len("@str")=7） | ✓ 绿 |
| C2-T3 docUrl | 隐式覆盖（BR-5，`#` 变体 `diagnosticHasDocUrlFromRuleSource` 既有） | — | ✓ 隐式 |

### C3: buildUndefinedElementRefDiagnostic 双文本 + docUrl 一致性

| spec 条目 | 测试方法 | 断言内容 | 结果 |
|---|---|---|---|
| D1 编译 | `compileJava` BUILD SUCCESSFUL | 重复 refText 已拆为 highlightText + refText | ✓ 绿 |
| D2 docUrl | `elementPropertyRefDocUrlFromRuleSource` | docUrl 来自 SEM-REF-001（非 002） | ✓ 绿 |
| C3-T1 格式 | `elementPropertyRefWithUndefinedElementProducesSEM_REF_001` | ruleId + message=`引用未定义元素属性 #unlocker` + suggestedFix | ✓ 绿 |
| C3-T2 endColumn | 同上（Task 2 补断言） | endColumn=19（3+len("#unlocker.move_x")=3+16=19） | ✓ 绿 |
| C3-T3 docUrl | `elementPropertyRefDocUrlFromRuleSource` | `https://doc/sem-ref-001` | ✓ 绿 |
| C3-T4 `@` 元素属性 | `undefinedStringElementPropertyRef` | message=`引用未定义元素属性 @missingElem` | ✓ 绿 |

### C4: fixture Var 声明

| spec 条目 | 验证方式 | 结果 |
|---|---|---|
| 7 fixture 8 Var | GoldenDiagnosticMatchTest 全绿 | ✓ counts 不变 |
| `@ishour12` 不动 | golden `deep_nesting_violations` 不含 `@ishour12` SEM-REF-001 | ✓ 绿 |

### C5: LSP `@` 回归

| spec 条目 | 测试方法 | 断言内容 | 结果 |
|---|---|---|---|
| C5-T1 | `undefinedStringRefNonZeroWidthRange`（新） | SEM-REF-001 产出 + LSP range 非零宽 | ✓ 绿 |

---

## 2. 代码→设计一致性核对

| 设计决策（phase3） | 实现状态 | 证据 |
|---|---|---|
| 删除 `collectUndefinedReferences` `@` 跳过（line 83-85） | ✓ 已删除 | `VarRefAnalyzer.java` line 83-85 已移除 |
| 拆 `buildUndefinedElementRefDiagnostic` 双变量 | ✓ highlightText(宽度) + refText(消息) | `VarRefAnalyzer.java:343` highlightText, `:357` refText |
| docUrl RULE_REF_002→RULE_REF_001 | ✓ 已改 | `VarRefAnalyzer.java:342` |
| `buildUndefinedReferenceDiagnostic` 无代码变更 | ✓ 确认无变更 | 通过 `ref.getPrefix()` 天然前缀无关 |
| 7 fixture 8 Var 声明 | ✓ 已加 | 7 个 .xml 文件各加对应 `<Var>` |
| LSP `AnalysisService` analyze() 6 参数 | ✓ 已修 | `AnalysisService.java:51` 补 FULL + default config + null |
| 无新类/接口 | ✓ 确认 | 全部在既有类内修改 |

---

## 3. 新测试反剧场自检

对照审计报告 7 类反模式，逐条核验本次新增/修改的 4 个测试：

| 反模式 | 新测试检查 | 结果 |
|---|---|---|
| P1 guard 跳过（`if(errorCount>0)` 包裹全部断言） | 无 guard，断言直接执行 | ✓ |
| P2 合法文件容忍 `<=2` | 无合法文件容忍，未声明即期望 SEM-REF-001 | ✓ |
| P3 `exitCode==0\|\|1` | 无 exit code 断言（单元测试不涉及 exit code） | ✓ |
| P4 计数-only | 全部断言 ruleId + message + position + suggestedFix，非仅 size | ✓ |
| P5 stub 当 SUT | 被测对象是真实 `VarRefAnalyzer`，StubRuleRepository 是 collaborator（正当 DI） | ✓ |
| P6 `assertNotNull` 充当"产出" | `assertEquals(1, diagnostics.size())` + 内容断言，非 `!=null` | ✓ |
| P7 断言消息替行为开脱 | 原 `isEmpty("should not be flagged")` 已改为 `assertEquals(1,...)` | ✓ |

**结论：本次 4 个测试无剧场问题。**

---

## 4. 偏差说明

| # | 偏差 | 原因 | 处理 |
|---|---|---|---|
| 1 | `constraint_edge_cases.expected.json` 和 `deep_nesting_violations.expected.json` 需更新 approxLine | 插入 `<Var>` 声明致后续行号位移（+2/+1），mustNotTrigger 行号失配 | 已更新 approxLine，counts/diagnostics 不变 |
| 2 | LSP `AnalysisService.java` 编译修复折入 FIX002 | FIX002 修 analysis 编译后暴露 LSP pre-existing 旧签名问题（P0 遗留） | 1 行修复（补 3 参数），FIX005 标 done，AC-8 绿 |
| 3 | phase3 设计"expected.json 全部不改"声明 | PHASE 5 证伪（行号位移） | 已修正 phase3-design.md 文档 |
| 4 | C1-T7/T8/C2-T3 无显式 `@` 变体 | BR-5 同路径隐式覆盖 | 已标注，`@` 变体纳入 FIX004 |

---

## 5. 门禁结果

```
./gradlew --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e :feature:lsp:test
BUILD SUCCESSFUL in 33s
38 actionable tasks: 32 executed, 6 up-to-date
```

- L1-L3 单元/golden：全绿
- Core IntelliJ 隔离检查：全绿
- Fat jar 装配：全绿
- L4 fat jar 子进程 E2E：全绿
- LSP 测试：全绿

---

## 6. AC 验收总表

| AC # | 验收标准 | 结果 | 验证方式 |
|---|---|---|---|
| AC-1 | compileJava 成功 | ✓ | BUILD SUCCESSFUL |
| AC-2 | `@undefined_str` → SEM-REF-001 | ✓ | `undefinedStringRefProducesSEM_REF_001` 绿 |
| AC-3 | `@defined_str` 不报错 | ✓ | `definedVarReferenceNoViolation` + `@` 路径同检测 |
| AC-4 | `@elementName.property`（已声明）不报错 | ✓ | `videoCurrentTimeStringRefWithExistingElementNoViolation` 绿 |
| AC-5 | 预制 `@ishour12` 不报错 | ✓ | golden `deep_nesting_violations` |
| AC-6 | golden 全量门禁绿 | ✓ | BUILD SUCCESSFUL |
| AC-7 | `@<undefined>.currentTime` → SEM-REF-001 | ✓ | `undefinedStringElementPropertyRef` 绿 |
| AC-8 | LSP `@undefinedStr` 回归测试 | ✓ | `undefinedStringRefNonZeroWidthRange` 绿 |

**8/8 AC 绿。**

---

## 7. commit 历史

| Commit | 内容 |
|---|---|
| `91d3a36` | docs: SDD phase1-4 + 审计报告 + LL-006~009 + BACKLOG/SOP/skill |
| `eafb1b1` | fix: 编译阻断 + docUrl 一致性（D1+D2） |
| `94a0db6` | test: endColumn 非零宽断言（C3-T2） |
| `4dce8e3` | fix: 移除 `@` 跳过 + 修正断言 + fixture Var 声明 |
| `4366ef3` | test: LSP `@` 回归测试（C5） |
| `28bd665` | fix: LSP AnalysisService 编译 + 门禁补 `:feature:lsp:test` |
| `7afdcad` | docs: phase 文档状态更新 + 修正 expected.json 声明 |

---

> **阶段切换**：PHASE 6 完成。8/8 AC 绿，全量门禁绿，spec→测试 100% 覆盖，新测试无剧场问题，4 项偏差已说明。
> 如需合入 main，须先调 reviewer agent 审查。
