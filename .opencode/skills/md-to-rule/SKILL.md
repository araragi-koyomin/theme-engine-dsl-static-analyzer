---
name: md-to-rule
description: Convert theme-engine developer markdown documentation into formal JSON element rule files. Use when transforming a markdown file under docs/themes_engine_next/raw_markdown/ (e.g. themes-engine-next-base-image-*.md) into a JSON rule under feature/analysis/src/main/resources/rules/elements/<category>/ (e.g. view/Image.json), including attribute types, scope, device support, and declarative constraints.
---

# Markdown → JSON Rule Conversion

Convert a developer-facing markdown document into a machine-readable element
rule JSON file. The JSON is consumed by `ConstraintAnalyzer` (and other
analyzers) at runtime — every field must be precise.

## 1. Inputs and outputs

- **Input**: a markdown file under
  `docs/themes_engine_next/raw_markdown/themes-engine-next-<...>-<id>.md`
- **Output**: a JSON file under
  `feature/analysis/src/main/resources/rules/elements/<category>/<ElementName>.json`

## 2. Markdown document structure

Each markdown doc follows this fixed layout:

| Section | Content |
|---|---|
| `# 视图：图片<Image>` | Title line: Chinese description + `<TagName>` in angle brackets. Extract `TagName`. |
| 功能概述 | One-paragraph functional description → maps to `description`. |
| 支持范围 → 起始规范版本 | HarmonyOS version (informational, not stored in JSON). |
| 支持范围 → 是否平台特性 | Yes/No (informational). |
| Scope table (5 cols) | `Lockscreen / Wallpaper / LongTake / Widget / ChargingSkin` with √ or × → maps to `scope`. |
| Device table (3 cols) | `直板机 / 折叠屏 / 平板` with √ or × → maps to `deviceSupport` (`barPhone / foldable / tablet`). |
| XML规范 | Code block showing `<TagName attr="" .../>` → reveals attribute names. |
| 参数说明 | Markdown table with columns **参数 / 类型 / 选项 / 注释** → maps to `requiredAttrs`, `optionalAttrs`, `attrTypes`. |
| 应用示例 | Usage examples with sample XML — use to infer constraints and validate attribute usage. |

### Scope table reading

```
|  | 锁屏（Lockscreen） | 桌面（Wallpaper） | 一镜到底（LongTake） | 百变卡片（Widget） | 充电动效（ChargingSkin） |
| 是否支持 | √ | √ | √ | √ | √ |
```
→ `"scope": { "Lockscreen": true, "Wallpaper": true, "LongTake": true, "Widget": true, "ChargingSkin": true }`

√ = `true`, × = `false`.

### Device table reading

```
|  | 直板机 | 折叠屏 | 平板 |
| 是否支持 | √ | √ | √ |
```
→ `"deviceSupport": { "barPhone": true, "foldable": true, "tablet": true }`

## 3. JSON rule structure

The output JSON maps directly to the `DslElementRule` model. Top-level fields:

| Field | Type | Description |
|---|---|---|
| `element` | string | Tag name, e.g. `"Image"`. Must match the `<TagName>` in the markdown title. |
| `category` | string | Element category. Determines the output subdirectory (see §6). |
| `description` | string | Functional overview from markdown (功能概述). Extra field, ignored by Gson but useful for documentation. |
| `requiredAttrs` | string[] | Attribute names marked 必填 in the 参数说明 table. |
| `optionalAttrs` | string[] | All other valid attribute names (选填 + common attrs, see §5). |
| `attrTypes` | object | Per-attribute type spec. Keyed by attribute name. See §4. |
| `allowedParents` | string[] | Legal parent tag names. Infer from scope + element type (see §7). |
| `inherits` | string\|null | Inheritance declaration. Use `null` unless the markdown states otherwise. |
| `scope` | object | 5-key map: `Lockscreen/Wallpaper/LongTake/Widget/ChargingSkin` → boolean. |
| `deviceSupport` | object | 3-key map: `barPhone/foldable/tablet` → boolean. |
| `constraints` | array | Declarative constraint rules. See §8. |

## 4. attrTypes field — AttrTypeSpec

Each attribute in `attrTypes` has this shape:

