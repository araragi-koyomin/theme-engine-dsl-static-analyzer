# CLI 管线集成分支诊断能力评估

> 评估日期: 2026-07-10
> 分支: `feat/cli-pipeline-integration` (HEAD=541a874)
> 测试方法: `java -jar dsl-analyzer.jar --format terminal --no-color` 对 14 个 fixture 全量测试
> 测试状态: `./gradlew :feature:analysis:test` — 792 tests **全部通过**

---

## 1. 总结

**结论: 当前 CLI 分支不建议合入 main。**

CLI 分支的诊断能力严重不完整：表达式语法检查、类型推断、声明式约束评估三大核心能力全部缺失。当前 CLI jar 仅能做"XML 结构级"校验（嵌套合法性、枚举值、必填属性、作用域），无法做"语义级"校验（表达式类型检查、约束条件评估、精度边界检测）。

---

## 2. 逐 Fixture 对比（CLI 原始分支 vs ANSWER_KEY 预期）

### 2.1 complex/ 目录（8 个 fixture）

| Fixture | CLI 实测 | ANSWER_KEY 预期 | 检出率 | 缺失的规则类别 |
|---------|---------|----------------|--------|---------------|
| `deep_nesting_violations.xml` | 19E+1W | 16E | 119% | 多报 SEM-REQ-001/SEM-TYPE-003；**缺报** SEM-ATTR-001(alpha), SEM-PERSIST-001, SYN-EXPR-004 |
| `type_inference_edge_cases.xml` | 5E+0W | 13E | 38% | **缺报** SEM-TYPE-001, SEM-TYPE-002(all), SYN-EXPR-001, SYN-EXPR-002, SEM-ATTR-001 |
| `constraint_edge_cases.xml` | 8E+0W | 6E | 133% | 多报 SEM-TYPE-003/SEM-ENUM-001；**缺报** SEM-ATTR-001(alpha×2), SYN-EXPR-004 |
| `variable_lifecycle_errors.xml` | 5E+0W | 12E | 42% | **缺报** SEM-PERSIST-001(all 4), SEM-VAR-003, SEM-VAR-004, SEM-ARR-001, SEM-TYPE-001 |
| `trigger_command_combos.xml` | 8E+1W | 6E | 150% | 多报 SEM-ENUM-001/SEM-TYPE-003；**缺报** SEM-TRIG-002, SEM-TRIG-003, SEM-CMD-004 |
| `scope_nesting_boundaries.xml` | 15E+0W | 8E | 188% | 多报 SEM-REQ-001×3 (Swiper/SourceImage 必填属性误报) |
| `expression_syntax_errors.xml` | 3E+0W | 11E | 27% | **缺报** SYN-EXPR-001(all 3), SYN-EXPR-002×2, SYN-EXPR-004, SYN-EXPR-ANTLR×3, SEM-TYPE-001 |
| `enum_boundary_tests.xml` | 16E+0W | 9E | 178% | 多报 SEM-REQ-001/SEM-TYPE-003；**缺报** SEM-ATTR-003(category) |

### 2.2 complex_expressions/ 目录（6 个 fixture）

| Fixture | CLI 实测 | ANSWER_KEY 预期 | 检出率 | 缺失的规则类别 |
|---------|---------|----------------|--------|---------------|
| `chained_function_hell.xml` | 1E+0W | 4E | 25% | **缺报** SEM-TYPE-001×2, SEM-TYPE-002, SEM-TYPE-003 |
| `string_expression_errors.xml` | 0E+0W | 7E | **0%** | **全部缺报**: SYN-EXPR-004×2, SYN-EXPR-005, SYN-EXPR-003, SYN-EXPR-006, SEM-TYPE-001, SYN-EXPR-ANTLR |
| `precision_boundary_tests.xml` | 0E+0W | 5-6W | **0%** | **全部缺报**: SYN-EXPR-002×5 |
| `array_index_edge_cases.xml` | 1E+0W | 1E | 100% | 仅 SEM-REF-002 检出（非表达式检查） |
| `operator_precedence_tests.xml` | 0E+0W | 2E | **0%** | **全部缺报**: SYN-EXPR-001×2 |
| `multi_element_expression_blast.xml` | 8E+0W | 16E+1W | 50% | **缺报** SYN-EXPR-001×3, SYN-EXPR-004×3, SYN-EXPR-002, SYN-EXPR-006, SEM-TYPE-001×3, SEM-TYPE-002, SEM-REF-001 |

