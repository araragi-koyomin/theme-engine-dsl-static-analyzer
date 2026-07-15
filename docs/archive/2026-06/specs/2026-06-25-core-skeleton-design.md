---
module_ids: [CORE]
doc_kind: spec
status: superseded
created: 2026-06-25
---
# dsl-analyzer-core 代码骨架设计 — 跨模块接口与数据模型

## 设计方案：方案B — 共享数据模型子包 + 接口隔离

**核心原则**：跨多个模块消费的数据模型提取到 `core/shared/` 独立子包，各模块只放自己的接口和单模块内部模型。遵循"接口在消费方模块、数据模型在提供方模块"原则，但跨模块消费的模型提到共享层。

## 1. 项目结构总览（骨架覆盖范围：shared + M0~M4）

```
feature/analysis/src/main/java/com/huawei/theme/analysis/core/
├── shared/                       ← 跨模块共享数据模型
│   ├── ast/                      ← AST节点层级（M3产出，M4消费）
│   │   ├── DslAstNode.java (abstract)
│   │   ├── ExpressionAstNode.java (接口, DIP修正)
│   │   ├── ExpressionKind.java (枚举, DIP修正)
│   │   ├── DslFileNode.java
│   │   ├── DslElementNode.java
│   │   ├── DslAttributeNode.java
│   │   └── DslAttributeValueNode.java
│   ├── type/                     ← 类型系统层级（M0定义，M4消费）
│   │   ├── DslType.java (abstract)
│   │   ├── DslNumberType.java
│   │   ├── DslStringType.java
│   │   └── DslArrayType.java
│   ├── diagnostic/               ← 诊断数据模型（迁移自原core/diagnostic/）
│   │   ├── Diagnostic.java
│   │   ├── DiagnosticSeverity.java
│   │   ├── TextRange.java
│   │   └── adapter/DiagnosticSeverityAdapter.java
│
├── expression/                   ← M0: 表达式解析基础设施
│   ├── ExpressionNode.java (abstract, M0内部AST基类)
│   ├── FunctionSignatureLibrary.java (接口)
│   ├── model/
│   │   ├── FunctionSignature.java
│   │   ├── FunctionParam.java
│   ├── generated/                ← ANTLR4 DslExpression.g4 生成代码
│
├── ruledsl/                      ← M0: 规则DSL解析器+求值器
│   ├── RuleDslEvaluator.java (接口)
│   ├── EvaluationContext.java
│   ├── generated/                ← ANTLR4 DslRuleCondition.g4 生成代码
│
├── function/                     ← M0: 函数签名库（JSON加载实现延后到实现阶段）
│
├── fileidentification/           ← M1: DSL文件识别
│   ├── DslFileMatcher.java (接口)
│
├── rulelibrary/                  ← M2: 规则数据模型+RuleRepository (已有代码，不在骨架设计范围)
│   ├── RuleRepository.java (接口)
│   ├── DefaultRuleRepository.java (已有实现)
│   ├── JsonRuleLoader.java (已有实现)
│   ├── model/
│   │   ├── DslElementRule.java
│   │   ├── AttrTypeSpec.java
│   │   ├── RuleConstraint.java
│   │   ├── DslGlobalVar.java
│   │   ├── RuleSource.java
│
├── syntaxanalysis/               ← M3: dom4j XML解析+AST构建+语法错误
│   ├── DslAstProvider.java (接口)
│
├── semanticanalysis/             ← M4: 语义分析引擎+符号表
│   ├── DiagnosticProvider.java (接口)
│   ├── model/
│   │   ├── SymbolTable.java
│   │   ├── VarDeclaration.java
│   │   ├── VarReference.java
│   │   ├── ReferenceKind.java

feature/analysis/src/main/resources/
├── rules/                        ← 规则库JSON文件 (已存在)
```

---

## 2. shared/ — 跨模块共享数据模型

### 2.1 shared/ast/ — AST节点层级

**包路径**: `com.huawei.theme.analysis.core.shared.ast`

**设计决策**：基类 `DslAstNode` 只定义所有子类都需要的通用字段（text/line/column），不定义 children。各子类各自定义语义明确的专属字段。这符合 LSP（里氏替换原则）——子类不需要接受基类中无意义的通用children列表；也符合 DIP（依赖倒置原则）——消费方依赖抽象基类的最小接口，而非基类的内部数据结构。

```java
// DslAstNode.java — 抽象基类，无children字段
@Data
public abstract class DslAstNode {
    String text;
    int line;
    int column;
}
```