```json
"attrName": {
  "type": "string",
  "enumValues": [],
  "aliases": [],
  "supportsExpression": false,
  "expressionKind": null,
  "defaultValue": null,
  "description": "(optional) the 注释 text from the markdown table"
}
```

| Field | Values | How to derive |
|---|---|---|
| `type` | `"string"` / `"number"` | From the 类型 column: 字符串→`"string"`, 数值/布尔值→`"number"` (booleans are modeled as string "true"/"false"). |
| `enumValues` | string[] | Extract enumerated options from the 注释 text (e.g. `true`/`false`, `fill`/`center_crop`/`hold_center_crop`). Empty array if none. |
| `aliases` | string[] | Known aliases from examples or convention (e.g. `width`→`["w"]`, `rotation`→`["angle"]`). Empty array if none. |
| `supportsExpression` | boolean | `true` if the 注释 says "支持表达式" or "支持数字表达式" or if values like `#var` appear in examples. |
| `expressionKind` | `"number"` / `"string"` / `"auto"` / `null` | `"number"` if numeric expression; `"string"` if string expression; `"auto"` if context-dependent (e.g. Var.expression); `null` if `supportsExpression` is false. |
| `defaultValue` | string\|null | From 注释 text: look for "默认X" (default X). `null` if no default stated. |
| `description` | string (optional) | The 注释 column text verbatim. Not consumed by the model but aids maintainers. |

### 4.1 Description text extraction gotchas

When extracting the `description` text for an attrType from the markdown's 注释 column:

- **Strip `**` (bold markers) and backticks**, but **preserve single `*`** — it is
  used for multiplication in Chinese text (e.g. `1920*1080`), not markdown formatting.
  Stripping single `*` corrupts the text (turning `1920*1080` into `19201080`).
- **Attribute aliases in parentheses**: some markdown tables list attributes with
  aliases in parentheses, e.g. `textExp（text）`. Extract only the part before the
  parenthesis as the attribute name (`textExp`), and the parenthesised part is the
  alias (`text`).
- **Common attribute descriptions** come from the `general.md` markdown's parameter
  table (§5), not from each element's own markdown.
- **Attributes not in any 参数说明 table** (e.g. `antiAlias`, `IsFullScreenNode` on
  Image) — search the XML规范 examples, other markdown sections (e.g.
  `precautions.md`), or other elements' tables (e.g. `isFullScreenNode` appears in
  `collisionworld.md`). If still not found, derive a brief description from context.

### 4.2 Description field placement

- **Top-level `description`**: placed right after `"category"`, before
  `"requiredAttrs"`.
- **attrType-level `description`**: placed after `"defaultValue"`, before the
  closing `}` of the attrType entry. Add a comma after the `defaultValue` line
  if one is not already present.

## 5. Common attributes (通用属性)

Most `view` and `layout` category elements support a standard set of common
attributes that are **not** listed in the markdown's 参数说明 table but appear
in the XML规范 examples and are mentioned as "支持通用属性". Always include
these in `optionalAttrs` and `attrTypes` for `view`/`layout` elements:

```
name, x, y, width, height, pivotX, pivotY, rotation, rotationX, rotationY,
alpha, visibility, category, align, alignV, enableMove, moveRect, active
```

Their standard type specs (copy verbatim):

| Attr | type | enumValues | aliases | supportsExpression | expressionKind | defaultValue |
|---|---|---|---|---|---|---|
| `name` | string | [] | [] | false | null | null |
| `x` | number | [] | [] | true | "number" | "0" |
| `y` | number | [] | [] | true | "number" | "0" |
| `width` | number | [] | ["w"] | true | "number" | null |
| `height` | number | [] | ["h"] | true | "number" | null |
| `pivotX` | number | [] | ["centerX"] | true | "number" | null |
| `pivotY` | number | [] | ["centerY"] | true | "number" | null |
| `rotation` | number | [] | ["angle"] | true | "number" | null |
| `rotationX` | number | [] | ["angleX"] | true | "number" | null |
| `rotationY` | number | [] | ["angleY"] | true | "number" | null |
| `alpha` | number | [] | [] | true | "number" | "255" |
| `visibility` | number | [] | [] | true | "number" | "1" |
| `category` | string | ["Normal","Charging","BatteryLow","BatteryFull"] | [] | false | null | null |
| `align` | string | ["left","center","right"] | [] | false | null | null |
| `alignV` | string | ["top","center","bottom"] | [] | false | null | null |
| `enableMove` | string | ["true","false"] | [] | false | null | "false" |
| `moveRect` | string | [] | [] | false | null | null |
| `active` | number | [] | [] | false | null | "1" |

