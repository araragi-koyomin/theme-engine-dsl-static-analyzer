---
module_ids: [M4]
phase: P0
doc_kind: spec
status: active
created: 2026-07-15
---
# FIX002 — PHASE 2 规格定义

> 阶段：PHASE 2（规格定义）
> 状态：已确认
> 原则：只定义契约（输入/输出/前后置/异常），不定义内部实现细节（留给 TDD）。

## 0. 契约总览

| 契约 | 方法/对象 | 修复的 Bug | 关键变更 |
|---|---|---|---|
| C1 | `VarRefAnalyzer.collectUndefinedReferences` | Bug B | `#`/`@` 前缀统一走存在性检测，移除 `@` 跳过 |
| C2 | `VarRefAnalyzer.buildUndefinedReferenceDiagnostic` | Bug B（`@` 诊断格式） | 契约明确：`@` 引用产出与 `#` 同构的 SEM-REF-001 |
| C3 | `VarRefAnalyzer.buildUndefinedElementRefDiagnostic` | Bug A（编译+docUrl） | 双文本变量（宽度/消息分离）+ docUrl 与 ruleId 一致 |
| C4 | golden fixture XML | FIX-D | 8 处伴生 `@xxx` 声明 string Var |

---

## 契约 C1：`collectUndefinedReferences` — 前缀无关的存在性检测

### 签名（不变）

```java
private void collectUndefinedReferences(DslElementNode elementNode, DslContext context,
                                        Map<String, Pattern> elementTemplates,
                                        List<Diagnostic> diagnostics)
```

### 业务规则

对元素每个含表达式属性的属性值，递归收集所有 `VARIABLE_REF` / `ARRAY_ACCESS` 表达式节点，对每个引用 `ref`：

| BR # | 规则 |
|---|---|
| BR-1 | `varName = ref.getVariableName()`；若 null/空 → skip |
| BR-2 | **前缀处理**：`#`（number）与 `@`（string）均为变量引用，**二者均须经历存在性检测**。不得因前缀为 `@` 而 skip。 |
| BR-3 | **存在性解析顺序**（prefix 无关）：① 先 `matchTemplate(varName, elementTemplates)` 匹配元素属性模板（如 `{elementName}.move_x`）。② 若匹配到 `elementName`：查 `elementNames.contains(elementName)`，不存在 → 调 C3 产出元素属性引用诊断。③ 若未匹配模板：`symbolTable.lookup(varName)`，不存在 → 调 C2 产出变量引用诊断。 |
| BR-4 | 预制全局变量（`global_vars.json` 中 scope=global 的条目，如 `ishour12`/`system.time.ampm`）由 SymbolTableBuilder 注册进 symbolTable，`lookup` 命中 → 不报错。 |
| BR-5 | `#` 与 `@` 在存在性检测上**无任何分支差异**——同一套解析路径、同一套诊断产出。类型差异由 TypeAnalyzer（SEM-TYPE-*）另行处理，不在本契约。 |

### 前置条件

- `context.getSymbolTable()` 非 null（null → 直接 return，不产出）。
- `elementTemplates` 已由 `compileElementTemplates` 编译（从 ruleRepo 的 scope=element 全局变量）。

### 后置条件

- 每个既未匹配元素模板、又未在 symbolTable 中找到声明的 `#`/`@` 变量引用 → 恰好产出 1 条 SEM-REF-001。
- 每个匹配元素模板但 `elementName` 不在 `elementNames` 中的 `#`/`@` 引用 → 恰好产出 1 条 SEM-REF-001（元素属性引用）。
- 已声明变量、预制全局、已存在元素属性引用 → 0 条诊断。

### 异常

- 无。symbolTable null 时优雅 return。

### 验收测试场景

| 场景 | 输入 | 期望 |
|---|---|---|
| C1-T1 | `@str`（未声明，无模板匹配） | SEM-REF-001, message=`引用未定义变量 @str` |
| C1-T2 | `#x`（未声明） | SEM-REF-001, message=`引用未定义变量 #x`（回归保护） |
| C1-T3 | `@s`（已声明 `<Var name="s" type="string"/>`） | 无诊断 |
| C1-T4 | `@ishour12`（预制全局） | 无诊断 |
| C1-T5 | `@myVideo.currentTime`（元素 myVideo 存在，模板匹配） | 无诊断 |
| C1-T6 | `@missingElem.currentTime`（元素不存在，模板匹配） | SEM-REF-001, message=`引用未定义元素属性 @missingElem`（P6 负测试） |
| C1-T7 | `@str + 'suffix'`（二元表达式中 `@str` 未声明） | SEM-REF-001 for `@str` |
| C1-T8 | `@arr[#i]`（`@arr` 未声明，`#i` 已声明） | SEM-REF-001 for `@arr` |