---

## 3. 按规则类别的能力矩阵

| 规则组 | 示例 Rule ID | 状态 | 说明 |
|--------|------------|------|------|
| **XML 语法错误** | SYN-SAX-001 | ❌ 缺失 | SyntaxErrorAnalyzer 未注册到 AnalyzerRegistry |
| **表达式语法检查** | SYN-EXPR-001~006, SYN-EXPR-ANTLR | ❌ 全部缺失 | ExpressionSyntaxChecker 实现不完整（前 Bug 10） |
| **枚举值检查** | SEM-ENUM-001 | ✅ 正常 | EnumAnalyzer 工作 |
| **嵌套合法性** | SEM-NEST-001 | ✅ 正常 | NestAnalyzer/ReqAnalyzer 工作 |
| **作用域检查** | SEM-SCOPE-001 | ✅ 正常 | ScopeAnalyzer 工作 |
| **必填属性** | SEM-REQ-001 | ⚠️ 部分误报 | 对 Swiper/SourceImage 等元素误报（Alias 识别问题） |
| **Trigger 规则** | SEM-TRIG-001/002/003 | ⚠️ 仅 001 工作 | TRIG-002（Button 缺 Trigger）和 TRIG-003（空 Triggers）未触发 |
| **变量引用** | SEM-REF-001/002/003 | ✅ 正常 | VarRefAnalyzer 工作 |
| **字面量类型** | SEM-TYPE-003 | ⚠️ 部分工作 | LiteralTypeAnalyzer 部分工作但过度触发 |
| **表达式类型推断** | SEM-TYPE-001/002 | ❌ 全部缺失 | TypeAnalyzer 无 FunctionSignatureLibrary 输入（前 Bug 3） |
| **声明式约束** | SEM-ATTR-001, SEM-PERSIST-001 | ❌ 全部缺失 | ConstraintAnalyzer 因 RuleDslEvaluator 不完整而失效 |
| **属性值检查** | SEM-ATTR-005, SEM-IMG-002/003, SEM-IMG-SRC | ✅ 正常 | 类同枚举/结构检查，不依赖表达式引擎 |
| **数组相关** | SEM-ARR-001, SEM-VAR-003/004 | ❌ 全部缺失 | 依赖 ConstraintAnalyzer |
| ** Command 规则** | SEM-CMD-001/004 | ⚠️ 仅 CMD-001 工作 | CMD-004 依赖 ConstraintAnalyzer |
| **3D 规则** | SEM-3D-STEREO-001 | ❌ 缺失 | 依赖 ConstraintAnalyzer children.filter |
| **精度检查** | SYN-EXPR-002 | ❌ 缺失 | 依赖 ExpressionSyntaxChecker |

---

## 4. 能力统计

| 维度 | 数值 | 说明 |
|------|------|------|
| 总 fixture 数 | 14 | complex 8 + complex_expressions 6 |
| 预期总诊断数 | ~197 | ANSWER_KEY 汇总 |
| CLI 实际诊断数 | ~90 | 仅结构级检出 |
| **有效诊断检出率** | **~46%** | |
| 完全通过的 fixture | 0/14 | 所有 fixture 都有缺失的诊断 |
| 零诊断的 fixture | 3/14 | string_expression_errors, precision_boundary_tests, operator_precedence_tests |
| 误报率 | 偏高 | 多处 SEM-TYPE-003/SEM-REQ-001 误报 |

---

## 5. 根因分析

### 5.1 架构层面

CLI jar 中缺失以下已修复但未合入的 Bug：

| Bug# | 描述 | 影响范围 |
|------|------|---------|
| Bug 1 | `JsonRuleLoader` 无 classpath 加载 → 规则库为空 | 约束检查全部失效 |
| Bug 2 | `SyntaxErrorAnalyzer` 未注册 | SYN-SAX-001 缺失 |
| Bug 3 | `FunctionSignatureLibrary` 为 null 传入 TypeAnalyzer | SEM-TYPE-001/002 全部缺失 |
| Bug 4 | AstBuilder 属性对齐 | 语义分析基线错误 |
| Bug 5 | SEM-ATTR-001 `parseInt()` 语法 | 属性范围检查全部缺失 |
| Bug 6 | MATCHES 运算符未实现 | SEM-PERSIST-001 缺失 |
| Bug 7 | children.filter 未预处理 | 所有子元素约束缺失 |
| Bug 8 | LiteralTypeAnalyzer 门控错误 | 字面量类型检查部分缺失 |
| Bug 9 | ifelse() ANTLR 解析失败 | 表达式检查阻塞 |
| Bug 10 | ExpressionSyntaxChecker 不完整 | SYN-EXPR-001~006 全部缺失 |

