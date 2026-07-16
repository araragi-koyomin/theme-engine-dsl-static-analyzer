---
module_ids: [CORE]
doc_kind: spec
status: superseded
created: 2026-06-17
---
# 架构重构设计文档

## 1. 设计决策记录

| # | 决策 | 选择 | 影响 |
|---|---|---|---|
| 1 | CLI能力边界 | 分析能力CLI化，交互能力仅插件 | Core层必须脱离IDEA PSI，自有独立AST |
| 2 | 类型系统边界 | 类型推断+签名验证 | 引入M0表达式引擎，不做常量折叠/符号执行 |
| 3 | 零代码扩展 | 声明式规则DSL | M2增加RuleConstraint，M0增加RuleDslEvaluator |
| 4 | 变量信息与导航 | 全部纳入 | Core层增加符号表，Plugin层增加M8导航模块+悬浮增强 |
| 5 | 项目架构 | 单项目包名隔离 | feature/analysis内包名隔离core/plugin，CLI jar只打包core |
| 6 | CLI命令 | 单命令多参数 | java -jar dsl-analyzer.jar [options] <path> |
| 7 | CLI输出 | JSON+终端+报告+退出码 | 4种输出方式全部支持 |
| 8 | 表达式解析范围 | 仅expression/reference属性走解析器 | 纯值属性直接字面量验证 |
| 9 | 重构路径 | 全新模块体系M0-M8 | 原M1-M7编号体系全面重构 |

## 2. 项目结构

```
theme-engine-dsl-static-analyzer/
├── feature/analysis/
│   ├── build.gradle
│   └── src/main/java/com/huawei/theme/analysis/
│       ├── core/                       ← 无IDEA依赖，CLI jar只打包这部分
│       │   ├── ast/                    ← AST节点定义 + AST构建器
│       │   ├── expression/             ← M0: 表达式解析器
│       │   ├── function/               ← M0: 函数签名库
│       │   ├── symboltable/            ← M0: 符号表收集器
│       │   ├── ruledsl/                ← M0: 规则DSL解释器
│       │   ├── fileidentification/     ← M1: DSL文件识别
│       │   ├── rulelibrary/            ← M2: 规则数据模型 + JSON加载 + RuleRepository
│       │   ├── syntaxanalysis/         ← M3: Parser + 独立AST构建 + 语法错误
│       │   ├── semanticanalysis/       ← M4: 分析引擎 + 类型推断 + 规则驱动 + 模式匹配
│       │   ├── quickfix/               ← M5: 修复逻辑（纯文本操作描述）
│       │   ├── batchinspection/        ← M7: 批量扫描 + 报告导出
│       │   ├── diagnostic/             ← Diagnostic数据模型（跨模块共享）
│       │   └── cli/                    ← CLI入口 + 参数解析 + 输出格式化
│       └── plugin/                     ← 依赖IDEA SDK + 依赖core层
│           ├── psiadapter/             ← DslAst ↔ PsiElement 双向桥接
│           ├── navigation/             ← M8: PsiReference + 跳转 + 查找引用 + 重命名
│           ├── ui/                     ← M6: Annotator + DocumentationProvider + ToolWindow + 右键菜单
│           ├── quickfixui/             ← M5-UI: IntentionAction桥接 + 候选对话框 + diff预览
│           └── language/               ← DslLanguage + DslParserDefinition注册
│   └── src/main/resources/             ← 规则JSON + 函数签名JSON + plugin.xml
│   └── src/test/java/com/huawei/theme/analysis/
│       ├── core/                       ← Core层单元测试
│       └── plugin/                     ← Plugin层测试（需IDEA测试框架）
├── docs/                               ← 架构文档（同步重构）
├── build.gradle                        ← 根项目配置
└── settings.gradle                     ← include feature:analysis
```