---

## 契约 C2：`buildUndefinedReferenceDiagnostic` — 变量引用诊断格式

### 签名（不变）

```java
private Diagnostic buildUndefinedReferenceDiagnostic(ExpressionNode ref, DslElementNode hostNode, DslContext context)
```

### 输出契约

对未定义变量引用 `ref`（prefix ∈ {`#`, `@`}，varName 非空）：

| 字段 | 值 |
|---|---|
| ruleId | `SEM-REF-001` |
| severity | `ERROR` |
| message | `引用未定义变量 <prefix><varName>`（prefix=null 时无前缀） |
| filePath | `context.getFilePath()` |
| line/column | `ref` 的 line/column；若均为 0 → 回退到 `hostNode` 的 line/column |
| endLine/endColumn | 若 ref 有位置：endLine=line, endColumn=column + len(`<prefix><varName>`)（非零宽高亮）；若回退到 host：用 host 的 endLine/endColumn |
| suggestedFixes | 单条：text=`声明 Var name="<varName>"`, type=`ADD_ATTR`, target=`name`, value=`<varName>` |
| ruleDocUrl | `resolveDocUrl(context, RULE_REF_001)`（SEM-REF-001 的 RuleSource.docUrl） |

### 关键约束

- `@` 引用与 `#` 引用产出**同构**诊断：仅 prefix/varName 不同，结构完全一致。
- 非零宽高亮：endColumn 必须大于 column（覆盖完整引用文本如 `@undefined_str`），避免 LSP 客户端/IntelliJ annotator 丢弃零宽诊断。

### 验收测试场景

| 场景 | 期望 |
|---|---|
| C2-T1 | `@str` 诊断：ruleId=SEM-REF-001, severity=ERROR, message=`引用未定义变量 @str`, suggestedFix=`声明 Var name="str"` |
| C2-T2 | `@str` 诊断：line=15, column=3, endColumn=3+len("@str")=7（非零宽） |
| C2-T3 | `@str` 诊断：ruleDocUrl 来自 SEM-REF-001 的 RuleSource |

---

## 契约 C3：`buildUndefinedElementRefDiagnostic` — 双文本 + docUrl 一致性（Bug A）

### 签名（不变）

```java
private Diagnostic buildUndefinedElementRefDiagnostic(ExpressionNode ref, String elementName,
                                                       DslElementNode hostNode, DslContext context)
```

### 缺陷清单（Bug A 三处）

| # | 位置 | 缺陷 | 修复 |
|---|---|---|---|
| D1 | line 343 + 357 | `String refText` 同方法作用域重复声明 → 编译错误 | 拆为两个不同变量名 |
| D2 | line 342 | `resolveDocUrl(context, RULE_REF_002)` 但 ruleId=RULE_REF_001 → docUrl 查错 rule | 改为 `RULE_REF_001` |

### 输出契约

对未定义元素属性引用 `ref`（如 `#unlocker.move_x`，elementName=`unlocker` 不存在）：

| 字段 | 值 | 文本来源 |
|---|---|---|
| ruleId | `SEM-REF-001` | — |
| severity | `ERROR` | — |
| message | `引用未定义元素属性 <prefix><elementName>`（如 `引用未定义元素属性 #unlocker`） | **messageRef** = prefix + elementName |
| filePath | `context.getFilePath()` | — |
| line/column | ref 位置 or hostNode 回退 | — |
| endLine/endColumn | 若 ref 有位置：endColumn = column + len(`<prefix><varName>`)（如 `#unlocker.move_x` 全长） | **highlightText** = prefix + varName（完整引用文本） |
| suggestedFixes | 单条：text=`声明带 name="<elementName>" 的元素`, type=`ADD_CHILD`, target=`<elementName>` | — |
| ruleDocUrl | `resolveDocUrl(context, RULE_REF_001)`（与 ruleId 一致） | — |

### 关键约束

