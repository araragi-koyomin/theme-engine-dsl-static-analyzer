# 详细设计文档 (DDD)：Bug 14-27 修复

> **版本**: v1.0  
> **日期**: 2026-07-13  
> **分支**: `fix/bugfix-14-27`  
> **前置文档**: [概要设计文档-bugfix-14-27.md](概要设计文档-bugfix-14-27.md)

---

## 目录

- [Bug 14: SEM-PERSIST-001 不检测 expression 引用的时间变量](#bug-14)
- [Bug 15: SEM-TYPE-001 类型传播链在 #var 引用处断裂](#bug-15)
- [Bug 16: SEM-TYPE-001 string Var 以 # 引用在 textExp 中不检出类型不匹配](#bug-16)
- [Bug 18: SEM-TYPE-003 被归类为 SYN-EXPR-ANTLR](#bug-18)
- [Bug 19: SYN-EXPR-004 引号检查结果被归类为 SYN-EXPR-ANTLR](#bug-19)
- [Bug 20: SEM-TYPE-003 被归类为 SEM-TYPE-001](#bug-20)
- [Bug 21: SYN-EXPR-002 计算结果精度溢出不检出（非问题）](#bug-21)
- [Bug 22: bogusFunc 未知函数被归类为 SEM-TYPE-001 而非 SEM-REF-001](#bug-22)
- [Bug 23: SEM-REF-001 前向引用（非问题 — 策略变更）](#bug-23)
- [Bug 24: SEM-REF-003 第三层级重复变量名不检出](#bug-24)
- [Bug 25: SEM-REF-001→SEM-REF-002 元素属性引用归类偏差](#bug-25)
- [Bug 26: SEM-CMD-004 被归类为 SEM-TYPE-003](#bug-26)
- [Bug 27: SEM-TYPE-002→SEM-TYPE-001 链式函数参数类型归类偏差](#bug-27)

---

<a id="bug-14"></a>
## Bug 14: SEM-PERSIST-001 不检测 expression 引用的时间变量

### 问题描述

Var 使用 `persist="true"` 且 expression 中引用时间变量 `#hour` 时，SEM-PERSIST-001 不触发。因为约束仅通过变量 `name` 属性 MATCHES 正则来检测时间变量，当 name 本身不匹配正则（如 "time_persist"），即使 expression 引用了 `#hour` 等时间变量，约束仍不触发。

### 根因分析

**当前约束条件**（`Var.json:148`）：
```
(persist='true' OR globalPersist='true' OR styleGlobalPersist='true')
AND name MATCHES '(hour|hour12|hour24|minute|year|month|date|day_of_week|lunarYear|lunarMonth|lunarDay|ishour12|system.time.*)'
```

- `MATCHES` 运算符在 `DefaultRuleDslEvaluator.visitCompareExpr()` 中使用 `Pattern.compile(right).matcher(left).matches()` 实现全字符串匹配
- `element.attrs['expression']` 可获取 expression 属性的原始字符串值（如 `#hour`）
- 但当前条件未检查 expression 属性内容

### 修复方案

**修改文件**: `feature/analysis/src/main/resources/rules/elements/variable/Var.json`

**修改约束条件**，在 MATCHES 条件中增加对 expression 属性的检查：

```
(persist='true' OR globalPersist='true' OR styleGlobalPersist='true')
AND (
  name MATCHES '(hour|hour12|hour24|minute|year|month|date|day_of_week|lunarYear|lunarMonth|lunarDay|ishour12|system.time.*)'
  OR expression MATCHES '.*#(hour|hour12|hour24|minute|year|month|date|day_of_week|lunarYear|lunarMonth|lunarDay|ishour12|system.time.[a-zA-Z0-9_]+)([^a-zA-Z0-9_].*|)'
)
```

**正则设计说明**：
- `.*#` — 匹配 `#` 前的任意字符（如 `sin(` 等）
- `(hour|hour12|...)` — 时间变量名
- `system.time.[a-zA-Z0-9_]+` — 匹配 `system.time.hour1` 等（`.` 为 regex any-char，匹配字面 `.`）
- `([^a-zA-Z0-9_].*|)` — 变量名后必须是非标识符字符或字符串结束，防止 `#hourly` 误匹配 `#hour`
- `.matches()` 要求全字符串匹配，`.*` 前缀和 `([^a-zA-Z0-9_].*|)` 后缀确保这一点

**匹配验证**：
| 表达式 | 是否匹配 | 正确？ |
|--------|----------|--------|
| `#hour` | ✓ | 是（时间变量） |
| `sin(#hour)` | ✓ | 是（引用时间变量） |
| `#hour + 1` | ✓ | 是（引用时间变量） |
| `#system.time.hour1` | ✓ | 是（时间变量） |
| `#hourly_rate` | ✗ | 是（非时间变量） |
| `#score` | ✗ | 是（非时间变量） |

### 验证方法

```bash
java -jar feature/analysis/build/cli/dsl-analyzer.jar --format json --no-color \
  feature/analysis/src/test/resources/fixtures/complex/deep_nesting_violations.xml
```

期望输出包含：`SEM-PERSIST-001` at line 9。

---

<a id="bug-15"></a>
## Bug 15: SEM-TYPE-001 类型传播链在 #var 引用处断裂

### 问题描述

变量 `bad_sin` 的 expression 有类型错误（`sin('not_a_number')`），但 `alpha="#bad_sin"` 不报告 SEM-TYPE-001。类型错误应沿 #var 引用链传播到属性赋值点。

### 根因分析

**代码路径**: `TypeAnalyzer.java`

1. `checkAttribute()` 在 line 107 调用 `checkRefVarExpressionErrors()`
2. `checkRefVarExpressionErrors()` 对 `#` 前缀的 VARIABLE_REF 调用 `checkSingleVarExprError()`（line 450）
3. `checkSingleVarExprError()` 使用内部 `inferExpressionType()` 推断 Var 表达式类型
4. `inferExpressionType()` 对 FUNCTION_CALL（非 ifelse）返回 `null`（line 338-342）
5. `declExprType == null` → 不满足 line 472 的类型不匹配条件
6. `hasIfelseMixedBranches()` 对非 ifelse 表达式返回 false（line 406-407）
7. 最终不报告任何诊断

**核心问题**：`inferExpressionType()` 不处理普通函数调用，无法检测 `sin('not_a_number')` 中 sin 参数类型错误。

### 修复方案

**修改文件**: `TypeAnalyzer.java`

在 `checkSingleVarExprError()` 方法中，增加对 Var 表达式函数调用错误的检查：

```java
// 在现有 declExprType 检查之后，增加函数调用错误传播检查
// 如果 Var 的 expression 包含函数调用错误（参数类型不匹配、未知函数等），
// 则在引用处报告 SEM-TYPE-001

List<Diagnostic> tempDiagnostics = new ArrayList<>();
TypeInferenceEngine engine = new TypeInferenceEngine(functionLibrary);
checkFunctionCalls(declExpr, varDeclaredType, engine, context, decl.getAstNode(), tempDiagnostics);
if (!tempDiagnostics.isEmpty()) {
    diagnostics.add(buildVarRefTypeErrorDiagnostic(ref, elementNode, decl, context));
    return;
}
```

**设计说明**：
- 使用临时 diagnostics 列表收集 Var 表达式的函数调用错误
- 如果有错误，在引用处报告 SEM-TYPE-001（"变量 #varName 的表达式存在类型错误"）
- 不重复报告 Var 声明处已有的 SEM-TYPE-002（那是在 `checkVarExpressionBody()` 中报告的）
- 使用 `decl.getAstNode()` 作为 host element，确保诊断定位正确

### 验证方法

```bash
java -jar feature/analysis/build/cli/dsl-analyzer.jar --format json --no-color \
  feature/analysis/src/test/resources/fixtures/complex/type_inference_edge_cases.xml
```

期望输出包含：`SEM-TYPE-001` at line 18（alpha="#bad_sin"）。

---

<a id="bug-16"></a>
## Bug 16: SEM-TYPE-001 string Var 以 # 引用在 textExp 中不检出类型不匹配

### 问题描述

Var `type="string"` 但用 `#` 前缀（数值访问方式）引用 string 变量 `color_dark` 时，类型不匹配不检出。`#` 是数值访问前缀，期望 number 类型但实际引用的变量是 string 类型。

### 根因分析

**代码路径**: `TypeAnalyzer.java` → `checkAttribute()` → `engine.inferType()`

1. `TypeInferenceEngine.inferVariableRef()` 对 `#` 前缀返回 `decl.getType()`（line 62-63）
2. `color_dark` 的声明类型是 `string` → 返回 `DslStringType`
3. `textExp` 的期望类型是 `DslStringType`
4. `typeEquals(DslStringType, DslStringType)` 为 true → 不报告类型不匹配

**核心问题**：`#` 前缀表示数值访问，引用 string 变量时，推断类型应反映"数值访问 string 变量"的冲突，但当前直接返回声明类型。

### 修复方案

**修改文件**: `TypeAnalyzer.java`

在 `checkRefVarExpressionErrors()` 或 `checkSingleVarExprError()` 中增加专门检查：

```java
// 在 checkSingleVarExprError 方法开头增加 # 前缀引用 string 变量的检查
private void checkSingleVarExprError(ExpressionNode ref, SymbolTable symbolTable,
        DslContext context, DslElementNode elementNode, List<Diagnostic> diagnostics) {
    if (symbolTable == null) {
        return;
    }
    VarDeclaration decl = symbolTable.lookup(ref.getVariableName()).orElse(null);
    if (decl == null || decl.getAstNode() == null) {
        return;
    }

    // 新增：# 前缀引用 string 类型变量 → SEM-TYPE-001
    if ("#".equals(ref.getPrefix()) && decl.getType() instanceof DslStringType) {
        diagnostics.add(buildHashPrefixOnStringVarDiagnostic(ref, elementNode, decl, context));
        return;
    }

    // ... 后续现有逻辑
}
```

新增诊断构建方法：
```java
private Diagnostic buildHashPrefixOnStringVarDiagnostic(ExpressionNode ref,
        DslElementNode elementNode, VarDeclaration decl, DslContext context) {
    return Diagnostic.builder()
            .severity(DiagnosticSeverity.ERROR)
            .ruleId(RULE_TYPE_001)
            .message("类型不匹配，" + ref.getText() + " 是 string 类型但以数值访问前缀 # 引用")
            .filePath(context.getFilePath())
            .astNode(elementNode)
            .ruleDocUrl(resolveDocUrl(context, RULE_TYPE_001))
            .build();
}
```

### 验证方法

```bash
java -jar feature/analysis/build/cli/dsl-analyzer.jar --format json --no-color \
  feature/analysis/src/test/resources/fixtures/complex_expressions/multi_element_expression_blast.xml
```

期望输出包含：`SEM-TYPE-001` at line 62（textExp="substr(#color_dark, 2, 6)"）。

---

<a id="bug-18"></a>
## Bug 18: SEM-TYPE-003 被归类为 SYN-EXPR-ANTLR（字符串字面量在 numeric 属性中）

### 问题描述

`x="'string_in_number'"` 或 `alpha="#multiplier + 'not_num'"` 等字符串字面量出现在 numeric 属性中，被报告为 SYN-EXPR-ANTLR 而非 SEM-TYPE-003。

### 根因分析

**两种场景**：

**场景 1**：纯字符串字面量 `x="'string_in_number'"`
- `ExpressionSyntaxChecker.checkAttr()`：对 numeric 属性，`expressionKind="number"`，`isStringExpr=false`（rawValue 含 `'` 但 expressionKind 不是 string）

  实际上 line 77：`isStringExpr = "string".equals(expressionKind) || rawValue.indexOf('\'') >= 0`
  对 `x="'string_in_number'"`，`rawValue.indexOf('\'') >= 0` 为 true → `isStringExpr=true`
  
- ANTLR 以 numeric 模式解析 `'string_in_number'` → 失败 → `parseFailed=true`
- 走到 line 115-118：`isStringExpr && parseFailed && !isEnumValue` → 报 SYN-EXPR-ANTLR

**场景 2**：混合表达式 `alpha="#multiplier + 'not_num'"`
- ANTLR 可能能或不能解析此表达式
- 如果解析失败 → SYN-EXPR-ANTLR
- 如果解析成功 → `checkStringLiteralInNumExpr()` 应检测到 `'not_num'` 并报告 SEM-TYPE-003

### 修复方案

**修改文件**: `ExpressionSyntaxChecker.java` + `LiteralTypeAnalyzer.java`

**步骤 1**：`ExpressionSyntaxChecker.java` — 在 `parseFailed` 分支前增加字符串字面量检测

在 `checkAttr()` 方法的 `parseFailed` 判断之前，增加以下检查：

```java
// 如果是纯字符串字面量（以单引号开头和结尾）在 numeric 上下文中，跳过 SYN-EXPR-ANTLR
// 交由 LiteralTypeAnalyzer 报告 SEM-TYPE-003
if (parseFailed && isQuotedStringLiteral(rawValue)) {
    return; // 不报告 SYN-EXPR-ANTLR，让语义层处理
}
```

新增辅助方法：
```java
private static boolean isQuotedStringLiteral(String value) {
    return value != null
            && value.startsWith("'")
            && value.endsWith("'")
            && value.length() >= 2;
}
```

**步骤 2**：`LiteralTypeAnalyzer.java` — 扩展检查范围

当前 LiteralTypeAnalyzer 仅检查 `value.isLiteral()` 为 true 的属性。但 `supportsExpression=false` 的属性（如 StyleCommand index）会被标记为 literal。对于 `x="'string_in_number'"`，如果 `supportsExpression=true`，AstBuilder 会尝试解析表达式，解析失败后 `isLiteral()` 可能为 false。

需要修改 LiteralTypeAnalyzer，增加对"表达式解析失败但值为字符串字面量"的检查：

```java
// 在 doAnalyze 方法中，对 value.isLiteral() 为 false 但 rawValue 是字符串字面量且属性类型为 number 的情况
// 也报告 SEM-TYPE-003
if (!value.isLiteral()) {
    String rawValue = value.getRawValue();
    if (rawValue != null && "number".equals(spec.getType())
            && rawValue.startsWith("'") && rawValue.endsWith("'")) {
        diagnostics.add(createDiagnostic(context, attr,
                "属性值类型错误: " + attr.getName() + " 期望 number, 实际 " + rawValue));
    }
    continue;
}
```

### 验证方法

```bash
# 场景 1
java -jar feature/analysis/build/cli/dsl-analyzer.jar --format json --no-color \
  feature/analysis/src/test/resources/fixtures/complex/deep_nesting_violations.xml

# 场景 2
java -jar feature/analysis/build/cli/dsl-analyzer.jar --format json --no-color \
  feature/analysis/src/test/resources/fixtures/complex_expressions/multi_element_expression_blast.xml
```

期望：
- deep_nesting_violations line 13: `SEM-TYPE-003`（非 SYN-EXPR-ANTLR）
- multi_element_expression_blast line 42: `SEM-TYPE-003`（非 SYN-EXPR-ANTLR）

---

<a id="bug-19"></a>
## Bug 19: SYN-EXPR-004 引号检查结果被归类为 SYN-EXPR-ANTLR

### 问题描述

未闭合单引号 `'hello world` 和嵌套单引号 `'Nested 'inner' quote'` 的字符串表达式，被报告为 SYN-EXPR-ANTLR 而非 SYN-EXPR-004。

### 根因分析

**代码路径**: `ExpressionSyntaxChecker.java` → `checkAttr()`

对 `'hello world`：
1. `isStringExpr=true`（含 `'`）
2. `isStandaloneBareWord()` 返回 false（含 `'`）
3. `hasBareWordInConcat()` — 分割 `+` 后，`'hello world` 不匹配 `^[a-zA-Z_]\w*$`（含 `'` 和空格）
4. `hasMissingBraces()` — 无 `+` 运算符
5. `parseFailed && !isEnumValue` → true → 报 SYN-EXPR-ANTLR

**核心问题**：缺少专门的引号完整性检查，导致引号问题被归入通用 ANTLR 解析失败。

### 修复方案

**修改文件**: `ExpressionSyntaxChecker.java`

在 `checkAttr()` 方法的 `parseFailed` 分支之前，增加引号完整性检查：

```java
// 在 isStandaloneBareWord 检查之后，parseFailed 分支之前
if (isStringExpr && hasQuoteError(rawValue)) {
    diagnostics.add(diag("SYN-EXPR-004", DiagnosticSeverity.ERROR,
            describeQuoteError(rawValue), filePath, attr));
    return;
}
```

新增辅助方法：
```java
private static boolean hasQuoteError(String rawValue) {
    if (rawValue == null || !rawValue.contains("'")) {
        return false;
    }
    int quoteCount = countChar(rawValue, '\'');
    // 奇数个单引号 = 未闭合
    if (quoteCount % 2 != 0) {
        return true;
    }
    // 偶数个单引号但存在嵌套（如 'Nested 'inner' quote'）
    // 检测：移除第一对引号内容后是否还有引号
    return hasNestedQuotes(rawValue);
}

private static int countChar(String s, char c) {
    int count = 0;
    for (int i = 0; i < s.length(); i++) {
        if (s.charAt(i) == c) count++;
    }
    return count;
}

private static boolean hasNestedQuotes(String rawValue) {
    // 'Nested 'inner' quote' — 4 个引号，但中间引号是嵌套的
    // 检测方法：找第一对 '...'，如果去掉后还有引号，则是嵌套
    int first = rawValue.indexOf('\'');
    if (first < 0) return false;
    int second = rawValue.indexOf('\'', first + 1);
    if (second < 0) return false;
    String after = rawValue.substring(second + 1);
    return after.contains("'");
}

private static String describeQuoteError(String rawValue) {
    int quoteCount = countChar(rawValue, '\'');
    if (quoteCount % 2 != 0) {
        return "未闭合单引号: " + rawValue;
    }
    return "嵌套单引号未转义: " + rawValue;
}
```

### 验证方法

```bash
java -jar feature/analysis/build/cli/dsl-analyzer.jar --format json --no-color \
  feature/analysis/src/test/resources/fixtures/complex/expression_syntax_errors.xml
```

期望：
- line 6: `SYN-EXPR-004`（非 SYN-EXPR-ANTLR）
- line 26: `SYN-EXPR-004`（非 SYN-EXPR-ANTLR）

---

<a id="bug-20"></a>
## Bug 20: SEM-TYPE-003 被归类为 SEM-TYPE-001（number expression 赋值给 string Var）

### 问题描述

number 表达式赋值给 string 类型 Var 时，报告 SEM-TYPE-001 而非 SEM-TYPE-003。

### 根因分析

**代码路径**: `TypeAnalyzer.java` → `checkVarExpressionBody()`

对 `Var name="type_mismatch" type="string" expression="100 + 50"`：
1. `inferExpressionType("100 + 50")` — BINARY_EXPR，检查子节点，第一个子节点 `100` 是 LITERAL + numeric → 返回 `DslNumberType`
2. `exprType = DslNumberType`, `varType = DslStringType`
3. `typeEquals(DslNumberType, DslStringType)` → false
4. 报告 `buildVarTypeMismatchDiagnostic()` → SEM-TYPE-001

**核心问题**：当表达式只含字面量（无变量引用、无函数调用）且类型不匹配时，应归类为 SEM-TYPE-003（字面量/简单值类型错误），而非 SEM-TYPE-001（复杂表达式类型不匹配）。

### 修复方案

**修改文件**: `TypeAnalyzer.java`

在 `checkVarExpressionBody()` 中，根据表达式复杂度选择 Rule ID：

```java
// 修改 line 256-257 附近
DslType exprType = inferExpressionType(exprNode, symbolTable, functionLibrary);
if (exprType != null && !TypeInferenceEngine.typeEquals(exprType, varType)) {
    // 如果表达式只包含字面量（无变量引用、无函数调用），使用 SEM-TYPE-003
    if (isSimpleLiteralExpression(exprNode)) {
        diagnostics.add(buildSimpleLiteralTypeMismatchDiagnostic(
                elementNode, varType, exprType, context));
    } else {
        diagnostics.add(buildVarTypeMismatchDiagnostic(elementNode, varType, exprType, context));
    }
}
```

新增辅助方法：
```java
private boolean isSimpleLiteralExpression(ExpressionNode node) {
    if (node == null) {
        return false;
    }
    switch (node.getKind()) {
        case LITERAL:
            return true;
        case BINARY_EXPR:
        case UNARY_EXPR:
            if (node.getChildren() != null) {
                for (ExpressionNode child : node.getChildren()) {
                    if (!isSimpleLiteralExpression(child)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        default:
            return false;
    }
}
```

新增诊断构建方法：
```java
private Diagnostic buildSimpleLiteralTypeMismatchDiagnostic(DslElementNode elementNode,
        DslType expected, DslType inferred, DslContext context) {
    String varName = getAttrValue(elementNode, "name");
    return Diagnostic.builder()
            .severity(DiagnosticSeverity.ERROR)
            .ruleId(RULE_TYPE_003)
            .message("属性值类型错误: Var type=" + expected.getName()
                    + " 但表达式返回 " + inferred.getName()
                    + "（Var name=\"" + (varName != null ? varName : "") + "\"）")
            .filePath(context.getFilePath())
            .astNode(elementNode)
            .ruleDocUrl(resolveDocUrl(context, RULE_TYPE_003))
            .build();
}
```

### 验证方法

```bash
# Fixture 1
java -jar feature/analysis/build/cli/dsl-analyzer.jar --format json --no-color \
  feature/analysis/src/test/resources/fixtures/complex/variable_lifecycle_errors.xml

# Fixture 2
java -jar feature/analysis/build/cli/dsl-analyzer.jar --format json --no-color \
  feature/analysis/src/test/resources/fixtures/complex/type_inference_edge_cases.xml
```

期望：
- variable_lifecycle_errors line 13: `SEM-TYPE-003`（非 SEM-TYPE-001）
- type_inference_edge_cases line 12: `SEM-TYPE-003`（非 SEM-TYPE-001）

---

<a id="bug-21"></a>
## Bug 21: SYN-EXPR-002 计算结果精度溢出不检出（非问题 — PRD 合规）

### 问题描述

表达式 `5000000 + 5000000` 的运算结果概念上为 10000000（8位），超过7位精度限制，但不触发 SYN-EXPR-002。

### 处理结论

**非问题 — PRD 合规**

PRD.md 第 185 行明确标注："不做常量折叠（不对表达式求值），不做符号执行"。

`ExpressionSyntaxChecker.checkPrecision()` 仅扫描表达式中的字面量数值位数，不进行编译期常量表达式求值。`5000000` 和 `5000000` 各为7位字面量（合法），不做求值则无法得知结果为8位。

### 处理措施

**不修改分析器代码**。更新 `ANSWER_KEY.md`，将该条目标记为"设计限制 — PRD 不支持常量折叠"。

---

<a id="bug-22"></a>
## Bug 22: bogusFunc 未知函数被归类为 SEM-TYPE-001 而非 SEM-REF-001

### 问题描述

表达式使用未定义函数名 `bogusFunc(1, 2)` 时，报告 SEM-TYPE-001 而非 SEM-REF-001。

### 根因分析

**代码路径**: `TypeAnalyzer.java` → `checkFunctionCalls()`

1. `functionLibrary.getSignature("bogusFunc", expressionKind)` 返回 empty
2. 报告 `buildFunctionNotApplicableDiagnostic()` → SEM-TYPE-001 "函数 bogusFunc 不适用于 number 表达式"

**核心问题**：函数名不在签名库中时，应归类为"未定义引用" SEM-REF-001，而非"函数不适用" SEM-TYPE-001。

### 修复方案

**修改文件**: `VarRefAnalyzer.java`

在 `collectUndefinedReferences()` 中增加对函数调用的检查。或者在 `TypeAnalyzer.checkFunctionCalls()` 中，当函数签名不存在时改报 SEM-REF-001。

**方案 A（推荐）**：在 `VarRefAnalyzer.collectUndefinedReferences()` 中增加函数调用收集

```java
// 在 collectUndefinedReferences 方法中，除了收集 VARIABLE_REF/ARRAY_ACCESS，
// 还收集 FUNCTION_CALL 节点，检查函数名是否在函数签名库中
List<ExpressionNode> functionCalls = new ArrayList<>();
collectFunctionCalls(exprNode, functionCalls);
FunctionSignatureLibrary funcLib = context.getRuleRepository().getFunctionSignatureLibrary();
for (ExpressionNode call : functionCalls) {
    String funcName = call.getFunctionName();
    if (funcName == null || "ifelse".equals(funcName)) {
        continue;
    }
    if (funcLib == null || funcLib.getSignature(funcName, "number").isEmpty()
            && funcLib.getSignature(funcName, "string").isEmpty()) {
        diagnostics.add(buildUndefinedFunctionDiagnostic(call, elementNode, context));
    }
}
```

新增方法：
```java
private void collectFunctionCalls(ExpressionNode node, List<ExpressionNode> calls) {
    if (node == null) return;
    if (node.getKind() == ExpressionKind.FUNCTION_CALL) {
        calls.add(node);
    }
    if (node.getChildren() != null) {
        for (ExpressionNode child : node.getChildren()) {
            collectFunctionCalls(child, calls);
        }
    }
    if (node.getIndexExpression() != null) {
        collectFunctionCalls(node.getIndexExpression(), calls);
    }
}

private Diagnostic buildUndefinedFunctionDiagnostic(ExpressionNode call,
        DslElementNode hostNode, DslContext context) {
    String docUrl = resolveDocUrl(context, RULE_REF_001);
    int line = call.getLine();
    int column = call.getColumn();
    if (line == 0 && column == 0) {
        line = hostNode.getLine();
        column = hostNode.getColumn();
    }
    return Diagnostic.builder()
            .severity(DiagnosticSeverity.ERROR)
            .ruleId(RULE_REF_001)
            .message("引用未定义函数 " + call.getFunctionName())
            .filePath(context.getFilePath())
            .line(line)
            .column(column)
            .endLine(call.getEndLine())
            .endColumn(call.getEndColumn())
            .ruleDocUrl(docUrl)
            .build();
}
```

**同时修改 `TypeAnalyzer.checkFunctionCalls()`**：当函数签名不存在时，不再报告 SEM-TYPE-001（避免重复），直接 continue：

```java
if (sigOpt.isEmpty()) {
    // 不在此处报告，由 VarRefAnalyzer 报告 SEM-REF-001
    continue;
}
```

### 验证方法

```bash
java -jar feature/analysis/build/cli/dsl-analyzer.jar --format json --no-color \
  feature/analysis/src/test/resources/fixtures/complex/expression_syntax_errors.xml
```

期望：line 7: `SEM-REF-001`（非 SEM-TYPE-001）。

---

<a id="bug-23"></a>
## Bug 23: SEM-REF-001 前向引用（非问题 — 策略变更）

### 问题描述

Image 的 `x="#later_declared"` 引用在其后才声明的变量，不报告 SEM-REF-001 前向引用错误。

### 处理结论

**非问题 — 策略变更**

经产品方确认：本项目不报告任何"声明/定义之前引用"的错误，仅检查"引用变量在文件中是否定义"。所有变量简化为全局变量处理，不判定声明与调用的顺序。

### 处理措施

**修改文件**: `VarRefAnalyzer.java`

1. 删除 `isForwardReference()` 方法（line 231-241）
2. 删除 `buildForwardReferenceDiagnostic()` 方法（line 271-296）
3. 删除 `collectUndefinedReferences()` 中 line 93-95 的 `else if (isForwardReference(...))` 分支

**修改文件**: ANSWER_KEY.md（两个）

移除前向引用相关条目：
- `complex/ANSWER_KEY.md`：variable_lifecycle_errors row 10（line 19 前向引用）
- `complex_expressions/ANSWER_KEY.md`：multi_element_expression_blast row 1（line 9 前向引用）

---

<a id="bug-24"></a>
## Bug 24: SEM-REF-003 第三层级重复变量名不检出

### 问题描述

同名变量 `dup_name` 在全局作用域出现2次后，在嵌套 Group 作用域中出现第3次，第3次不报告 SEM-REF-003。

### 根因分析

**代码路径**: `VarRefAnalyzer.java` → `detectDuplicateVarDeclaration()`

```java
SymbolTable globalTable = symbolTable.getGlobalTable();
VarDeclaration effective = globalTable.getDeclarations().get(varName);
if (effective == null) {
    return;
}
if (effective.getAstNode() != elementNode) {
    diagnostics.add(buildDuplicateVarDiagnostic(elementNode, varName, context));
}
```

- `globalTable.getDeclarations().get(varName)` 返回全局表中该变量名的"最终生效声明"（后声明覆盖语义，存的是最后声明的 Var）
- 对第3个 `dup_name`（在 Group 内）：其符号表是 Group 作用域，`getGlobalTable()` 返回根表。如果根表中 `dup_name` 的最终声明是第2个（全局），第3个的 astNode != 第2个的 astNode → 应报告
- **但实际不报告**：可能是 Group 内的 Var 在符号表构建时被放入了子作用域，而 `detectDuplicateVarDeclaration` 使用的是 `symbolTable.getGlobalTable()`，如果当前 `symbolTable` 是子作用域，`getGlobalTable()` 返回根表，根表的 declarations 不包含子作用域中的 Var

需要检查 `SymbolTableBuilderImpl` 如何处理 Group 内 Var 的作用域。Group 内的 Var 可能被放入了子作用域的 declarations，而非全局表。

**核心问题**：子作用域中的 Var 声明不会被全局表的 `declarations` 包含。`detectDuplicateVarDeclaration` 检查全局表时找不到子作用域的 Var，但第3个 Var 的 astNode 在子作用域中，全局表中 `dup_name` 的声明是第1个或第2个，astNode 不同 → 应该报告。但实际不报告，说明：

1. 子作用域的 `symbolTable.getGlobalTable()` 返回的根表中，`dup_name` 的声明可能不是第1个或第2个
2. 或者 `detectDuplicateVarDeclaration` 在遍历到第3个 Var 时，`symbolTable` 参数是子作用域，但 `globalTable.getDeclarations().get(varName)` 可能恰好返回第3个 Var 自己（如果 SymbolTableBuilderImpl 将 Group 内 Var 也放入了全局表）

### 修复方案

**修改文件**: `VarRefAnalyzer.java`

修改 `detectDuplicateVarDeclaration()` 方法，改为检查文件中所有同名 Var 声明：

```java
private void detectDuplicateVarDeclaration(DslElementNode elementNode, DslContext context,
        List<Diagnostic> diagnostics) {
    if (!VAR_TAG.equals(elementNode.getTagName())) {
        return;
    }
    SymbolTable symbolTable = context.getSymbolTable();
    if (symbolTable == null) {
        return;
    }
    String varName = getAttrValue(elementNode, NAME_ATTR);
    if (varName == null || varName.isEmpty()) {
        return;
    }

    // 沿作用域链查找所有同名声明
    // 如果在任意作用域（包括全局）找到同名声明且 astNode 不是当前节点，则报告重复
    SymbolTable globalTable = symbolTable.getGlobalTable();
    VarDeclaration globalDecl = globalTable.getDeclarations().get(varName);
    if (globalDecl != null && globalDecl.getAstNode() != elementNode) {
        diagnostics.add(buildDuplicateVarDiagnostic(elementNode, varName, context));
        return;
    }

    // 检查当前作用域链中的所有声明
    SymbolTable current = symbolTable;
    while (current != null) {
        VarDeclaration decl = current.getDeclarations().get(varName);
        if (decl != null && decl.getAstNode() != elementNode) {
            diagnostics.add(buildDuplicateVarDiagnostic(elementNode, varName, context));
            return;
        }
        current = current.getParent();
    }
}
```

**注意**：需要验证 `SymbolTableBuilderImpl` 如何构建作用域链。如果 Group 内 Var 不在子作用域的 declarations 中，需要调整 SymbolTableBuilderImpl。但优先尝试在 VarRefAnalyzer 中修复，如果不行再修改 SymbolTableBuilderImpl。

### 验证方法

```bash
java -jar feature/analysis/build/cli/dsl-analyzer.jar --format json --no-color \
  feature/analysis/src/test/resources/fixtures/complex/variable_lifecycle_errors.xml
```

期望：line 27（Group 内第3个 dup_name）: `SEM-REF-003`。

---

<a id="bug-25"></a>
## Bug 25: SEM-REF-001→SEM-REF-002 元素属性引用归类偏差

### 问题描述

`#ghost_img.actual_w` 和 `#ghost_elem.actual_x` 等元素属性引用被归类为 SEM-REF-002（元素引用）而非 SEM-REF-001（变量引用）。

### 根因分析

**代码路径**: `VarRefAnalyzer.java` → `collectUndefinedReferences()`

1. `matchTemplate(varName, elementTemplates)` 将 `ghost_img.actual_w` 匹配到 `{elementName}.actual_w` 模板
2. 提取 `ghost_img` 作为元素名
3. `elementNames.contains("ghost_img")` → false
4. 报告 `buildUndefinedElementRefDiagnostic()` → SEM-REF-002

### 修复方案

**修改文件**: `VarRefAnalyzer.java`

将 `buildUndefinedElementRefDiagnostic()` 的 Rule ID 从 SEM-REF-002 改为 SEM-REF-001，并更新消息：

```java
private Diagnostic buildUndefinedElementRefDiagnostic(ExpressionNode ref, String elementName,
        DslElementNode hostNode, DslContext context) {
    String docUrl = resolveDocUrl(context, RULE_REF_001);  // 改为 REF_001
    int line = ref.getLine();
    int column = ref.getColumn();
    int endLine = ref.getEndLine();
    int endColumn = ref.getEndColumn();
    if (line == 0 && column == 0) {
        line = hostNode.getLine();
        column = hostNode.getColumn();
        endLine = hostNode.getEndLine();
        endColumn = hostNode.getEndColumn();
    }
    return Diagnostic.builder()
            .severity(DiagnosticSeverity.ERROR)
            .ruleId(RULE_REF_001)  // 改为 REF_001
            .message("引用未定义元素属性 " + (ref.getPrefix() != null ? ref.getPrefix() : "")
                    + elementName)  // 消息包含完整引用文本
            .filePath(context.getFilePath())
            .line(line)
            .column(column)
            .endLine(endLine)
            .endColumn(endColumn)
            .suggestedFixes(List.of(SuggestedFix.builder()
                    .text("声明带 name=\"" + elementName + "\" 的元素")
                    .type("ADD_CHILD").target(elementName).build()))
            .ruleDocUrl(docUrl)
            .build();
}
```

### 验证方法

```bash
# Fixture 1
java -jar feature/analysis/build/cli/dsl-analyzer.jar --format json --no-color \
  feature/analysis/src/test/resources/fixtures/complex_expressions/array_index_edge_cases.xml

# Fixture 2
java -jar feature/analysis/build/cli/dsl-analyzer.jar --format json --no-color \
  feature/analysis/src/test/resources/fixtures/complex_expressions/multi_element_expression_blast.xml
```

期望：
- array_index_edge_cases line 15: `SEM-REF-001`（非 SEM-REF-002）
- multi_element_expression_blast line 69: `SEM-REF-001`（非 SEM-REF-002）

---

<a id="bug-26"></a>
## Bug 26: SEM-CMD-004 被归类为 SEM-TYPE-003（StyleCommand index 使用表达式）

### 问题描述

`<StyleCommand index="#runtime_var"/>` 使用表达式作为 index 属性值时，报告 SEM-TYPE-003 而非 SEM-CMD-004。

### 根因分析

**约束条件**（`StyleCommand.json:71`）：
```
element.attrs['index'] != null AND containsExpression(element.attrs['index'])
```

**问题**：`containsExpression()` 函数在 `DefaultRuleDslEvaluator` 中未实现。DslRuleCondition grammar 不识别此函数名 → 解析异常 → `parseAndEvaluate()` catch 异常返回 false → 约束不触发。

同时，StyleCommand index 属性 `supportsExpression=false`，AstBuilder 将 `#runtime_var` 标记为 literal。LiteralTypeAnalyzer 检测到 `#runtime_var` 无法解析为 Double → 报告 SEM-TYPE-003。

### 修复方案

**修改文件**: `DefaultRuleDslEvaluator.java`

在 `evaluate()` 方法中，在 `preprocessChildrenExpressions()` 之后增加 `preprocessContainsExpression()` 预处理步骤，采用与 `preprocessChildrenExpressions` 相同的模式：

```java
@Override
public boolean evaluate(String condition, EvaluationContext context) {
    this.context = context;
    String processed = preprocessChildrenExpressions(condition, context);
    processed = preprocessContainsExpression(processed, context);
    return parseAndEvaluate(processed);
}

private String preprocessContainsExpression(String condition, EvaluationContext context) {
    if (condition == null || !condition.contains("containsExpression(")) {
        return condition;
    }
    // 匹配 containsExpression(element.attrs['attrName']) 模式
    Pattern pattern = Pattern.compile(
            "containsExpression\\(element\\.attrs\\[\\s*'([^']+)'\\s*\\]\\)");
    Matcher m = pattern.matcher(condition);
    StringBuilder result = new StringBuilder();
    int lastEnd = 0;
    while (m.find()) {
        result.append(condition, lastEnd, m.start());
        String attrName = m.group(1);
        Map<String, String> attrs = context.getElementAttrs();
        String attrValue = attrs != null ? attrs.get(attrName) : null;
        boolean isExpr = attrValue != null && looksLikeExpression(attrValue);
        result.append(isExpr ? "'1'=='1'" : "'1'=='0'");
        lastEnd = m.end();
    }
    result.append(condition, lastEnd, condition.length());
    return result.toString();
}

private static boolean looksLikeExpression(String value) {
    if (value == null || value.isEmpty()) {
        return false;
    }
    // 纯数字不算表达式
    if (value.matches("^[+-]?\\d+(\\.\\d+)?$")) {
        return false;
    }
    // 含 # @ ' + - * / % ( 等字符视为表达式
    return value.indexOf('#') >= 0
            || value.indexOf('@') >= 0
            || value.indexOf('\'') >= 0
            || value.indexOf('+') >= 0
            || value.indexOf('-') >= 1
            || value.indexOf('*') >= 0
            || value.indexOf('/') >= 0
            || value.indexOf('%') >= 0
            || value.indexOf('(') >= 0;
}
```

### 验证方法

```bash
java -jar feature/analysis/build/cli/dsl-analyzer.jar --format json --no-color \
  feature/analysis/src/test/resources/fixtures/complex/trigger_command_combos.xml
```

期望：line 23: `SEM-CMD-004`（非 SEM-TYPE-003）。

---

<a id="bug-27"></a>
## Bug 27: SEM-TYPE-002→SEM-TYPE-001 链式函数参数类型归类偏差

### 问题描述

`sin(substr('hello', 0, 3))` 中 substr 返回 string 传给 sin（期望 number），报告 SEM-TYPE-001 而非 SEM-TYPE-002。

### 根因分析

**代码路径**: `TypeAnalyzer.java` → `checkFunctionCalls()` → `checkFunctionParams()`

对 `sin(substr('hello', 0, 3))`：
1. `collectFunctionCalls()` 收集 [sin, substr]
2. 对 sin：`getSignature("sin", "number")` 返回签名，`checkFunctionParams()` 检查 sin 的参数
3. sin 的参数是 `substr('hello', 0, 3)`。`engine.inferType(substr(...), number, symbolTable)` 调用 `TypeInferenceEngine.inferFunctionCall()`
4. `inferFunctionCall()`：`getSignature("substr", "number")` → empty（substr 是 string 函数）
5. 返回 null（line 95：`return sig != null ? sig.getReturnType() : null`）

**等等，返回 null 而非 DslNumberType**。那么 `argType` 是 null。`checkFunctionParams()` line 162：`if (argType != null && !typeEquals(argType, param.getType()))` → argType 为 null，条件不满足 → 不报告 SEM-TYPE-002。

然后对 substr 本身：`getSignature("substr", "number")` → empty → `buildFunctionNotApplicableDiagnostic()` → SEM-TYPE-001 "函数 substr 不适用于 number 表达式"。

**核心问题**：`TypeInferenceEngine.inferFunctionCall()` 在函数不存在于当前上下文时返回 null，导致 `checkFunctionParams()` 无法检测参数类型不匹配。应尝试跨上下文查找函数签名获取返回类型。

### 修复方案

**修改文件**: `TypeInferenceEngine.java`

修改 `inferFunctionCall()` 方法，当 `getSignature(funcName, expectedContext)` 为空时，尝试其他上下文：

```java
private DslType inferFunctionCall(ExpressionNode node, DslType expectedContext) {
    if (functionLibrary == null || node.getFunctionName() == null) {
        return null;
    }
    String expressionKind = expectedContext != null ? expectedContext.getName() : "number";
    // 首先尝试当前上下文
    FunctionSignature sig = functionLibrary.getSignature(node.getFunctionName(), expressionKind).orElse(null);
    if (sig == null) {
        // 跨上下文查找：尝试 string 和 number 两种上下文
        sig = functionLibrary.getSignature(node.getFunctionName(), "string").orElse(null);
        if (sig == null) {
            sig = functionLibrary.getSignature(node.getFunctionName(), "number").orElse(null);
        }
    }
    return sig != null ? toDslType(sig.getReturnType()) : null;
}

private static DslType toDslType(String typeName) {
    if ("number".equals(typeName)) {
        return new DslNumberType();
    }
    if ("string".equals(typeName)) {
        return new DslStringType();
    }
    return null;
}
```

**效果**：
- 对 `substr('hello', 0, 3)` 在 number 上下文中：`getSignature("substr", "number")` 为空 → 尝试 `getSignature("substr", "string")` → 找到，返回类型 string
- `argType = DslStringType`, `param.getType() = DslNumberType` → 不匹配 → 报告 SEM-TYPE-002 ✓

**同时修改 `TypeAnalyzer.checkFunctionCalls()`**：当 `getSignature(funcName, expressionKind)` 为空但函数存在于其他上下文时，不再报告"函数不适用" SEM-TYPE-001（避免误报）：

```java
if (sigOpt.isEmpty()) {
    // 检查函数是否在其他上下文中存在
    boolean existsInAnyContext = functionLibrary.getSignature(call.getFunctionName(), "number").isPresent()
            || functionLibrary.getSignature(call.getFunctionName(), "string").isPresent();
    if (!existsInAnyContext) {
        // 函数完全不存在，由 VarRefAnalyzer 报告 SEM-REF-001（Bug 22）
        continue;
    }
    // 函数存在于其他上下文，参数类型检查会捕获不匹配
    // 但仍需检查参数，使用跨上下文签名
    Optional<FunctionSignature> altSig = functionLibrary.getSignature(call.getFunctionName(), "string");
    if (altSig.isEmpty()) {
        altSig = functionLibrary.getSignature(call.getFunctionName(), "number");
    }
    if (altSig.isPresent()) {
        checkFunctionParams(call, altSig.get(), engine, context, elementNode, diagnostics);
    }
    continue;
}
```

### 验证方法

```bash
java -jar feature/analysis/build/cli/dsl-analyzer.jar --format json --no-color \
  feature/analysis/src/test/resources/fixtures/complex_expressions/chained_function_hell.xml
```

期望：line 7: `SEM-TYPE-002`（非 SEM-TYPE-001）。

---

## 附录：Bug 间依赖关系

```
Bug 23 (策略变更: 删除前向引用) ─┐
                                  ├──→ 无依赖，独立修改
Bug 21 (PRD 合规: 标记设计限制) ─┘

Bug 25 (SEM-REF-002→001) ──→ 独立修改
Bug 22 (未知函数→SEM-REF-001) ──→ 依赖 TypeAnalyzer 修改（不再报告 SEM-TYPE-001）
Bug 26 (containsExpression) ──→ 独立修改
Bug 18 (SYN-EXPR-ANTLR→SEM-TYPE-003) ──→ 依赖 LiteralTypeAnalyzer 扩展
Bug 19 (SYN-EXPR-ANTLR→SYN-EXPR-004) ──→ 独立修改
Bug 20 (SEM-TYPE-001→SEM-TYPE-003) ──→ 独立修改
Bug 27 (跨上下文返回类型) ──→ 依赖 TypeAnalyzer checkFunctionCalls 修改

Bug 14 (Var.json 约束扩展) ──→ 独立修改
Bug 16 (# 前缀 string 变量) ──→ 独立修改
Bug 24 (跨作用域重复检测) ──→ 可能依赖 SymbolTableBuilderImpl

Bug 15 (类型传播) ──→ 依赖 Bug 27 修复（确保函数调用错误正确检测）
```

### 建议实施顺序

1. Phase 1: Bug 21 + Bug 23（策略变更，无代码依赖）
2. Phase 2: Bug 25 → Bug 22 → Bug 26 → Bug 18 → Bug 19 → Bug 20 → Bug 27（Rule ID 重分类）
3. Phase 3: Bug 14 → Bug 16 → Bug 24（检测缺失）
4. Phase 4: Bug 15（类型传播，最后修复以避免干扰）
