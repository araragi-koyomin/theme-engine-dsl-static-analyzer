---
module_ids: [M4, M7, CLI, E2E]
doc_kind: report
status: active
created: 2026-07-15
---
# 测试剧场审计报告

> 触发：FIX002（`@undefined_str` 不报错）暴露 `VarRefAnalyzerTest.undefinedStringRefProducesSEM_REF_001` 测试名声称产出 SEM-REF-001、断言却写 `isEmpty()`。
> 范围：全测试套件 89 个 `*Test.java`，4 个 explore agent 并行逐文件精读。
> 方法：对照"测试名/注释声称的行为"与"断言实际检查的行为"，找出名实不符。

## 0. 总览

| 严重度 | 数量 | 含义 |
|---|---|---|
| CRITICAL | 15 | 测试名声称行为 X，断言检查相反/空——**直接掩盖真实 bug** |
| HIGH | 34 | 平凡真断言（`size()>=0`/`!=null`）/ 仅计数不验内容 / guard 跳过全部断言 |
| MEDIUM | 59 | 断言过弱（只查存在不查值）/ 名不副实 / 计数-only |
| LOW | 9 | 冗余断言 / setter 往返不完整 |

**新发现的真实 bug（被测试剧场掩盖）**：`TypeAnalyzer.java:53-56` 当 `functionLibrary == null` 时 `return Collections.emptyList()`，导致**所有** SEM-TYPE-001（含与函数无关的字面量类型不匹配）被静默吞掉。`TypeAnalyzerTest.nullFunctionLibrarySkipped` 把这个 bug 编码为"正确"。

## 1. 反复出现的反模式（系统性问题）

| # | 反模式 | 出现位置 | 危害 |
|---|---|---|---|
| P1 | **guard 跳过断言**：`if (errorCount>0) {...}` / `if (diagnostics.isEmpty()) return;` | PipelineEndToEndTest ×5, BatchInspectionRealScenarioTest ×3, BatchInspectionIntegrationTest ×4, CliMainE2ETest ×1 | analyzer 完全坏掉（0 诊断）时测试**无断言执行即通过** |
| P2 | **合法文件容忍 false positive**：`errorCount <= 2` on "valid"/"clean" fixture | PipelineEndToEndTest ×2, BatchInspectionRealScenarioTest ×1 | 把"合法文件产生 ≤2 报错"当正确，掩盖 false positive |
| P3 | **`exitCode == 0 \|\| exitCode == 1`** on "withErrors" 文件 | cli/ 共 33 处 | 名含"Errors"却接受 exit 0（未检出），掩盖漏检 |
| P4 | **计数-only**：`size==N` 不查 ruleId/message | VarRefAnalyzerTest ×3, ConstraintAnalyzerTest ×2, BatchInspectionRealScenarioTest ×4 | 错规则/错消息凑够数即通过 |
| P5 | **stub 当 SUT**：被测对象是测试内自定义 stub | DiagnosticProviderTest ×2, FunctionSignatureLibraryTest ×5, DslFileMatcherTest ×1 | 测试 stub 自身硬编码值，不覆盖真实实现 |
| P6 | **`assertNotNull` 充当"产出"验证**：名含 Produces，断言只 `!=null`（空列表也通过） | BatchInspectionIntegrationTest ×1, DiagnosticProviderDegradationTest ×1 | 修复链产出空也通过 |
| P7 | **断言消息替行为开脱**：`"@`-prefixed ... should not be flagged"` | VarRefAnalyzerTest ×1（触发本次审计的元凶） | 把 bug 编码为"设计如此" |

## 2. CRITICAL 发现（15 项，按模块）

### M4 语义分析（2 项）

**C1. `VarRefAnalyzerTest.java:66` `undefinedStringRefProducesSEM_REF_001`**
- 名：未定义 `@` 引用产出 SEM-REF-001
- 断言：`assertTrue(diagnostics.isEmpty(), "@-prefixed variable refs should not be flagged as undefined")`
- 掩盖：`VarRefAnalyzer.java:83-85` `@` 跳过（FIX002 Bug B 本体）

**C2. `TypeAnalyzerTest.java:232` `nullFunctionLibrarySkipped`**
- 名：函数库为 null 时类型检查"跳过"
- 断言：字符串字面量 `'hello'` 在 number 属性中 → `isEmpty()`
- 掩盖：`TypeAnalyzer.java:53-56` `functionLibrary==null` 整体返回空，连字面量不匹配都不报。任何无函数库的调用方 SEM-TYPE-* 全静默。**这是与 Bug B 同类的真实 bug，建议立 FIX003。**

### M7 批量检查（5 项）

**C3. `BatchInspectionIntegrationTest.java:112` `runOnFileWithInvalidAttrValue`**
- guard `if (errorCount>0||warningCount>0)` —— 漏检时跳过全部断言通过

**C4. `BatchInspectionIntegrationTest.java:220` `diagnosticProviderAndQuickFixChainProducesFixActions`**
- 名"ProducesFixActions"，断言 `assertNotNull(fileResult.getFixActions())`（空列表通过）