- **双文本**：`highlightText`（prefix+varName，用于高亮宽度）与 `messageRef`（prefix+elementName，用于消息）是**两个不同变量**，承载不同语义。前者覆盖完整引用文本，后者只显示元素名。
- **docUrl/ruleId 一致**：ruleId 与 docUrl 解析必须用同一个 RULE 常量（RULE_REF_001）。
- `@` 元素属性引用（如 `@missingElem.currentTime`）走同一方法，产出 `引用未定义元素属性 @missingElem`。

### 验收测试场景

| 场景 | 期望 |
|---|---|
| C3-T1 | `#unlocker.move_x`（元素不存在）→ SEM-REF-001, message=`引用未定义元素属性 #unlocker`, suggestedFix=`声明带 name="unlocker" 的元素` |
| C3-T2 | 同上 → endColumn = column + len(`#unlocker.move_x`)（完整引用高亮，非零宽） |
| C3-T3 | 同上 → ruleDocUrl 来自 SEM-REF-001 的 RuleSource（非 SEM-REF-002） |
| C3-T4 | `@missingElem.currentTime`（元素不存在）→ SEM-REF-001, message=`引用未定义元素属性 @missingElem` |

---

## 契约 C4：golden fixture string Var 声明

### 规则

对 8 个 fixture 中作为伴生测试数据的未声明 `@xxx` 引用，在根元素下声明对应 string Var：

| Fixture | 声明 |
|---|---|
| wallpaper_constraint_enum | `<Var name="dynamic_bg" type="string"/>` |
| lockscreen_nesting_var | `<Var name="dynamic_overlay" type="string"/>` |
| charging_skin_cmd_nest | `<Var name="charge_icon" type="string"/>` |
| deep_nesting_violations | `<Var name="deep_dynamic" type="string"/>` |
| constraint_edge_cases | `<Var name="dynamic_src" type="string"/>` + `<Var name="only_srcexp" type="string"/>` |
| lockscreen_multi_error | `<Var name="weather_icon" type="string"/>` |
| wallpaper_invalid_enum | `<Var name="icon" type="string"/>` |

### 后置条件

- 各 fixture 的 `expected.json` counts/diagnostics **不变**（`@xxx` 变已定义，不新增 SEM-REF-001）。
- `@ishour12`（deep_nesting_violations:39）是预制全局，无需声明。
- Var 声明置于根元素首部（与其他 Var 声明同列），不改变既有元素结构。

### 验收测试场景

| 场景 | 期望 |
|---|---|
| C4-T1 | 8 个 fixture 各自 GoldenDiagnosticMatchTest 通过（counts/diagnostics 与 expected.json 一致） |
| C4-T2 | deep_nesting_violations 的 `@ishour12` 不新增 SEM-REF-001（预制全局 lookup 命中） |

---

## 契约 C5：LSP `@` 回归测试（审计 C15）

### 规则

`AnalysisServiceTest` 补一条 `@` 前缀未定义引用的回归测试，与现有 `#undefinedVar` 测试对称：

- 输入：`<Lockscreen><Text name="t" textExp="@undefinedStr"/></Lockscreen>`
- 期望：core 诊断非空，含 SEM-REF-001，LSP range 非零宽（endCharacter > startCharacter）

### 验收测试场景

| 场景 | 期望 |
|---|---|
| C5-T1 | `@undefinedStr` → SEM-REF-001 产出，LSP range 非零宽 |

---

## spec 条目 ↔ 验收测试清单汇总

| Spec 条目 | 验收测试 | 对应 PHASE 1 AC |
|---|---|---|
| C1 BR-2（`@` 不跳过） | C1-T1, C1-T7, C1-T8 | AC-2 |
| C1 BR-3（存在性解析） | C1-T3, C1-T4, C1-T5 | AC-3, AC-4, AC-5 |
| C1 BR-3 元素属性路径 | C1-T6 | AC-7 |
| C1 BR-5（`#` 回归） | C1-T2 | AC-2 |
| C2 诊断格式 | C2-T1, C2-T2, C2-T3 | AC-2 |
| C3 D1（双文本/编译） | C3-T1, C3-T2 | AC-1 |
| C3 D2（docUrl 一致） | C3-T3 | AC-1 |
| C3 `@` 元素属性 | C3-T4 | AC-7 |
| C4 fixture 声明 | C4-T1, C4-T2 | AC-6 |
| C5 LSP 回归 | C5-T1 | AC-8 |

---

> **阶段切换**：PHASE 2 完成。请用户确认以上规格（4 契约 + 1 LSP 回归契约 + 验收测试清单），确认后进入 PHASE 3（设计）。
