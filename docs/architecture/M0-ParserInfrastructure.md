# M0 解析器基础设施模块 - 架构设计

## 1. 模块职责

ANTLR4 grammar定义 + 自动生成的表达式解析器 + 规则DSL条件解析器 + 函数签名库JSON加载与查询。仅解析层基础设施，不含分析逻辑。

**单一职责**：为M3语法分析和M4语义分析提供表达式/条件解析能力与函数签名数据。

## 2. 三层划分

| 层级 | 功能 | 说明 |
|---|---|---|
| **Core** | DslExpression.g4 + DslRuleCondition.g4 + 自动生成代码 + 函数签名库JSON加载 | MVP必交 |
| **Extension** | 函数签名库热更新（监听JSON文件变更，自动重建缓存） | 性能与维护增强 |
| **Optional** | 自定义运算符扩展（用户可在.g4中新增运算符并重新生成） | 后续迭代 |

## 3. 核心组件

### 3.1 DslExpression.g4 — DSL表达式语法

**解析范围**：仅对标记为expression/reference类型或显式包含表达式语法（#var、@var、函数调用）的属性值做解析。纯字面量属性（x="100"）直接走字面量验证，不走解析器。

**Grammar文件位置**：`core/expression/grammar/DslExpression.g4`

**核心规则**：

```
grammar DslExpression;

expression : conditionalExpr | binaryExpr | unaryExpr | functionCall | variableRef | literal ;

conditionalExpr : 'ifelse' '(' exprList ')' ;
binaryExpr : left=expression op=('+'|'-'|'*'|'/'|'%') right=expression ;
unaryExpr : 'not' '(' expression ')' ;
functionCall : ID '(' exprList ')' ;
variableRef : '#' ID | '@' ID | '#' ID '[' expression ']' ;
literal : NUMBER | STRING | BOOLEAN ;

exprList : expression (',' expression)* ;
```

**词法规则要点**：

| Token | 定义 | 说明 |
|---|---|---|
| NUMBER | 整数/浮点数 | 支持0、负数、小数 |
| STRING | 单引号字符串 `'...'` | 字符串字面量 |
| BOOLEAN | `'true'` / `'false'` | 布尔字面量 |
| ID | `[a-zA-Z_][a-zA-Z0-9_]*` | 函数名/变量名 |

**自动生成代码**：DslExpressionLexer、DslExpressionParser、DslExpressionVisitor、DslExpressionBaseVisitor。生成代码位于`core/expression/generated/`。

**ExpressionNode数据模型**：

```java
@Data
@Builder
public class ExpressionNode implements ExpressionAstNode {
    ExpressionKind kind;                     // 表达式类别: number/string
    String operator;                         // 运算符(二元/一元表达式)
    List<ExpressionNode> children;           // 子表达式列表
    String functionName;                     // 函数名(函数调用表达式)
    String variableName;                     // 变量名(变量引用表达式)
    String literalValue;                     // 字面量值(字面量表达式)
    int line;                                // 源码行号
    int column;                              // 源码列号
}
```

> **设计决策(DIP修正)**：ExpressionAstNode是定义在`shared/ast/`包的抽象接口，ExpressionNode在M0中实现该接口。这使M3的DslAttributeValueNode可以引用抽象接口ExpressionAstNode而非M0具体类ExpressionNode，避免M3对M0实现细节的直接依赖。

### 3.2 DslRuleCondition.g4 — 规则DSL条件语法

**Grammar文件位置**：`core/ruledsl/grammar/DslRuleCondition.g4`

**核心规则**：

```
grammar DslRuleCondition;

condition : logicExpr ;

logicExpr : logicExpr op=('AND'|'OR') compareExpr | NOT logicExpr | compareExpr ;

compareExpr : valueExpr op=('=='|'!='|'>'|'<'|'>='|'<=') valueExpr
            | valueExpr 'IN' setLiteral
            | valueExpr 'NOT' 'IN' setLiteral ;

valueExpr : elementAttr | literal | 'null' | 'true' | 'false' ;

elementAttr : 'element.attrs[' STRING ']' | 'element.tagName' | 'element.parent.tagName' ;

setLiteral : '[' literal (',' literal)* ']' ;

literal : NUMBER | STRING ;
```

**重要边界**：规则DSL不做类型推断。typeOf()不在语法中——类型推断完全由M4 TypeInferenceEngine驱动。规则DSL职责边界为：属性存在性、值比较、集合包含、逻辑组合。

**自动生成代码**：DslRuleConditionLexer、DslRuleConditionParser、DslRuleConditionVisitor、DslRuleConditionBaseVisitor。生成代码位于`core/ruledsl/generated/`。

**示例约束条件**：

