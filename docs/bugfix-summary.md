# Bug Fix Summary

Comprehensive catalog of all bugs discovered and tested, organized by category.
Updated 2026-07-13 based on `java -jar dsl-analyzer.jar --format markdown` E2E test on 14 fixtures + 2 directory scans.

---

## Part I: 已修复 Bug 归档

Bug 1–10 已在之前的开发周期中修复并验证，以下为归档摘要。

### Bug 1: JAR包运行时规则加载失败 ✅ 已修复

- **修复内容**: `JsonRuleLoader.java` 新增 `loadFromClasspath()` 方法支持 jar 协议；`CliMain.java` 改用 classpath 加载
- **验证**: 任意目录运行 jar → 正确检测 5 errors (SEM-TRIG-002, SEM-TRIG-001, SEM-NEST-001, SEM-IMG-SRC, SEM-ATTR-001)

### Bug 2: XML语法错误不报告诊断 ✅ 已修复

- **修复内容**: 新增 `SyntaxErrorAnalyzer`，检测 `hasError` 节点产出 SYN-SAX-001；注册到 `AnalyzerRegistry`
- **验证**: 格式错误 XML → 正确报告 SYN-SAX-001

### Bug 3: TypeAnalyzer 不产出诊断（FunctionSignatureLibrary 缺失）✅ 已修复

- **修复内容**: `JsonFunctionSignatureLoader.java` 新增 jar 协议 classpath 加载；`CliMain.java` 同步加载函数签名库
- **验证**: SEM-TYPE-001/002 正确产出（E2E 实测 type_inference_edge_cases 12E+1W）

### Bug 4: 属性名/值对齐错误 ✅ 已修复

- **修复内容**: `AstBuilder.java` 改用 `reader.getAttributeLocalName(i)` 作为属性名，构建 name→AttrPos 映射
- **验证**: 诊断消息正确显示属性值（E2E 实测无误报属性值错乱）

### Bug 5: SEM-ATTR-001 (alpha范围 0-255) 不触发 ✅ 已修复

- **修复内容**: `Image.json` 约束简化为 `element.attrs['alpha'] < 0 OR element.attrs['alpha'] > 255`
- **验证**: constraint_edge_cases 正确报告 alpha=256 和 alpha=-1 (5E+1W 全匹配)

### Bug 6: SEM-PERSIST-001 不触发 ✅ 已修复

- **修复内容**: `DslRuleCondition.g4` 新增 MATCHES token；`DefaultRuleDslEvaluator.java` 实现 MATCHES 处理
- **验证**: variable_lifecycle_errors 正确报告 4 条 SEM-PERSIST-001 (hour/minute/ishour12/system.time.hour1)

### Bug 7: SEM-TRIG-002 / element.children 约束不触发 ✅ 部分修复

- **修复内容**: `EvaluationContext.java` 新增 childElements；`DefaultRuleDslEvaluator.java` 新增 preprocessChildrenExpressions；`ConstraintAnalyzer.java` 新增 buildChildElementInfos
- **验证**: SEM-TRIG-002 在 widget_missing_required.xml 中可触发，但在 trigger_command_combos.xml 中仍不触发（见 Bug 12）

### Bug 8: SEM-TYPE-003 (字面量类型错误) 不触发 ✅ 已修复

- **修复内容**: `LiteralTypeAnalyzer.java` 将 isLiteral() 门控替换为表达式解析成功判断
- **验证**: SEM-TYPE-003 在部分场景工作，但仍有归类偏差（见 Bug 18、Bug 20）

### Bug 9: ifelse(...) 被 ANTLR 解析器误报 ✅ 已修复

- **修复内容**: `DslExpression.g4` primaryExpr 规则新增 `'{' expression '}'` 替代项
- **验证**: chained_function_hell 正确报告 4 条 SEM-TYPE-001/002/003，无 ANTLR 误报

### Bug 10: SYN-EXPR-002/003/004 缺失或不全 ✅ 部分修复

- **修复内容**: ExpressionSyntaxChecker 新增 countSignificantDigits/checkPrecision 递归、ANTLR 前引号检查
- **验证**: SYN-EXPR-002 字面量精度检出正常；SYN-EXPR-004 部分退化（裸词不检出，见 Bug 17）；SYN-EXPR-002 计算结果不检出（见 Bug 21）

---

## Part II: 未修复 Bug（2026-07-13 E2E Markdown 导出实测发现）

---

## 1. 规则引擎类

### Bug 12: SEM-TRIG-002 在 trigger_command_combos.xml 中不触发

