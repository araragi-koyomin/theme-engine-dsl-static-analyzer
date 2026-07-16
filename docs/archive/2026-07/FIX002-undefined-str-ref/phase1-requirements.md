---
module_ids: [M4]
phase: P0
doc_kind: spec
status: archived
created: 2026-07-15
---
# 未定义 String 变量引用不报错 + main 编译断裂 — PHASE 1 需求澄清

> 阶段：PHASE 1（需求澄清）
> 状态：已确认
> 调查方法：systematic-debugging Phase 1-2（根因调查 + 模式对比）

## 1. 背景

用户报告：DSL 脚本中 `#undefined_num`（number 前缀）会产出 SEM-REF-001，但 `@undefined_str`（string 前缀）不报错。期望所有未定义变量引用都应正常抛出。

调查中发现 main 分支**当前无法编译**（一个独立的阻塞性 bug），导致整个 feature:analysis 模块不可构建、所有测试无法运行。

## 2. 根因（两个独立 bug，同文件 `VarRefAnalyzer.java`）

### Bug A（BLOCKER — 编译断裂）

- **位置**: `VarRefAnalyzer.java:343` + `:357`
- **症状**: `:feature:analysis:compileJava` 失败：`已在方法 buildUndefinedElementRefDiagnostic 中定义了变量 refText`
- **引入**: `6262bfd`（2026-07-14 "零宽 range 修复"）新增 line 343 `String refText = prefix + varName`（用于 `endColumn` 高亮宽度），未察觉 `e4d713d`（2026-07-13）早已在 line 357 声明 `String refText = prefix + elementName`（用于 message 文本）。两 `refText` 同方法作用域 → Java 编译错误。
- **意图还原**:
  - line 343 的 `refText`（基于 `varName`，如 `#unlocker.move_x`）→ 用于高亮宽度（`endColumn = column + refText.length()`）
  - line 357 的 `refText`（基于 `elementName`，如 `#unlocker`）→ 用于诊断消息（`引用未定义元素属性 #unlocker`）
  - 两者语义不同，需用**不同变量名**承载。
- **影响**: main 分支昨天起不可编译，`#`/`@` 所有检测均无法运行验证。

### Bug B（用户报告 — `@` 前缀跳过）

- **位置**: `VarRefAnalyzer.java:83-85`
- **代码**:
  ```java
  if ("@".equals(ref.getPrefix())) {
      continue;   // 所有 string 变量引用永不检查存在性
  }
  ```
- **根因**: `#`=number ref、`@`=string ref，二者都是变量引用，都应查 symbolTable 存在性。此处 `continue` 让 `@undefined_str` 永不产出 SEM-REF-001。
- **测试编码了 bug**: `VarRefAnalyzerTest.java:66-73` 测试名 `undefinedStringRefProducesSEM_REF_001`（名含"ProducesSEM_REF_001"），但断言写 `assertTrue(diagnostics.isEmpty(), "@-prefixed variable refs should not be flagged as undefined")` —— 测试名与断言自相矛盾，实现跟了错误断言。

### 前缀分支审查结论（用户要求"顺带审查所有前缀分支"）

`collectUndefinedReferences` 中前缀相关分支仅有 line 83 的 `@` 跳过。移除后 `#`/`@` 走**同一**存在性检测流程（模板匹配 → 元素存在性 → symbolTable lookup）。无其他前缀分支不一致。

预制全局变量清单（`global_vars.json`）确认 string 型全局（`@ishour12`/`@system.time.ampm`/`@matchSkill_value`/`@media_title` 等）修复后 lookup 命中→不误报。

## 3. 影响范围（受波及的 golden fixture 与处理策略）

经逐一核对 `global_vars.json`：8 处 `@xxx` 引用确为未声明（非预制全局）；1 处 `@ishour12` 是预制全局（修复后 lookup 命中→不报错）。

按 PHASE 1 确认的**混合策略**：8 处均为各 fixture 测试其他规则时的伴生数据（非专门测未定义检测），故声明对应 string Var 使其合法，**不新增 SEM-REF-001**，fixture 聚焦原测试目标不变。

| Fixture | `@` 引用 | 行 | 预制全局? | 处理 |
|---|---|---|---|---|
| wallpaper_constraint_enum | @dynamic_bg | 4 | 否 | 声明 `<Var name="dynamic_bg" type="string"/>` |
| lockscreen_nesting_var | @dynamic_overlay | 7 | 否 | 声明 `<Var name="dynamic_overlay" type="string"/>` |
| charging_skin_cmd_nest | @charge_icon | 6 | 否 | 声明 `<Var name="charge_icon" type="string"/>` |
| deep_nesting_violations | @deep_dynamic | 18 | 否 | 声明 `<Var name="deep_dynamic" type="string"/>` |
| deep_nesting_violations | @ishour12 | 39 | **是** | 无需改动（预制全局，lookup 命中） |
| constraint_edge_cases | @dynamic_src | 16 | 否 | 声明 `<Var name="dynamic_src" type="string"/>` |
| constraint_edge_cases | @only_srcexp | 25 | 否 | 声明 `<Var name="only_srcexp" type="string"/>` |
| lockscreen_multi_error | @weather_icon | 7 | 否 | 声明 `<Var name="weather_icon" type="string"/>` |
| wallpaper_invalid_enum | @icon | 4 | 否 | 声明 `<Var name="icon" type="string"/>` |