```java
// DslFileNode.java — 文件根节点
@Data
public class DslFileNode extends DslAstNode {
    String xmlDeclaration;
    DslElementNode rootElement;
}
```

```java
// DslElementNode.java — 元素节点
@Data
public class DslElementNode extends DslAstNode {
    String tagName;
    List<DslAttributeNode> attributes;
    List<DslElementNode> childElements;
    boolean selfClosing;
    boolean hasError;
    String errorMessage;
}
```

```java
// DslAttributeNode.java — 属性节点
@Data
public class DslAttributeNode extends DslAstNode {
    String name;
    DslAttributeValueNode value;
}
```

```java
// DslAttributeValueNode.java — 属性值节点
@Data
public class DslAttributeValueNode extends DslAstNode {
    String rawValue;
    Optional<ExpressionAstNode> expression;  // Optional处理可空值；引用shared抽象而非M0具体类
    boolean isLiteral;
}
```

**DIP修正说明**：`DslAttributeValueNode.expression` 原设计引用M0的 `ExpressionNode`，导致shared层反向依赖模块层（违反DIP）。修正方案：在shared/ast/中引入 `ExpressionAstNode` 接口（3个方法：getText/getLine/getColumn + getKind），M0的 `ExpressionNode` 实现此接口。DslAttributeValueNode引用shared自己的抽象，M4（合法依赖M0的模块）通过 `instanceof ExpressionNode` 在需要深度遍历时安全下转。

```java
// ExpressionAstNode.java — shared中的表达式AST抽象接口
// 包路径: com.huawei.theme.analysis.core.shared.ast
public interface ExpressionAstNode {
    String getText();
    int getLine();
    int getColumn();
    ExpressionKind getKind();
}
```

```java
// ExpressionKind.java — 表达式节点类别枚举
// 包路径: com.huawei.theme.analysis.core.shared.ast
public enum ExpressionKind {
    LITERAL, VARIABLE_REF, FUNCTION_CALL, BINARY_EXPR,
    UNARY_EXPR, CONDITIONAL, ARRAY_ACCESS, UNKNOWN
}
```

**Null安全修正**：expression字段可空（纯字面量属性无表达式），按AGENTS.md§4.3使用 `Optional<ExpressionAstNode>` 处理。消费方通过 `attrValue.getExpression()` 获得Optional，用 `isPresent()/ifPresent()` 安全判断，而非直接null检查。

**跨包依赖**：shared/ast/ → shared/ast/（自身抽象），无外部依赖。M0 ExpressionNode `implements ExpressionAstNode` 是M0→shared的正向依赖，符合依赖方向。

### 2.2 shared/type/ — 类型系统层级

**包路径**: `com.huawei.theme.analysis.core.shared.type`

**设计决策**：类型层级只包含表达式推断引擎实际产出的3种类型——number、string、array。Enum/boolean/void不作为独立类型存在：enum和boolean是属性值的**约束**（由M2 AttrTypeSpec.enumValues承载），而非表达式返回的**类型**；void在DSL表达式中没有使用场景（所有函数都返回number或string）。`#var`→DslNumberType / `@var`→DslStringType 由引用前缀决定，不需要DslReferenceType。

```java
// DslType.java — 抽象基类
public abstract class DslType {
    public abstract String getName();
}
```

```java
// DslNumberType.java — 数值类型
public class DslNumberType extends DslType {
    @Override
    public String getName() { return "number"; }
}
```

```java
// DslStringType.java — 字符串类型
public class DslStringType extends DslType {
    @Override
    public String getName() { return "string"; }
}
```

```java
// DslArrayType.java — 数组类型（number[] / string[]）
@Data
public class DslArrayType extends DslType {
    String baseType;     // "number" 或 "string"
    @Override
    public String getName() { return "array"; }
}
```

### 2.3 shared/diagnostic/ — 诊断数据模型

**包路径**: `com.huawei.theme.analysis.core.shared.diagnostic`

**迁移说明**：Diagnostic、DiagnosticSeverity、TextRange、DiagnosticSeverityAdapter 从原 `core/diagnostic/` 包物理迁移到 `core/shared/diagnostic/` 包。包路径从 `com.huawei.theme.analysis.core.diagnostic` 变为 `com.huawei.theme.analysis.core.shared.diagnostic`。所有引用这些类的代码（包括M2的RuleConstraint）需同步更新import路径。