```json
{
  "ruleId": "SEM-CMD-001",
  "condition": "element.attrs['play'] != null AND element.attrs['sound'] != null",
  "message": "VideoCommand中play和sound互斥，不能同时存在",
  "severity": "error",
  "suggestedFixes": ["移除play属性", "移除sound属性"]
}
```

### 3.3 ANTLR4自动生成代码

**Gradle集成**：build.gradle添加antlr4插件，配置自动生成任务。

| 配置项 | 说明 |
|---|---|
| 插件 | `org.antlr/antlr4-plugin` |
| .g4位置 | 各包grammar/子目录 |
| 生成代码位置 | 各包generated/子目录 |
| 生成包名 | 与grammar包名一致 |
| runtime依赖 | `org.antlr:antlr4-runtime` |

**4组×2 grammar = 8个自动生成类**：

| Grammar | Lexer | Parser | Visitor | BaseVisitor |
|---|---|---|---|---|
| DslExpression.g4 | DslExpressionLexer | DslExpressionParser | DslExpressionVisitor | DslExpressionBaseVisitor |
| DslRuleCondition.g4 | DslRuleConditionLexer | DslRuleConditionParser | DslRuleConditionVisitor | DslRuleConditionBaseVisitor |

**不手写解析器**：所有词法/语法分析由ANTLR4自动生成，不维护手写递归下降代码。开发者只需维护.g4 grammar文件。

### 3.4 DslExpressionVisitorAdapter

将ANTLR4 Visitor输出转换为M3内部ExpressionNode子树。M3调用DslExpressionParser解析属性值后，通过此Adapter将ANTLR4 ParseTree转换为M3 AST中的ExpressionNode。

**Adapter职责**：

- 遍历ANTLR4 ParseTree，提取表达式语义信息
- 构建M3 AST所需的ExpressionNode子树节点
- 保留行列号定位信息（从ParseTree Token提取）

**调用链**：M3 → DslExpressionParser.parse(rawValue) → ParseTree → DslExpressionVisitorAdapter.visit(ParseTree) → ExpressionNode子树 → 存入DslAttributeValueNode.expression

### 3.5 RuleDslEvaluator

使用DslRuleConditionVisitor解释执行规则库中的声明式约束条件。M4 ConstraintAnalyzer调用此Evaluator执行条件求值。

**执行机制**：

1. 遍历AST每个DslElement
2. 从RuleRepository获取该元素的RuleConstraint列表
3. 对每个constraint.condition字符串：
   - 用DslRuleConditionParser解析condition → ParseTree
   - 使用visitor模式，将element引用替换为当前AST节点的属性值
   - 递归求值条件表达式
   - 条件为true → 产出Diagnostic（使用constraint.ruleId + message + severity + suggestedFixes）

**element引用替换**：

| 规则DSL语法 | 替换逻辑 | 示例 |
|---|---|---|
| `element.attrs['xxx']` | 从当前DslElementNode获取属性xxx的值，属性不存在则为null | `element.attrs['play'] != null` → 检查VideoCommand是否有play属性 |
| `element.tagName` | 当前DslElementNode的tagName | `element.tagName == 'VideoCommand'` |
| `element.parent.tagName` | 当前DslElementNode父节点的tagName | `element.parent.tagName == 'Lockscreen'` |

### 3.6 FunctionSignature数据模型

**存储方式**：JSON文件，存放于`resources/functions/`目录，零代码扩展。

```java
@Data
@Builder
public class FunctionSignature {
    String name;
    List<FunctionParam> params;
    DslType returnType;
    String expressionKind;          // "number" | "string"
}

@Data
@Builder
public class FunctionParam {
    String name;
    DslType type;
    boolean isVariadic;
}
```

**DslType与JSON映射**：JSON中type字段为字符串（"number"/"string"/"number[]"/"string[]"), 加载时映射为DslType子类实例（DslNumberType/DslStringType/DslArrayType)。

> Enum/Boolean由AttrTypeSpec.enumValues承载值约束, Void在DSL表达式中无使用场景, 故不作为DslType子类。

**示例JSON定义**：

```json
{
  "functions": [
    {"name":"ifelse","params":[{"name":"cond","type":"number"},{"name":"y","type":"number"},{"name":"z","type":"number","variadic":true}],"returnType":"number","expressionKind":"number"},
    {"name":"ifelse","params":[{"name":"cond","type":"number"},{"name":"y","type":"string"},{"name":"z","type":"string","variadic":true}],"returnType":"string","expressionKind":"string"},
    {"name":"sin","params":[{"name":"x","type":"number"}],"returnType":"number","expressionKind":"number"},
    {"name":"substr","params":[{"name":"str","type":"string"},{"name":"pos","type":"number"},{"name":"len","type":"number"}],"returnType":"string","expressionKind":"string"}
  ]
}
```