**Ordering convention**: in `optionalAttrs`, list common attributes first
(starting with `name, x, y, ...`), then element-specific attributes in the
order they appear in the markdown's 参数说明 table. In `attrTypes`, define
common attributes first, then element-specific ones, each in the same order.

## 6. Category → directory mapping

The `category` field determines the output subdirectory and the allowed
parent set conventions:

| category | Output dir | Typical allowedParents |
|---|---|---|
| `root` | `elements/root/` | (root elements — `allowedParents` is empty) |
| `view` | `elements/view/` | `Lockscreen, Wallpaper, Widget, ChargingSkin, Group, Array, LongTake, GroupImage, GroupBattery, StereoGroup` |
| `layout` | `elements/layout/` | (container elements — depends on element) |
| `variable` | `elements/variable/` | `Lockscreen, Wallpaper, Widget, ChargingSkin, Group, ...` (depends on element) |
| `control` | `elements/control/` | (interactive elements) |
| `command` | `elements/commands/` | (command elements — often nested in commands or triggers) |
| `animation` | `elements/animation/` | (animation elements — parent is the element being animated) |
| `effect` | `elements/effect/` | (physics/effect elements) |
| `three_d` | `elements/three_d/` | (3D stereoscopic elements) |
| `trigger` | `elements/trigger/` | (trigger elements) |
| `longtake` | `elements/longtake/` | (one-shot camera elements) |
| `data_open` | `elements/data_open/` | (data-binding / open-data elements) |

Determine `allowedParents` by:
1. Checking the scope — elements supported in `Lockscreen` can usually be
   direct children of `Lockscreen`.
2. Checking existing JSON rules for the same category.
3. If uncertain, include the root elements whose scope matches plus `Group`
   and `Array` (common container elements).

## 7. Workflow

1. **Read the markdown** file fully.
2. **Determine if this is an element markdown** — see §13.1. Many markdowns
   in `raw_markdown/` are feature/syntax docs that do **not** describe XML
   elements and should **not** produce JSON files. Skip non-element markdowns.
3. **Extract `element`**: the tag name from the title `<TagName>`.
4. **Determine `category`**: from the markdown title prefix (视图→`view`,
   命令→`commands`, 变量→`variable`, etc.) or by checking sibling files in
   the same raw_markdown directory.
5. **Extract `description`**: the 功能概述 paragraph (first paragraph between
   the 功能概述 header and the 支持范围 header).
6. **Extract `scope` and `deviceSupport`**: from the two support tables.
7. **Extract attributes** from the 参数说明 table:
   - 选项 = 必填 → add to `requiredAttrs`.
   - 选项 = 选填 → add to `optionalAttrs`.
   - For each, build an `attrTypes` entry (§4), including the `description`
     field from the 注释 column (§4.1).
8. **Add common attributes** (§5) to `optionalAttrs` and `attrTypes` if the
   element is `view` or `layout` category and the markdown mentions
   "通用属性". Use the common attribute descriptions from `general.md`.
9. **Determine `allowedParents`** (§6).
10. **Derive `constraints`** from the 注释 text and 应用示例 (§8).
11. **Write the JSON** to
    `feature/analysis/src/main/resources/rules/elements/<category>/<ElementName>.json`.
12. **Validate**: run `./gradlew :feature:analysis:test --tests "ConstraintAnalyzerTest"`
    to ensure no constraint syntax errors.

## 8. Constraint DSL — declarative constraint syntax

Constraints are the most error-prone part. The `condition` field is parsed by
an ANTLR4 grammar (`DslRuleCondition.g4`) and evaluated by
`DefaultRuleDslEvaluator`. **Only the syntax below is supported.** Any other
syntax causes a parse failure → the evaluator catches the exception and
returns `false`, meaning the constraint **silently never fires**.

### 8.1 Constraint object shape