- **症状**: `<Button>` 元素没有 `<Trigger>` 子元素时，SEM-TRIG-002 不产出诊断
- **DSL**: `trigger_command_combos.xml` line 3:
  ```xml
  <Button name="no_trigger_btn" x="100" y="100" width="200" height="80"/>
  ```
- **Expected**: SEM-TRIG-002（Trigger缺失规则：Button 元素必须包含至少一个 Trigger 子元素来响应用户交互，否则按钮无法被点击触发任何行为）
  ```
  SEM-TRIG-002 (line 3): Button 'no_trigger_btn' is missing a Trigger child element
  ```
- **Actual**: 无诊断（jar 输出中完全没有 SEM-TRIG-002）
- **Root cause**: Bug 7 修复了 children.filter 预处理机制，使 widget_missing_required.xml 中的 SEM-TRIG-002 可触发。但 trigger_command_combos.xml 中 Button 无任何子元素（children 为空），约束 `element.children.filter(tagName, Trigger).count == 0` 在 children 为空时可能不正确评估——当 Button 完全无子元素时，childElements 列表可能未被正确填充，导致约束条件始终为 false
- **影响文件**: `ConstraintAnalyzer.java`, `DefaultRuleDslEvaluator.java`

---

### Bug 13: SEM-TRIG-003 空Triggers容器不触发

- **症状**: `<Triggers></Triggers>` 空容器没有 `<Trigger>` 子元素时，SEM-TRIG-003 不产出诊断
- **DSL**: `trigger_command_combos.xml` lines 28-30:
  ```xml
  <Button name="empty_triggers_btn" x="100" y="550" width="200" height="80">
      <Triggers>
      </Triggers>
  </Button>
  ```
- **Expected**: SEM-TRIG-003（Triggers空容器规则：Triggers 容器元素必须包含至少一个 Trigger 子元素。空的 Triggers 元素不仅无意义，还会误导开发者认为按钮已有交互定义，但实际上没有任何触发响应）
  ```
  SEM-TRIG-003 (line 29): Triggers container has no Trigger child elements
  ```
- **Actual**: 无诊断
- **Root cause**: 同 Bug 12——Triggers 有子元素但子元素列表为空（无 Trigger），children.filter 约束条件在空子元素列表时评估不正确。Triggers.json 中约束 `element.children.filter(tagName, Trigger).count == 0` 的预处理可能将空列表视为"无子元素"而非"有子元素但无Trigger"
- **影响文件**: `ConstraintAnalyzer.java`, `DefaultRuleDslEvaluator.java`

---

### Bug 14: SEM-PERSIST-001 不检测 expression 引用的时间变量

- **症状**: Var 使用 `persist="true"` 且 expression 中引用时间变量 `#hour` 时，SEM-PERSIST-001 不触发（但 Var name 直接为 "hour" 时可触发）
- **DSL**: `deep_nesting_violations.xml` line 9:
  ```xml
  <Var name="time_persist" type="number" persist="true" expression="#hour"/>
  ```
- **Expected**: SEM-PERSIST-001（时间变量persist禁止规则：对时间/日期类变量（如hour、minute、ishour12、system.time.*等）使用 persist/globalPersist/styleGlobalPersist 属性是禁止的，因为这些变量的值由系统时钟自动更新，persist 会导致旧值被缓存而无法同步最新时间）
  ```
  SEM-PERSIST-001 (line 9): persist on time variable #hour is forbidden
  ```
- **Actual**: 无诊断（变量名 "time_persist" 不匹配 MATCHES 正则 `(hour|hour12|hour24|...)`）
- **Root cause**: `Var.json` 约束仅通过变量 `name` 属性 MATCHES 正则来检测时间变量。当 name 本身不匹配正则（如 "time_persist"），即使 expression 引用了 `#hour` 等时间变量，约束仍不触发。需要增加对 expression 内容的检查，识别其中引用的时间变量名
- **影响文件**: `Var.json` (约束定义), `DefaultRuleDslEvaluator.java` (可能需扩展)

---

## 2. 类型推断类

### Bug 15: SEM-TYPE-001 类型传播链在 #var 引用处断裂（alpha=#bad_sin）

- **症状**: 变量 `bad_sin` 的 expression 有类型错误（sin('not_a_number')），但 `alpha="#bad_sin"` 不报告 SEM-TYPE-001 类型错误
- **DSL**: `type_inference_edge_cases.xml` lines 7, 18:
  ```xml
  <Var name="bad_sin" type="number" expression="sin('not_a_number')"/>
  ...
  <Image name="bg" ... alpha="#bad_sin" ... src="bg.png"/>
  ```
