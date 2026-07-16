# Complex Fixture Answer Key

## Fixture 1: `deep_nesting_violations.xml` (Lockscreen)

### Expected Diagnostics

| # | Rule ID | Approx Line | Severity | Description |
|---|---------|-------------|----------|-------------|
| 1 | SEM-ATTR-001 | 5 | ERROR | `Group name="level1" alpha="300"` — alpha value 300 exceeds max 255 |
| 2 | SEM-ENUM-001 | 8 | ERROR | `Group name="level2" category="INVALID_CATEGORY"` — category not in [Normal, Charging, BatteryLow, BatteryFull] |
| 3 | SEM-PERSIST-001 | 9 | ERROR | `Var name="time_persist" persist="true"` with expression referencing `#hour` — persist on time variable `hour` is forbidden |
| 4 | SEM-ENUM-001 | 11 | ERROR | `Group name="level3" enableMove="BAD_BOOL"` — enableMove must be "true" or "false" |
| 5 | SEM-TYPE-003 | 13 | ERROR | `Image name="deepest_img" x="'string_in_number'"` — string literal used in numeric attribute `x` |
| 6 | SEM-REF-001 | 14 | ERROR | `Image name="deepest_img" y="#undefined_var"` — variable `undefined_var` was never declared |
| 7 | SEM-ATTR-001 | 15 | ERROR | `Image name="deepest_img" alpha="999"` — alpha value 999 exceeds max 255 |
| 8 | SEM-ENUM-001 | 16 | ERROR | `Image name="deepest_img" category="IMAGINARY"` — category not in [Normal, Charging, BatteryLow, BatteryFull] |
| 9 | SEM-IMG-002 | 17–18 | ERROR | `Image name="deepest_img"` has both `src="deep.png"` AND `srcExp="@deep_dynamic"` — mutual exclusion violation |
| 10 | SEM-ENUM-001 | 19 | ERROR | `Image name="deepest_img" scaleType="wrong_scale"` — scaleType not in [fill, center_crop, hold_center_crop] |
| 11 | SEM-IMG-003 | 20 | WARNING | `Image name="deepest_img" isBackground="true"` + `align="center"` — isBackground and align conflict |
| 12 | SEM-SWIPER-001 | 23 | ERROR | `Swiper name="nested_swiper"` nested inside `Group name="level3"` — Swiper must be a direct child of a root tag |
| 13 | SEM-NEST-001 | 25 | ERROR | `Button name="bad_swiper_child"` inside `Swiper` — Button.allowedParents does not include Swiper |
| 14 | SEM-NEST-001 | 24 | ERROR | `Image name="slide1"` inside `Swiper` — Image.allowedParents does not include Swiper |
| 15 | SEM-TRIG-001 | 25 | ERROR | `Trigger action="slide"` — action "slide" not in valid Trigger action set |
| 16 | SEM-NEST-001 | 33 | ERROR | `Layer name="bad_layer"` inside `Group name="level1"` — Layer.allowedParents is only ["MultiLayer"] |

### Valid Elements (no violations expected)
- `Var name="valid_var"` (line 3) — valid declaration
- `Var name="nested_var"` (line 6) — valid nested declaration
- `Text name="bottom_text"` (line 36) — valid Text with global var reference
- `Image name="level2_img"` (line 29) — valid Image with scaleType=fill

---

## Fixture 2: `type_inference_edge_cases.xml` (Lockscreen)

### Expected Diagnostics