> 修复后各 fixture 的 `expected.json` counts/diagnostics **不变**（伴生引用变合法，无新增诊断）。

## 4. 目标

1. 修复 Bug A（编译断裂），恢复 main 可构建。
2. 修复 Bug B（`@` 跳过），使 `@undefined_str` 与 `#undefined_num` 行为一致地产出 SEM-REF-001。
3. 修正 `VarRefAnalyzerTest.undefinedStringRefProducesSEM_REF_001` 断言以反映正确契约。
4. 维护受波及 golden fixture，保持全量门禁绿。
5. 补充 `@` 路径负测试与 LSP 回归测试，消除测试剧场审计（`docs/development/reports/test-theater-audit-2026-07-15.md`）中与本 bug 直接相关的 C1/P6/C15 三项缺陷。

## 5. 范围

### 包含

| 项 | 范围 |
|---|---|
| FIX-A | 消除 `buildUndefinedElementRefDiagnostic` 重复 `refText`：用不同变量名承载"高亮宽度文本"与"消息文本" |
| FIX-B | 移除 `VarRefAnalyzer.java:83-85` 的 `@` 跳过，`#`/`@` 走同一存在性检测流程 |
| FIX-C | 修正 `VarRefAnalyzerTest.undefinedStringRefProducesSEM_REF_001` 断言为期望 SEM-REF-001（审计 C1） |
| FIX-D | golden fixture 维护（混合策略，见下方决策） |
| FIX-E | 补 `@` 路径负测试：`@<undefinedElem>.currentTime` 应产出 SEM-REF-001（消除 `videoCurrentTimeStringRefWithExistingElementNoViolation` 的 false-confidence，审计 P6）；LSP `AnalysisServiceTest` 补 `@undefined_str` 回归测试（审计 C15） |

### Fixture 维护策略（用户已确认：混合策略）

- **伴生数据型 fixture**（不专门测未定义检测）：为 8 处 `@xxx` 声明对应 `<Var name="xxx" type="string"/>`，使其合法，不新增 SEM-REF-001，保持 fixture 聚焦原测试目标。
- **专门测未定义的用例**（`VarRefAnalyzerTest.undefinedStringRefProducesSEM_REF_001`）：修正断言，让它如实产出 SEM-REF-001。
- `@ishour12`（deep_nesting_violations:39）是预制全局，无需改动。

### 不包含

- SEM-REF-002（Command target 元素名引用）/ SEM-REF-003（重复定义）行为不变。
- TypeAnalyzer null 函数库静默吞 SEM-TYPE-*（审计 C2，**立 FIX003 独立修**）。
- 其余测试剧场问题（14 CRITICAL + 34 HIGH，见审计报告），**立 FIX004 治理**。
- "先使用后定义" / "嵌套范围外使用" 检测（设计约束：变量皆全局作用域，不检测此类错误）。

## 6. 约束

- Core 层无 `com.intellij` import（`checkCoreIntellijDependency` 强制）。
- 无新引入依赖。
- 变量作用域设计约束：所有变量为全局作用域，不检测"先使用后定义"或"嵌套范围外使用"。
- 每项修复后全量门禁必须全绿：`./gradlew --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e`。

## 7. 验收标准（每条可测试）

| AC # | 验收标准 | 测试方式 |
|---|---|---|
| AC-1 | `:feature:analysis:compileJava` 成功（Bug A 修复） | 编译命令 BUILD SUCCESSFUL |
| AC-2 | `@undefined_str` 产出 SEM-REF-001（ERROR，message=`引用未定义变量 @undefined_str`，suggestedFix=`声明 Var name="undefined_str"`） | `VarRefAnalyzerTest.undefinedStringRefProducesSEM_REF_001` 断言修正后通过 |
| AC-3 | `@defined_str`（已声明 string Var）不报错 | 单元测试：声明 `<Var name="s" type="string"/>` + `@s` 引用 → 无 SEM-REF-001 |
| AC-4 | `@elementName.property`（已声明元素）不报错（走模板匹配路径） | 现有 `videoCurrentTimeStringRefWithExistingElementNoViolation` 仍绿 |
| AC-5 | 预制 string 全局（`@ishour12`）不报错 | `deep_nesting_violations` fixture 中 `@ishour12` 不新增 SEM-REF-001 |
| AC-6 | golden 全量门禁绿（fixture 维护后 counts/diagnostics 一致） | `./gradlew --no-daemon :feature:analysis:test` + `:feature:analysis:e2e` 全绿 |
| AC-7 | `@<undefinedElem>.currentTime` 产出 SEM-REF-001（`@` 元素属性路径负测试，审计 P6） | 新增 `VarRefAnalyzerTest` 负测试通过 |
| AC-8 | LSP 侧 `@undefined_str` 回归测试存在且通过（审计 C15） | `AnalysisServiceTest` 新增 `@` 回归测试通过 |

---

> **阶段切换**：PHASE 1 完成。请用户确认以上需求文档，确认后进入 PHASE 2（规格定义）。