### 5.2 测试层面

虽然 792 个测试全部通过，但测试存在**覆盖率盲区**：
- `ExpressionSyntaxCheckerTest` 中的测试依赖 Bugfix 后的实现（当前分支上 ExpressionSyntaxChecker 的方法签名已变，但测试预期未同步更新）
- 测试使用 `StubRuleRepository`，不走真实 JSON 加载路径 — Bug 1（classpath 加载）无法被测试捕获
- `PipelineEndToEndTest` 和 `BatchInspectionRealScenarioTest` 的断言使用了 ">=N" 的宽松条件，未能捕获诊断缺失

---

## 6. 合并建议

### 不建议合入 main 的原因

1. **核心功能缺失**: 表达式语法检查、类型推断、声明式约束评估 — CLI 工具的三大核心价值全部不可用
2. **3 个 fixture 零诊断**: 完全无法检测表达式相关问题
3. **误报率偏高**: 多处 SEM-REQ-001/SEM-TYPE-003 误报会降低用户信任
4. **与 CLU-Usage.md 承诺的能力不符**: 文档声明的 SYN-EXPR/SEM-TYPE/SEM-ATTR 诊断在当前 jar 中大量缺失

### 合入前提条件

| 条件 | 当前状态 |
|------|---------|
| 10 个 confirmed bugfix 全部合入 | ❌ 仅 0/10 |
| 全部 14 个 fixture 诊断数 ≥ ANSWER_KEY 的 90% | ❌ 当前 ~46% |
| 无零诊断 fixture | ❌ 3 个 fixture 零诊断 |
| 误报率 < 10% | ❌ 多处误报 |
| 792 测试通过 | ✅ |
| Core IntelliJ 隔离验证 | ✅ |

### 建议路径

```
当前状态: feat/cli-pipeline-integration (46% 诊断覆盖率)
         ↓
Step 1: 将 10 个 Bugfix commit 合并到 CLI 分支
         ↓ (预计覆盖率回升至 ~81%)
Step 2: 修复剩余 9 个 Gap (B1/B2/B3/B16/B18/B19/C1/D1/D2)
         ↓ (预计覆盖率升至 ~90%+）
Step 3: 更新测试断言收紧边界条件
         ↓
Step 4: 合并到 main
```

---

## 7. 附录: 完整 jar 输出

### deep_nesting_violations.xml
```
19 errors, 1 warnings:
  SEM-ENUM-001 x5 (category/enableMove/scaleType/action misplacement)
  SEM-NEST-001 x4 (Swiper in Group, Button/Image in Swiper, Layer in Group)
  SEM-SWIPER-001 (Swiper nesting)
  SEM-REQ-001 x2 (Swiper missing currentIndex/animationTime)
  SEM-IMG-002 (src/srcExp conflict)
  SEM-ATTR-005 (isBackground needs center_crop)
  SEM-REF-001 x2 (#undefined_var, @deep_dynamic)
  SEM-TYPE-003 (height has string value)
  SEM-TRIG-001 (invalid action)
  SEM-IMG-003 warning (isBackground+align)
```

### type_inference_edge_cases.xml
```
5 errors:
  SEM-ENUM-001 (const has expression value)
  SEM-TYPE-003 x2 (size=true, alpha=icon.png)
  SEM-IMG-SRC (missing src)
  SEM-REF-001 (#not_declared)
```

### expression_syntax_errors.xml
```
3 errors:
  SEM-ENUM-001 (const has expression)
  SEM-IMG-SRC (missing src)
  SEM-TYPE-003 (alpha=dot.png)
```

### string_expression_errors.xml
```
0 errors, 0 warnings — 完全空白！
```

### precision_boundary_tests.xml
```
0 errors, 0 warnings — 完全空白！
```

### operator_precedence_tests.xml
```
0 errors, 0 warnings — 完全空白！
```

### chained_function_hell.xml
```
1 error:
  SEM-IMG-SRC (missing src)
```

### multi_element_expression_blast.xml
```
8 errors:
  SEM-TYPE-003 x4 (size=true, alpha=images, etc.)
  SEM-IMG-SRC x3
  SEM-REF-002 (ghost_elem)
  SEM-ENUM-001 (const has expression)
```