**C5. `BatchInspectionRunnerImplTest.java:719` `runOnDirectoryWithUnreadableFileGracefullyDegraded`**
- 名"UnreadableFile"，**从未创建不可读文件**，只建可读文件断言 `totalFiles>=1`

**C6. `PipelineEndToEndTest.java:307` `cleanFileHasMinimalOrZeroErrors`**
- 合法文件 `errorCount <= 2`（反模式 P2）

**C7. `PipelineEndToEndTest.java:108` `exitCodeZeroForCleanFile`**
- 名"Zero"，断言接受 exit 1 + `errorCount<=2`

**C8. `BatchInspectionRealScenarioTest.java:194` `cleanLockscreenHasMinimalErrors`**
- 同 P2，`lockscreen_valid.xml` `errorCount<=2`

### CLI（5 项）

**C9. `CliMainE2ETest.java:180` `singleFileWithErrors_returnsOne_terminalFormatShowsErrors`**
- 名"returnsOne"+"ShowsErrors"，断言 `exitCode==0||1` + `stdout.length()>0`

**C10. `CliMainE2ETest.java:201` `singleFileWithErrors_jsonFormat_producesOutput`**
- 名"WithErrors"，接受 exit 0，只查输出以 `{`/`[` 开头

**C11. `CliMainE2ETest.java:223` `singleFileWithErrors_markdownFormat_producesReport`**
- 同 C10，markdown 标题在零诊断时也存在

**C12. `CliMainE2ETest.java:257` `syntaxOnlyMode_producesOutput_skipsSemanticDiags`**
- 名"skipsSemanticDiags"，**从未验证输出中无 SEM-\***。`ModeGoldenTest` 正确做了此事，但本 E2E 未做。

**C13.** C9-C12 同源：`exitCode==0||1` 在 cli/ 共 33 处（反模式 P3）

### M3 语法分析（1 项）

**C14. `SyntaxCheckerTest.java:69` `xmlFormatErrorReturnsEmpty`**（待确认设计意图）
- 畸形 XML（`<Lockscreen><Image></Lockscreen>`）→ `assertTrue(isEmpty())`
- 疑点：AST 设 `hasError=true` 但从不作为诊断抛出。若设计应告知用户 XML 损坏，则此测试把静默吞错当正确。（需 PHASE 1 确认设计意图后定级）

### M4 表达式（1 项，LSP 侧）

**C15. `AnalysisServiceTest.java`（LSP）——`@` 引用回归保护缺失**
- 原 CRITICAL 测试 `undefinedStringRefProducesSEM_REF_001` 被删除而非修正，LSP 侧无任何 `@undefined` 回归测试。bug 持续或被修均无测试守护。

## 3. HIGH 发现（34 项，按反模式归类，节选代表性）

### P1 guard 跳过（11 项）
`PipelineEndToEndTest`: `allFormatsContainSameRuleIdsForDirectory:140`, `allFormatsSummaryMatchesExitCode:158`, `functionCallInExpressionParsedCorrectly:204`, `eachErrorFileHasThreeOrMoreDiagnostics:290`, `terminalColorAndNoColorConsistentContent:329`; `BatchInspectionRealScenarioTest`: `widgetMissingRequiredAttrs:136`, `noColorFormatterProducesValidReport:238`, `colorFormatterProducesAnsiReport:249`; `BatchInspectionIntegrationTest`: `terminalFormatterNoColorOutputIsValid:192`, `terminalFormatterColorOutputIsValid:207`, `severityCountsAreConsistentWithDiagnostics:234`

### P4 计数-only（8 项）
`VarRefAnalyzerTest`: `binaryExprReportsBothUndefinedRefs:126`, `commandTargetUndefinedElementAndInvalidPropertyReportsBoth:461`, `tripleDuplicateVarDeclarations:555`; `BatchInspectionRealScenarioTest`: `multiErrorLockscreenProducesDiverseRuleIds:81`; `ConstraintAnalyzerTest`: `evaluationContextIncludesScopeAndDeviceSupport:324`; `DiagnosticProviderDegradationTest`: `analyzeWithNormalAnalyzersProducesDiagnostics:47`（`!=null`）; `BatchInspectionRunnerModeTest`: `fullModeIncludesAllDiagnostics:119`（`size()>=0` 平凡真）, `noTypeCheckDisablesTypeAnalyzer:132`（stub 不分支）

### P5 stub 当 SUT（4 项）
`DiagnosticProviderTest`: `analyzeReturnsDiagnosticList:23`（测 StubDiagnosticProvider 非 Impl，`//TODO`）, `analyzeReturnsEmptyListForEmptyAst:35`（同）; `FunctionSignatureLibraryTest`（整文件测 StubFunctionSignatureLibrary，且 stub 忽略 expressionKind 参数——契约 bug）; `DslFileMatcherTest`: `dslFileMatcherInterfaceExists:9`（stub 硬编码 `return true`）