| # | Rule ID | Approx Line | Severity | Description |
|---|---------|-------------|----------|-------------|
| 1 | SEM-TYPE-001 | 3 | ERROR | `Var name="num_or_str" type="number" expression="ifelse(#touch_x > 500, 100, 'string_branch')"` — ifelse branches return mixed types (number in then-branch, string in else-branch), type mismatch for number Var |
| 2 | SEM-TYPE-002 | 7 | ERROR | `Var name="bad_sin" expression="sin('not_a_number')"` — sin() expects number param but receives string literal |
| 3 | SEM-TYPE-002 | 8 | ERROR | `Var name="bad_substr" expression="substr(12345, 'two', 5)"` — substr() expects (string, number, number) but receives (number, string, number) |
| 4 | SEM-TYPE-001 | 9 | ERROR | `Var name="mixed_ifelse_type" type="number" expression="ifelse(1, 2.5, 'fallback')"` — ifelse returns number|string but Var type is number |
| 5 | SYN-EXPR-001 | 10 | ERROR | `Var name="neg_expr" expression="-#valid_num"` — numeric expression uses `-#var` syntax |
| 6 | SYN-EXPR-002 | 11 | ERROR | `Var name="high_prec" expression="0.123456789"` — 9 decimal digits exceeds 7-digit precision limit |
| 7 | SEM-TYPE-003 / SEM-TYPE-001 | 12 | ERROR | `Var name="no_type_expr" type="string" expression="#valid_num + 10"` — number expression assigned to string-typed Var |
| 8 | SEM-TYPE-001 | 14 | ERROR | `Image name="bg" x="#str_var"` — `x` expects number but `str_var` is type string |
| 9 | SEM-TYPE-001 | 15 | ERROR | `Image name="bg" y="#num_or_str"` — `y` expects number but `num_or_str` has ambiguous type from ifelse |
| 10 | SEM-TYPE-001 | 18 | ERROR | `Image name="bg" alpha="#bad_sin"` — `alpha` expects number but `bad_sin` has type error from sin() |
| 11 | SEM-REF-001 | 21 | ERROR | `Image name="icon" x="#not_declared"` — variable `not_declared` was never declared |
| 12 | SEM-REF-001 / SEM-ARR-001 | 21 | ERROR | `Image name="icon" y="#arr_var[7]"` — array `arr_var` size=5, index 7 is out of bounds |
| 13 | SYN-EXPR-001 | 24 | ERROR | `Group name="container" x="-#valid_num"` — numeric expression uses `-#var` syntax in attribute |

### Valid Elements (no violations expected)
- `Var name="valid_num"` (line 5) — valid number const
- `Var name="str_var"` (line 4) — valid string const
- `Var name="arr_var"` (line 6) — valid array declaration with size
- `Image name="valid_img"` (line 29) — valid Image with numeric variable references
- `Text name="info"` category="Normal" (line 26) — valid category enum

---

## Fixture 3: `constraint_edge_cases.xml` (Wallpaper)

### Expected Diagnostics

| # | Rule ID | Approx Line | Severity | Description |
|---|---------|-------------|----------|-------------|
| 1 | SEM-ATTR-001 | 9 | ERROR | `Image name="alpha_over" alpha="256"` — alpha 256 exceeds max 255 |
| 2 | SEM-ATTR-001 | 12 | ERROR | `Image name="alpha_neg" alpha="-1"` — alpha -1 is below min 0 |
| 3 | SEM-IMG-002 | 15 | ERROR | `Image name="both_sources"` — both `src="static.png"` AND `srcExp="@dynamic_src"` present |
| 4 | SEM-IMG-003 | 18 | WARNING | `Image name="bg_with_align" isBackground="true"` + `align="center"` — conflict, isBackground may not take effect |
| 5 | SEM-IMG-SRC | 27 | ERROR | `Image name="neither_src"` — missing both `src` and `srcExp` (at least one required) |
| 6 | SEM-ATTR-005 | 29 | ERROR | `Image name="bg_no_center_crop" isBackground="true"` + `scaleType="fill"` — isBackground requires scaleType=center_crop |

### Boundary (NO violation expected)
- `Image name="alpha_zero" alpha="0"` (line 3) — alpha=0 is the minimum valid boundary
- `Image name="alpha_max" alpha="255"` (line 6) — alpha=255 is the maximum valid boundary

### Valid Elements (no violations expected)
- `Image name="src_only_valid"` (line 21) — src only, no srcExp, valid
- `Image name="srcExp_only_valid"` (line 24) — srcExp only, no src, valid
- `Image name="valid_full"` (line 32) — fully valid Image

---

## Fixture 4: `variable_lifecycle_errors.xml` (Lockscreen)

### Expected Diagnostics

