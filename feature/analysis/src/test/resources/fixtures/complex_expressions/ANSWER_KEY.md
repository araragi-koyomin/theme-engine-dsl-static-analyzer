# Complex Expressions Fixture Answer Key

## Fixture 1: `chained_function_hell.xml` (Lockscreen)

**Purpose:** Tests deeply nested ifelse chains, chained function calls with type mismatches across layers, and mixed numeric/string types in complex expressions.

### Expected Diagnostics

| # | Rule ID | Approx Line | Severity | Description |
|---|---------|-------------|----------|-------------|
| 1 | SEM-TYPE-001 | 5 | ERROR | `Var name="type_mix_deep" type="string"` — inner `ifelse(#darkMode == 2, 'dark_mode', 123)` returns `string\|number` because else-branch is numeric literal `123`, which cannot unify to `string` for the outer ifelse in a string-typed Var |
| 2 | SEM-TYPE-002 | 7 | ERROR | `Var name="chained_bad_type" type="number"` — `sin(substr('hello', 0, 3))` passes a `string` (result of `substr`) as the argument to `sin()`, which expects `number` |
| 3 | SEM-TYPE-003 | 13 | ERROR | `Var name="bad_arithmetic" type="number"` — expression `#screen_width + 'hello'` contains string literal `'hello'` in a numeric expression context |
| 4 | SEM-TYPE-001 | 19 | ERROR | `Image name="bad_expr_img" x="ifelse(#touch_x > 500, 'left', 100)"` — `x` expects `number` but then-branch of ifelse returns string literal `'left'` |

### Valid Elements (no violations expected)
- `Var name="deep_ifelse"` (line 3) — all ifelse branches return numbers, type is consistent
- `Var name="complex_math"` (line 9) — valid chained trig functions with arithmetic, all numeric
- `Var name="ultra_deep_ifelse"` (line 11) — 4-level nested ifelse, all branches return numbers
- `Var name="boundary_calc"` (line 15) — boundary value ifelse chains, all numeric
- `Image name="deep_expr_img"` (line 17) — deeply nested numeric functions (max, min, sqrt, abs) in x attribute
- `Text name="complex_text"` (line 21) — valid string expression with properly braced `{#hour24}` and `{#minute}`

---

## Fixture 2: `string_expression_errors.xml` (Lockscreen)

**Purpose:** Tests every string expression syntax error rule: missing quotes, unclosed quotes, missing braces around embedded numeric expressions, `preciseeval`-with-operator, ANTLR parse failures, and wrong function-in-context.

### Expected Diagnostics

| # | Rule ID | Approx Line | Severity | Description |
|---|---------|-------------|----------|-------------|
| 1 | SYN-EXPR-004 | 5 | ERROR | `Var name="no_quote_string" type="string" expression="hello world"` — bare words without enclosing single quotes in a string expression |
| 2 | SYN-EXPR-004 | 7 | ERROR | `Var name="unclosed_quote" type="string" expression="'unclosed string"` — opening single quote has no closing quote |
| 3 | SYN-EXPR-005 | 9 | ERROR | `Var name="no_brace_num" type="string" expression="'Value: ' + #battery_level + '%'"` — numeric variable `#battery_level` embedded in string expression without `{}` braces |
| 4 | SYN-EXPR-003 | 15 | ERROR | `Var name="string_num_calc" type="string" expression="#battery_level + 10 + '%'"` — string expression starts with a numeric calculation beginning with `#` |
| 5 | SYN-EXPR-006 | 17 | ERROR | `Var name="bad_preciseeval" type="string" expression="preciseeval('3.14159', 2) + ' is pi'"` — `preciseeval(...)` followed by `+` operator |
| 6 | SEM-TYPE-001 | 19 | ERROR | `Var name="string_func_in_num" type="number" expression="substr('hello', 0, 3)"` — `substr()` returns `string` but Var type is `number` |
| 7 | SYN-EXPR-ANTLR | 23 | ERROR | `Var name="garbage_expr" type="number" expression="!@#$%^&*()"` — completely malformed expression that ANTLR lexer/parser cannot parse |
| 8 | None / non-expr | 29 | WARNING | `Text name="bad_format" format="'%d%%'"` — `format` attribute on `Text` may not support expressions; the single-quoted value is treated as an expression string where it shouldn't be. May not trigger expression diagnostic if `format` doesn't have `supportsExpression=true`. |