```java
// Diagnostic.java — 跨模块诊断数据模型
@Data @Builder
public class Diagnostic {
    DiagnosticSeverity severity;
    String ruleId;
    String message;
    String filePath;
    int line;
    int column;
    @Builder.Default List<String> suggestedFixes = Collections.emptyList();  // 默认空列表而非null
    String ruleDocUrl;         // 可空，按AGENTS.md§4.3消费方用Optional处理
}
```

```java
// DiagnosticSeverity.java
public enum DiagnosticSeverity {
    ERROR, WARNING, INFO
}
```

```java
// TextRange.java — 精确文本范围（FixAction定位/Annotation映射）
@Data @Builder
public class TextRange {
    int startLine;
    int startColumn;
    int endLine;
    int endColumn;
}
```

```java
// adapter/DiagnosticSeverityAdapter.java — GSON序列化桥接
// （保留现有实现，仅更新包路径）
public class DiagnosticSeverityAdapter extends TypeAdapter<DiagnosticSeverity> {
    // ... 现有代码不变
}
```

**迁移影响清单**：

| 受影响文件 | 变更内容 |
|---|---|
| `RuleConstraint.java` (M2) | import DiagnosticSeverity 路径更新 |
| `DiagnosticSeverityAdapter.java` | 包路径迁移自身 |
| 所有测试文件引用Diagnostic | import路径更新 |
| `plugin.xml` 中如有引用 | 路径更新 |

---

## 3. M0 — 解析器基础设施

**模块定位**：基础设施层，无上游依赖。下游：M3（表达式解析器）、M4（规则DSL求值器+函数签名库）。

### 3.1 expression/ — 表达式解析基础设施

**包路径**: `com.huawei.theme.analysis.core.expression`

```java
// ExpressionNode.java — M0表达式AST抽象基类
// 仅M0/M3/M4消费，不放在shared中
// 实现shared中的ExpressionAstNode接口（DIP修正）
@Data
public abstract class ExpressionNode implements ExpressionAstNode {
    String text;
    int line;
    int column;

    @Override
    public String getText() { return text; }
    @Override
    public int getLine() { return line; }
    @Override
    public int getColumn() { return column; }
}
```

**ANTLR4 DslExpression.g4 生成的子类**（放在 `expression/generated/` 子包，骨架阶段不手写）：
- `NumberLiteralNode`、`StringLiteralNode`、`VariableReferenceNode`、`ArrayAccessNode`
- `BinaryExpressionNode`、`UnaryExpressionNode`、`FunctionCallNode`
- `ConditionalExpressionNode`、`StringConcatNode`

```java
// FunctionSignatureLibrary.java — 函数签名库查询接口
public interface FunctionSignatureLibrary {
    Optional<FunctionSignature> getSignature(String name, String expressionKind);
    List<FunctionSignature> getSignatures(String name);
    boolean hasFunction(String name);
}
```

```java
// model/FunctionSignature.java
// 包路径: com.huawei.theme.analysis.core.expression.model
@Data @Builder
public class FunctionSignature {
    String name;
    List<FunctionParam> params;
    DslType returnType;              // 引用shared/type/DslType
    String expressionKind;           // "number" | "string"
}
```

```java
// model/FunctionParam.java
// 包路径: com.huawei.theme.analysis.core.expression.model
@Data @Builder
public class FunctionParam {
    String name;
    DslType type;                    // 引用shared/type/DslType
    boolean isVariadic;
}
```

### 3.2 ruledsl/ — 规则DSL解析器+求值器

**包路径**: `com.huawei.theme.analysis.core.ruledsl`

```java
// RuleDslEvaluator.java — 声明式约束条件求值接口
public interface RuleDslEvaluator {
    boolean evaluate(String condition, EvaluationContext context);
}
```

```java
// EvaluationContext.java — 求值上下文数据模型
@Data @Builder
public class EvaluationContext {
    Map<String, String> elementAttrs;
    String elementName;
    String elementCategory;
    Map<String, Boolean> scope;
    Map<String, Boolean> deviceSupport;
}
```

**ANTLR4 DslRuleCondition.g4 生成的类**（放在 `ruledsl/generated/` 子包，骨架阶段不手写）：
- `DslRuleConditionLexer`、`DslRuleConditionParser`
- `DslRuleConditionVisitor`、`DslRuleConditionBaseVisitor`

### 3.3 function/ — 函数签名库

**包路径**: `com.huawei.theme.analysis.core.function`

