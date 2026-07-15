---
module_ids: [CORE]
doc_kind: architecture
status: active
created: 2026-06-15
---
# 主题引擎DSL静态分析工具 - 技术设计文档

## 1. 技术架构

### 1.1 整体架构

基于编译器前端技术构建，采用模块化分层架构：

```
┌─────────────────────────────────────────────────┐
│                  dsl-intellij-plugin             │
│  PSI Adapter │ M6 UI │ M5-UI │ M8 Navigation    │
│         ↓ 依赖 IDEA SDK + core层                 │
├─────────────────────────────────────────────────┤
│                  dsl-analyzer-core               │
│  M0 Parser │ M1 FileID │ M2 Rules │ M3 Syntax   │
│  M4 Semantic+Type │ M5 Fix │ M7 Batch │ CLI     │
│         ↓ 无IDEA依赖，可独立运行                  │
├─────────────────────────────────────────────────┤
│                  外部依赖                        │
│  JDK StAX(XML解析) │ ANTLR4(表达式+规则DSL) │ GSON   │
└─────────────────────────────────────────────────┘
```

- **Core层**：无IDEA SDK依赖，可独立运行（CLI jar只打包core包）
- **Plugin层**：依赖IDEA SDK + core层，在core基础上叠加交互能力
- **隔离保障**：编译期扫描core包内无com.intellij import

### 1.2 技术选型

| 组件 | 选型 | 用途 |
|---|---|---|
| 语言 | Java 17 | 全项目开发语言 |
| 构建工具 | Gradle 8.2 + gradle-intellij-plugin 1.13.3 | 插件构建 + CLI fat jar |
| XML结构解析 | JDK StAX (javax.xml.stream) | DSL文件XML结构解析（不使用ANTLR4） |
| 表达式解析 | ANTLR4 | DSL表达式 + 规则DSL条件解析（.g4 grammar自动生成） |
| 规则库数据格式 | GSON 2.9.0 | 规则JSON反序列化 |
| 数据模型简化 | Lombok 1.18.22 | @Data/@Builder注解 |
| IDEA集成 | IntelliJ Platform Plugin SDK | 仅Plugin层使用 |
| CLI参数解析 | 手写或args4j | CLI入口参数解析 |

### 1.3 检测策略

- **实时检测**：Plugin层Annotator实现，编辑即触发（依赖PSI Adapter桥接core诊断）
- **批量检测**：Plugin层LocalInspectionTool + CLI入口双重通道
- **异步与增量**：（Plugin层DumbService后台线程/PSI增量解析为目标设计,未实现）CLI单线程顺序执行
- **Core层纯分析**：所有分析逻辑在core层完成，产出Diagnostic列表；Plugin层只负责展示与交互
- **规则来源**：每个诊断引用DSL规范具体章节/条款，提供可追溯规则依据

### 1.4 解析策略分层

| 解析层级 | 工具 | 职责 | 输出 |
|---|---|---|---|
| XML结构解析 | JDK StAX | XML文件→XMLStreamReader事件流，捕获XML格式错误 | XMLStreamException直接报出，不做额外包装映射 |
| DSL AST构建 | M3 AstBuilder | StAX事件流→DslAstNode独立AST（XMLStreamReader.getLocation()捕获行列号） | DslFileNode（含DslElementNode、DslAttributeNode） |
| DSL表达式解析 | ANTLR4 (DslExpression.g4) | 仅expression/reference类型属性值解析 | ExpressionNode子树 |
| 规则DSL条件解析 | ANTLR4 (DslRuleCondition.g4) | 声明式约束条件字符串解析 | ConditionNode |
| 纯字面量验证 | 直接比对 | 非expression类型属性值直接验证 | 不走解析器 |

**重要边界**：ANTLR4仅用于表达式和规则DSL条件解析，不用于XML结构解析（JDK StAX保留）。

## 2. 模块设计（10个模块）

### 2.1 M0 解析器基础设施

**职责**：ANTLR4 grammar + 自动生成的解析器，仅解析层，无分析逻辑。