### P6 false-confidence（`@` 路径无负测试）
`VarRefAnalyzerTest.videoCurrentTimeStringRefWithExistingElementNoViolation:343`——因 `@` 跳过 bug，所有 `@` 引用都返回空，此"合法不报错"测试无法区分"合法"与"被吞"。需补 `@<undefined>.currentTime` 负测试。

### LSP HIGH
`SemanticTokensProviderTest.emitsTokensAcrossMultipleLines:86`——`hasToken` 只查类型存在不查行号，名"acrossMultipleLines"未验证。

## 4. MEDIUM 发现（59 项，按反模式归类）

- **P4 弱断言（只查 ruleId 不查 message/severity/position）**：VarRefAnalyzerTest ×6（`arrayAccessReportsUndefinedArrayVar`, `functionCallArgReferenceChecked`, `nonTemplateRefFallsBackToSEM_REF_001`, `indexFlagLocalVarOutOfScopeProducesSEM_REF_001`, `varExpressionReferencingUndefinedProducesSEM_REF_001` 等）; TypeAnalyzerTest ×2; ConstraintAnalyzerTest ×2; ScopeAnalyzerTest ×1; SemanticAnalysisIntegrationTest ×9（全用 `assertHasRule` 存在-only helper）
- **名不副实**：ParentChildAnalyzerTest ×2（`parentNotAllowedProducesSYN_002` 实际断言 SEM-NEST-001）; `FunctionSignatureLibraryTest.getSignaturesReturnsAllOverloads`（只 1 个重载）; `JsonFunctionSignatureLoaderTest.dslArrayTypeDeserialization`（根本没测数组类型）; `ConstraintAnalyzerTest.verifyScopeMatrix`（只查矩阵 1 格）
- **`anyMatch`-of-many（OR 断言）**：BatchInspectionRealScenarioTest ×3（`multiErrorLockscreenTriggersConstraintErrors` 等，任一命中即过）
- **LSP 弱断言**：`AnalysisServiceTest.analyzesMalformedXmlGracefully`（只 `!=null`）; `ConfigIntegrationTest.configAwareWrapperDoesNotBreakAnalysis`（禁用不存在的规则=无操作）; `HoverProviderTest.attributeHoverRendersTypeSpec`（单字符 `contains("x")` 被"Expression"满足）; `CompletionProviderTest.enumValueCompletionForVarType`（4 值只验 1 个）

## 5. LOW 发现（9 项）
`DslExpressionStringTokenTest.tokenizeStringLiteral`（纯 println 无断言）; `DiagnosticTest.diagnosticBuilderDefaults`（重复断言）; `DslAstNodeTest.dslAstNodeBaseFields`（8 字段只读 3）; `BatchInspectionExceptionTest.constructorWithMessage`（`assertNotNull` 刚构造的对象）; `FatJarSubprocessE2ETest.fatJarOutput_matchesGoldenExpectation`（positionAgnostic 跳过 mustNotTrigger）; 等。

## 6. 确认干净的文件

M4: `EnumValueAnalyzerTest`, `LiteralTypeAnalyzerTest`, `RequiredAttrAnalyzerTest`, `VerboseCollectorTest`, `DiagnosticProviderModeTest`（mode 隔离验证到位）
M7: `ExitCodeCalculatorTest`, `TerminalFormatterTest`, `ReportExporterImplTest`, `JsonReportSerializerTest`, `model/*`
CLI: `InspectionConfigLoaderTest`, `CliConfigTest`, `ConfigAwareRuleRepositoryTest`, `CliOutputFormatterExtendedTest`（错误路径测 exit 2 + 具体 stderr，干净）
E2E: `GoldenDiagnosticMatchTest`, `GoldenMatcherTest`, `GoldenMatcherExpectedFixesTest`, `GoldenExpectationParserTest`, `FixtureCoverageTest`, `ModeGoldenTest`（**本仓库最佳实践标杆**——ModeGoldenTest 正确做了 C12 未做的事）
表达式: `TypeInferenceEngineTest`（`hashUndefinedVarReturnsNull` 正确 `assertNull`）, `ExpressionNodeTest`, `DefaultRuleDslEvaluatorTest`（极其彻底）

## 7. 建议

1. **立 FIX003** 修 TypeAnalyzer null 函数库静默吞 SEM-TYPE-*（C2，与 Bug B 同类真实 bug）。
2. **FIX002 范围内修正**：C1（`@` 测试断言）+ P6（补 `@undefined` 负测试）+ C15（LSP 补 `@` 回归测试）。
3. **立 FIX004（测试剧场治理）**：按本报告 CRITICAL/HIGH 逐项修。优先 P1（guard 跳过）、P2（`<=2` 容忍）、P3（`0||1`），这三类直接掩盖漏检/false positive。MEDIUM 的 `assertHasRule` helper 应升级为查 ruleId+message。
4. **纳入 lessons-learned**：测试名与断言必须一致；"合法文件"断言必须是 `errorCount==0`；禁用 `if(errorCount>0)` guard 包裹全部断言；禁用 stub 当 SUT。
5. **CI 守护**：考虑加静态检查扫描 `exitCode == 0 || exitCode == 1` 与 `isEmpty()` guard 模式。