| # | Rule ID | Approx Line | Severity | Description |
|---|---------|-------------|----------|-------------|
| 1 | SEM-PERSIST-001 | 3 | ERROR | `Var name="hour" persist="true"` — persist on time variable `hour` is forbidden |
| 2 | SEM-PERSIST-001 | 4 | ERROR | `Var name="minute" globalPersist="true"` — globalPersist on time variable `minute` is forbidden |
| 3 | SEM-PERSIST-001 | 5 | ERROR | `Var name="ishour12" styleGlobalPersist="true"` — styleGlobalPersist on time variable `ishour12` is forbidden |
| 4 | SEM-PERSIST-001 | 6 | ERROR | `Var name="system.time.hour1" persist="true"` — persist on time variable matching `system.time.*` pattern is forbidden |
| 5 | SEM-REF-003 | 8–9 | ERROR | `Var name="dup_name"` declared twice at same scope — duplicate name definition |
| 6 | SEM-TYPE-001 / SEM-REF-001 | 11 | ERROR | `Var name="runtime_const" const="true" expression="#dup_name"` — const Var references non-const variable |
| 7 | SEM-TYPE-003 | 13 | ERROR | `Var name="type_mismatch" type="string" expression="100 + 50"` — number expression assigned to string-typed Var |
| 8 | SEM-VAR-004 | 15 | WARNING | `Var name="arr_no_size" type="number[]" expression="[1, 2, 3]"` — array type declared but no `size` attribute |
| 9 | SEM-VAR-003 | 17 | WARNING | `Var name="arr_with_values"` — both `values` and `size` present simultaneously |
| 10 | ~~SEM-REF-001~~ | ~~19~~ | ~~ERROR~~ | ~~`Image name="early_ref" x="#later_declared"` — variable `later_declared` referenced before its declaration~~ **[已移除：策略变更 — 不检测前向引用，变量统一按全局处理]** |
| 11 | SEM-REF-003 | 27 | ERROR | `Var name="dup_name"` inside `Group` — third occurrence of duplicate name `dup_name` (Bug 24: 待修复) |
| 12 | SEM-REF-001 / SEM-ARR-001 | 28 | ERROR | `Image name="inner" x="#arr_no_size[0]"` — array `arr_no_size` has no valid values/size defined |

### Valid Elements (no violations expected)
- `Var name="later_declared"` (line 22) — valid const number Var (前向引用不再报告)
- `Image name="valid_ref"` (line 31) — valid reference to already-declared variable
- `Var name="system.time.ampm"` (line 24) — may or may not trigger name conflict with global var (depends on analyzer strictness)

---

## Fixture 5: `trigger_command_combos.xml` (Lockscreen)

### Expected Diagnostics

| # | Rule ID | Approx Line | Severity | Description |
|---|---------|-------------|----------|-------------|
| 1 | SEM-TRIG-002 | 3 | ERROR | `Button name="no_trigger_btn"` — Button is missing a Trigger child element |
| 2 | SEM-TRIG-001 | 5 | ERROR | `Trigger action="swipe_left"` inside `bad_action_btn` — "swipe_left" not in valid Trigger action set |
| 3 | SEM-TRIG-001 | 15 | ERROR | `Trigger action="invalid_action2"` inside `Triggers` — "invalid_action2" not in valid action set |
| 4 | SEM-CMD-001 | 18 | ERROR | `VideoCommand` inside Trigger — `play="1"` and `sound="1"` are mutually exclusive |
| 5 | SEM-CMD-004 | 21 | ERROR | `StyleCommand index="#runtime_var"` — index attribute uses expression, but StyleCommand index must be a plain number |
| 6 | SEM-TRIG-003 | 26 | ERROR | `Triggers` container inside `empty_triggers_btn` — Triggers has no Trigger child elements |

### Valid Elements (no violations expected)
- `Button name="mixed_actions_btn"` with `Triggers` wrapper (lines 10–24) — Triggers container structure is valid
- `Trigger action="down"` with VariableCommand (line 13) — valid action + command combo
- `Trigger action="up"` (line 17) — valid action
- `Trigger action="click"` (line 20) — valid action
- `Trigger action="double"` with GroupCommand (line 31) — GroupCommand with method="perform" is valid
- `Image name="valid_bg"` (line 37) — valid image

---

## Fixture 6: `scope_nesting_boundaries.xml` (Widget)

### Expected Diagnostics

| # | Rule ID | Approx Line | Severity | Description |
|---|---------|-------------|----------|-------------|
| 1 | SEM-NEST-001 | 3 | ERROR | `Layer name="bad_layer"` directly under `Widget` — Layer.allowedParents is only ["MultiLayer"], Widget is not an allowed parent |
| 2 | SEM-SCOPE-001 | 3 | ERROR | `Layer name="bad_layer"` in Widget scope — Layer.scope.Widget = false, Layer is Lockscreen-only |
| 3 | SEM-SCOPE-001 | 5 | ERROR | `SourceImage name="src_img"` in Widget scope — SourceImage.scope.Widget = false, Lockscreen-only element |
| 4 | SEM-SCOPE-001 | 10 | ERROR | `StereoView name="stereo"` in Widget scope — StereoView.scope.Widget = false, Lockscreen-only element |
| 5 | SEM-3D-STEREO-001 | 11 | ERROR | `StereoView name="stereo"` has only 3 StereoGroup children (needs 3–10, but check actual count; 3 is the minimum boundary — if boundary-inclusive, 3 is OK). May not trigger if 3 is valid. |
| 6 | SEM-REQ-001 | 11–14 | ERROR | `StereoView` children lack required attributes — `StereoGroup` elements missing required attrs (depends on StereoGroup rules) |

