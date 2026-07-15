---
module_ids: [M4]
doc_kind: architecture
status: active
created: 2026-06-17
---
# M4 语义分析与类型系统模块 - 架构设计

## 1. 模块职责

基于AST和规则库，对DSL文件进行语义约束检查、类型推断、符号表构建、函数签名验证、声明式约束条件执行。产出诊断结果（Diagnostic）供M5/M7/PSI Adapter消费。

**单一职责**：语义约束检查 + 类型推断 + 符号表 + 函数签名验证 + 约束检查 + 诊断结果产出。

**深度重构**：原M0的分析层组件（TypeInferenceEngine、FunctionSignatureLibrary、SymbolTableBuilder）并入M4，类型推断自然位于M3之后。Analyzer接口从PsiElement改为DslAstNode。

## 2. 三层划分

| 层级 | 功能 | 说明 |
|---|---|---|
| **Core** | 7个模式匹配Analyzer + TypeAnalyzer + ConstraintAnalyzer + Diagnostic模型 + AnalyzerRegistry + SymbolTableBuilder | MVP必交 |
| **Extension** | 符号表增量更新 + 语义相似度匹配 + 上下文约束分析 | 正式版本 |
| **Optional** | 继承链分析 + 重复ID检测 + 完整引用完整性 | 后续迭代 |

## 3. 核心组件

### 3.1 Diagnostic数据模型

```java
@Data
@Builder
public class Diagnostic {
    DiagnosticSeverity severity;     // ERROR | WARNING | INFO
    String ruleId;                   // 规则ID，如 SEM-REF-001
    String message;                  // 诊断描述
    String filePath;                 // 文件路径
    int line;                        // 行号
    int column;                      // 列号
    @Builder.Default List<String> suggestedFixes = Collections.emptyList();
    String ruleDocUrl;               // 规则文档URL
}
```

> Null安全: 列表字段永不为null, 空列表表示无建议, 消费方无需null检查.

**包路径**: `com.huawei.theme.analysis.core.shared.diagnostic`（已从`core.diagnostic`迁移至`core.shared.diagnostic`）.

**关键变更**：Diagnostic定位使用filePath+line+column，不使用PsiElement。Core层无IDEA类型依赖。

### 3.2 DiagnosticProvider（接口）

```java
public interface DiagnosticProvider {
    List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo,
                              SymbolTableBuilder symbolTableBuilder,
                              PipelineMode mode, InspectionConfig config,
                              VerboseCollector collector);
}
```

**模式感知派发（P0 调整后）**：接口由原 2 参 `(filePath, content)` 升级为 6 参模式感知派发。`PipelineMode mode` 取 `SYNTAX_ONLY` / `SEMANTIC_ONLY` / `FULL`：`FULL` 顺序执行 M3 语法（SyntaxChecker + ExpressionSyntaxChecker）+ M4 语义；`SYNTAX_ONLY` 只跑 M3；`SEMANTIC_ONLY` 只跑 M4（并过滤 TypeAnalyzer + SyntaxErrorAnalyzer）。`InspectionConfig config` 携带 `typeCheck`/`quiet`/`verbose` 等开关；`VerboseCollector collector` 在 `--verbose` 时收集统计，否则传 null。

### 3.3 Analyzer注册机制

每种语义检测类型对应一个Analyzer实现，通过注册机制管理：

```java
public interface DslAnalyzer {
    List<Diagnostic> analyze(DslAstNode element, DslContext context);
}
```

**关键变更**：analyze参数从PsiElement改为DslAstNode；第二参由 RuleRepository 改为 DslContext（聚合 RuleRepository + SymbolTable + filePath + rootNode + VerboseCollector）。

```java
public class AnalyzerRegistry {
    private AnalyzerRegistry() {}

    public static void register(DslAnalyzer analyzer);
    public static List<DslAnalyzer> getAnalyzers();
}
```

### 3.4 三层检查机制并行

M4包含三类检查机制并行运行：

| 类别 | Analyzer | 检测方式 | 数据来源 |
|---|---|---|---|
| **类型推断类** | TypeAnalyzer | M0 TypeInferenceEngine | M0函数签名库 + M2 AttrTypeSpec + SymbolTable |
| **规则驱动类** | ConstraintAnalyzer | M0 RuleDslEvaluator | M2 RuleConstraint |
| **模式匹配类** | UnknownElementAnalyzer等 | 名称/属性/关系集合比对 | M2 DslElementRule |

#### 3.4.1 类型推断类 — TypeAnalyzer

**TypeInferenceEngine**：从表达式AST + 符号表 + 函数签名库，推断表达式类型，验证与属性期望类型是否匹配。

**类型系统定义**：