- **Expected**: SEM-TYPE-001（表达式类型不匹配规则：属性 alpha 期望 number 类型，但引用的变量 bad_sin 因其 expression sin('not_a_number') 存在类型错误，推断结果类型不匹配 number 期望。类型错误应沿 #var 引用链传播到属性赋值点）
  ```
  SEM-TYPE-001 (line 18): 类型不匹配，期望number但变量#bad_sin的表达式返回值类型不匹配（属性 alpha）
  ```
- **Actual**: 无 SEM-TYPE-001 诊断（仅 SEM-ATTR-001 for alpha 范围错误）
  ```
  SEM-ATTR-001 (line 18): alpha值应在0-255范围内
  ```
- **Root cause**: TypeAnalyzer 的 `inferVariableRef()` 从 SymbolTable 获取 Var 声明类型时，`bad_sin` 的推断类型因 sin() 参数类型错误返回 `DslMixedType` 或 `DslErrorType`，但 TypeAnalyzer 未将此"带错误标记的类型"传播到 alpha 属性赋值点。类型传播链在 #var 引用处中断——只有简单类型冲突（如 string→number）能传播，带表达式错误的类型无法传播
- **影响文件**: `TypeAnalyzer.java`, `TypeInferenceEngine.java`

---

### Bug 16: SEM-TYPE-001 string Var 以 # 引用在 textExp 中不检出类型不匹配

- **症状**: Var type="string" 但用 `#` 前缀（数值访问方式）引用 string 变量 `color_dark` 时，类型不匹配不检出
- **DSL**: `multi_element_expression_blast.xml` lines 4, 62:
  ```xml
  <Var name="color_dark" type="string" const="true" expression="'#333333'"/>
  ...
  <Text name="big_mixed_text" textExp="substr(#color_dark, 2, 6)" category="Normal"/>
  ```