### Valid Elements (no violations expected)
- `Var name="msg_prefix"` (line 3) — valid simple string expression `'Hello'`
- `Var name="with_brace_num"` (line 11) — `#battery_level` properly wrapped in `{}` braces
- `Var name="multi_embed"` (line 13) — multiple braced numeric expressions including `int()` call, all valid
- `Var name="num_func_ok"` (line 21) — valid numeric functions (`sin`, `cos`) with arithmetic in number-typed Var
- `Text name="complex_text"` (lines 25–26) — valid string with ifelse and properly braced expressions, all valid

---

## Fixture 3: `precision_boundary_tests.xml` (Wallpaper)

**Purpose:** Tests SYN-EXPR-002 numeric precision boundaries at exactly 7 digits vs 8+ digits, including integer literals, decimal literals, compound expressions whose sub-literals exceed the limit, and expressions whose computed result conceptually exceeds the limit.

### Expected Diagnostics

| # | Rule ID | Approx Line | Severity | Description |
|---|---------|-------------|----------|-------------|
| 1 | SYN-EXPR-002 | 5 | WARNING | `Var name="bad_8digit" expression="12345678"` — 8-digit integer literal exceeds 7-digit precision limit |
| 2 | SYN-EXPR-002 | 6 | WARNING | `Var name="bad_8digit_expr" expression="99999999 + 1"` — contains 8-digit literal `99999999` |
| 3 | SYN-EXPR-002 | 8 | WARNING | `Var name="bad_result_8digit" expression="5000000 + 5000000"` — each operand is 7 digits, if the analyzer evaluates compile-time constants the result `10000000` is 8 digits |
| 4 | SYN-EXPR-002 | 9 | WARNING | `Var name="border_8digit" expression="10000000"` — exactly 8 digits, exceeds 7-digit boundary |
| 5 | SYN-EXPR-002 | 10 | WARNING | `Var name="decimal_7digit" expression="1.1234567"` — total 8 significant digits (`1` + `1234567`), exceeds 7-digit limit. Note: if the analyzer only counts fractional digits (7 here), this may NOT trigger. |
| 6 | SYN-EXPR-002 | 11 | WARNING | `Var name="decimal_8digit" expression="1.12345678"` — 9 significant digits, unambiguously exceeds limit |

### Boundary Cases (may or may not trigger)
- `Var name="edge_7digit" expression="9999999"` (line 4) — exactly 7 digits, should NOT trigger
- `Var name="valid_7digit_expr" expression="9999999 - 1"` (line 7) — contains 7-digit literal, should NOT trigger

### Valid Elements (no violations expected)
- `Var name="valid_7digit"` (line 3) — exactly 7 digits, at boundary
- `Image name="test"` (line 13) — literal `0` values, no precision issues

---

## Fixture 4: `array_index_edge_cases.xml` (Lockscreen)

**Purpose:** Tests element property references (`#elem.prop`), system property references (`#system.time.*`), deeply chained references, forward and undefined element property access, and array-like arithmetic with multiple indexed values.

### Expected Diagnostics

| # | Rule ID | Approx Line | Severity | Description |
|---|---------|-------------|----------|-------------|
| 1 | SEM-REF-001 | 15 | ERROR | `Var name="bad_elem_prop" expression="#ghost_img.actual_w * 2"` — element `ghost_img` is never declared in this file |