```
DslType (抽象基类, abstract getName())
├── DslNumberType        // 数值类型
├── DslStringType        // 字符串类型
└── DslArrayType         // 数组类型(携带baseType: DslType)
```

> 设计决策: Enum/Boolean是值约束(由M2 AttrTypeSpec.enumValues承载), 不作为推断类型; Void在DSL表达式中无使用场景. 保持类型层级精简符合YAGNI原则.

**推断规则**：

| 表达式类型 | 推断方式 |
|---|---|
| NumberLiteral | → DslNumberType |
| StringLiteral | → DslStringType（仅在字符串表达式中） |
| #varName | → DslNumberType（始终返回数值） |
| @varName | → DslStringType（始终返回字符串） |
| #arr[expr] | → DslNumberType（数值数组访问） |
| @arr[expr] | → DslStringType（字符串数组访问） |
| FunctionCall | 查函数签名库 → 返回类型 |
| BinaryExpression(+,-,*,/,%) | 上下文决定：目标属性为number→数值运算；string且含+→字符串拼接 |
| ConditionalExpression(ifelse) | y/z类型必须兼容，返回公共类型 |

**推断签名**：`inferType(ExpressionNode, DslType expectedContext)` — 携带目标属性期望类型作为上下文。

**重构说明**：原DslBooleanType/DslExpressionType/DslReferenceType已删除。Boolean属性归入AttrTypeSpec.enumValues约束, 不作为独立DslType子类；expressionKind为AttrTypeSpec的解析上下文标记而非类型；#var→DslNumberType、@var→DslStringType由前缀决定无需单独类型。新增DslArrayType支持Var type="number[]"/"string[]"和#arr[expr]/@arr[expr]引用验证。

**检查逻辑**：
1. 从M2获取元素的AttrTypeSpec
2. 对supportsExpression=true的属性，调用TypeInferenceEngine.inferType()
3. 比较推断类型与期望类型
4. 类型不匹配 → 产出SEM-TYPE-001诊断
5. 函数参数类型不匹配 → 产出SEM-TYPE-002诊断

**重要边界**：不做常量折叠（不对表达式求值），不做符号执行。isConstAttr仅反映Var的const="true"属性声明。

> **过滤与追踪（P0 调整后）**：`DiagnosticProviderImpl.filterAnalyzers` 在 `config.typeCheck=false`（`--no-type-check`）或 `PipelineMode=SEMANTIC_ONLY` 时将 TypeAnalyzer 移除，类型推断与函数签名验证整体跳过。`--verbose` 时，TypeAnalyzer 通过 `DslContext.getVerboseCollector().recordTypeInference(attrDesc, inferred, expected, match)` 记录类型推断链（attr→推断类型→期望类型→是否匹配），由 `VerboseCollector.render()` 统一输出。

#### 3.4.2 规则驱动类 — ConstraintAnalyzer

使用M0 RuleDslEvaluator解释执行规则库中的声明式约束条件：

1. 遍历AST每个DslElement
2. 从RuleRepository获取该元素的RuleConstraint列表
3. 对每个constraint.condition，调用M0 RuleDslEvaluator.evaluate()
4. 条件为true → 产出对应Diagnostic（使用constraint.ruleId + message + severity + suggestedFixes）

**示例**：

| 规则ID | 检测内容 | 声明式条件 |
|---|---|---|
| SEM-CMD-001 | VideoCommand中sound和play共存 | `element.attrs['play'] != null AND element.attrs['sound'] != null` |
| SEM-PERSIST-001 | 时间/日期变量使用persist | `element.attrs['persist'] != null AND element.attrs['type'] IN ['time','date','week']` |
| SEM-PERSIST-002 | VariableCommand使用persist | `element.attrs['persist'] != null AND element.tagName == 'VariableCommand'` |

#### 3.4.3 模式匹配类 — 各Analyzer

| Analyzer | 检测方式 | 数据来源 | 规则ID范围 |
|---|---|---|---|
| UnknownElementAnalyzer | 名称集合比对 | M2 DslElementRule | SYN-003 |
| RequiredAttrAnalyzer | 属性存在性检查 | M2 requiredAttrs | SYN-005 |
| UnknownAttrAnalyzer | 属性名比对 | M2 optionalAttrs+requiredAttrs | SYN-004 |
| EnumValueAnalyzer | 枚举值比对 | M2 enumValues | SYN-007 |
| ParentChildAnalyzer | 父子关系比对 | M2 allowedParents/allowedChildren | SYN-002 |
| ScopeAnalyzer | 作用域矩阵比对 | M2 scope | SEM-SCOPE-001/002 |
| VarRefAnalyzer | 变量引用存在性 | SymbolTable + 全局变量目录 | SEM-REF-001/002/003 |

### 3.5 SymbolTableBuilder

遍历M3产出的AST，收集所有Var声明和变量引用，构建符号表：