**设计决策**：骨架阶段只定义 `FunctionSignatureLibrary` 接口（在expression包中）和数据模型（FunctionSignature/FunctionParam）。函数签名库的JSON加载实现类（JsonFunctionSignatureLoader）属于实现细节，延后到实现阶段设计。`function/` 包保留在架构文档定义的位置，与 `expression/` 分工明确——`expression` 定义类型系统+接口+数据模型，`function` 负责JSON加载和索引构建（实现阶段）。

---

## 4. 跨模块依赖关系图（shared + M0 部分）

```
shared/ast/DslAstNode ←── M3 (提供), M4 (消费)
shared/ast/ExpressionAstNode ←── shared定义抽象, M0 ExpressionNode实现 (DIP修正)
shared/type/DslType ←── M0 (定义基础), M4 (消费)
shared/diagnostic/* ←── M4 (产出)
expression/ExpressionNode ←── M0 (实现shared ExpressionAstNode)
expression/FunctionSignatureLibrary ←── M0 (提供), M4 (消费)
ruledsl/RuleDslEvaluator ←── M0 (提供), M4 ConstraintAnalyzer (消费)
```

---

## 5. M1 — 文件识别

**包路径**: `com.huawei.theme.analysis.core.fileidentification`

**模块定位**：纯逻辑模块，无数据模型。上游：M2（RuleRepository.getRootElementNames()）。下游：CLI入口（延后）。

```java
// DslFileMatcher.java — DSL文件识别接口
public interface DslFileMatcher {
    boolean isDslFile(String filePath, String content);
}
```

**设计要点**：
- 纯字符串接口（filePath + content），符合架构文档§6"Core层所有接口使用纯字符串/基本类型参数"原则
- 无数据模型，无上游依赖声明在接口中——实现类注入RuleRepository获取根元素名称集合

---

## 6. M2 — 规则库（已有代码+迁移影响）

**包路径**: `com.huawei.theme.analysis.core.rulelibrary`

M2已有完整实现，骨架阶段不需要新增接口或模型。主要工作是Diagnostic迁移影响评估。

### 6.1 已有代码清单（保留不变）

| 文件 | 变更 |
|---|---|
| `RuleRepository.java` (接口) | 不变 |
| `DefaultRuleRepository.java` | 不变 |
| `JsonRuleLoader.java` | 不变 |
| `model/DslElementRule.java` | 不变 |
| `model/AttrTypeSpec.java` | 不变 |
| `model/DslGlobalVar.java` | 不变 |
| `model/RuleSource.java` | 不变 |

### 6.2 迁移影响：`model/RuleConstraint.java`

`RuleConstraint.severity` 字段类型是 `DiagnosticSeverity`，Diagnostic迁移后import路径变更：

```
变更前: import com.huawei.theme.analysis.core.diagnostic.DiagnosticSeverity;
变更后: import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
```

**其他受影响文件**：

| 文件 | 变更 |
|---|---|
| `DiagnosticSeverityAdapter.java` | 物理迁移到 `shared/diagnostic/adapter/`，包路径更新 |
| `JsonRuleLoader.java` | 若引用DiagnosticSeverityAdapter，import路径更新 |
| 所有测试文件（5个） | import Diagnostic/DiagnosticSeverity 路径更新 |

### 6.3 正式认可：`category`字段纳入设计

现有`DslElementRule`比架构文档多一个`category`字段（用于`getRootElementNames()`过滤根元素）。骨架阶段正式认可此字段，不再视为"文档缺失"。category取值为：root, view, layout, variable, control, command, animation, animation_frame, effect, three_d, trigger, data_open。

---

## 7. M3 — 语法分析

**包路径**: `com.huawei.theme.analysis.core.syntaxanalysis`

**模块定位**：编译器前端，核心产出是独立AST（已在shared/ast/定义）。上游：M0（ExpressionParser）、M2（AttrTypeSpec.supportsExpression）。下游：M4。

```java
// DslAstProvider.java — AST访问接口
public interface DslAstProvider {
    DslFileNode getDslAst(String filePath, String content);
}
```

**设计决策**：
- 接口只保留核心方法`getDslAst()`——这是M3的唯一核心产出接口，整个分析管线的AST入口
- AST遍历查询方法（findElementsByName/findByTag）不放在接口中，也不在骨架阶段定义在AST节点上。遍历方法是**实现细节**而非数据契约——骨架阶段定义"节点是什么"（字段），不定义"节点做什么"（遍历方法）。遍历语义（递归深度？是否过滤scope？）依赖M3/M4实际使用时才确定，延后到实现阶段设计