### Valid Elements (no violations expected)
- `Var name="arr_val_0"`, `arr_val_1`, `arr_val_2` (lines 3–5) — valid const number declarations
- `Var name="arr_access"` (line 7) — valid reference to declared var with arithmetic
- `Var name="result_nested"` (line 9) — valid arithmetic with multiple declared var references
- `Image name="test_img"` (line 11) — valid element declaration, provides target for `.actual_w` reference
- `Var name="elem_prop"` (line 13) — valid reference to `#test_img.actual_w` (declared Image element's property)
- `Var name="sys_prop"` (line 17) — valid system property reference `#system.time.hour1` and `#system.time.min1`
- `Var name="deep_ref"` (line 19) — valid deeply chained reference combining `#touch_x`, `#test_img.actual_x`, `#test_img.actual_w`, `#screen_width`

---

## Fixture 5: `operator_precedence_tests.xml` (Lockscreen)

**Purpose:** Tests arithmetic operator precedence, the `-#var` banned syntax (SYN-EXPR-001) in various positions, modulo operator usage, deeply nested parentheses, and the `0 - #var` valid negation workaround.

### Expected Diagnostics

| # | Rule ID | Approx Line | Severity | Description |
|---|---------|-------------|----------|-------------|
| 1 | SYN-EXPR-001 | 10 | ERROR | `Var name="prec5" expression="-#base * #base"` — expression starts with `-#var` syntax |
| 2 | SYN-EXPR-001 | 13 | ERROR | `Var name="prec7" expression="-#base"` — lone `-#var` syntax as entire expression |

### Valid Elements (no violations expected)
- `Var name="base"` (line 3) — valid const number declaration
- `Var name="prec1"` through `prec4` (lines 5–8) — valid precedence demonstrations with proper parentheses
- `Var name="prec6"` (line 11) — uses `0 - #base` instead of `-#base`, which is the valid negation workaround
- `Var name="prec8"` (line 14) — valid `0 - #base` pattern
- `Var name="mod1"` and `mod2` (lines 17–18) — valid modulo operations
- `Var name="deep_parens"` (line 20) — valid 4-level nested parentheses
- `Image name="test"` (line 22) — valid image with literal values

---

## Fixture 6: `multi_element_expression_blast.xml` (Lockscreen)

**Purpose:** Ultimate stress test combining every expression error rule across many element types (Var, Image, Text, Group, Button, Trigger, VariableCommand). Tests forward references, `-#var` in attributes, precision violations, alpha range violations, undefined element properties, string expression embedding errors, `preciseeval`-with-operator, mixed type ifelse, function parameter type errors, and read-through type violations.

### Expected Diagnostics

| # | Rule ID | Approx Line | Severity | Description |
|---|---------|-------------|----------|-------------|
| 1 | SEM-REF-001 | 9 | ERROR | `Var name="ref_forward" expression="#late_var * 2"` — references `late_var` which is declared later at line 57. Forward reference to variable not yet declared at point of use. |
| 2 | SEM-TYPE-001 | 10 | ERROR | `Var name="implicit_num" type="number" expression="#str_value + 10"` — `str_value` is type `string` (declared at line 7), referenced with `#` prefix which attempts numeric access on a string-typed variable |
| 3 | SYN-EXPR-001 | 16 | ERROR | `Image name="neg_x_img" x="-#multiplier"` — `-#var` syntax in numeric attribute |
| 4 | SYN-EXPR-005 | 28 | ERROR | `Text name="bad_nested_text" textExp="'Value: ' + #multiplier + ' is the answer'"` — `#multiplier` embedded in string expression without `{}` braces |
| 5 | SYN-EXPR-002 | 39 | WARNING | `Image name="ultra_precise" x="3.1415926535"` — 11 significant digits, exceeds 7-digit precision limit |
| 6 | SEM-TYPE-003 | 42 | ERROR | `Image name="weird_alpha" alpha="#multiplier + 'not_num'"` — string literal `'not_num'` used in numeric attribute `alpha` |
| 7 | SEM-ATTR-001 | 46 | ERROR | `Image name="bad_alpha_over" alpha="300"` — alpha value 300 exceeds maximum 255 |
| 8 | SEM-ATTR-001 | 50 | ERROR | `Image name="bad_alpha_neg" alpha="-10"` — alpha value -10 is below minimum 0 |
| 9 | SEM-REF-001 | 55 | ERROR | `Var name="arr_bad_ref" expression="#typed_arr[0] * #undefined_global"` — variable `undefined_global` was never declared |
| 10 | SEM-TYPE-001 | 62 | ERROR | `Text name="big_mixed_text" textExp="substr(#color_dark, 2, 6)"` — `color_dark` is type `string` but referenced with `#` prefix, which attempts numeric access. Either SEM-TYPE-001 or the `substr` call is valid because `#color_dark` auto-converts. Use of `#` instead of `@` for a string var in string context may trigger type mismatch. |
| 11 | SYN-EXPR-005 | 66 | ERROR | `Text name="embed_err_text" textExp="'H:' + #hour24 + ' M:' + #minute"` — both `#hour24` and `#minute` embedded without `{}` braces in string expression |
| 12 | SEM-REF-001 | 70 | ERROR | `Image name="propref_bad" x="#ghost_elem.actual_x"` — element `ghost_elem` never declared |
| 13 | SYN-EXPR-006 | 73 | ERROR | `Var name="bad_preciseeval" type="string" expression="preciseeval('3.14159', 2) + ' is pi'"` — `preciseeval(...)` followed by `+` operator |
| 14 | SYN-EXPR-001 | 75 | ERROR | `Var name="bad_neg_expr" type="number" expression="-#multiplier + 10"` — `-#var` syntax at start of expression |
| 15 | SYN-EXPR-001 | 77 | ERROR | `Image name="neg_in_attr" x="-#multiplier * 3"` — `-#var` syntax in numeric attribute |
| 16 | SEM-TYPE-001 | 80 | ERROR | `Text name="type_mix_text" textExp="ifelse(#darkMode == 1, sin(0.5), 'hello')"` — ifelse branches return `number` (from sin) and `string` (literal 'hello'), type cannot unify for `textExp` which expects `string` |
| 17 | SEM-TYPE-001 | 83 | ERROR | `Var name="mixed_type_ifelse" type="number" expression="ifelse(#battery_level > 50, sin(0.5), 'fallback')"` — ifelse branches return `number` and `string`, cannot unify to `number` for number-typed Var |

### Valid Elements (no violations expected)
- `Var name="multiplier"` (line 3) — valid const number
- `Var name="color_dark"`, `color_light` (lines 4–5) — valid const string
- `Var name="x_offset"`, `str_value` (lines 6–7) — valid const declarations
- `Var name="num_array"` (line 8) — valid array declaration with size
- `Image name="bg"` (lines 12–14) — valid Image with ifelse alpha expression, all numeric
- `Group name="container"` (line 19) — valid Group container
- `Image name="nested_img"` (lines 20–22) — valid deeply nested ifelse with max/min boundary expressions
- `Text name="nested_text"` (lines 24–26) — valid string with properly braced embedded numeric expressions
- `Button name="action_btn"` with Trigger and VariableCommand (lines 31–35) — valid Button with valid expression in command
- `Var name="typed_arr"` (line 54) — valid array declaration
- `Var name="late_var"` (line 57) — valid const number, resolves forward reference from line 9
- `Image name="refs_forward"` (lines 59–60) — references `#ref_forward` which is a declared Var

### Ambiguous / Will NOT Trigger Expression Diagnostics
- `Text name="bad_format"` (line 29 in fixture 2) — `format="'%d%%'"` may not trigger any expression diagnostic if `format` attribute lacks `supportsExpression=true`; the single-quoted value might be treated as a plain attribute value.

---

## Summary of Rules Tested

### Expression Syntax Rules
| Rule ID | Fixtures | Description |
|---------|----------|-------------|
| SYN-EXPR-001 | 5, 6 | `-#var` banned negation syntax |
| SYN-EXPR-002 | 3, 6 | Numeric precision > 7 digits |
| SYN-EXPR-003 | 2 | String expr starts with numeric `#var + ...` |
| SYN-EXPR-004 | 2 | Missing or unclosed single quotes in string expr |
| SYN-EXPR-005 | 2, 6 | Numeric variable `#var` without `{}` braces in string expr |
| SYN-EXPR-006 | 2, 6 | `preciseeval(...)` followed by `+` operator |
| SYN-EXPR-ANTLR | 2 | ANTLR lexer/parser failure on malformed expression |

### Semantic Type Rules
| Rule ID | Fixtures | Description |
|---------|----------|-------------|
| SEM-TYPE-001 | 1, 2, 6 | Expression type mismatch with expected attribute/Var type |
| SEM-TYPE-002 | 1 | Function parameter type mismatch |
| SEM-TYPE-003 | 1, 6 | Literal value type error (e.g. string in number context) |

### Other Semantic Rules
| Rule ID | Fixtures | Description |
|---------|----------|-------------|
| SEM-ATTR-001 | 6 | Attribute value out of range (alpha [0,255]) |
| SEM-REF-001 | 4, 6 | Undefined variable or element reference |

### Expected Error/Warning Counts Per File

| Fixture | ERRORS | WARNINGS | INFO |
|---------|--------|----------|------|
| `chained_function_hell.xml` | 4 | 0 | 0 |
| `string_expression_errors.xml` | 7 | 0 | 0 |
| `precision_boundary_tests.xml` | 0 | 5–6 | 0 |
| `array_index_edge_cases.xml` | 1 | 0 | 0 |
| `operator_precedence_tests.xml` | 2 | 0 | 0 |
| `multi_element_expression_blast.xml` | 16 | 1 | 0 |

**Grand Total:** ~30 errors, ~6–7 warnings, 0 info