```json
{
  "ruleId": "SEM-IMG-002",
  "condition": "element.attrs['src'] != null AND element.attrs['srcExp'] != null",
  "message": "src与srcExp无法共存，当存在src值时图片使用src作为地址",
  "severity": "error",
  "suggestedFixes": ["移除srcExp属性", "移除src属性"]
}
```

| Field | Description |
|---|---|
| `ruleId` | Unique ID, format `SEM-<GROUP>-<NNN>`. See §8.6. |
| `condition` | DSL expression (§8.2–8.5). Evaluates to `true` = violation. |
| `message` | Human-readable diagnostic message (Chinese, matching markdown wording). |
| `severity` | `"error"`, `"warning"`, or `"info"` (lowercase in JSON). |
| `suggestedFixes` | Array of fix suggestion strings. |

### 8.2 Value expressions

| Syntax | Meaning | Example |
|---|---|---|
| `element.attrs['attrName']` | The raw string value of the attribute, or `null` if absent. | `element.attrs['src']` |
| `element.tagName` | The element's tag name string. | `element.tagName == 'Image'` |
| `'literal'` | Single-quoted string literal. | `'true'` |
| `123` / `1.5` | Numeric literal. | `0` |
| `null` | Null literal. | `element.attrs['src'] == null` |

> **IMPORTANT**: Do NOT use `element.parent.tagName`, `parseInt(...)`,
> `MATCHES`, `element.parent.children.filter(...)`, arithmetic, or any
> function call. These are **not** in the grammar and will silently fail.

### 8.3 Comparison operators

| Operator | Semantics | Null behavior |
|---|---|---|
| `==` | String equality | `null == null` → true; `null == 'x'` → false |
| `!=` | String inequality | `null != null` → false; `null != 'x'` → true |
| `>` | Numeric (falls back to string compare) | Either side null → false |
| `<` | Numeric | Either side null → false |
| `>=` | Numeric | Either side null → false |
| `<=` | Numeric | Either side null → false |
| `IN` | Set membership | Value null → false |
| `NOT IN` | Set non-membership | Value null → true |

### 8.4 Set literals

```sql
element.attrs['scaleType'] IN ['fill', 'center_crop', 'hold_center_crop']
element.attrs['category'] NOT IN ['Normal', 'Charging', 'BatteryLow', 'BatteryFull']
```

Format: `[` literal `,` literal ... `]`. Literals are single-quoted strings
or numbers.

### 8.5 Logical operators

| Operator | Example |
|---|---|
| `AND` | `element.attrs['isBackground'] == 'true' AND element.attrs['scaleType'] != 'center_crop'` |
| `OR` | `element.attrs['loop'] != 'true' OR element.attrs['unlockTo'] == null` |
| `NOT` | `NOT element.attrs['src'] == null` |
| `( )` | `(element.attrs['width'] == null OR element.attrs['height'] == null)` |

Operator words are **uppercase**: `AND`, `OR`, `NOT`, `IN`, `null`.

### 8.6 Rule ID convention

Format: `SEM-<GROUP>-<NNN>` where `<GROUP>` is a short uppercase tag and
`<NNN>` is a zero-padded 3-digit number.

| Group pattern | Use when |
|---|---|
| `SEM-IMG-*` | Image-specific rules (src/srcExp, scaleType, isBackground) |
| `SEM-SRCIMG-*` | SourceImage-specific rules |
| `SEM-VAR-*` | Var-specific rules |
| `SEM-CMD-*` | Command-specific rules (per command type) |
| `SEM-ATTR-*` | General attribute rules shared across elements (alpha range, category enum, etc.) |
| `SEM-PERSIST-*` | Persistence-related rules |
| `SEM-NEST-*` | Nesting/parent-child rules |
| `SEM-SCOPE-*` | Scope/device rules |
| `SEM-REQ-*` | Required-attribute-missing rules |

If a rule is conceptually the same as one already used by another element
(e.g. alpha range check `SEM-ATTR-001`), reuse the same `ruleId`.

### 8.7 Deriving constraints from markdown

Read the **注释** column of the 参数说明 table carefully. Common constraint
patterns:

