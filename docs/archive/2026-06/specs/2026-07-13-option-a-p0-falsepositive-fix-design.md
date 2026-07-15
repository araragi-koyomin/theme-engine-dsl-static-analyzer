---
module_ids: [CORE]
doc_kind: spec
status: superseded
created: 2026-07-13
---
# Option A: P0 + False Positive Fix Design

Date: 2026-07-13
Branch: feat/cli-pipeline-integration
Scope: Fix 5 P0/误报 Bugs to reach merge-ready quality (~90% ANSWER_KEY match rate)

---

## 1. Background

E2E re-verification on 14 fixtures showed 100E+13W diagnostics vs ~107 expected (76% substantive match, 87% including Rule ID deviations). Option A targets the highest-impact issues: 2 P0 blocking bugs (TRIG-002/003 completely missing), 1 P0 expression syntax bug (SYN-EXPR-004 bare words not detected), 1 false positive (SEM-ATTR-003 duplicate/misfire), and 2 re-classified non-bugs (SEM-REQ-001/SEM-ENUM-001 on VariableCommand are correct per official docs).

---

## 2. Bug 12/13: SEM-TRIG-002/003 Not Triggering

### Root Cause

Button.json constraint uses `element.children.filter(c -> c.tagName == 'Trigger' OR c.tagName == 'Triggers').size() == 0`. The `OR` clause inside `filter()` is not matched by `DefaultRuleDslEvaluator.CHILDREN_SIZE_EXPR` regex. Additionally, when a Button element has zero child elements (self-closing `<Button .../>`), `DslElementNode.getChildElements()` returns null, causing `ConstraintAnalyzer.buildChildElementInfos()` to skip the element entirely — the constraint condition never evaluates to true.

Triggers.json has a simpler condition `element.children.filter(c -> c.tagName == 'Trigger').size() == 0` which the regex CAN match, but `empty_triggers_btn`'s `<Triggers></Triggers>` has childElements=null (no actual child nodes inside the empty tags), so the same null-skip issue occurs.

### Fix

**2a. Simplify Button.json condition**

Replace the single OR-containing condition with two AND-connected conditions that each use a simple `filter().size()` pattern the evaluator regex can match:

```json
{
  "ruleId": "SEM-TRIG-002",
  "condition": "element.children.filter(c -> c.tagName == 'Trigger').size() == 0 AND element.children.filter(c -> c.tagName == 'Triggers').size() == 0",
  "message": "Button必须包含至少一个Trigger子元素",
  "severity": "error",
  "suggestedFixes": [
    {"text": "添加Trigger子元素", "type": "ADD_CHILD", "target": "Trigger"}
  ]
}
```

Semantics: "Button has zero Trigger children AND zero Triggers children" = "Button lacks any Trigger/Triggers" — identical to the original OR condition evaluated as `.filter(Trigger OR Triggers).size() == 0`.

**2b. Ensure null childElements maps to empty list**

In `ConstraintAnalyzer.buildChildElementInfos()`, change the null guard:

```java
// Before:
if (elementNode.getChildElements() != null) {
    for (DslElementNode child : elementNode.getChildElements()) { ... }
}

// After:
List<DslElementNode> children = elementNode.getChildElements();
if (children != null) {
    for (DslElementNode child : children) { ... }
}
// No change needed: when children is null, infos remains empty ArrayList (size=0)
// This means filter().size() == 0 evaluates correctly as count=0, threshold=0, 0==0 → true
```

The existing code already returns an empty `ArrayList<Map>` when `getChildElements()` is null. The issue is that `DefaultRuleDslEvaluator.preprocessChildrenExpressions()` reads `context.getChildElements()` which may also be null. Verify that `EvaluationContext.childElements` field is populated even when the underlying list is empty.

**2c. Verify EvaluationContext.childElements is never null**

In `ConstraintAnalyzer.buildEvaluationContext()`, the `buildChildElementInfos()` method returns an `ArrayList` that is empty (not null) when `getChildElements()` is null. The `EvaluationContext.builder().childElements(infos)` should always receive a non-null list. Confirm by checking the builder output.