### Valid Elements (no violations expected)
- `Var name="widget_var"` (line 16) — valid Var in Widget scope (Var.scope.Widget = true)
- `Group name="valid_group"` (line 18) — valid Group in Widget (Group.scope.Widget = true)
- `Image name="valid_img"` inside `valid_group` (line 19) — valid nested Image (Image.scope.Widget = true, parent Group is allowed)
- `Text name="valid_text"` inside `valid_group` (line 21) — valid nested Text
- `Button name="valid_btn"` inside `valid_group` (line 23) — valid Button in Widget (Button.scope.Widget = true)
- `Trigger action="click"` inside `valid_btn` (line 24) — valid Trigger with valid action
- `Image name="valid_icon"` (line 28) — valid standalone Image in Widget
- `Swiper name="widget_swiper"` (line 10) — Swiper.scope.Widget = true; however Image inside Swiper may trigger SEM-NEST-001 (depends on analyzer's parent-child check for Swiper children)

---

## Fixture 7: `expression_syntax_errors.xml` (Lockscreen)

### Expected Diagnostics

| # | Rule ID | Approx Line | Severity | Description |
|---|---------|-------------|----------|-------------|
| 1 | SYN-EXPR-001 | 4 | ERROR | `Var name="neg_syntax" expression="-#base_val"` — numeric expression using `-#var` syntax |
| 2 | SYN-EXPR-002 | 5 | ERROR | `Var name="high_precision" expression="0.12345678910"` — 11 decimal digits exceeds 7-digit precision limit |
| 3 | SYN-EXPR-004 / SYN-EXPR-ANTLR | 6 | ERROR | `Var name="unclosed_quote" expression="'hello world"` — unclosed single quote in string expression |
| 4 | SYN-EXPR-ANTLR / SEM-REF-001 | 7 | ERROR | `Var name="invalid_func" expression="bogusFunc(1, 2)"` — unknown function name `bogusFunc` |
| 5 | SEM-TYPE-002 | 8 | ERROR | `Var name="too_many_args" expression="sin(1, 2, 3, 4, 5)"` — sin() expects 1 argument but received 5 |
| 6 | SYN-EXPR-ANTLR | 9 | ERROR | `Var name="bad_ifelse_syntax" expression="ifelse(#base_val, 1,)"` — trailing comma in ifelse (ANTLR parse error) |
| 7 | SYN-EXPR-001 | 11 | ERROR | `Image name="neg_x" x="-#base_val"` — `-#var` syntax in numeric attribute |
| 8 | SYN-EXPR-001 | 14 | ERROR | `Group name="mid"` → `Image name="inner_neg" x="-#base_val"` — `-#var` syntax in nested context |
| 9 | SYN-EXPR-002 | 18 | ERROR | `Image name="precise_x" x="0.123456789"` — 9 decimal digits in x attribute exceeds precision limit |
| 10 | SYN-EXPR-005 | 21 | ERROR | `Text name="bad_str_expr" textExp="'Result: '+ #base_val"` — numeric expression `#base_val` embedded in string without `{}` braces |
| 11 | SYN-EXPR-004 | 24 | ERROR | `Text name="bad_str_quote" textExp="'Nested 'inner' quote'"` — nested single quotes in string expression without escaping |

### Valid Elements (no violations expected)
- `Var name="base_val"` (line 3) — valid const number Var
- `Text name="complex_valid"` (line 26) — valid complex string expression with chained ifelse
- `Text name="argb_text"` (line 31) — valid hex color `color="#FF800000"`
- `Var name="chained_valid"` (line 34) — valid chained expression `ifelse(..., sin(), cos())`
- `Image name="valid_img"` (line 37) — valid Image with literal alpha

---

## Fixture 8: `enum_boundary_tests.xml` (ChargingSkin)

### Expected Diagnostics

| # | Rule ID | Approx Line | Severity | Description |
|---|---------|-------------|----------|-------------|
| 1 | SEM-ENUM-001 | 12 | ERROR | `Image name="invalid_scale" scaleType="INVALID"` — scaleType not in [fill, center_crop, hold_center_crop] |
| 2 | SEM-ENUM-001 | 23 | ERROR | `Text name="txt_invalid_cat" category="INVALID_CAT"` — category not in [Normal, Charging, BatteryLow, BatteryFull] |
| 3 | SEM-ENUM-001 | 32 | ERROR | `Image name="bad_bool_img" enableMove="INVALID_BOOL"` — enableMove must be "true" or "false" |
| 4 | SEM-SCOPE-001 | 35 | ERROR | `Button name="btn_valid"` in ChargingSkin — Button.scope.ChargingSkin = false; Button only supported in Lockscreen and Widget |
| 5 | SEM-TRIG-002 | 35–37 | ERROR | `Button name="btn_valid"` → has a Trigger child, so TRIG-002 may NOT fire here. But the Button itself triggers SEM-SCOPE-001 (not allowed in ChargingSkin). |
| 6 | SEM-SCOPE-001 | 40 | ERROR | `Button name="btn_invalid_bool"` in ChargingSkin — same scope violation |
| 7 | SEM-ENUM-001 | 40 | ERROR | `Button name="btn_invalid_bool" enableMove="NOT_A_BOOL"` — enableMove not "true" or "false" |
| 8 | SEM-SCOPE-001 | 46 | ERROR | `Button name="btn_no_category"` in ChargingSkin — same scope violation |
| 9 | SEM-ENUM-001 | 46 | ERROR | `Button name="btn_no_category" category="NONEXISTENT_CAT"` — category not in valid set |

### Boundary (NO violation expected)
- `Image name="fill_valid" scaleType="fill"` (line 3) — valid scaleType enum value
- `Image name="center_crop_valid" scaleType="center_crop"` (line 6) — valid scaleType enum value
- `Image name="hold_center_crop_valid" scaleType="hold_center_crop"` (line 9) — valid scaleType enum value
- `DateTime name="dt_valid" format="HH:mm"` (line 14) — valid DateTime with standard format
- `DateTime name="dt_date" format="yyyy-MM-dd"` (line 16) — valid DateTime with date format
- `Text name="txt_normal" category="Normal"` (line 19) — valid category
- `Text name="txt_charging" category="Charging"` (line 23) — valid category
- `Group name="container"` (line 27) — valid Group with valid nested elements
- `Group name="container"` → `Image name="group_img" enableMove="true"` (line 28) — valid enableMove
- `Group name="container"` → `Text name="group_txt" category="BatteryFull"` (line 33) — valid category
- `Var name="charge_var"` (line 50) — valid Var in ChargingSkin scope
- `Image name="valid_power"` (line 48) — valid Image with valid category

---

## Summary of Rules Tested

### Constraint-based rules (from element JSON `constraints`)
- SEM-ATTR-001: alpha out of range [0,255]
- SEM-ATTR-005: isBackground requires scaleType=center_crop
- SEM-IMG-SRC: Image missing both src and srcExp
- SEM-IMG-002: Image has both src and srcExp
- SEM-IMG-003: isBackground + align conflict (WARNING)
- SEM-PERSIST-001: persist on time/date variables
- SEM-VAR-003: Var values + size simultaneously (WARNING)
- SEM-TRIG-001: Trigger action not in valid set
- SEM-TRIG-002: Button without Trigger child
- SEM-TRIG-003: Triggers without Trigger child
- SEM-CMD-001: VideoCommand play + sound mutual exclusion
- SEM-CMD-004: StyleCommand index using expression
- SEM-SWIPER-001: Swiper not direct child of root tag
- SEM-3D-STEREO-001: StereoView child count validation

### Analyzer-based rules
- SEM-NEST-001: Tag violates parent-child allowedParents constraint
- SEM-SCOPE-001: Element not supported in current scope
- SEM-ENUM-001: Invalid enum value
- SEM-TYPE-001: Expression type mismatch with attribute/Var type
- SEM-TYPE-002: Function parameter type mismatch
- SEM-TYPE-003: Literal value type error
- SEM-REF-001: Undefined variable reference
- SEM-REF-003: Duplicate name definition
- SEM-ARR-001: Array index out of bounds
- SEM-VAR-004: Array variable missing size declaration

### Syntax rules
- SYN-EXPR-001: Numeric expression using `-#var` syntax
- SYN-EXPR-002: Expression exceeds 7-digit precision limit
- SYN-EXPR-004: Unclosed/malformed single-quoted string
- SYN-EXPR-005: String expression numeric calc missing `{}` braces
- SYN-EXPR-ANTLR: ANTLR parse errors (bad syntax, unknown functions)