**同名函数多签名**：ifelse等函数支持多个签名（不同参数类型组合），按expressionKind区分。查询时按(name, expressionKind)匹配。

### 3.7 FunctionSignatureLibrary

JSON加载 + 查询接口。从`resources/functions/`目录加载所有JSON文件，构建内存索引。

```java
public interface FunctionSignatureLibrary {
    Optional<FunctionSignature> getSignature(String name, String expressionKind);
    List<FunctionSignature> getSignatures(String name);
    boolean hasFunction(String name);
}
```

> **设计决策(DIP修正)**：FunctionSignatureLibrary从具体类改为接口, 定义在`core/expression/`包; 实现类(JsonFunctionSignatureLoader等)放在`core/function/`包, 骨架阶段延后。

**加载时机**：CLI启动时预加载；IDEA插件在plugin初始化时预加载。

**缓存管理**（Extension层）：监听JSON文件变更，自动重建functionIndex。

## 4. 模块依赖

| 上游依赖 | 说明 |
|---|---|
| 无 | 解析层基础设施，无上游依赖 |

| 下游消费 | 提供接口 | 说明 |
|---|---|---|
| M3 语法分析 | DslExpressionParser + DslExpressionVisitorAdapter | 属性值表达式解析 |
| M4 语义分析 | DslRuleConditionParser + RuleDslEvaluator | 声明式约束条件执行 |
| M4 语义分析 | FunctionSignatureLibrary | 函数签名查询（类型推断用） |

## 5. CLI相关

### 5.1 CLI命令调用

M0无独立CLI命令入口，作为CLI管线的基础依赖模块被间接调用：

```
java -jar dsl-analyzer.jar [options] <file-or-directory>
```

M0在CLI管线中的位置：

```
文件输入 → M1识别 → M3语法分析(调用M0表达式解析器) → M4语义分析(调用M0规则DSL解析器+函数签名库) → 输出
```

### 5.2 CLI参数与M0的关系

| 参数 | 影响范围 | M0相关说明 |
|---|---|---|
| `--rule-dir <path>` | M2规则库目录 | 函数签名库JSON默认在`resources/functions/`；自定义规则目录需包含对应的`functions/`子目录 |
| `--type-check` | M4类型推断开关 | 开启时M4调用FunctionSignatureLibrary进行函数签名验证；关闭时跳过函数签名检查 |
| `--verbose` | 详细输出 | 开启时CLI输出包含M0表达式解析过程信息（如：表达式AST结构、函数签名匹配详情） |

### 5.3 CLI输出中M0的贡献

M0不直接产出CLI可见输出，但通过下游模块间接贡献：

| CLI输出字段 | 来源路径 | M0贡献 |
|---|---|---|
| `ruleId: SEM-EXPR-ANTLR` | M3语法分析 → M0表达式解析 | 表达式解析失败时（ANTLR4解析错误），M3产出此诊断 |
| `ruleId: SEM-TYPE-002` | M4类型推断 → FunctionSignatureLibrary | 函数签名不匹配时，M4产出此诊断 |
| `ruleId: SEM-CMD-xxx` | M4 ConstraintAnalyzer → RuleDslEvaluator | 规则DSL条件为true时，M4产出此诊断 |

### 5.4 CLI异常场景

| 异常场景 | 退出码 | 说明 |
|---|---|---|
| 函数签名库JSON加载失败 | 2 | `resources/functions/`目录不存在或JSON格式错误 |
| 自定义规则目录缺少functions子目录 | 1（降级运行） | 使用内置函数签名库继续运行，终端输出warning提示 |

## 6. 设计要点

- **ANTLR4仅用于表达式和规则DSL**：XML结构解析使用dom4j，不使用ANTLR4；纯字面量属性直接验证不走解析器
- **规则DSL不做类型推断**：typeOf()不在语法中，类型推断归M4 TypeInferenceEngine；规则DSL职责边界为属性存在性、值比较、集合包含、逻辑组合
- **函数签名库独立于M2规则库**：JSON文件存放在`resources/functions/`，与规则库JSON同级但不归M2管理；M2只存储RuleConstraint数据，执行归RuleDslEvaluator
- **不手写解析器**：所有词法/语法分析由ANTLR4自动生成，开发者只维护.g4 grammar文件
- **同名函数多签名**：ifelse等函数支持不同参数类型组合的多个签名，按expressionKind区分
- **Gradle自动生成**：antlr4插件配置.g4→generated/自动生成流程，构建时自动触发
- **Core层无IDEA依赖**：ANTLR4 runtime打包进CLI fat jar，不依赖IDEA环境