构建策略：
- CLI jar：自定义Gradle task，只打包 core/**，排除 plugin/**，fat jar包含GSON等依赖
- IDEA插件：标准intellij plugin build，包含全部代码
- 隔离保障：Gradle task扫描core包内无 com.intellij import

## 3. 模块体系总览（10个模块）

### dsl-analyzer-core 中的模块

| 模块 | 职责 | 新增/重构说明 |
|---|---|---|---|
| M0 表达式引擎与类型系统 | 表达式解析→AST、类型推断、函数签名库、符号表、规则DSL解释器 | 全新模块，核心基础设施 |
| M1 文件识别 | DSL文件识别与过滤 | 接口重构：VirtualFile/PsiFile → String filePath + String content |
| M2 规则库 | 规则条目存储+查询+函数签名+声明式约束条件 | 模型增强：新增RuleConstraint、AttrTypeSpec扩展 |
| M3 语法分析 | 独立AST构建+语法错误检测 | 完全重构：自有AST替代PSI依赖 |
| M4 语义分析 | 类型感知检查+规则驱动检查+模式匹配检查 | 深度重构：三层检查机制 |
| M5 修复逻辑 | 修复策略生成（纯文本操作描述，无UI） | 接口重构：PsiElement → DslAstNode + TextEdit |
| M7 批量检查与报告 | 批量扫描+报告导出 | 接口重构：使用core抽象 |

### dsl-intellij-plugin 中的模块

| 模块 | 职责 | 说明 |
|---|---|---|---|
| PSI Adapter | DslAst ↔ PsiElement 双向桥接 | 全新模块，core与IDEA的桥梁 |
| M6 UI交互 | Annotator+悬浮（含变量信息）+面板+右键菜单 | 增强悬浮：非错误场景也响应 |
| M8 导航与重构 | PsiReference+跳转+查找引用+重命名重构 | 全新模块，仅Plugin层 |
| M5-UI | Quick Fix交互UI | IntentionAction桥接+候选对话框+diff预览 |

### 模块依赖关系

```
dsl-analyzer-core:
    M0 ← 无上游依赖（核心基础设施）
    M1 ← M2 (获取根元素集合)
    M2 ← 无上游依赖（纯数据层，M0函数签名独立存储）
    M3 ← M0 (表达式解析) + M2 (合法元素名)
    M4 ← M0 (类型推断+符号表+规则DSL) + M2 (规则+约束条件) + M3 (AST)
    M5 ← M0 (符号表) + M2 (修复建议数据) + M4 (诊断)
    M7 ← M1 (文件过滤) + M2 (规则) + M3 (AST) + M4 (诊断)
    CLI ← M1+M3+M4+M7+M0 (组合入口)

dsl-intellij-plugin:
    PSI Adapter ← core M3 (AST转PSI) + M0 (符号表→引用)
    M6 ← PSI Adapter + core M4 (诊断) + M5 (修复注册)
    M8 ← PSI Adapter (符号解析) + M0 (符号表)
    M5-UI ← core M5 (修复逻辑) + PSI Adapter (PSI操作)
```

## 4. M0 表达式引擎与类型系统

### 4.1 表达式解析器（ExpressionParser）

职责：将DSL表达式字符串解析为表达式AST（ExpressionNode）。

解析范围：仅对标记为 expression/reference 类型或显式包含表达式语法（#var、@var、函数调用）的属性值做解析。纯字面量属性（x="100"）直接走字面量验证。

表达式AST节点类型：

```
ExpressionNode (抽象基类)
├── NumberLiteral        // 100, -3.14, 0
├── StringLiteral        // 'hello', "file.png"
├── BooleanLiteral       // true, false
├── VariableReference    // #steps_value, @varName, #arr[expr]
├── FunctionCall         // ifelse(#steps_value,1,0), sin(x), abs(#w)
│     ├── functionName: String
│     └── arguments: List<ExpressionNode>
├── BinaryExpression     // #a + 10, #b * 2, 0 - #w
│     ├── operator: +, -, *, /, %
│     ├── left: ExpressionNode
│     └── right: ExpressionNode
└── ConditionalExpression // ifelse多条件形式: ifelse(x1,y1,x2,y2,...,z)
```

### 4.2 类型推断引擎（TypeInferenceEngine）

职责：从表达式AST + 符号表 + 函数签名库，推断每个表达式节点的类型，验证与属性期望类型是否匹配。

类型系统定义：

```
DslType (抽象基类)
├── DslNumberType        // 数值类型
├── DslStringType        // 字符串类型
├── DslBooleanType       // 布尔类型（语义上0/非0，表达式返回number）
├── DslEnumType          // 枚举类型（携带合法值集合）
├── DslExpressionType    // 表达式类型（标记：数值表达式|字符串表达式）
├── DslReferenceType     // 引用类型（#varName取数值, @varName取字符串）
└── DslVoidType          // 无返回值
```

推断函数签名：`inferType(ExpressionNode node, DslType expectedContext)` — expectedContext携带目标属性期望类型，决定+运算符语义（number上下文→加法，string上下文→拼接）。

推断规则：
- NumberLiteral → DslNumberType
- StringLiteral → DslStringType（仅在字符串表达式中）
- VariableReference(#var) → 查符号表 → Var.type → 对应DslType
- VariableReference(@var) → 查符号表 → DslStringType
- FunctionCall → 查函数签名库 → 返回类型（参数类型也要匹配）
- BinaryExpression(+, -, *, /, %) → 上下文决定+语义，算术运算需number左右，拼接需string至少一侧
- ConditionalExpression ifelse(cond,y,z) → y和z类型必须兼容，返回公共类型

示例推断过程：
```
visibility="ifelse(#steps_value,1,0)"
  #steps_value → 符号表: Var type=number → DslNumberType
  1 → NumberLiteral → DslNumberType
  0 → NumberLiteral → DslNumberType
  ifelse(number, number, number) → 签名库: 返回number → DslNumberType
  visibility期望类型: number → DslNumberType
  类型匹配 → 无错误
```

### 4.3 函数签名库（FunctionSignatureLibrary）

职责：存储DSL所有内置函数的参数类型和返回类型。

数据模型：
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

存储方式：JSON文件，与规则库同级存放在resources目录，零代码扩展。

示例片段：
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

### 4.4 符号表收集器（SymbolTableBuilder）

职责：遍历AST，收集所有Var声明和变量引用，构建符号表。

数据模型：
```java
public class SymbolTable {
    Map<String, VarDeclaration> declarations;
    List<VarReference> references;
}

@Data @Builder
public class VarDeclaration {
    String name;
    DslType type;               // 从Var的type属性推断
    String expression;           // expression属性值
    boolean isConstAttr;         // 仅反映const="true"声明，不做求值判断
    DslAstNode astNode;          // 对应AST节点（用于跳转定位）
}

@Data @Builder
public class VarReference {
    String name;
    ReferenceKind kind;          // # (数值) | @ (字符串)
    DslAstNode astNode;          // 引用位置AST节点
}
```

不包含常量折叠。悬浮显示变量值由Plugin层DocumentationProvider运行时处理。

### 4.5 规则DSL解释器（RuleDslEvaluator）

职责：解释执行规则库中的声明式约束条件，实现零代码扩展。

条件表达式语法（规则DSL）：
- 字面量：null, true, false, 数字, 字符串
- 属性访问：attr（当前元素属性名），attr != null（属性存在性检查）
- 比较运算：==, !=, >, <, >=, <=
- 逻辑运算：AND, OR, NOT
- 集合运算：attr IN ['a','b','c']

规则DSL不做类型推断（typeOf()不属于规则DSL），职责边界为：属性存在性、值比较、集合包含、逻辑组合。类型推断由M0 TypeInferenceEngine在M4 TypeAnalyzer中驱动。

执行机制：
1. 遍历AST每个DslElement
2. 从RuleRepository获取该元素的RuleConstraint列表
3. 对每个constraint.condition，RuleDslEvaluator.evaluate(condition, elementNode)
4. 条件为true → 产出Diagnostic（使用constraint.ruleId + message + severity）

## 5. M1-M7 Core层重构细节

### M1 文件识别

接口重构：
```java
public interface DslFileMatcher {
    boolean isDslFile(String filePath, String content);
}
```

Plugin层适配：
```java
public class PsiDslFileMatcherAdapter {
    boolean isDslFile(VirtualFile file) {
        return coreMatcher.isDslFile(file.getPath(), readFileContent(file));
    }
}
```

三层划分不变（Core双重识别 / Extension FileType注册 / Optional 可配置）。

### M2 规则库

保持纯数据层定位，不依赖M0。M2只负责存储和查询，执行归M0。

增强数据模型：
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
    Map<String, Boolean> scope;              // 作用域支持矩阵
    List<RuleConstraint> constraints;        // 声明式约束条件列表
}

@Data @Builder
public class RuleConstraint {
    String ruleId;
    String condition;
    String message;
    DiagnosticSeverity severity;
    List<String> suggestedFixes;
}

@Data @Builder
public class AttrTypeSpec {
    String type;
    List<String> enumValues;
    List<String> aliases;
    boolean supportsExpression;              // 是否支持表达式
    String expressionKind;                   // "number"/"string"
}
```

新增接口：
```java
List<RuleConstraint> getConstraints(String elementName);
```

### M3 语法分析

自有AST替代PSI依赖。

DslAst节点类型：
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
    ExpressionNode expression;       // M0解析（仅expression/reference类型）
    boolean isLiteral;
}
```

语法错误检测：

| 规则ID | 检测内容 | AST标记方式 |
|---|---|---|---|
| SYN-001 | 标签未闭合 | DslElementNode.hasError=true |
| SYN-002 | 嵌套违反父子约束 | 同上 |
| SYN-003 | 属性引号缺失 | 同上 |
| SYN-009 | 缺少XML声明头 | DslFileNode级别 |
| SYN-010 | 根元素标签错误 | DslFileNode级别 |

DslAstProvider接口：
```java
public interface DslAstProvider {
    DslFileNode getDslAst(String filePath, String content);
    List<DslElementNode> findElementsByName(DslFileNode ast, String elementName);
    List<DslElementNode> findElementsByTag(DslFileNode ast, String tagName);
}
```

### M4 语义分析

三层检查机制：

| 类别 | Analyzer | 检测方式 | 数据来源 |
|---|---|---|---|---|
| 类型推断类 | TypeAnalyzer | M0类型推断引擎 | M0函数签名+符号表+M2 AttrTypeSpec |
| 规则驱动类 | ConstraintAnalyzer | M0规则DSL解释器 | M2 RuleConstraint |
| 模式匹配类 | UnknownElementAnalyzer | 名称集合比对 | M2 DslElementRule |
| | RequiredAttrAnalyzer | 属性存在性 | M2 requiredAttrs |
| | UnknownAttrAnalyzer | 属性名比对 | M2 attrs |
| | EnumValueAnalyzer | 枚举值比对 | M2 enumValues |
| | ParentChildAnalyzer | 父子关系比对 | M2 allowedParents/Children |
| | ScopeAnalyzer | 作用域矩阵比对 | M2 scope |

TypeAnalyzer检查逻辑：
1. 从M2获取元素AttrTypeSpec
2. 对supportsExpression=true的属性值，调用M0 inferType(expressionNode, expectedType)
3. 推断类型与期望类型不匹配 → SEM-TYPE-001
4. 表达式内部参数类型不匹配 → SEM-TYPE-002

ConstraintAnalyzer替代大量硬编码Analyzer：SEM-CMD-001(互斥属性)、SEM-PERSIST-001(persist禁用)、SEM-ATTR-004(clip+无wh)等，通过JSON constraints条目驱动。

Diagnostic数据模型：
```java
@Data @Builder
public class Diagnostic {
    DiagnosticSeverity severity;
    String ruleId;
    String message;
    String filePath;
    int line;
    int column;
    DslAstNode targetNode;
    List<String> suggestedFixes;
    String ruleDocUrl;
}
```

### M5 修复逻辑

脱离PSI，纯文本操作描述：
```java
@Data @Builder
public class FixAction {
    String description;
    List<TextEdit> edits;
}

@Data @Builder
public class TextEdit {
    String filePath;
    int startLine;
    int startColumn;
    int endLine;
    int endColumn;
    String replacementText;
}

@Data @Builder
public class CandidateFixAction {
    String description;
    double similarityScore;
    FixAction fixAction;
}
```

### M7 批量检查与报告

接口重构：
```java
public interface BatchInspectionRunner {
    BatchInspectionResult runOnFile(String filePath);
    BatchInspectionResult runOnDirectory(String dirPath);
    BatchInspectionResult runOnProject(String projectDir);
}
```

## 6. Plugin层模块设计

### PSI Adapter

双向桥接策略：不重新解析XML，在IDEA原生XML PSI Tree上叠加DSL语义标注。Core层DslAst通过offset映射与PSI Tree节点关联。

```java
public class DslPsiBridge {
    Map<DslAstNode, SmartPsiElementPointer> astToPsi;
    Map<SmartPsiElementPointer, DslAstNode> psiToAst;

    DslAstNode getAstNode(PsiElement psiElement);
    PsiElement getPsiElement(DslAstNode astNode);
    Diagnostic mapDiagnostic(DslDiagnostic coreDiagnostic);
}
```

### M8 导航与重构

| 功能 | IDEA API | 数据来源 |
|---|---|---|---|
| 跳转定义(Ctrl+Click) | PsiReference.resolve() | Core符号表 → PSI定位 |
| 查找所有引用 | FindUsagesProvider | Core符号表 → PSI定位 |
| 重命名重构 | RenamePsiElementProcessor | Core符号表 + PSI Tree文本替换 |

PsiReference实现：对属性值中#varName/@varName文本创建PsiReference，resolve()返回Var声明PSI元素。

### M6 UI交互增强

DslDocumentationProvider扩展：
- 有Diagnostic → 显示错误信息（原有逻辑）
- 无Diagnostic + 变量引用(#/@var) → 显示变量类型+声明位置
- 无Diagnostic + 元素标签 → 显示元素规则摘要
- Var声明 → 显示变量类型+常量标记

### M5-UI

将Core FixAction转为IntentionAction，文本范围映射为PSI范围（通过PSI Adapter），执行文本替换。

## 7. CLI接口设计

### 命令结构

java -jar dsl-analyzer.jar [options] <file-or-directory>

参数列表：

| 参数 | 说明 | 默认值 |
|---|---|---|---|
| <path> | 目标文件或目录路径（必填） | — |
| --syntax-only | 只做语法检查 | 全量检查 |
| --semantic-only | 只做语义检查（不含类型推断） | 全量检查 |
| --type-check | 启用类型推断检查 | 全量检查时启用 |
| --rule-dir <path> | 自定义规则库目录 | 内置规则 |
| --format <format> | 输出格式：json/terminal/markdown | terminal |
| --output <path> | 报告文件输出路径（仅markdown/json） | stdout |
| --no-color | 禁止终端彩色输出 | 自动检测 |
| --quiet | 只输出error级别诊断 | 全级别 |
| --config <path> | 检查配置文件路径 | 默认配置 |
| --verbose | 详细输出（含推断过程） | 标准 |

### 输出格式

JSON stdout (--format json)：每条诊断一个对象，含severity/file/line/col/code/message/suggestedFixes/ruleDocUrl。

终端彩色输出 (--format terminal)：file:line:col: severity: message [code] 格式，类似gcc/clang。

报告文件导出 (--format markdown --output path)：与M7 ReportExporter格式一致。

退出码：0=无error, 1=有error, 2=执行异常。