| Markdown wording | Constraint pattern | Example |
|---|---|---|
| "X与Y无法共存" / "无法与X共存" | `element.attrs['X'] != null AND element.attrs['Y'] != null` | src vs srcExp |
| "X必须与Y配合使用" | `element.attrs['X'] == 'val' AND element.attrs['Y'] != 'expected'` | isBackground + scaleType |
| "X与Y同时使用时X不生效" (warning) | `element.attrs['X'] != null AND element.attrs['Y'] != null` | isBackground + align |
| "X默认为Y" | Set `defaultValue` in attrTypes, no constraint needed | — |
| "X仅在有Y时有意义" (warning) | `element.attrs['X'] != null AND element.attrs['Y'] == null` | marqueeRepeatLimit + scrollDisplay |
| "X取值范围为A-B" | `element.attrs['X'] != null AND (element.attrs['X'] < 'A' OR element.attrs['X'] > 'B')` | blur 0-24, alpha 0-255 |
| "X必须是png/jpg格式" | `element.attrs['X'] != null AND element.attrs['X'] NOT IN ['png', 'jpg']` | format enum |
| At least one of X/Y required | `element.attrs['X'] == null AND element.attrs['Y'] == null` | src/srcExp |
| Enum with fixed values | `element.attrs['X'] != null AND element.attrs['X'] NOT IN ['v1', 'v2']` | category, scaleType |

> **Note on range checks**: The grammar supports `<` and `>` with numeric
> semantics (parsed as `double`). So
> `element.attrs['blur'] < '0' OR element.attrs['blur'] > '24'` is valid.
> Do NOT wrap values in `parseInt(...)` — that syntax is unsupported.

## 9. JSON formatting conventions

- 2-space indentation.
- String values use `\"`, unicode escapes for single quotes inside conditions:
  `'` → `\u0027` (e.g. `element.attrs[\u0027src\u0027]`).
  However, plain `'` also works in practice since Gson handles it; the
  `\u0027` encoding is a convention used in existing files for safety.
  Either form is acceptable.
- `<` and `>` in conditions: encode as `\u003c` and `\u003e` respectively,
  or write them directly (Gson handles both, but `\u003c`/`\u003e` avoids
  issues if the JSON is ever embedded in XML/HTML).
- `constraints` array entries are separated by `, ` with the opening brace
  of the next entry on the same line (matching existing file style):
  `}, {`
- Empty arrays on one line: `[]`
- Empty `requiredAttrs` as `[]` (not omitted).

## 10. Complete worked example

**Input** (excerpt from `themes-engine-next-base-image-*.md`):

```markdown
# 视图：图片<Image>
功能概述
用于在界面上展示一张图片，可以指定图片路径，模糊程度等属性...
| 参数 | 类型 | 选项 | 注释 |
| src | 字符串 | 选填 | 图片名称的相对路径...src与srcExp两个参数必须填写一个... |
| blur | 字符串 | 选填 | 模糊半径值的设置，其值为0到24... |
| scaleType | 字符串 | 选填 | ...目前支持三种模式：fill、center_crop和hold_center_crop，默认为center_crop |
| isBackground | 字符串 | 选填 | ...允许设置为"true"或"false"，默认为false...必须与scaleType="center_crop"配合使用。isBackground与align同时使用时不生效 |
| srcExp | 字符串 | 选填 | 图片源表达式...无法与src共存... |
```

**Output** (`elements/view/Image.json`, abbreviated):