```java
public class SymbolTable {
    Map<String, VarDeclaration> declarations;   // name → Var声明信息
    List<VarReference> references;              // 所有#/@引用位置
}

@Data
@Builder
public class VarDeclaration {
    String name;
    DslType type;               // 从Var的type属性推断
    String expression;           // expression属性值
    boolean isConstAttr;         // 仅反映const="true"属性声明，不做常量折叠
    DslElementNode astNode;          // 对应的AST节点（用于跳转定位）
}
```

> 类型精度修正: Var声明始终对应DslElementNode(<Var>元素), 使用更具体的类型而非泛型DslAstNode基类.

@Data
@Builder
public class VarReference {
    String name;
    ReferenceKind kind;          // # (数值) | @ (字符串)
    DslAstNode astNode;          // 引用位置AST节点
}
```

**关键设计决策**：
- isConstAttr仅反映Var的const="true"属性声明，不做表达式求值判断
- 不存储constantValue，不做常量折叠
- astNode字段用于M8导航定位（Plugin层通过PSI Adapter桥接）

### 3.6 Trigger/Command链分析

Trigger-Command链是DSL的核心交互机制，部分以声明式RuleConstraint实现，部分需硬编码Analyzer：

| Command类型 | 关键约束 | 实现方式 |
|---|---|---|
| VideoCommand | play与sound互斥 | RuleConstraint声明式 |
| VariableCommand | 不支持persist | RuleConstraint声明式 |
| Var(时间/日期) | 禁止persist | RuleConstraint声明式 |
| StyleCommand | index不支持表达式 | Analyzer硬编码 |
| ExternCommand | 仅unlock命令+作用域限制 | Analyzer硬编码 |

### 3.7 语义相似度匹配（Extension层）

当检测到SYN-003(未知元素)或SYN-004(未知属性)时，基于相似度推荐最接近的合法候选：

```java
public interface SimilarityMatcher {
    List<String> matchElement(String unknownName, List<String> candidates);
    List<String> matchAttribute(String unknownName, String elementName, List<String> candidates);
}
```

**匹配策略优先级**：完全匹配 > 编辑距离匹配(Levenshtein) > 语义匹配（关键词）
**候选排序**：按similarityScore降序，上限5条。CandidateItem被M5 FixAction使用。

### 3.8 继承链分析 + 引用完整性（Optional层）

| Optional Analyzer | 检测内容 |
|---|---|
| InheritanceAnalyzer | 继承链断裂检测 |
| DuplicateIdAnalyzer | 重复ID/名称定义 |
| ReferenceAnalyzer | 完整引用完整性校验 |

## 4. 模块依赖

| 上游依赖 | 说明 |
|---|---|
| M0 解析器基础设施 | DslRuleConditionParser + RuleDslEvaluator（约束条件执行） + FunctionSignatureLibrary（函数签名查询） |
| M2 规则库 | DslElementRule + AttrTypeSpec + RuleConstraint + DslGlobalVar（所有规则数据） |
| M3 语法分析 | DslAstProvider → DslFileNode（AST供遍历+类型推断+符号表构建） |

| 下游消费 | 提供接口 | 说明 |
|---|---|---|
| M5 修复逻辑 | DiagnosticProvider + SymbolTable + SimilarityMatcher | 诊断定位+变量信息+修复候选 |
| M7 批量检查 | DiagnosticProvider.analyze() | 批量扫描管线（6 参模式感知派发） |
| PSI Adapter | Diagnostic列表 + SymbolTable | Diagnostic→Annotation映射 + 引用定位 |
| CLI入口 | DiagnosticProvider.analyze() | CLI管线语义分析（6 参模式感知派发） |

## 5. CLI相关

### 5.1 CLI命令调用

M4是CLI管线语义分析步骤：

```
java -jar dsl-analyzer.jar [options] <file-or-directory>
```

M4在CLI管线中的位置：

```
文件输入 → M1识别 → M3语法分析 → M4语义分析+类型推断 → Diagnostic列表 → 输出
```

### 5.2 CLI参数与M4的关系

| 参数 | 影响范围 | M4相关说明 |
|---|---|---|
| `--semantic-only` | 只做语义检查 | 只跑 M4 analyzer（不含 TypeAnalyzer + SyntaxErrorAnalyzer），以 SEMANTIC_ONLY 模式派发 |
| `--no-type-check` | 关闭类型推断检查 | 默认启用类型推断；传入 `--no-type-check` 时 `config.typeCheck=false`，DiagnosticProvider 过滤 TypeAnalyzer（函数签名验证随之跳过） |
| `--syntax-only` | 只做语法检查 | 以 SYNTAX_ONLY 模式派发，只跑 SyntaxChecker + ExpressionSyntaxChecker，不进入M4阶段 |
| `--rule-dir <path>` | M2规则库目录 | 影响M2提供的RuleConstraint和AttrTypeSpec，间接影响M4约束检查和类型推断 |
| `--verbose` | 详细输出 | 开启时CLI输出包含类型推断过程（推断类型链、函数签名匹配详情、符号表内容摘要） |
| `--quiet` | 只输出error级别 | 过滤WARNING/INFO级别诊断，M4 ScopeAnalyzer部分诊断可能被过滤 |

### 5.3 CLI输出中M4的贡献

| CLI输出字段 | 来源路径 | M4贡献 |
|---|---|---|
| `ruleId: SEM-TYPE-001/002` | TypeAnalyzer → TypeInferenceEngine | 类型不匹配+函数参数不匹配诊断 |
| `ruleId: SEM-CMD-xxx` | ConstraintAnalyzer → M0 RuleDslEvaluator | 声明式约束条件诊断 |
| `ruleId: SEM-REF-001/002/003` | VarRefAnalyzer → SymbolTable | 变量/元素引用诊断 |
| `ruleId: SEM-SCOPE-001/002` | ScopeAnalyzer → M2 scope/deviceSupport | 作用域+设备类型诊断 |
| `ruleId: SEM-ATTR-xxx/SEM-VAR-xxx` | 各模式匹配Analyzer | 属性组合+变量使用诊断 |
| `summary.errors/warnings/info` | DiagnosticProvider | 各级别诊断计数 |

### 5.4 CLI异常场景

| 异常场景 | 退出码 | 说明 |
|---|---|---|
| M3产出的AST为null（XML严重格式错误） | 1 | M4无法执行，跳过语义分析 |
| SymbolTable构建异常 | 1 | Var声明解析失败，VarRefAnalyzer产出SEM-REF-001诊断 |
| RuleDslEvaluator执行异常 | 1（降级运行） | 约束条件求值失败时跳过该约束，终端输出warning |

### 5.5 CLI输出示例

**全量检查**（M3+M4合并输出）：

```
$ java -jar dsl-analyzer.jar theme.xml

