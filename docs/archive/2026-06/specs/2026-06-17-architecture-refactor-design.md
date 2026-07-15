---
module_ids: [CORE]
doc_kind: spec
status: superseded
created: 2026-06-17
---
# 主题引擎DSL静态分析工具 - 架构重构设计文档

**日期**: 2026-06-17
**状态**: 待用户审查

## 1. 重构背景与动机

原架构以IDEA插件为唯一交付形态，分析逻辑（M3/M4）直接依赖PSI API，导致：
- 无法脱离IDEA独立运行，无法提供CLI分析能力
- 表达式检查停留在模式匹配层面，无法做类型推断
- 新增检测逻辑必须编写Analyzer代码，无法零代码扩展
- 变量引用只做存在性校验，不支持跳转定义/查找引用/重命名

本次重构引入5项核心能力拓展，同时将分析核心与IDEA UI解耦。

## 2. 关键决策记录

| # | 决策 | 选择 | 理由 |
|---|---|---|---|
| 1 | CLI能力边界 | 分析能力CLI化，交互能力仅插件 | Quick Fix/悬浮/跳转等依赖IDEA UI，但语法/语义/类型检查可独立运行 |
| 2 | 类型系统边界 | 类型推断+签名验证（不做常量折叠/符号执行） | 类比TypeScript类型检查，不做运行时求值 |
| 3 | 零代码扩展 | 声明式规则DSL（对象属性访问式语法） | 复用M0表达式解析器，规则编写者用element.attrs['play'] != null描述条件 |
| 4 | 变量信息与导航 | 全部纳入（悬浮变量信息+跳转定义+查找引用+重命名重构） | 符号表在Core层构建，PsiReference在Plugin层实现 |
| 5 | 项目架构 | 单项目包名隔离（core/plugin在feature/analysis内） | 插件在core基础上开发，CLI jar只打包core包 |
| 6 | CLI命令 | 单命令多参数模式 | 类比eslint/clang-tidy |
| 7 | CLI输出 | JSON stdout+终端彩色+报告文件+退出码语义 | 兼顾CI/CD流水线与人工阅读 |
| 8 | 表达式解析范围 | 仅expression/reference类型属性走解析器 | 纯字面量属性直接验证，避免不必要性能开销 |

## 3. 项目结构

```
feature/analysis/src/main/java/com/huawei/theme/analysis/
├── core/                       ← 无IDEA依赖，CLI jar只打包这部分
│   ├── ast/                    ← AST节点定义 + AST构建器
│   ├── expression/             ← M0: DslExpression.g4 + ANTLR4生成代码
│   ├── ruledsl/                ← M0: DslRuleCondition.g4 + ANTLR4生成代码 + RuleDslEvaluator
│   ├── fileidentification/     ← M1: DSL文件识别
│   ├── rulelibrary/            ← M2: 规则数据模型 + JSON加载 + RuleRepository
│   ├── syntaxanalysis/         ← M3: dom4j XML解析 + 独立AST构建 + 语法错误
│   ├── semanticanalysis/       ← M4: 分析引擎 + 类型推断引擎 + 符号表 + 函数签名库 + 约束检查
│   ├── quickfix/               ← M5: 修复逻辑（纯文本操作描述）
│   ├── batchinspection/        ← M7: 批量扫描 + 报告导出
│   ├── diagnostic/             ← Diagnostic数据模型（跨模块共享）
│   └── cli/                    ← CLI入口 + 参数解析 + 输出格式化
│
├── plugin/                     ← 依赖IDEA SDK + 依赖core层
│   ├── psiadapter/             ← DslAst ↔ PsiElement 双向桥接
│   ├── navigation/             ← M8: PsiReference + 跳转 + 查找引用 + 重命名
│   ├── ui/                     ← M6: Annotator + DocumentationProvider + ToolWindow + 右键菜单
│   ├── quickfixui/             ← M5-UI: IntentionAction桥接 + 候选对话框 + diff预览
│   └── language/               ← DslLanguage + DslParserDefinition注册
│
feature/analysis/src/main/resources/
├── rules/                      ← 规则库JSON文件（元素规则+命令规则+全局变量）
├── functions/                  ← 函数签名库JSON文件
└── plugin.xml                  ← IDEA插件配置（仅plugin层需要）
```