```json
{
  "element": "Image",
  "category": "view",
  "description": "用于在界面上展示一张图片...",
  "requiredAttrs": [],
  "optionalAttrs": [
    "name", "x", "y", "width", "height", "pivotX", "pivotY", "rotation", "rotationX", "rotationY", "alpha",
    "visibility", "category", "align", "alignV", "enableMove", "moveRect", "active", "src", "srcid", "srcExp", "blur",
    "scaleType", "isBackground", "useVirtualScreen"
  ],
  "attrTypes": {
    "name": { "type": "string", "enumValues": [], "aliases": [], "supportsExpression": false, "expressionKind": null, "defaultValue": null },
    "x": { "type": "number", "enumValues": [], "aliases": [], "supportsExpression": true, "expressionKind": "number", "defaultValue": "0" },
    "src": {
      "type": "string", "enumValues": [], "aliases": [],
      "supportsExpression": false, "expressionKind": null, "defaultValue": null,
      "description": "图片名称的相对路径..."
    },
    "blur": {
      "type": "string", "enumValues": [], "aliases": [],
      "supportsExpression": false, "expressionKind": null, "defaultValue": null,
      "description": "模糊半径值的设置，其值为0到24..."
    },
    "scaleType": {
      "type": "string", "enumValues": ["fill", "center_crop", "hold_center_crop"], "aliases": [],
      "supportsExpression": false, "expressionKind": null, "defaultValue": "center_crop",
      "description": "图片的缩放模式..."
    },
    "isBackground": {
      "type": "string", "enumValues": ["true", "false"], "aliases": [],
      "supportsExpression": false, "expressionKind": null, "defaultValue": "false",
      "description": "允许设置为true或false..."
    },
    "srcExp": {
      "type": "string", "enumValues": [], "aliases": [],
      "supportsExpression": true, "expressionKind": "string", "defaultValue": null,
      "description": "图片源表达式...无法与src共存..."
    }
  },
  "allowedParents": ["Lockscreen", "Wallpaper", "Widget", "ChargingSkin", "Group", "Array", "LongTake", "GroupImage", "GroupBattery", "StereoGroup"],
  "inherits": null,
  "scope": { "Lockscreen": true, "Wallpaper": true, "LongTake": true, "Widget": true, "ChargingSkin": true },
  "deviceSupport": { "barPhone": true, "foldable": true, "tablet": true },
  "constraints": [
    {
      "ruleId": "SEM-ATTR-005",
      "condition": "element.attrs['isBackground'] == 'true' AND element.attrs['scaleType'] != 'center_crop'",
      "message": "isBackground=true时必须配合scaleType=center_crop",
      "severity": "error",
      "suggestedFixes": ["设置scaleType=center_crop"]
    },
    {
      "ruleId": "SEM-IMG-002",
      "condition": "element.attrs['src'] != null AND element.attrs['srcExp'] != null",
      "message": "src与srcExp无法共存，当存在src值时图片使用src作为地址",
      "severity": "error",
      "suggestedFixes": ["移除srcExp属性", "移除src属性"]
    },
    {
      "ruleId": "SEM-IMG-003",
      "condition": "element.attrs['isBackground'] == 'true' AND element.attrs['align'] != null",
      "message": "isBackground与align同时使用时isBackground不生效",
      "severity": "warning",
      "suggestedFixes": ["移除align属性或移除isBackground属性"]
    },
    {
      "ruleId": "SEM-IMG-SRC",
      "condition": "element.attrs['src'] == null AND element.attrs['srcExp'] == null",
      "message": "Image的src与srcExp至少需要填写一个以指定显示图片",
      "severity": "error",
      "suggestedFixes": ["添加src属性指定图片路径", "添加srcExp属性指定图片源表达式"]
    }
  ]
}
```

## 11. Validation

After writing the JSON, verify:

```bash
./gradlew :feature:analysis:test --tests "ConstraintAnalyzerTest"
```

If a constraint condition has a syntax error, the evaluator silently returns
`false` and the constraint never fires — there will be **no test failure**.
To catch this, manually verify each `condition` string against §8.2–8.5.

### 11.1 Description coverage check

A Python script `check_coverage.py` is provided alongside this skill to verify
that every attrType entry in every JSON rule file has a `description` field.
Run it after making changes:

```bash
python .opencode/skills/md-to-rule/check_coverage.py
```

It reports the total number of attrTypes, how many have descriptions, and lists
any files/attributes still missing them.

## 12. Key files for reference

| File | Purpose |
|---|---|
| `feature/analysis/src/main/java/com/huawei/theme/analysis/core/ruledsl/grammar/DslRuleCondition.g4` | ANTLR4 grammar — the authoritative constraint DSL syntax. |
| `feature/analysis/src/main/java/com/huawei/theme/analysis/core/ruledsl/DefaultRuleDslEvaluator.java` | Evaluator implementation — shows exact null/comparison semantics. |
| `feature/analysis/src/main/java/com/huawei/theme/analysis/core/rulelibrary/model/DslElementRule.java` | JSON model — field-to-field mapping. |
| `feature/analysis/src/main/java/com/huawei/theme/analysis/core/rulelibrary/model/AttrTypeSpec.java` | Attribute type spec model. |
| `feature/analysis/src/main/java/com/huawei/theme/analysis/core/rulelibrary/model/RuleConstraint.java` | Constraint model. |
| `feature/analysis/src/main/java/com/huawei/theme/analysis/core/semanticanalysis/analyzers/ConstraintAnalyzer.java` | How constraints are evaluated at runtime. |
| `feature/analysis/src/test/java/com/huawei/theme/analysis/core/semanticanalysis/ConstraintAnalyzerTest.java` | Test cases — examples of valid constraint conditions. |
| `.opencode/skills/md-to-rule/check_coverage.py` | Description coverage checker — verifies all attrTypes have descriptions. |