theme.xml:3:5: error: 未知元素标签 'UnknownTag' [SYN-003]            ← M3产出
theme.xml:15:3: error: 引用未定义变量 #steps_value [SEM-REF-001]     ← M4产出
theme.xml:20:8: error: 类型不匹配，期望number实际string [SEM-TYPE-001] ← M4产出
theme.xml:22:2: error: VideoCommand中play和sound互斥 [SEM-CMD-001]   ← M4产出

4 errors, 0 warnings, 0 info
```

**`--semantic-only`模式**（跳过类型推断）：

```
$ java -jar dsl-analyzer.jar --semantic-only theme.xml

theme.xml:15:3: error: 引用未定义变量 #steps_value [SEM-REF-001]
theme.xml:22:2: error: VideoCommand中play和sound互斥 [SEM-CMD-001]

2 errors, 0 warnings, 0 info
```

**`--verbose`模式**（含推断过程）：

```
$ java -jar dsl-analyzer.jar --verbose theme.xml

[TypeAnalyzer] visibility="ifelse(#steps_value,1,0)"
  #steps_value → SymbolTable: Var type=number → DslNumberType ✓
  1 → NumberLiteral → DslNumberType ✓
  0 → NumberLiteral → DslNumberType ✓
  ifelse(number, number, number) → signature: returnType=number ✓
  visibility期望: DslNumberType → 类型匹配 ✓

[ConstraintAnalyzer] VideoCommand → constraint SEM-CMD-001
  element.attrs['play'] != null → true (play="1")
  element.attrs['sound'] != null → true (sound="0.5")
  condition result: true → 产出诊断

theme.xml:22:2: error: VideoCommand中play和sound互斥 [SEM-CMD-001]
...
```

## 6. 设计要点

- **三层检查并行**：类型推断类(TypeAnalyzer)、规则驱动类(ConstraintAnalyzer)、模式匹配类(7个Analyzer)三类检查机制并行运行
- **Analyzer注册机制**：新增检测类型只需实现DslAnalyzer并注册，不修改引擎核心代码
- **DslAstNode替代PsiElement**：所有Analyzer.analyze参数从PsiElement改为DslAstNode，Core层无IDEA依赖
- **Diagnostic纯字符串定位**：filePath+line+column定位，不使用PsiElement
- **类型推断边界**：不做常量折叠/符号执行，仅推断类型+验证签名匹配
- **规则DSL不做类型推断**：typeOf()不在语法中，类型推断归TypeInferenceEngine
- **符号表含astNode**：VarDeclaration和VarReference的astNode字段用于M8导航定位
- **数据驱动**：Analyzer从RuleRepository查询规则数据进行比对，规则变更不影响Analyzer逻辑