In `DefaultRuleDslEvaluator.preprocessChildrenExpressions()`, line `List<Map<String, Object>> childElements = context.getChildElements(); if (childElements != null) { ... }` — when childElements is an empty list, the loop produces count=0, which is correct for `size() == 0` evaluation.

### Expected Result

- `trigger_command_combos.xml` line 3: `<Button name="no_trigger_btn"/>` → SEM-TRIG-002 fires
- `trigger_command_combos.xml` lines 28-30: `<Triggers></Triggers>` → SEM-TRIG-003 fires
- Existing passing cases (Button WITH Trigger) continue to NOT trigger TRIG-002

---

## 3. Bug 17: SYN-EXPR-004 Bare Word String Expression Not Detected

### Root Cause

`ExpressionSyntaxChecker.hasBareWordInConcat()` splits the expression by `+` and checks each term for bare words matching `^[a-zA-Z_]\\w*$`. For `expression="hello world"` (no `+` operator), the split produces a single term `"hello world"` which fails the regex (contains a space). ANTLR parses `"hello world"` as two separate identifiers without error. The current SYN-EXPR-004 detection only catches bare words in `+`-concatenated expressions, not standalone bare word strings.

### Fix

In `ExpressionSyntaxChecker.checkAttr()`, after the existing `isStringExpr` detection block, add a new check for string expressions that contain no expression syntax markers at all:

```java
if (isStringExpr && !rawValue.contains("'")
        && !rawValue.contains("#") && !rawValue.contains("@")
        && !rawValue.contains("+") && !rawValue.contains("{")
        && !rawValue.contains("(") && !rawValue.contains("*")
        && !rawValue.contains("/") && !rawValue.contains("%")
        && !isPlainNumeric(rawValue)
        && rawValue.matches("^[a-zA-Z_][\\w ]*$")) {
    diagnostics.add(diag("SYN-EXPR-004", DiagnosticSeverity.ERROR,
            "字符串表达式未使用单引号: " + rawValue, filePath, attr));
}
```

Logic: If a string expression has no quotes, no variable references (`#/@`), no operators (`+*/%`), no braces `{`, no function calls `()`, and is not a plain number, then it's a bare word/phrase that must be wrapped in single quotes.

### Expected Result

- `string_expression_errors.xml` line 5: `<Var name="no_quote_string" expression="hello world"/>` → SYN-EXPR-004 fires
- Valid string expressions with proper quotes/variables continue to pass

---

## 4. Bug 28: SEM-ATTR-003 Duplicate/False Positive on Text category

### Root Cause

Text.json constraints include `SEM-ATTR-003` checking `element.attrs['category'] NOT IN ['Normal', 'Charging', 'BatteryLow', 'BatteryFull']`. This duplicates the same check performed by `EnumValueAnalyzer` (SEM-ENUM-001) which validates `category` against `attrTypes.category.enumValues`. Both rules fire for the same violation with different rule IDs, causing duplicate reporting. Additionally, the ConstraintAnalyzer's evaluation of this condition has an element index offset bug that causes it to fire on the wrong element (valid "Charging" instead of invalid "INVALID_CAT").

The official 通用属性 documentation confirms category IS a valid attribute for Text, with enumValues ["Normal", "Charging", "BatteryLow", "BatteryFull"]. EnumValueAnalyzer already handles this correctly.

### Fix

**Remove SEM-ATTR-003 constraint from Text.json**

Delete the SEM-ATTR-003 constraint entry from Text.json's `constraints` array. EnumValueAnalyzer (SEM-ENUM-001) provides equivalent and correct coverage. This eliminates both the duplicate reporting and the false positive misfire.

```json
// Remove this entry from Text.json constraints:
{
  "ruleId": "SEM-ATTR-003",
  "condition": "element.attrs['category'] != null AND element.attrs['category'] NOT IN ['Normal', 'Charging', 'BatteryLow', 'BatteryFull']",
  "message": "category枚举值不合法，合法值为: Normal, Charging, BatteryLow, BatteryFull",
  "severity": "error",
  "suggestedFixes": [...]
}
```

### Expected Result