- **Expected**: SEM-TYPE-001（表达式类型不匹配规则：textExp 中的 substr(#color_dark, 2, 6) 使用 `#` 前缀引用 string 类型变量 color_dark，`#` 是数值访问前缀，期望 number 类型但实际引用的变量是 string 类型，应报告类型不匹配）
  ```
  SEM-TYPE-001 (line 62): 类型不匹配，#color_dark 是 string 类型但以数值访问前缀 # 引用
  ```
- **Actual**: 无诊断
- **Root cause**: TypeAnalyzer 对 textExp 属性的表达式类型推断未检测 `#` 前缀引用 string 变量的类型冲突。当 expression 使用 `#var` 引用 string 类型变量时，分析器可能将其自动转换为数值上下文，跳过类型检查
- **影响文件**: `TypeAnalyzer.java`

---

## 3. 表达式语法检查类

### Bug 17: SYN-EXPR-004 裸词字符串表达式不检出

- **症状**: `expression="hello world"`（无单引号包裹的裸词）在 type="string" Var 上不触发 SYN-EXPR-004
- **DSL**: `string_expression_errors.xml` line 5:
  ```xml
  <Var name="no_quote_string" type="string" expression="hello world"/>
  ```
- **Expected**: SYN-EXPR-004（字符串引号缺失规则：string 类型表达式必须使用单引号包裹字符串字面量。裸词 "hello world" 缺少单引号，既不是合法的变量引用（无 #/@ 前缀），也不是合法的字符串字面量，ANTLR 解析器无法识别为有效表达式）
  ```
  SYN-EXPR-004 (line 5): 字符串表达式未使用单引号: hello world
  ```
- **Actual**: 无诊断（完全缺失）
- **Root cause**: ExpressionSyntaxChecker 的 SYN-EXPR-004 检查逻辑仅针对含单引号但不闭合的情况（`'unclosed`），不针对完全无引号的裸词。当 expression 不含任何单引号字符时，检查器跳过引号检查。需要增加对 type="string" 表达式中无引号裸词的检测
- **影响文件**: `ExpressionSyntaxChecker.java`

---

### Bug 18: SEM-TYPE-003 被归类为 SYN-EXPR-ANTLR（字符串字面量在 numeric 属性中）

- **症状**: `x="'string_in_number'"` 或 `alpha="#multiplier + 'not_num'"` 等字符串字面量出现在 numeric 属性中，被报告为 SYN-EXPR-ANTLR 而非 SEM-TYPE-003
- **DSL 1**: `deep_nesting_violations.xml` line 13:
  ```xml
  <Image name="deepest_img" x="'string_in_number'" .../>
  ```
- **DSL 2**: `multi_element_expression_blast.xml` line 42:
  ```xml
  <Image name="weird_alpha" alpha="#multiplier + 'not_num'" .../>
  ```
- **Expected**: SEM-TYPE-003（字面量类型错误规则：属性 x/alpha 期望 number 类型，但表达式值包含字符串字面量 'string_in_number' 或 'not_num'，字面量类型与属性期望类型不匹配）
  ```
  SEM-TYPE-003 (line 13): 属性值类型错误: x 期望 number, 实际 'string_in_number'
  SEM-TYPE-003 (line 42): 属性值类型错误: alpha 期望 number, 实际包含字符串字面量 'not_num'
  ```
- **Actual**: SYN-EXPR-ANTLR（表达式语法错误——ANTLR 解析失败）
  ```
  SYN-EXPR-ANTLR (line 24): 表达式语法错误: 'string_in_number'
  SYN-EXPR-ANTLR (line 46): 表达式语法错误: #multiplier + 'not_num'
  ```
- **Root cause**: 当 ANTLR 解析失败时（字符串字面量在 numeric 上下文中无法解析为有效数值表达式），ExpressionSyntaxChecker 或 LiteralTypeAnalyzer 产出 SYN-EXPR-ANTLR 而非 SEM-TYPE-003。语义层类型检查应在语法层 ANTLR 失败后补充执行，将 ANTLR 无法解析的值视为字面量进行类型检查
- **影响文件**: `LiteralTypeAnalyzer.java`, `ExpressionSyntaxChecker.java`

---

### Bug 19: SYN-EXPR-004 引号检查结果被归类为 SYN-EXPR-ANTLR

- **症状**: 未闭合单引号和嵌套单引号的字符串表达式，ExpressionSyntaxChecker 检出了问题但产出的 Rule ID 是 SYN-EXPR-ANTLR 而非 SYN-EXPR-004
- **DSL 1**: `expression_syntax_errors.xml` line 6（未闭合引号）:
  ```xml
  <Var name="unclosed_quote" type="string" expression="'hello world"/>
  ```
- **DSL 2**: `expression_syntax_errors.xml` line 26（嵌套引号）:
  ```xml
  <Text name="bad_str_quote" textExp="'Nested 'inner' quote'"/>
  ```
- **Expected**: SYN-EXPR-004（字符串引号语法错误规则：字符串表达式中的单引号必须正确闭合，嵌套单引号需使用 `\'` 转义。未闭合引号和嵌套引号是字符串语法层面的错误，应归类为 SYN-EXPR-004 而非通用的 ANTLR 解析失败）
  ```
  SYN-EXPR-004 (line 6): 未闭合单引号: 'hello world
  SYN-EXPR-004 (line 26): 嵌套单引号未转义: 'Nested 'inner' quote'
  ```
- **Actual**: SYN-EXPR-ANTLR（通用ANTLR解析错误）
  ```
  SYN-EXPR-ANTLR (line 7): 表达式语法错误: 'hello world
  SYN-EXPR-ANTLR (line 29): 表达式语法错误: 'Nested 'inner' quote'
  ```
- **Root cause**: ExpressionSyntaxChecker 的引号检查逻辑检测到了问题，但产出的诊断使用了 SYN-EXPR-ANTLR Rule ID 而非 SYN-EXPR-004。引号检查在 ANTLR 解析前执行，但结果被合并到 ANTLR 错误通道而非独立产出 SYN-EXPR-004。需分离引号检查结果和 ANTLR 解析结果，使用不同的 Rule ID
- **影响文件**: `ExpressionSyntaxChecker.java`

---

### Bug 20: SEM-TYPE-003 被归类为 SEM-TYPE-001（number expression 赋值给 string Var）

- **症状**: number 表达式赋值给 string 类型 Var 时，报告 SEM-TYPE-001 而非 SEM-TYPE-003
- **DSL 1**: `variable_lifecycle_errors.xml` line 13:
  ```xml
  <Var name="type_mismatch" type="string" expression="100 + 50"/>
  ```
- **DSL 2**: `type_inference_edge_cases.xml` line 12:
  ```xml
  <Var name="no_type_expr" type="string" expression="#valid_num + 10"/>
  ```
- **Expected**: SEM-TYPE-003（字面量/简单表达式类型错误规则：表达式值 100+50 或 #valid_num+10 的推断结果类型为 number，但 Var 声明类型为 string，这是字面量/简单值的类型不匹配，应归类为 SEM-TYPE-003 以区分于复杂的表达式类型推断不匹配 SEM-TYPE-001）
  ```
  SEM-TYPE-003 (line 13): 属性值类型错误: Var type=string 但表达式返回 number
  ```
- **Actual**: SEM-TYPE-001（表达式类型推断不匹配）
  ```
  SEM-TYPE-001 (line 15): 类型不匹配，期望string类型但表达式的返回值类型为number
  ```
- **Root cause**: TypeAnalyzer 和 LiteralTypeAnalyzer 的职责边界不清晰——当表达式能被 ANTLR 解析时，TypeAnalyzer 处理并产出 SEM-TYPE-001；当表达式是简单值且类型明确不匹配时，应由 LiteralTypeAnalyzer 产出 SEM-TYPE-003。但当前 LiteralTypeAnalyzer 的触发条件与 TypeAnalyzer 重叠，导致部分本应归类为 SEM-TYPE-003 的场景被 TypeAnalyzer 以 SEM-TYPE-001 处理
- **影响文件**: `LiteralTypeAnalyzer.java`, `TypeAnalyzer.java`

---

### Bug 21: SYN-EXPR-002 计算结果精度溢出不检出

- **症状**: 表达式 `5000000 + 5000000` 的运算结果概念上为 10000000（8位），超过7位精度限制，但不触发 SYN-EXPR-002
- **DSL**: `precision_boundary_tests.xml` line 8:
  ```xml
  <Var name="bad_result_8digit" type="number" expression="5000000 + 5000000"/>
  ```
- **Expected**: SYN-EXPR-002 WARNING（数值精度溢出规则：数值表达式的运算结果超过7位有效数字精度限制时应报告警告。每个操作数 5000000 本身为7位（合法），但相加结果 10000000 为8位，超出 DSL 引擎的7位精度限制）
  ```
  SYN-EXPR-002 WARNING (line 8): 数值表达式值超过7位精度限制: 5000000 + 5000000 → 10000000
  ```
- **Actual**: 无诊断
- **Root cause**: ExpressionSyntaxChecker 的 SYN-EXPR-002 检查仅扫描表达式中的字面量数值位数，不进行 compile-time 常量表达式求值。`5000000` 和 `5000000` 各为7位字面量（不触发），但相加结果为8位。需要实现 compile-time 常量折叠求值并检查结果精度
- **影响文件**: `ExpressionSyntaxChecker.java`

---

### Bug 22: bogusFunc 未知函数被归类为 SEM-TYPE-001 而非 SYN-EXPR-ANTLR/SEM-REF-001

- **症状**: 表达式使用未定义函数名 `bogusFunc(1, 2)` 时，报告 SEM-TYPE-001 而非 SYN-EXPR-ANTLR 或 SEM-REF-001
- **DSL**: `expression_syntax_errors.xml` line 7:
  ```xml
  <Var name="invalid_func" type="number" expression="bogusFunc(1, 2)"/>
  ```
- **Expected**: SYN-EXPR-ANTLR（表达式语法错误规则：bogusFunc 不是 DSL 定义的合法函数名，ANTLR 解析器无法识别此函数调用，应归类为语法层面的表达式错误）或 SEM-REF-001（未知函数引用规则：函数名 bogusFunc 在函数签名库中不存在，类似引用未定义变量）
  ```
  SYN-EXPR-ANTLR (line 7): 未知函数 bogusFunc 不在合法函数定义集中
  ```
- **Actual**: SEM-TYPE-001（表达式类型不匹配）
  ```
  SEM-TYPE-001 (line 8): 函数 bogusFunc 不适用于 number 表达式
  ```
- **Root cause**: TypeAnalyzer 在检查函数调用时，先在 FunctionSignatureLibrary 中查找函数名。当函数名不存在时，TypeAnalyzer 将其视为"函数不适用于当前表达式类型"并产出 SEM-TYPE-001，而非报告函数名本身不存在。语义层类型推断覆盖了语法层应报告的"未知函数名"错误
- **影响文件**: `TypeAnalyzer.java`

---

## 4. 变量引用类

### Bug 23: SEM-REF-001 前向引用在 Image 属性表达式中不检出

- **症状**: Image 的 `x="#later_declared"` 引用在其后才声明的变量，不报告 SEM-REF-001 前向引用错误
- **DSL**: `variable_lifecycle_errors.xml` line 19:
  ```xml
  <Image name="early_ref" x="#later_declared" y="0" width="1080" height="1920" src="early.png"/>
  ...
  <Var name="later_declared" type="number" const="true" expression="540"/>
  ```
- **Expected**: SEM-REF-001（未定义/前向引用变量规则：变量 later_declared 在使用点 #later_declared（line 19）时尚未声明（声明在 line 22），属于前向引用。DSL 变量必须先声明后使用，前向引用会导致运行时取值为空或0）
  ```
  SEM-REF-001 (line 19): 前向引用变量 #later_declared（变量定义在使用之后）
  ```
- **Actual**: 无诊断
- **Root cause**: VarRefAnalyzer 的前向引用检测当前仅覆盖 Var 元素的 expression 属性中的 `#var` 引用，不覆盖 Image/Text 等非 Var 元素的属性表达式中的 `#var` 引用。当引用出现在 Image x 属性中时，分析器跳过前向引用检查
- **影响文件**: `VarRefAnalyzer.java`

---

### Bug 24: SEM-REF-003 第三层级重复变量名不检出

- **症状**: 同名变量 `dup_name` 在全局作用域出现2次后，在嵌套 Group 作用域中出现第3次，第3次不报告 SEM-REF-003
- **DSL**: `variable_lifecycle_errors.xml` lines 8-9, 27:
  ```xml
  <Var name="dup_name" type="number" const="true" expression="10"/>
  <Var name="dup_name" type="string" const="true" expression="'duplicate'"/>
  ...
  <Group name="container" ...>
      <Var name="dup_name" type="number" expression="999"/>
  </Group>
  ```
- **Expected**: SEM-REF-003（重复定义变量规则：变量名 dup_name 在同一可见作用域内重复定义。即使第3次定义在嵌套 Group 内，dup_name 已在父作用域定义2次，第3次定义仍然与父作用域同名，造成变量遮蔽和混淆）
  ```
  SEM-REF-003 (line 27): 重复定义变量 dup_name
  ```
- **Actual**: 仅报告前2个 dup_name 的 SEM-REF-003，第3个不报告
- **Root cause**: VarRefAnalyzer 的重复名检测可能仅在同一直接作用域内比对，不跨嵌套作用域层级检查。当 Group 内的 Var 与全局 Var 同名时，分析器认为"不同作用域允许同名"，不报告重复定义。但 DSL 规范要求变量名全局唯一或至少在可见作用域内唯一
- **影响文件**: `VarRefAnalyzer.java`

---

### Bug 25: SEM-REF-001→SEM-REF-002 元素属性引用归类偏差

- **症状**: `#ghost_img.actual_x` 和 `#ghost_elem.actual_x` 等元素属性引用被归类为 SEM-REF-002（元素引用）而非 SEM-REF-001（变量引用）
- **DSL 1**: `array_index_edge_cases.xml` line 15:
  ```xml
  <Var name="bad_elem_prop" expression="#ghost_img.actual_w * 2"/>
  ```
- **DSL 2**: `multi_element_expression_blast.xml` line 69:
  ```xml
  <Image name="propref_bad" x="#ghost_elem.actual_x" .../>
  ```
- **Expected**: SEM-REF-001（未定义变量引用规则：#ghost_img.actual_w 中的 ghost_img 是未声明的元素名，其属性 .actual_w 也无法解析。应归类为变量/引用层面的未定义错误 SEM-REF-001，因为 `#elem.prop` 是变量引用语法的一种形式）
  ```
  SEM-REF-001 (line 15): 引用未定义元素属性 #ghost_img.actual_w
  ```
- **Actual**: SEM-REF-002（未定义元素引用）
  ```
  SEM-REF-002 (line 17): 引用未定义元素 ghost_img
  ```
- **Root cause**: VarRefAnalyzer 正确区分了变量引用(#var)和元素属性引用(#elem.prop)，对 #elem.prop 形式的引用使用 SEM-REF-002 Rule ID。语义上此归类是合理的（引用的是元素而非变量），但 ANSWER_KEY 预期归类为 SEM-REF-001。需统一 Rule ID 归类约定：元素属性引用应使用 SEM-REF-002 还是 SEM-REF-001
- **影响文件**: `VarRefAnalyzer.java`

---

## 5. Command 规则类

### Bug 26: SEM-CMD-004 被归类为 SEM-TYPE-003（StyleCommand index 使用表达式）

- **症状**: `<StyleCommand index="#runtime_var"/>` 使用表达式作为 index 属性值时，报告 SEM-TYPE-003 而非 SEM-CMD-004
- **DSL**: `trigger_command_combos.xml` line 23:
  ```xml
  <StyleCommand name="style" index="#runtime_var"/>
  ```
- **Expected**: SEM-CMD-004（StyleCommand index表达式规则：StyleCommand 的 index 属性必须使用纯数字字面量（如 index="0"），不能使用表达式（如 index="#runtime_var"）。StyleCommand 在运行时需要立即确定目标样式的索引位置，表达式引用会导致无法在应用样式时确定目标）
  ```
  SEM-CMD-004 (line 23): StyleCommand index 属性必须为纯数字字面量，不能使用表达式 #runtime_var
  ```
- **Actual**: SEM-TYPE-003（属性值类型错误）
  ```
  SEM-TYPE-003 (line 23): 属性值类型错误: index 期望 number, 实际 #runtime_var
  ```
- **Root cause**: StyleCommand.json 中 index 属性的约束可能未定义 SEM-CMD-004 专用 Rule ID，或 ConstraintAnalyzer 未产出 CMD-004。当前 LiteralTypeAnalyzer 将 #runtime_var 在 index 上下文中检测为"非纯数字字面量"，产出通用的 SEM-TYPE-003 而非 Command 专用的 SEM-CMD-004
- **影响文件**: `StyleCommand.json`, `ConstraintAnalyzer.java`, `LiteralTypeAnalyzer.java`

---

### Bug 27: SEM-TYPE-002→SEM-TYPE-001 链式函数参数类型归类偏差

- **症状**: `sin(substr('hello', 0, 3))` 中 substr 返回 string 传给 sin(期望 number)，报告 SEM-TYPE-001 而非 SEM-TYPE-002
- **DSL**: `chained_function_hell.xml` line 7:
  ```xml
  <Var name="chained_bad_type" type="number" expression="sin(substr('hello', 0, 3))"/>
  ```
- **Expected**: SEM-TYPE-002（函数参数类型不匹配规则：sin() 函数期望 number 类型参数，但实际传入的是 substr() 的返回值，类型为 string。函数参数层面的类型不匹配应归类为 SEM-TYPE-002）
  ```
  SEM-TYPE-002 (line 7): 函数 sin 参数 1 类型不匹配，期望number实际string（substr返回值）
  ```
- **Actual**: SEM-TYPE-001（表达式整体类型不匹配）
  ```
  SEM-TYPE-001 (line 9): 函数 substr 不适用于 number 表达式
  ```
- **Root cause**: TypeAnalyzer 在检查链式函数调用时，先检查外层函数 sin() 的参数类型，发现 substr() 返回 string→产出类型不匹配。但 Rule ID 归类为 SEM-TYPE-001（表达式整体类型）而非 SEM-TYPE-002（函数参数类型）。原因可能是 TypeAnalyzer 将嵌套函数的返回类型检查统一归类为表达式类型不匹配
- **影响文件**: `TypeAnalyzer.java`

---

## 6. 误报类

### Bug 28: SEM-ATTR-003 对合法枚举值 "Charging" 误报

- **症状**: Text 元素的 `category="Charging"` 是合法枚举值，但被报告为 SEM-ATTR-003 不合法
- **DSL**: `enum_boundary_tests.xml` line 26-27:
  ```xml
  <Text name="txt_charging" x="200" y="600" size="20" color="#00FF00"
        text="Charging" category="Charging"/>
  ```
- **Expected**: 无诊断（category="Charging" 在合法枚举值 [Normal, Charging, BatteryLow, BatteryFull] 中）
- **Actual**: SEM-ATTR-003（category 枚举值不合法）
  ```
  SEM-ATTR-003 (line 26): category枚举值不合法，合法值为: Normal, Charging, BatteryLow, BatteryFull
  ```
- **Root cause**: SEM-ATTR-003 和 SEM-ENUM-001 可能存在重叠触发。Text.json 中 category 属性同时有 ConstraintAnalyzer 的 SEM-ATTR-003 约束和 EnumAnalyzer 的 SEM-ENUM-001 检查。当合法值 "Charging" 被 EnumAnalyzer 正确放行时，ConstraintAnalyzer 的 SEM-ATTR-003 约束条件可能误判（约束条件评估逻辑错误或与 EnumAnalyzer 的判断逻辑不一致）
- **影响文件**: `Text.json` (约束定义), `ConstraintAnalyzer.java`

---

### Bug 29: SEM-REQ-001 对 VariableCommand expression 属性误报必填

- **症状**: `<VariableCommand name="v1" type="set" value="1"/>` 缺少 expression 属性时，被报告为 SEM-REQ-001 缺失必填属性，但 expression 对 VariableCommand 并非必填
- **DSL**: `trigger_command_combos.xml` line 14:
  ```xml
  <VariableCommand name="v1" type="set" value="1"/>
  ```
- **Expected**: 无诊断（VariableCommand 使用 type="set" + value="1" 是合法写法，expression 属性非必填）
- **Actual**: SEM-REQ-001（缺失必填属性 expression）
  ```
  SEM-REQ-001 (line 14): 缺失必填属性: expression
  ```
- **Root cause**: `VariableCommand.json` 的 `requiredAttrs` 列表中可能包含了 `expression`，但 VariableCommand 的合法使用方式包括：`type="set" value="literal"`（直接赋值，无需 expression）和 `type="set" expression="#var"`（表达式赋值）。expression 应为可选属性而非必填
- **影响文件**: `VariableCommand.json`

---

### Bug 30: SEM-ENUM-001 对 VariableCommand type="set" 误报枚举值不合法

- **症状**: VariableCommand 的 `type="set"` 是合法枚举值，但被报告为 SEM-ENUM-001 不合法
- **DSL**: `trigger_command_combos.xml` line 14:
  ```xml
  <VariableCommand name="v1" type="set" value="1"/>
  ```
- **Expected**: 无诊断（VariableCommand type 属性合法值为 ["set", "add"]，"set" 是合法值）
- **Actual**: SEM-ENUM-001（枚举值错误: type=set, 合法值: [number, string]）
  ```
  SEM-ENUM-001 (line 14): 枚举值错误: type=set, 合法值: [number, string]
  ```
- **Root cause**: `VariableCommand.json` 中 type 属性的 `enumValues` 定义为 `["number", "string"]`（这是 Var 元素的 type 枚举值，而非 VariableCommand 的 type 枚举值）。VariableCommand 的 type 应为 `["set", "add"]`（操作类型），但规则 JSON 错误地继承了 Var 的 type 枚举定义
- **影响文件**: `VariableCommand.json`

---

## 统计数据

| 指标 | 数值 |
|------|------|
| 已修复 Bug | 10 (Bug 1–10) |
| 未修复 Bug | 19 (Bug 12–30) |
| 14 fixture ANSWER_KEY 总预期诊断 | ~107 |
| 14 fixture Jar 实测总诊断 | 102 (96E+6W) |
| 内容实质性匹配率 | ~76% (81/107) |
| 含 Rule ID 偏差匹配率 | ~87% (93/107) |
| 完全匹配 fixture | 3/14 |
| 零诊断 fixture | 0/14 |
| 误报率 | 3/102 ≈ 3% |
| 测试方法 | `java -jar dsl-analyzer.jar --format markdown --output <path> <fixture>` |
| 测试环境 | 分支 `feat/cli-pipeline-integration`, HEAD=`1786677` |

---

## 经验教训

1. **ANTLR grammar 设计**: 函数调用的参数应支持完整的表达式语法，包括花括号内的嵌套表达式
2. **双来源数据对齐**: SAX 和手动扫描器的属性顺序必须一致，应按名称匹配而非索引匹配
3. **RuleDsl 语法扩展性**: 新增运算符（如 MATCHES）需要同步更新 grammar、evaluator 和 parser
4. **Classpath vs Filesystem**: JAR 部署时必须使用 classpath 资源加载
5. **Analyzer 注册**: 所有 Analyzer 必须显式注册到 AnalyzerRegistry
6. **Constraint 条件简化**: 复杂函数调用应在 evaluator 中实现，保持条件纯声明式
7. **TypeAnalyzer 依赖链**: TypeAnalyzer → FunctionSignatureLibrary → RuleRepository，断链会导致全链失效
8. **parseInt() 批量修复**: Bug 5 修复了 Image.json，但 Group.json/Text.json 可能存在相同问题
9. **PERSIST 检测范围**: SEM-PERSIST-001 应检查 expression 内容中引用的变量名，而非仅检查 Var 的 name 属性
10. **ScopeAnalyzer 优先级**: Scope 检查目前低于约束检查，导致 Button in ChargingSkin 等错误先行被其他规则掩盖
11. **Rule ID 归类一致性**: 同一问题在不同分析路径下可能产出不同 Rule ID，需统一归类策略
12. **行号定位精度**: SAX/StAX 行号与 XML 源文件行号存在系统性偏差，需在 AST 构建层对齐
13. **children.filter 空列表处理**: 约束预处理需正确处理子元素列表为空的情况（Button 无子元素、Triggers 无 Trigger）
14. **类型传播链完整性**: #var 引用的类型错误需沿引用链传播到属性赋值点，不能在引用处中断
15. **规则 JSON 枚举值准确性**: 元素的枚举值定义必须反映该元素的语义（VariableCommand.type=["set","add"] 而非 ["number","string"]）