## 13. Practical notes & gotchas

### 13.1 Non-element markdowns — do NOT produce JSON

Many markdowns in `raw_markdown/` describe features, syntax, or variables
rather than XML elements. **These should not produce JSON rule files.** To
identify them, check the XML规范 section:

- If the XML规范 shows `<ExistingTag attr="">` (an existing tag with
  attributes), it is a **feature doc** — the title's `<TagName>` is a concept
  name, not a new element. Examples: `BatteryCharging` (uses `<Text>`),
  `FrameRate` (uses `<Lockscreen frameRate="">`), `PicMultiLanguage` (uses
  `<Image>`), `Shake` (global variable `#shake`), `StepCount` (global
  variable `#steps_value`), `VarSpeedFun` (attribute `varSpeedFlag`).
- If the XML规范 says `不涉及` (not applicable) or shows no XML, it is a
  **syntax/reference doc**. Examples: `Expression`, `StringExpression`.
- Info docs (`introduction`, `precautions`, `scope`, `general`) have no
  `<TagName>` in the title — skip them.

**Rule of thumb**: if the markdown's XML规范 shows a tag name that already
has a JSON rule file, the markdown is a feature doc for that existing element
— do not create a new JSON.

### 13.2 Multi-element markdowns

Some markdowns describe multiple elements in a single file:

- The title may contain multiple tags: `# 网格/水波纹<MeshImage-SinMotion>`
  → elements `MeshImage` and `SinMotion`.
- Sub-section headers like `**CollBody参数说明**` or `**ItemGroup**`
  introduce each element's parameter table or description.
- When converting, produce **one JSON per element** (e.g.
  `collisionworld.md` → `CollisionWorld.json`, `CollBody.json`,
  `Texture2D.json`).

For child element descriptions (功能概述), look for bold sub-headers in the
markdown (e.g. `**ItemGroup**` followed by a description paragraph). If a
child element has no explicit description, derive one from its role in the
XML examples and parameter table.

### 13.3 Child elements without their own markdown

Animation keyframe elements (`Alpha`, `Position`, `Rotation`, `Size`,
`Source`, `AniFrame`) do not have their own markdown files — their
attributes appear in the parent animation's markdown 参数说明 table. For
example, `alphaanimation.md` lists both `delay`/`repeat` (for
`AlphaAnimation`) and `a`/`time` (for `Alpha` keyframes).

When adding descriptions for these child elements:
- Use the parent markdown's parameter table to find the attribute descriptions.
- A **global attr→desc map** (built from all markdown tables) is the most
  robust approach — most attribute descriptions are identical across
  elements (e.g. `time` always means "相对于起始帧的间隔时间").

### 13.4 Updating existing JSON files — minimal changes

When the task is to **supplement** existing JSON files (not create new ones):

1. **Top-level `description`**: add after `"category"`, before
   `"requiredAttrs"`. Skip if already present.
2. **attrType `description`**: add after `"defaultValue"`, before the closing
   `}`. Skip if already present. Use the 注释 text from the markdown's
   参数说明 table.
3. Do **not** modify any other fields — keep changes minimal.
4. The `description` field is ignored by Gson (not in the model class), so it
   has no runtime impact — it is purely for documentation and maintainability.

### 13.5 Category value vs directory name

The `category` value in JSON is **singular** while the directory name may be
**plural**:

| JSON `category` value | Output directory |
|---|---|
| `"command"` | `elements/commands/` |
| `"animation"` | `elements/animation/` |
| `"view"` | `elements/view/` |
| `"effect"` | `elements/effect/` |
| `"three_d"` | `elements/three_d/` |
| ... | ... |

When editing existing files, always read the file first to get the exact
`category` value — do not assume it matches the directory name.