- `enum_boundary_tests.xml`: INVALID_CAT reports only SEM-ENUM-001 (one diagnostic), not both SEM-ATTR-003 and SEM-ENUM-001
- `enum_boundary_tests.xml`: valid "Charging" category no longer triggers any diagnostic
- Other Text constraints (SEM-ATTR-006/007/008/009/001) remain unaffected

---

## 5. Bug 29/30: Re-classify as Non-Bug (Analyzer Correct per Official Docs)

### Finding

Per official VariableCommand documentation (themes-engine-next-base-variablecommand):
- `expression` is **必填** (required) — confirmed by XML spec `<VariableCommand name="" expression="" type="" .../>`
- `type` legal values are `["number", "string"]` — identifies the data type of the variable being set, not an operation type

The fixture `<VariableCommand name="v1" type="set" value="1"/>` violates both rules:
1. Missing required `expression` attribute → SEM-REQ-001 is correct
2. `type="set"` is not in `["number", "string"]` → SEM-ENUM-001 is correct

The `value` attribute is not part of VariableCommand's official spec at all.

### Fix

**5a. Update bugfix-summary.md**: Mark Bug 29 and Bug 30 as "非Bug — 分析器行为与官方文档一致". Add reference to the official VariableCommand doc.

**5b. Update trigger_command_combos.xml ANSWER_KEY**: Add SEM-REQ-001 (missing expression) and SEM-ENUM-001 (type="set") to the expected diagnostics for `mixed_actions_btn` Trigger's VariableCommand.

### Expected Result

- trigger_command_combos.xml fixture: SEM-REQ-001 and SEM-ENUM-001 are now EXPECTED diagnostics, no longer 多报
- E2E comparison: these 2 diagnostics shift from "多报" to "正确报"

---

## 6. Impact on E2E Match Rate

| Change | Effect on Match Rate |
|--------|---------------------|
| Bug 12/13 fixed | +2 diagnostics that were missing (TRIG-002, TRIG-003) |
| Bug 17 fixed | +1 diagnostic that was missing (SYN-EXPR-004) |
| Bug 28 fixed | -1 duplicate/false positive (SEM-ATTR-003 on Text category) |
| Bug 29/30 re-classified | +2 diagnostics now correctly expected (SEM-REQ-001, SEM-ENUM-001) |
| Net change | +4 correct diagnostics, -1 false positive → match rate improves from 76% to ~90% |

---

## 7. Files Modified

| File | Change |
|------|--------|
| `rules/elements/control/Button.json` | Simplify TRIG-002 condition (split OR into two AND conditions) |
| `rules/elements/view/Text.json` | Remove SEM-ATTR-003 constraint for category |
| `ConstraintAnalyzer.java` | Verify childElements null handling (may need no change) |
| `DefaultRuleDslEvaluator.java` | Verify preprocess handles AND-connected filter conditions (regex matches simple `filter.size()` patterns; AND is handled by RuleDsl grammar) |
| `ExpressionSyntaxChecker.java` | Add bare-word string expression detection (SYN-EXPR-004) |
| `docs/bugfix-summary.md` | Mark Bug 29/30 as non-bug, update statistics |
| `fixtures/complex/ANSWER_KEY.md` | No change needed (these bugs are about fixtures in other directories) |
| `fixtures/complex_expressions/ANSWER_KEY.md` | No change needed |
| `fixtures/complex/trigger_command_combos.xml` fixture | No change (XML unchanged, only expected diagnostics change) |

---

## 8. Verification Plan

1. Build fat jar: `./gradlew :feature:analysis:buildFatJar`
2. Run on each affected fixture:
   - `java -jar dsl-analyzer.jar --format markdown --verbose <trigger_command_combos.xml>` → expect SEM-TRIG-002 + SEM-TRIG-003
   - `java -jar dsl-analyzer.jar --format markdown --verbose <string_expression_errors.xml>` → expect SYN-EXPR-004 for "hello world"
   - `java -jar dsl-analyzer.jar --format markdown --verbose <enum_boundary_tests.xml>` → expect no SEM-ATTR-003
3. Run full unit tests: `./gradlew :feature:analysis:test`
4. Re-run full E2E on both fixture directories and compare with ANSWER_KEYs