**两个.g4 grammar文件**：

| Grammar | 职责 | 生成代码 | 调用方 |
|---|---|---|---|
| DslExpression.g4 | DSL表达式（数值+字符串）词法/语法分析 | DslExpressionLexer/Parser/Visitor | M3（属性值表达式解析） |
| DslRuleCondition.g4 | 规则DSL条件表达式词法/语法分析 | DslRuleConditionLexer/Parser/Visitor | M4（ConstraintAnalyzer） |

**DslExpression.g4核心规则**：

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

**DslRuleCondition.g4核心规则**：

```
condition : logicExpr ;
logicExpr : logicExpr op=('AND'|'OR') compareExpr | NOT logicExpr | compareExpr ;
compareExpr : valueExpr op=('=='|'!='|'>'|'<'|'>='|'<=') valueExpr | valueExpr 'IN' setLiteral | valueExpr 'NOT' 'IN' setLiteral ;
valueExpr : elementAttr | literal | 'null' | 'true' | 'false' ;
elementAttr : 'element.attrs[' STRING ']' | 'element.tagName' | 'element.parent.tagName' ;
setLiteral : '[' literal (',' literal)* ']' ;
literal : NUMBER | STRING ;
```

**重要边界**：规则DSL不做类型推断。typeOf()不在语法中——类型推断完全由M4 TypeInferenceEngine驱动。规则DSL职责边界为：属性存在性、值比较、集合包含、逻辑组合。

**解析范围边界**：仅对标记为expression/reference类型或显式包含表达式语法（#var、@var、函数调用）的属性值做解析。纯字面量属性（x="100"）直接走字面量验证，不走解析器。

### 2.2 M1 文件识别

**接口重构**：去除PSI依赖，使用纯字符串参数。

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

### 2.3 M2 规则库

**保持纯数据层定位**，不依赖M0。新增数据模型：

```java
@Data @Builder
public class DslElementRule {
    String elementName;
    List<String> requiredAttrs;
    List<String> optionalAttrs;
    Map<String, AttrTypeSpec> attrTypes;
    List<String> allowedParents;
    String inherits;
    Map<String, Boolean> scope;              // 作用域支持矩阵
    List<RuleConstraint> constraints;        // 声明式约束条件列表
}
```

> `allowedChildren` 不存储在 DslElementRule 中，由 `DefaultRuleRepository.buildChildrenMap()` 从所有元素的 `allowedParents` 反向推导构建反向索引，通过 `RuleRepository.getAllowedChildren(elementName)` 查询。