---

## 8. M4 — 语义分析

**包路径**: `com.huawei.theme.analysis.core.semanticanalysis`

**模块定位**：编译器后端。上游：M0（RuleDslEvaluator+FunctionSignatureLibrary）、M2（RuleRepository+规则数据）、M3（DslAstProvider→DslFileNode）。下游：PSI Adapter/M8（SymbolTable）、CLI（DiagnosticProvider）。

### 8.1 跨模块接口

骨架阶段只定义M4的**跨模块接口**——架构文档§6唯一列入的 `DiagnosticProvider`。M4内部接口（DslAnalyzer、AnalyzerRegistry、TypeInferenceEngine）和Extension层接口（SimilarityMatcher）延后到实现阶段设计。

```java
// DiagnosticProvider.java — 语义诊断产出接口（跨模块）
// 签名确认：接收filePath+content纯字符串参数，内部调用M3获取AST再运行分析器
public interface DiagnosticProvider {
    List<Diagnostic> analyzeFile(String filePath, String content);
}
```

### 8.2 跨模块数据模型

SymbolTable系列是M4的产出契约——虽然其主要消费方（PSI Adapter/M8）在延后模块中，但数据形状必须在骨架中定义，否则下游模块无法设计。类比Diagnostic——其消费方也延后了，但仍需定义其数据契约。

```java
// model/SymbolTable.java — 符号表
@Data @Builder
public class SymbolTable {
    Map<String, VarDeclaration> declarations;
    List<VarReference> references;
}
```

```java
// model/VarDeclaration.java — Var声明信息
@Data @Builder
public class VarDeclaration {
    String name;
    DslType type;              // 引用shared/type/DslType
    String expression;
    boolean isConstAttr;
    DslElementNode astNode;    // 引用shared/ast/DslElementNode，PSI Adapter/M8导航定位用
}
```

```java
// model/VarReference.java — 变量引用信息
@Data @Builder
public class VarReference {
    String name;
    ReferenceKind kind;        // # (numeric) | @ (string)
    DslAstNode astNode;        // 引用shared/ast/DslAstNode
}
```

```java
// model/ReferenceKind.java — 引用类型枚举
public enum ReferenceKind {
    NUMERIC,    // #varName
    STRING      // @varName
}
```

### 8.3 设计决策

**骨架范围界定**：
- `DiagnosticProvider`：跨模块接口，架构文档§6明确列入 → **纳入骨架**
- `DslAnalyzer`：M4内部注册模式接口，仅被AnalyzerRegistry消费 → **延后到实现阶段**
- `AnalyzerRegistry`：M4内部实现机制 → **延后到实现阶段**
- `SimilarityMatcher`：Extension层接口，唯一消费方是延后的M5 → **延后到实现阶段**
- `SymbolTable/VarDeclaration/VarReference`：M4产出契约数据模型，下游模块需依赖其数据形状 → **纳入骨架**
- `DiagnosticProvider.analyzeFile(filePath, content)`：内部组合M3获取AST+运行分析器，AST不作为接口参数传入——符合架构文档§6"纯字符串/基本类型参数"原则

---

## 9. 跨模块依赖关系图（shared + M0~M4 全部）

```
shared/ast/DslAstNode ←── M3 (提供), M4 (消费)
shared/ast/ExpressionAstNode ←── shared定义抽象, M0 ExpressionNode实现 (DIP修正)
shared/ast/DslAttributeValueNode.expression ──→ Optional<ExpressionAstNode> (不再直接引用M0 ExpressionNode)
shared/type/DslType ←── M0 (定义基础), M4 (消费)
shared/diagnostic/* ←── M4 (产出)
expression/ExpressionNode implements ExpressionAstNode ←── M0 (实现shared抽象)
expression/FunctionSignatureLibrary ←── M0 (提供), M4 (消费)
ruledsl/RuleDslEvaluator ←── M0 (提供), M4 ConstraintAnalyzer (消费)
fileidentification/DslFileMatcher ←── M1 (提供), CLI (消费)
rulelibrary/RuleRepository ←── M2 (提供), M1/M3/M4 (消费)
syntaxanalysis/DslAstProvider ←── M3 (提供), M4 (消费)
semanticanalysis/DiagnosticProvider ←── M4 (提供), CLI (消费)
semanticanalysis/model/SymbolTable ←── M4 (产出)
semanticanalysis/model/VarDeclaration ──→ shared/ast/DslElementNode + shared/type/DslType
semanticanalysis/model/VarReference ──→ shared/ast/DslAstNode
```