**构建策略**：
- CLI jar：自定义Gradle task，只打包core/**，排除plugin/**，不含IDEA SDK依赖
- IDEA插件：标准intellij plugin build，包含全部代码
- 隔离保障：Gradle task扫描core包内无com.intellij import，编译期验证

## 4. 模块体系（10个模块）

### dsl-analyzer-core 模块

| 模块 | 职责 | 新增/重构说明 |
|---|---|---|
| M0 解析器基础设施 | ANTLR4 .g4 grammar + 自动生成的表达式解析器 + 规则DSL解析器 | 全新模块，仅解析层，无分析逻辑 |
| M1 文件识别 | DSL文件识别与过滤 | 接口重构：VirtualFile/PsiFile → String filePath + String content |
| M2 规则库 | 规则条目存储+查询+规则DSL条件数据 | 模型增强：新增RuleConstraint、AttrTypeSpec增加supportsExpression/expressionKind |
| M3 语法分析 | 独立AST构建+语法错误检测 | 完全重构：自有AST替代PSI依赖，表达式子树由M0解析器生成 |
| M4 语义分析与类型系统 | 语义检查+类型推断+函数签名库+符号表+规则DSL解释器执行 | 深度重构：原M0的分析层并入M4，类型推断在M3之后 |
| M5 修复逻辑 | 修复策略生成（纯文本操作描述，无UI） | 接口重构：PsiElement → DslAstNode + 文本范围 |
| M7 批量检查与报告 | 批量扫描+报告导出 | 接口重构：使用core抽象而非PSI |

### dsl-intellij-plugin 模块

| 模块 | 职责 | 说明 |
|---|---|---|
| PSI Adapter | DslAst ↔ PsiElement 双向桥接 | 全新模块，core与IDEA的桥梁 |
| M6 UI交互 | Annotator+悬浮（含变量信息）+面板+右键菜单 | 增强悬浮：扩展为非错误场景也响应 |
| M8 导航与重构 | PsiReference+跳转+查找引用+重命名重构 | 全新模块，仅Plugin层 |
| M5-UI | Quick Fix交互UI | IntentionAction桥接+候选对话框+diff预览 |

### 模块依赖关系

```
dsl-analyzer-core:
    M0 ← 无上游依赖（ANTLR4解析器基础设施）
    M1 ← M2 (获取根元素集合)
    M2 ← 独立（纯数据层）
    M3 ← M0 (表达式解析器) + M2 (合法元素名)
    M4 ← M0 (规则DSL解析器) + M2 (规则+约束条件) + M3 (AST) + 内含类型推断引擎+符号表+函数签名库
    M5 ← M4 (符号表+诊断) + M2 (修复建议数据)
    M7 ← M1 (文件过滤) + M2 (规则) + M3 (AST) + M4 (诊断)
    CLI ← M1+M3+M4+M7 (组合入口)

dsl-intellij-plugin:
    PSI Adapter ← core M3 (AST转PSI) + M4 (符号表→引用定位)
    M6 ← PSI Adapter + core M4 (诊断) + M5 (修复注册)
    M8 ← PSI Adapter (符号解析) + M4 (符号表)
    M5-UI ← core M5 (修复逻辑) + PSI Adapter (PSI操作)
```

## 5. M0 解析器基础设施 — 详细设计

### 5.0 ANTLR4集成

**采用ANTLR4**替代手写递归下降解析器，用于DSL表达式和规则DSL条件表达式的词法分析和语法分析阶段。XML结构解析仍使用dom4j（不使用ANTLR4）。

**Gradle集成**：添加antlr4插件到build.gradle，.g4 grammar文件放在对应包的grammar/子目录下，生成代码放在generated/子目录下。

**两个.g4 grammar文件**：
- `DslExpression.g4`：DSL表达式语法（数值表达式+字符串表达式）
- `DslRuleCondition.g4`：规则DSL条件表达式语法

### 5.1 DslExpression.g4 — DSL表达式解析

**解析范围**：仅对标记为expression/reference类型或显式包含表达式语法（#var、@var、函数调用）的属性值做解析。纯字面量属性（x="100"）直接走字面量验证。

**ANTLR4 grammar核心规则**：

```
expression : conditionalExpr | binaryExpr | unaryExpr | functionCall | variableRef | literal ;

conditionalExpr : 'ifelse' '(' exprList ')' ;
binaryExpr : left=expression op=('+'|'-'|'*'|'/'|'%') right=expression ;
unaryExpr : 'not' '(' expression ')' ;
functionCall : ID '(' exprList ')' ;
variableRef : '#' ID | '@' ID | '#' ID '[' expression ']' ;
literal : NUMBER | STRING | BOOLEAN ;

exprList : expression (',' expression)* ;
```

**ANTLR4自动生成**：DslExpressionLexer、DslExpressionParser、DslExpressionVisitor、DslExpressionBaseVisitor。

**M3调用方式**：M3在构建DslAttributeValueNode时，对expression/reference类型属性调用DslExpressionParser，将解析结果存入DslAttributeValueNode.expression字段。

### 5.2 DslRuleCondition.g4 — 规则DSL条件解析

**ANTLR4 grammar核心规则**：

```
condition : logicExpr ;
logicExpr : logicExpr op=('AND'|'OR') compareExpr | NOT logicExpr | compareExpr ;
compareExpr : valueExpr op=('=='|'!='|'>'|'<'|'>='|'<=') valueExpr | valueExpr 'IN' setLiteral | valueExpr 'NOT' 'IN' setLiteral ;
valueExpr : elementAttr | literal | 'null' | 'true' | 'false' ;
elementAttr : 'element.attrs[' STRING ']' | 'element.tagName' | 'element.parent.tagName' ;
setLiteral : '[' literal (',' literal)* ']' ;
literal : NUMBER | STRING ;
```

**ANTLR4自动生成**：DslRuleConditionLexer、DslRuleConditionParser、DslRuleConditionVisitor。

**M4调用方式**：ConstraintAnalyzer从M2获取RuleConstraint.condition字符串，调用DslRuleConditionParser解析后由RuleDslEvaluator使用visitor模式执行求值。

**重要边界**：规则DSL不做类型推断。typeOf()不在语法中——类型推断完全由M4 TypeInferenceEngine在TypeAnalyzer中驱动。规则DSL职责边界为：属性存在性、值比较、集合包含、逻辑组合。

**示例**：

JSON规则条目中的constraint：
```json
{
  "ruleId": "SEM-CMD-001",
  "condition": "element.attrs['play'] != null AND element.attrs['sound'] != null",
  "message": "VideoCommand中play和sound互斥，不能同时存在",
  "severity": "error",
  "suggestedFixes": ["移除play属性", "移除sound属性"]
}
```

## 6. M4 语义分析与类型系统 — 详细设计

M4包含原M0的分析层组件：TypeInferenceEngine、FunctionSignatureLibrary、SymbolTableBuilder，以及语义分析引擎。依赖链：M2→M0→M3→M4，类型推断自然位于M3之后。

### 6.1 TypeInferenceEngine（类型推断引擎）

**职责**：从表达式AST + 符号表 + 函数签名库，推断表达式类型，验证与属性期望类型是否匹配。在M4内部，需要M3产出的AST才能执行。

**类型系统定义**：

```
DslType (抽象基类)
├── DslNumberType        // 数值类型
├── DslStringType        // 字符串类型
├── DslBooleanType       // 布尔类型（语义：0/非0）
├── DslEnumType          // 枚举类型（携带合法值集合）
├── DslExpressionType    // 表达式类型（标记：数值表达式 | 字符串表达式）
├── DslReferenceType     // 引用类型（#varName取数值, @varName取字符串）
└── DslVoidType          // 无返回值（命令类属性）
```

**推断规则**：
- NumberLiteral → DslNumberType
- StringLiteral → DslStringType（仅在字符串表达式中）
- VariableReference(#var) → 查符号表 → Var.type → 对应DslType
- VariableReference(@var) → 查符号表 → DslStringType
- FunctionCall → 查函数签名库 → 返回类型
- BinaryExpression(+,-,*,/,%) → 上下文决定：目标属性为number→数值运算；目标属性为string且含+→字符串拼接
- ConditionalExpression(ifelse) → y/z类型必须兼容，返回公共类型

**推断签名**：inferType(ExpressionNode, DslType expectedContext) — 携带目标属性期望类型作为上下文。

**示例推断过程**：

```
visibility="ifelse(#steps_value,1,0)"
  #steps_value → 符号表: Var type=number → DslNumberType ✓
  1 → NumberLiteral → DslNumberType ✓
  0 → NumberLiteral → DslNumberType ✓
  ifelse(number, number, number) → 签名库: 返回number → DslNumberType ✓
  visibility期望: number → DslNumberType ✓
  类型匹配 ✓

visibility="'hello'"
  'hello' → StringLiteral → DslStringType
  visibility期望: DslNumberType
  string ≠ number → SEM-TYPE-001: "属性类型不匹配，期望number，实际string"
```

### 6.2 FunctionSignatureLibrary（函数签名库）

**数据模型**：

```java
@Data @Builder
public class FunctionSignature {
    String name;
    List<FunctionParam> params;
    DslType returnType;
    String expressionKind;          // "number" | "string"
}

@Data @Builder
public class FunctionParam {
    String name;
    DslType type;
    boolean isVariadic;
}
```

**存储方式**：JSON文件，存放于resources/functions/目录，零代码扩展。

**示例定义**：

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

### 6.3 SymbolTableBuilder（符号表收集器）

**职责**：遍历M3产出的AST，收集所有Var声明和变量引用，构建符号表。

**数据模型**：

```java
public class SymbolTable {
    Map<String, VarDeclaration> declarations;   // name → Var声明信息
    List<VarReference> references;              // 所有#/@引用位置
}

@Data @Builder
public class VarDeclaration {
    String name;
    DslType type;               // 从Var的type属性推断
    String expression;           // expression属性值
    boolean isConstAttr;         // 仅反映const="true"属性声明，不做常量折叠
    DslAstNode astNode;          // 对应的AST节点（用于跳转定位）
}

@Data @Builder
public class VarReference {
    String name;
    ReferenceKind kind;          // # (数值) | @ (字符串)
    DslAstNode astNode;          // 引用位置AST节点
}
```

**关键设计决策**：
- isConstAttr仅反映Var的const="true"属性声明，不做表达式求值判断
- 不存储constantValue，不做常量折叠（超出类型推断+签名验证边界）
- 悬浮显示变量值由Plugin层DocumentationProvider运行时处理

### 6.4 RuleDslEvaluator（规则DSL解释器执行）

**职责**：使用M0 DslRuleConditionParser生成的visitor，解释执行规则库中的声明式约束条件。

**执行机制**：
1. 遍历AST每个DslElement
2. 从RuleRepository获取该元素的RuleConstraint列表
3. 对每个constraint.condition，RuleDslEvaluator执行：
   - 用M0的DslRuleConditionParser解析condition字符串
   - 使用visitor模式，将element引用替换为当前AST节点的属性值
   - 递归求值条件表达式
   - 条件为true → 产出Diagnostic（使用constraint.ruleId + message + severity + suggestedFixes）

### 6.5 语义分析三层检查机制

**接口重构**：

```java
public interface DslFileMatcher {
    boolean isDslFile(String filePath, String content);
}

public class DslFileIdentifier implements DslFileMatcher {
    // 1. 检查扩展名是否为.xml
    // 2. 解析content前N行，提取根元素标签名
    // 3. 从M2 RuleRepository.getRootElementNames() 匹配
}
```

Plugin层提供PsiDslFileMatcherAdapter将VirtualFile/PsiFile适配为String参数。

### 6.2 M2 规则库

**保持纯数据层定位**，不依赖M0。新增两类数据模型：

```java
@Data @Builder
public class DslElementRule {
    String elementName;
    List<String> requiredAttrs;
    List<String> optionalAttrs;
    Map<String, AttrTypeSpec> attrTypes;
    List<String> allowedParents;
    List<String> allowedChildren;
    String inherits;
    Map<String, Boolean> scope;              // 新增：作用域支持矩阵
    List<RuleConstraint> constraints;        // 新增：声明式约束条件列表
}

@Data @Builder
public class AttrTypeSpec {
    String type;
    List<String> enumValues;
    List<String> aliases;
    boolean supportsExpression;              // 新增：是否支持表达式
    String expressionKind;                   // 新增：表达式类别 "number"/"string"
}

@Data @Builder
public class RuleConstraint {
    String ruleId;
    String condition;                        // 声明式条件表达式
    String message;
    DiagnosticSeverity severity;
    List<String> suggestedFixes;
}
```

RuleRepository新增接口：
```java
List<RuleConstraint> getConstraints(String elementName);
```

函数签名库独立于M2，归属M0的function包，但JSON文件与规则库同级存放。M2只负责存储和查询约束条件数据，执行归M0的RuleDslEvaluator。

### 6.3 M3 语法分析

**完全重构**：自有独立AST替代PSI依赖。

**DslAst节点类型**：

```java
public abstract class DslAstNode {
    String text;
    int line;
    int column;
    List<DslAstNode> children;
}

public class DslFileNode extends DslAstNode {
    String xmlDeclaration;
    DslElementNode rootElement;
}

public class DslElementNode extends DslAstNode {
    String tagName;
    List<DslAttributeNode> attributes;
    List<DslAstNode> children;
    boolean selfClosing;
    boolean hasError;
    String errorMessage;
}

public class DslAttributeNode extends DslAstNode {
    String name;
    DslAttributeValueNode value;
}

public class DslAttributeValueNode extends DslAstNode {
    String rawValue;
    ExpressionNode expression;       // M0解析的表达式AST（仅expression/reference类型）
    boolean isLiteral;
}
```

**DslAstProvider接口**（替代原PsiTreeProvider）：

```java
public interface DslAstProvider {
    DslFileNode getDslAst(String filePath, String content);
    List<DslElementNode> findElementsByName(DslFileNode ast, String elementName);
    List<DslElementNode> findElementsByTag(DslFileNode ast, String tagName);
}
```

### 6.4 M4 语义分析

**三层检查机制并行**：

| 类别 | Analyzer | 检测方式 | 数据来源 |
|---|---|---|---|
| 类型推断类 | TypeAnalyzer | M0类型推断引擎 | M0函数签名库+符号表+M2 AttrTypeSpec |
| 规则驱动类 | ConstraintAnalyzer | M0规则DSL解释器 | M2 RuleConstraint |
| 模式匹配类 | UnknownElementAnalyzer | 名称集合比对 | M2 DslElementRule |
| | RequiredAttrAnalyzer | 属性存在性检查 | M2 requiredAttrs |
| | UnknownAttrAnalyzer | 属性名比对 | M2 optionalAttrs+requiredAttrs |
| | EnumValueAnalyzer | 枚举值比对 | M2 enumValues |
| | ParentChildAnalyzer | 父子关系比对 | M2 allowedParents/allowedChildren |
| | ScopeAnalyzer | 作用域矩阵比对 | M2 scope |

**TypeAnalyzer检查逻辑**：
1. 从M2获取元素的AttrTypeSpec
2. 对supportsExpression=true的属性，调用M4 TypeInferenceEngine.inferType()
3. 比较推断类型与期望类型
4. 类型不匹配 → 产出SEM-TYPE-001诊断

**ConstraintAnalyzer检查逻辑**：
1. 从M2获取元素的RuleConstraint列表
2. 对每个constraint.condition，调用M0 RuleDslEvaluator.evaluate()
3. 条件为true → 产出对应Diagnostic

### 6.5 M5 修复逻辑

**接口重构**：产出FixAction（纯文本操作描述），不依赖PsiElement。

```java
@Data @Builder
public class TextRange {
    int startLine;
    int startColumn;
    int endLine;
    int endColumn;
}

@Data @Builder
public class FixAction {
    String fixType;            // "close_tag"/"add_quotes"/"insert_attr"/"replace_element"/...
    TextRange targetRange;     // 目标文本范围（起止行列）
    String replacementText;    // 替换文本内容
    List<CandidateItem> candidates;  // 需确认类修复的候选列表
    String description;
}

@Data @Builder
public class CandidateItem {
    String description;
    String previewText;
    double similarityScore;
}
```

### 6.6 M7 批量检查与报告

**接口重构**：使用core抽象而非PSI。

```java
public interface BatchInspectionRunner {
    BatchInspectionResult runOnFile(String filePath);
    BatchInspectionResult runOnDirectory(String directoryPath);
    BatchInspectionResult runOnProject(String projectPath);
}
```

## 7. Plugin层模块 — 详细设计

### 7.1 PSI Adapter

**桥接策略**：不重新解析XML，在IDEA原生XML PSI Tree上叠加DSL语义标注。DslAstNode记录对应PSI元素的offset/范围。

```java
public class DslPsiBridge {
    Map<DslAstNode, Integer> astToOffset;     // AST节点 → PSI文本offset
    Map<Integer, DslAstNode> offsetToAst;     // PSI文本offset → AST节点

    DslAstNode getAstNode(PsiElement psiElement);  // 通过psiElement.getTextOffset()查offset
    PsiElement getPsiElement(DslAstNode astNode);   // 通过offset在PSI Tree中定位
    Diagnostic mapDiagnostic(DslDiagnostic coreDiagnostic);
}
```

### 7.2 M8 导航与重构

| 功能 | IDEA API | 数据来源 |
|---|---|---|
| 跳转定义 | PsiReference.resolve() | Core符号表 → PSI定位 |
| 查找引用 | FindUsagesProvider | Core符号表 → PSI定位 |
| 重命名重构 | RenamePsiElementProcessor | Core符号表 + PSI Tree文本替换 |

PsiReference实现：对属性值中#varName/@varName文本创建PsiReference，resolve()返回对应Var声明的PSI元素。

### 7.3 M6 UI交互 — 增强

DslDocumentationProvider扩展为非错误场景也响应：

```java
// 1. 有Diagnostic → 显示错误信息（原有逻辑）
// 2. 无Diagnostic → 查询Core符号表
//    → 变量引用(#/@var) → 显示变量类型+声明位置
//    → 元素标签 → 显示元素规则摘要
// 3. Var声明 → 显示变量类型+isConstAttr标记
```

### 7.4 M5-UI

将Core FixAction桥接为IDEA IntentionAction：
- 无确认类：直接将FixAction的文本范围映射为PSI范围，执行WriteCommandAction文本替换
- 需确认类：弹出CandidateSelectionDialog，选中后执行FixAction

## 8. CLI接口设计

### 8.1 命令结构

```
java -jar dsl-analyzer.jar [options] <file-or-directory>
```

| 参数 | 说明 | 默认值 |
|---|---|---|
| <path> | 目标文件或目录路径（必填） | — |
| --syntax-only | 只做语法检查 | 全量检查 |
| --semantic-only | 只做语义检查（不含类型推断） | 全量检查 |
| --type-check | 启用类型推断检查 | 全量检查时启用 |
| --rule-dir <path> | 自定义规则库目录 | 内置规则 |
| --format <format> | 输出格式：json/terminal/markdown | terminal |
| --output <path> | 报告文件输出路径（仅md/json） | stdout |
| --no-color | 禁止终端彩色输出 | 自动检测 |
| --quiet | 只输出error级别诊断 | 全级别 |
| --config <path> | 检查配置文件路径 | 默认配置 |
| --verbose | 详细输出（含推断过程） | 标准 |

### 8.2 输出格式

**JSON stdout** (--format json)：

单文件：
```json
{
  "file": "theme.xml",
  "diagnostics": [
    {"severity":"error","line":15,"col":3,"ruleId":"SEM-REF-001","message":"引用未定义变量 #steps_value","suggestedFixes":["声明Var name=\"steps_value\""],"ruleDocUrl":"https://dsl-docs.example.com/rules/SEM-REF-001"}
  ],
  "summary": {"errors":1,"warnings":0,"info":0}
}
```

多文件（目录/项目扫描）：
```json
{
  "files": [
    {"file":"theme.xml","diagnostics":[...],"summary":{"errors":1,"warnings":0,"info":0}},
    {"file":"layout.xml","diagnostics":[...],"summary":{"errors":0,"warnings":2,"info":1}}
  ],
  "summary": {"totalFiles":2,"errors":1,"warnings":2,"info":1}
}
```

**终端彩色输出** (--format terminal)：

```
theme.xml:15:3: error: 引用未定义变量 #steps_value [SEM-REF-001]
  建议修复: 声明Var name="steps_value"

1 error, 0 warnings, 0 info
```

**报告文件导出** (--format markdown --output report.md)：与M7的Markdown格式一致。

### 8.3 退出码语义

| 退出码 | 含义 |
|---|---|
| 0 | 无error级诊断 |
| 1 | 有error级诊断 |
| 2 | 执行异常（文件不存在、规则库加载失败等） |

## 9. 开发阶段重新规划

原Phase 1-6基于IDEA插件交付闭环设计。重构后需要调整：分析Core先行交付（含CLI验证），Plugin层随后叠加。

### 新Phase划分

| 阶段 | 目标 | 模块范围 | 闭环能力 | 交付形态 |
|---|---|---|---|---|
| Phase 1: Core基础闭环 | AST构建+语法检查+CLI可运行 | M0基础 + M1 + M2 + M3 + CLI入口 | CLI可检查DSL文件语法错误 | dsl-analyzer.jar |
| Phase 2: Core语义闭环 | 语义检查+类型推断+规则DSL+符号表 | M0完整 + M4 + M7基础报告 | CLI可检查语法+语义+类型 | dsl-analyzer.jar |
| Phase 3: Core修复闭环 | 修复逻辑+报告导出 | M5 + M7完整报告 | CLI可输出修复建议+导出报告 | dsl-analyzer.jar |
| Phase 4: Plugin基础闭环 | IDEA插件加载+编辑器标注+悬浮 | PSI Adapter + M6标注+悬浮 + M5-UI基础 | IDEA中可见语法+语义标注 | plugin.zip |
| Phase 5: Plugin交互闭环 | Quick Fix+诊断面板+右键菜单 | M6完整 + M5-UI完整 + M7触发 | IDEA中可修复+面板查看 | plugin.zip |
| Phase 6: Plugin导航闭环 | 跳转定义+查找引用+重命名 | M8 | IDEA中Ctrl+Click跳转 | plugin.zip |
| Phase 7: 完善扩展 | 各模块Extension/Optional层 | 全模块 | 完整功能 | jar + plugin.zip |

每个Phase完成后：
1. Core层Phase：运行dsl-analyzer.jar验证CLI闭环
2. Plugin层Phase：构建plugin.zip，在IDEA中验证UI闭环
3. 单元测试：./gradlew :feature:analysis:test
4. 文档同步更新