@Data @Builder
public class AttrTypeSpec {
    String type;
    List<String> enumValues;
    List<String> aliases;
    boolean supportsExpression;              // 是否支持表达式
    String expressionKind;                   // 表达式类别 "number"/"string"
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

### 2.4 M3 语法分析

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

**语法错误检测分层**：

| 错误层级 | 检测机制 | 规则ID | 说明 |
|---|---|---|---|
| XML结构语法 | StAX XMLStreamException直接报出 | — | 标签未闭合、属性引号缺失、缺少XML声明等XML格式错误，不做额外包装映射 |
| DSL结构语法 | M3 AST构建+M2规则库比对 | SYN-002, SYN-004, SYN-005, SYN-006, SYN-010 | 嵌套约束、未知元素/属性、必填缺失、根元素错误 |
| DSL表达式语法 | ANTLR4 DslExpressionParser | SEM-EXPR-001~006 | `-#var`模式、单引号缺失、花括号嵌套等 |

### 2.5 M4 语义分析与类型系统

M4包含三层检查机制并行运行：

#### 2.5.1 类型推断类 — TypeAnalyzer

**TypeInferenceEngine**：从表达式AST + 符号表 + 函数签名库，推断表达式类型，验证与属性期望类型是否匹配。

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

**检查逻辑**：
1. 从M2获取元素的AttrTypeSpec
2. 对supportsExpression=true的属性，调用TypeInferenceEngine.inferType()
3. 比较推断类型与期望类型
4. 类型不匹配 → 产出SEM-TYPE-001/SEM-TYPE-002诊断

**重要边界**：不做常量折叠（不对表达式求值），不做符号执行。isConstAttr仅反映Var的const="true"属性声明。

#### 2.5.2 函数签名库 — FunctionSignatureLibrary

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

#### 2.5.3 符号表收集器 — SymbolTableBuilder

遍历M3产出的AST，收集所有Var声明和变量引用，构建符号表。

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

#### 2.5.4 规则驱动类 — ConstraintAnalyzer

使用M0 DslRuleConditionParser生成的visitor，解释执行规则库中的声明式约束条件。

**执行机制**：
1. 遍历AST每个DslElement
2. 从RuleRepository获取该元素的RuleConstraint列表
3. 对每个constraint.condition，RuleDslEvaluator执行：
   - 用M0的DslRuleConditionParser解析condition字符串
   - 使用visitor模式，将element引用替换为当前AST节点的属性值
   - 递归求值条件表达式
   - 条件为true → 产出Diagnostic

#### 2.5.5 模式匹配类 — 各Analyzer

| Analyzer | 检测方式 | 数据来源 |
|---|---|---|
| UnknownElementAnalyzer | 名称集合比对 | M2 DslElementRule |
| RequiredAttrAnalyzer | 属性存在性检查 | M2 requiredAttrs |
| UnknownAttrAnalyzer | 属性名比对 | M2 optionalAttrs+requiredAttrs |
| EnumValueAnalyzer | 枚举值比对 | M2 enumValues |
| ParentChildAnalyzer | 父子关系比对 | M2 allowedParents（allowedChildren由反向索引推导） |
| ScopeAnalyzer | 作用域矩阵比对 | M2 scope |
| VarRefAnalyzer | 变量引用存在性 | SymbolTable + 全局变量目录 |

#### 2.5.6 Trigger/Command链分析

Trigger-Command链是DSL的核心交互机制，需进行结构和语义层面的约束检查。

**Trigger宿主元素**：
- `<Button>` — 按钮触发
- `<Unlocker>` — 解锁触发
- `<Slider>` — 滑动触发
- `<Var>`（threshold属性） — 变量阈值触发
- `<ExternalCommands>` — 外部命令触发

**Trigger action类型**：down, up, double, click, long, resume, pause

**Command类型约束**（部分以声明式RuleConstraint实现，部分仍需硬编码Analyzer）：

| Command类型 | 关键约束 | 实现方式 |
|---|---|---|
| VideoCommand | play与sound互斥 | RuleConstraint: `element.attrs['play'] != null AND element.attrs['sound'] != null` |
| VariableCommand | 不支持persist | RuleConstraint: `element.attrs['persist'] != null` |
| Var(时间/日期) | 禁止persist | RuleConstraint: `element.attrs['persist'] != null AND element.tagName == 'Var' AND type in ['time','date','week']` |
| StyleCommand | index不支持表达式 | Analyzer硬编码 |
| ExternCommand | 仅unlock命令+作用域限制 | Analyzer硬编码 |

### 2.6 M5 修复逻辑

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

**修复策略明细**：

| 修复类型 | 确认要求 | 说明 |
|---|---|---|
| 补闭合标签/补属性引号/删除多余结束标签 | 无需确认 | 直接执行 |
| 插入必填属性占位值/默认值 | 无需确认 | 直接执行 |
| 数字/布尔/路径格式归一化 | 无需确认 | 直接执行 |
| 表达式语法修正（`-#varName`→`-1*#varName`） | 无需确认 | 直接执行 |
| 移除互斥属性/禁止属性组合 | 无需确认 | 直接执行 |
| 替换为最接近的合法组件名 | 需确认 | 基于编辑距离匹配 |
| 替换为别名属性/删除属性/转为通用属性 | 需确认 | 候选列表+diff预览 |
| 替换为最接近合法枚举值 | 需确认 | 候选列表 |
| clamp到合法范围 | 需确认 | diff预览 |

Plugin层M5-UI将FixAction桥接为IntentionAction：无需确认类直接执行WriteCommandAction文本替换；需确认类弹出CandidateSelectionDialog。

### 2.7 M6 UI交互（Plugin层）

| 功能 | IDEA API | 数据来源 |
|---|---|---|
| 编辑器标注 | Annotator | Core M4 Diagnostic → PSI Adapter映射 |
| 悬浮提示（错误） | DocumentationProvider | Core Diagnostic |
| 悬浮提示（变量信息） | DocumentationProvider | Core SymbolTable → 变量类型+声明位置 |
| 悬浮提示（元素规则） | DocumentationProvider | Core M2 RuleRepository |
| 悬浮提示（Var声明） | DocumentationProvider | Core SymbolTable → 类型+isConstAttr |
| 诊断面板 | ToolWindow | Core M4 Diagnostic |
| 右键菜单批量检查 | ActionGroup | Core M7 BatchInspectionRunner |
| 文件图标标注 | FileType + IconProvider | Core M1 DslFileMatcher |

### 2.8 M7 批量检查与报告

**接口重构**：使用core抽象而非PSI。

```java
public interface BatchInspectionRunner {
    BatchInspectionResult runOnFile(String filePath);
    BatchInspectionResult runOnDirectory(String directoryPath);
    BatchInspectionResult runOnProject(String projectPath);
}
```

**报告导出格式**：
- JSON：结构化诊断数据（CLI stdout + 报告文件）
- Markdown：按严重级别分组（报告文件）
- Terminal：gcc/clang格式（CLI stdout）

**报告内容**：severity/file/line/col/ruleId/message/suggestedFixes/ruleDocUrl，多文件扫描时按文件聚合+汇总统计。

### 2.9 M8 导航与重构（Plugin层）

| 功能 | IDEA API | 数据来源 |
|---|---|---|
| 跳转定义 | PsiReference.resolve() | Core SymbolTable → PSI定位 |
| 查找所有引用 | FindUsagesProvider | Core SymbolTable → PSI定位 |
| 重命名重构 | RenamePsiElementProcessor | Core SymbolTable + PSI Tree文本替换 |

PsiReference实现：对属性值中#varName/@varName文本创建PsiReference，resolve()返回对应Var声明的PSI元素。

### 2.10 PSI Adapter

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

### 2.11 CLI入口

```java
public class DslAnalyzerCli {
    public static void main(String[] args) {
        // 1. 解析参数
        // 2. 加载规则库（内置 or --rule-dir；--rule-dir 仅控制规则库,不含函数签名库）
        // 3. 识别DSL文件（M1）
        // 4. 构建AST（M3）
        // 5. 语义分析+类型推断（M4）
        // 6. 格式化输出（JSON/terminal/markdown）
        // 7. 退出码：0=无error, 1=有error, 2=异常
    }
}
```

## 3. 模块依赖关系

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

## 4. Diagnostic数据模型（跨模块共享）

```java
@Data @Builder
public class Diagnostic {
    DiagnosticSeverity severity;    // error/warning/info
    String filePath;
    int line;
    int column;
    String ruleId;                  // SYN-001, SEM-EXPR-001, SEM-TYPE-001等
    String message;
    List<String> suggestedFixes;
    String ruleDocUrl;
    TextRange textRange;            // 诊断精确范围
}
```

Diagnostic使用filePath+line+column定位，不依赖PsiElement，core与plugin共享。

## 5. 规则库数据源

### 5.1 规则来源

规则库数据来源于`docs/themes_engine_next/`目录，该目录包含从华为开发者官网爬取的规范页面：
- 82个规范页面，437个章节
- 每个页面包含：功能概述、支持范围矩阵、XML规范、参数说明表、约束注意事项、应用示例

### 5.2 规则提取策略

- 自动提取：从规范页面的Markdown结构化数据中自动解析规则
- 手动补充：对注意事项页面中的隐含规则手动录入规则库
- 规则分类存储：
  - 元素定义规则：标签名、属性列表、属性类型、必填/选填、枚举值
  - 支持范围矩阵：元素 × 5个应用位置的✓/✗映射
  - 函数签名规则：函数名、参数数量、参数类型、返回类型（独立JSON文件）
  - 全局变量目录：变量名、类型、所属分类、约束
  - 声明式约束规则：互斥约束、禁止约束等（RuleConstraint condition字段）
  - 注意事项规则：从precautions页面提取

### 5.3 规则库格式

- 规则以声明式JSON定义，不硬编码
- 新增元素规则只需在JSON中追加条目
- 新增检测逻辑通过constraints数组追加声明式条件条目（零代码扩展）
- 规则条目包含source_url字段，指向规范原始页面
- 函数签名库独立JSON文件（resources/functions/目录）

## 6. 扩展性设计

### 6.1 零代码扩展（声明式规则DSL）

新增检测逻辑无需编写Analyzer代码：
- 在元素规则的constraints数组中追加RuleConstraint条目
- condition字段使用规则DSL语法：`element.attrs['play'] != null AND element.attrs['sound'] != null`
- ConstraintAnalyzer自动执行，无需手动注册
- CLI可通过`--rule-dir`指定外部规则库目录（仅控制规则库,不含函数签名库；函数签名库仍从内置 resources/functions/ 加载）

### 6.2 规则库扩展

- 新增元素/属性/枚举值/作用域：在JSON中追加条目
- 新增函数签名：在functions JSON中追加条目
- 规范页面更新时重新执行爬取+规则提取流程

### 6.3 硬编码Analyzer扩展

部分复杂约束仍需编写Analyzer代码：
- Trigger/Command链结构约束（宿主元素合法性、action值合法性）
- 资源文件约束（视频大小、图片命名序列）
- 上下文依赖约束（Group clip+layered组合）

新增Analyzer只需实现接口并注册到M4引擎。

### 6.4 Plugin层扩展

- Quick Fix通过IntentionAction注册机制扩展
- PsiReference可扩展新的引用类型
- DocumentationProvider可扩展新的悬浮场景

## 7. 性能设计

### 7.1 响应时间目标

| 场景 | 目标 |
|---|---|
| 单文件实时检测（IDEA插件） | ≤ 50ms |
| CLI单文件检查 | ≤ 100ms |
| CLI批量检查 | ≤ 5s/100文件 |
| 表达式类型推断单属性 | ≤ 5ms |

### 7.2 性能保障措施

- **异步执行**：（Plugin层DumbService后台线程为目标设计,未实现）CLI单线程顺序执行
- **增量分析**：（Plugin层PSI增量解析为目标设计,未实现；当前全量分析）CLI无增量能力
- **规则库缓存**：RuleRepository预加载并缓存，避免重复IO
- **表达式解析缓存**：同一属性值未变更时跳过重复解析
- **全局变量目录常驻内存**：预置全局变量目录作为不可变数据常驻
- **ANTLR4解析优化**：expression/reference类型属性才走解析器，纯字面量直接验证

## 8. 兼容性

### 8.1 平台兼容

| 形态 | 要求 |
|---|---|
| IDEA插件 | IntelliJ IDEA 2024.1+（Ultimate/Community Edition） |
| CLI jar | Java 17+ |

### 8.2 规范版本兼容

- 规范起始版本：HarmonyOS 5.0
- 规则库标注每个元素的起始规范版本，检测时可按目标版本过滤规则
- 部分属性标注更高起始版本（如IntentCommand.uri/type起始HarmonyOS 6.0），低版本目标时应报警告

## 9. 相关文档

| 文档 | 说明 |
|---|---|
| [PRD.md](PRD.md) | 产品需求文档（双形态交付） |
| [DSL-Rule-Spec.md](DSL-Rule-Spec.md) | DSL规则规范、错误检测类型定义、规则库数据结构、声明式约束 |
| [Architecture.md](Architecture.md) | 软件架构总览 |
| [Development-Plan.md](Development-Plan.md) | 开发计划与Phase划分 |
| [UX-Design.md](UX-Design.md) | UX交互设计文档 |