---

## 10. 骨架设计边界说明

**骨架覆盖范围**：`shared/` + `M0` + `M1` + `M2` + `M3` + `M4`

**设计边界决策**：骨架停在M4，不继续设计M5/M7/CLI接口。理由：

1. **依赖链方向**：M0/M2 → M1 → M3 → M4 是**基础设施层**，定义了数据契约和核心能力接口，是所有下游模块必须依赖的基石。M5/M7/CLI是**消费层/组合层**，其接口应由M3/M4的实际使用模式自然推导。

2. **YAGNI**：FixAction的fixType列表、CandidateItem的similarityScore、BatchInspectionResult的字段设计——这些细节依赖M4产出的Diagnostic实际模式。过早定义容易被M3/M4实现时的发现推翻。

3. **逆向风险**：预设下游接口可能约束上游实现，迫使M3/M4适配已写好的M5/M7接口，违反依赖方向。

**延后模块**（待M3/M4实现完成后设计）：

- **M5 修复逻辑** — QuickFixProvider/FixActionGenerator接口 + FixAction/CandidateItem模型
- **M7 批量检查** — BatchInspectionRunner/ReportExporter接口 + BatchInspectionResult模型
- **CLI入口** — DslAnalyzerCli骨架

---

## 11. 与架构文档的偏差说明

以下设计决策与架构文档原有定义存在差异，此处明确记录以避免混淆：

| # | 偏差项 | 架构文档定义 | 骨架设计 | 理由 |
|---|---|---|---|---|
| 1 | AST节点位置 | `core/ast/` | `core/shared/ast/` | 方案B：跨模块共享数据模型提取到shared子包，消除包归属争议 |
| 2 | Diagnostic位置 | `core/diagnostic/` | `core/shared/diagnostic/` | 同上 |
| 3 | DslAstNode基类children | `List<DslAstNode> children` | 无children字段 | LSP原则：基类只定义所有子类都需要的字段，各子类定义语义明确的专属字段 |
| 4 | DslElementNode子节点类型 | `List<DslAstNode> children` | `List<DslElementNode> childElements` | 子节点语义明确为"子元素"，不是混合AST节点 |
| 5 | DslAstProvider方法数 | 3个方法（含查询） | 1个方法（仅getDslAst） | 遍历查询是数据结构操作而非语法分析能力，延后到实现阶段 |
| 6 | FunctionSignatureLibrary性质 | 具体类+字段 | 接口+3方法 | DIP原则：依赖抽象而非具体实现 |
| 7 | FunctionSignatureLibrary位置 | `core/function/` | `core/expression/`（接口），`core/function/`（实现） | expression定义接口+模型，function提供实现，职责更清晰 |
| 8 | DslType层级成员 | 含EnumType/VoidType/BooleanType | 仅NumberType/StringType/ArrayType | Enum/Boolean是AttrTypeSpec的值约束而非推断类型；Void无使用场景 |
| 9 | DslAttributeValueNode.expression类型 | M0 ExpressionNode具体类 | shared ExpressionAstNode接口 | DIP修正——shared不应反向依赖模块层；M0 ExpressionNode实现此接口 |
| 10 | Diagnostic.suggestedFixes默认值 | 无@Builder.Default（默认null） | @Builder.Default Collections.emptyList() | Null安全——列表字段不应为null，空列表表示无建议 |

---

## 12. Null安全规范

按AGENTS.md§4.3"使用Optional<T>处理可能为null的值"，骨架设计中的Null安全策略：

| 场景 | 策略 | 示例 |
|---|---|---|
| 接口返回单个可能不存在的结果 | `Optional<T>` | `RuleRepository.getElementRule()` → `Optional<DslElementRule>` |
| 数据模型中可空的引用字段 | `Optional<T>` | `DslAttributeValueNode.expression` → `Optional<ExpressionAstNode>` |
| 数据模型中可空但非引用的字段 | 字段保留null + 文档标注 | `Diagnostic.ruleDocUrl` — 可空字符串，消费方自行Optional包装 |
| 列表字段 | `@Builder.Default` 空列表，永不为null | `Diagnostic.suggestedFixes` → `@Builder.Default Collections.emptyList()` |
