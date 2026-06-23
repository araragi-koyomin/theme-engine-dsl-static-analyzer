# M2 规则库模块 - 架构设计

## 1. 模块职责

以纯数据形式定义所有DSL元素的约束规则，供其他模块查询使用。不包含任何逻辑判断或分析行为。

**单一职责**：规则条目的定义、存储与查询服务。

**重要边界**：函数签名库（FunctionSignatureLibrary）独立于M2，归属M0的function包。M2只负责存储和查询约束条件数据（RuleConstraint），执行归M0的RuleDslEvaluator。

## 2. 三层划分

| 层级 | 功能 | 说明 |
|---|---|---|
| **Core** | 规则条目数据模型 + JSON加载 + RuleRepository查询接口 | MVP必交 |
| **Extension** | 规则库缓存管理 + 热更新机制 | 性能与维护增强 |
| **Optional** | 规则条目编辑界面（IDEA Settings内可视化维护规则） | 后续迭代 |

## 3. 核心组件

### 3.1 DslElementRule — 元素规则条目数据模型

每条规则条目定义一个DSL元素的完整约束：

```java
@Data
@Builder
public class DslElementRule {
    String elementName;
    List<String> requiredAttrs;
    List<String> optionalAttrs;
    Map<String, AttrTypeSpec> attrTypes;
    List<String> allowedParents;
    List<String> allowedChildren;
    String inherits;
    Map<String, Boolean> scope;              // 作用域支持矩阵
    Map<String, Boolean> deviceSupport;      // 设备类型支持矩阵
    List<RuleConstraint> constraints;        // 声明式约束条件列表
}
```

### 3.2 AttrTypeSpec — 属性类型规范

```java
@Data
@Builder
public class AttrTypeSpec {
    String type;                             // 属性期望值类型（开放式字符串，非硬编码枚举）
    List<String> enumValues;                 // 枚举类型的合法取值集合
    List<String> aliases;                    // 属性别名列表，规范名在optionalAttrs/attrTypes中，别名仅在aliases字段
    boolean supportsExpression;              // 是否支持表达式语法
    String expressionKind;                   // 表达式类别 "number"/"string"/"auto"
    String defaultValue;                     // 属性默认值，省略该属性时引擎使用的隐式值。null=无默认值
}
```

**新增字段说明**：

| 新增字段 | 说明 | 消费方 |
|---|---|---|
| `supportsExpression` | 标记属性是否接受表达式语法（#var、@var、函数调用）。false时属性值只能是纯字面量 | M3（决定是否调用ANTLR4解析器）、M4（TypeAnalyzer） |
| `expressionKind` | 表达式类别："number"表示期望数值表达式、"string"表示期望字符串表达式、"auto"表示根据上下文(如Var的type属性)动态推断 | M4 TypeInferenceEngine（推断期望类型） |
| `defaultValue` | 属性默认值，省略该属性时引擎使用的隐式值。null表示无默认值（省略=属性不存在，不影响分析）。如Var.type默认"number"、persist默认"false"、x/y默认"0"、alpha默认"255" | M4 TypeAnalyzer（推断省略属性的类型）、SEM-VAR-005（type缺失时expression须为数值表达式） |
**`type` 与 `supportsExpression` 的语义关系**：

* `type`描述的是**属性期望值类型**（字面量或表达式返回值的类型），`supportsExpression`描述值是否可以是表达式形式。两者是独立的维度：

    
    - `{"type": "number", "supportsExpression": false}` — 纯字面量数值（如x="0"）
    - `{"type": "number", "supportsExpression": true, "expressionKind": "number"}` — 可以是数值表达式（如x="#screen_width/2"）
    - `{"type": "string", "supportsExpression": true, "expressionKind": "string"}` — 可以是字符串表达式（如textExp="@var+'hello'"）
    - `{"type": "string", "supportsExpression": true, "expressionKind": "auto"}` — 根据上下文动态推断（如Var.expression由type决定是数值/字符串表达式）

**别名处理机制**：

    
    - optionalAttrs和attrTypes只包含规范名（如"width"、"height"、"pivotX"等）
    - 别名（如"w"、"h"、"centerX"等）仅出现在attrTypes规范名条目的aliases字段
    - 别名不作为独立条目出现在optionalAttrs或attrTypes中
    - RuleRepository提供`resolveAttrAlias()`方法：别名→规范名映射
    - `getAttrTypeSpec()`自动处理别名：传入别名时先resolve到规范名再查询
    - M3 SYN-004检测使用`getCanonicalAttrNames()`比对规范属性名集合

**`type` 字段完整列表**：

| 类型 | 说明 | 示例属性 |
|---|---|---|
| `string` | 字符串值 | name, src |
| `number` | 数值（整数或浮点） | x, y, w, h, alpha |
| `boolean` | 布尔值（true/false） | loop, clip, const |
| `enum` | 枚举值（需配合enumValues） | type, action, category |
| `expression` | DSL表达式 | expression, srcExp |
| `action` | 动作/命令类型 | IntentCommand action |
| `object` | 对象引用 | Command target |
| `reference` | 变量/元素引用（#varName / @varName） | threshold, condition |

> `AttrTypeSpec.type` 字段设计为开放式字符串而非硬编码枚举，以支持未来新增类型无需修改数据模型。开发阶段必须逐一审阅官方规范文档确定每个属性的实际类型。

### 3.3 RuleConstraint — 声明式约束条件

```java
@Data
@Builder
public class RuleConstraint {
    String ruleId;                           // 规则ID，如 SEM-CMD-001
    String condition;                        // 声明式条件表达式（规则DSL语法）
    String message;                          // 条件满足时的诊断消息
    DiagnosticSeverity severity;             // 诊断严重级别
    List<String> suggestedFixes;             // 修复建议文本列表
}
```

**condition字段语法**：使用M0 DslRuleCondition.g4定义的规则DSL语法，由M4 ConstraintAnalyzer → M0 RuleDslEvaluator执行求值。

**示例**：

```json
{
  "ruleId": "SEM-CMD-001",
  "condition": "element.attrs['play'] != null AND element.attrs['sound'] != null",
  "message": "VideoCommand中play和sound互斥，不能同时存在",
  "severity": "error",
  "suggestedFixes": ["移除play属性", "移除sound属性"]
}
```

### 3.4 DslGlobalVar — 全局变量条目

```java
@Data
@Builder
public class DslGlobalVar {
    String name;
    String type;
    String scope;
    String description;
    String accessPattern;
    List<RuleConstraint> constraints;
}
```

### 3.5 RuleSource — 规则来源标识

```java
@Data
@Builder
public class RuleSource {
    String ruleId;                           // 格式: [类别]-[编号]
    String category;                         // SYN(语法) | SEM(语义)
    String description;
    String docUrl;                           // https://dsl-docs.example.com/rules/{ruleId}
}
```

### 3.6 RuleRepository — 查询接口

```java
public interface RuleRepository {
    Optional<DslElementRule> getElementRule(String elementName);
    List<DslElementRule> getAllElementRules();
    List<String> getAllElementNames();
    List<String> getRootElementNames();
    Optional<AttrTypeSpec> getAttrTypeSpec(String elementName, String attrName);
    Optional<String> resolveAttrAlias(String elementName, String attrName);
    Set<String> getCanonicalAttrNames(String elementName);
    List<String> getAllowedParents(String elementName);
    List<String> getAllowedChildren(String elementName);
    List<RuleConstraint> getConstraints(String elementName);
    Optional<DslGlobalVar> getGlobalVar(String varName);
    List<DslGlobalVar> getAllGlobalVars();
    Optional<RuleSource> getRuleSource(String ruleId);
}
```

**新增接口**：

| 新增方法 | 说明 | 消费方 |
|---|---|---|
| `getConstraints(elementName)` | 获取元素的声明式约束条件列表 | M4 ConstraintAnalyzer |
| `getGlobalVar(varName)` | 获取全局变量条目 | M4 VarRefAnalyzer |
| `getAllGlobalVars()` | 获取全部全局变量列表 | M4 SymbolTableBuilder |
| `resolveAttrAlias(elementName, attrName)` | 将属性别名resolve为规范名（如"h"→"height"）；规范名直接返回自身 | M3属性名规范化、M5 QuickFix |
| `getCanonicalAttrNames(elementName)` | 获取元素规范属性名集合（不含别名），用于SYN-004比对 | M3 SYN-004检测 |

**`getAttrTypeSpec`别名自动处理**：当attrName是别名时，内部自动调用`resolveAttrAlias()`映射到规范名后再查询attrTypes。消费方无需关心传入的是规范名还是别名。

### 3.7 JsonRuleLoader — Core层实现

从JSON文件加载规则数据：

- 规则文件存放于`resources/rules/`目录
- 加载时机：CLI启动时预加载；IDEA插件在plugin初始化时预加载
- 数据格式：与DSL-Rule-Spec.md第6章定义的规则条目Schema一致
- 三类JSON文件：元素规则条目（6.1）、命令规则条目（6.2）、全局变量条目（6.3）

**加载逻辑**：

```java
public class JsonRuleLoader {
    Map<String, DslElementRule> loadElementRules(String rulesDir);
    Map<String, DslGlobalVar> loadGlobalVars(String rulesDir);
    Map<String, RuleSource> loadRuleSources(String rulesDir);

    RuleRepository buildRuleRepository(Map<String, DslElementRule> elements,
                                        Map<String, DslGlobalVar> globalVars,
                                        Map<String, RuleSource> sources);
}
```

### 3.8 RuleCacheManager — Extension层

- 规则库数据预加载并缓存到内存
- 工具类设计：私有构造函数，静态方法访问缓存
- 提供`invalidateCache()`静态方法，支持规则文件变更时重建缓存
- 缓存结构：`Map<String, DslElementRule>` + `Map<String, DslGlobalVar>` + `Map<String, RuleSource>`
- 热更新：监听规则目录文件变更事件，自动重建缓存

### 3.9 RuleEditorUI — Optional层（Plugin层实现）

IDEA Settings页面内嵌规则编辑器：

- 表格形式展示所有规则条目
- 支持新增/编辑/删除规则条目
- 编辑后自动触发缓存重建

## 4. 模块依赖

| 上游依赖 | 说明 |
|---|---|
| 无 | 纯数据层，不依赖其他模块（包括M0） |

| 下游消费 | 提供接口 | 说明 |
|---|---|---|
| M1 文件识别 | `getRootElementNames()` | 获取合法根元素集合用于双重识别 |
| M3 语法分析 | `getElementRule()` + `getAttrTypeSpec()` | 获取语法验证规则+属性类型规范 |
| M4 语义分析 | `getElementRule()` + `getAttrTypeSpec()` + `getConstraints()` + `getGlobalVars()` | 语义约束+类型规范+声明式约束+全局变量 |
| M5 修复逻辑 | `getElementRule()` + `getAttrTypeSpec()` + RuleConstraint.suggestedFixes | 修复建议数据 |
| M7 批量检查 | `getAllElementRules()` + `getAllGlobalVars()` | 全量规则用于批量扫描 |

## 5. CLI相关

### 5.1 CLI命令调用

M2无独立CLI命令入口，作为数据层被管线间接加载：

```
java -jar dsl-analyzer.jar [options] <file-or-directory>
```

M2在CLI管线中的位置：

```
CLI入口 → JsonRuleLoader.loadElementRules(ruleDir) → RuleRepository构建 → 供M1/M3/M4/M5/M7查询
```

### 5.2 CLI参数与M2的关系

| 参数 | 影响范围 | M2相关说明 |
|---|---|---|
| `--rule-dir <path>` | M2规则库加载目录 | 指定自定义规则库目录替代内置`resources/rules/`；目录下需包含元素规则JSON、命令规则JSON、全局变量JSON |
| `--config <path>` | 检查配置文件 | 配置文件中可指定规则子集（如启用/禁用特定ruleId）、severity覆盖 |

### 5.3 CLI输出中M2的贡献

M2不直接产出Diagnostic，但为下游模块提供诊断规则数据：

| CLI输出字段 | 来源路径 | M2贡献 |
|---|---|---|
| `ruleId: SYN-003/004/005/006/007` | M3语法分析 → M2 DslElementRule名称集合+属性集合+enumValues | 未知元素/未知属性/缺失必填/类型错误/枚举错误的比对数据 |
| `ruleId: SEM-SCOPE-001/002` | M4 ScopeAnalyzer → M2 scope/deviceSupport | 作用域/设备类型比对数据 |
| `ruleId: SEM-CMD-xxx` | M4 ConstraintAnalyzer → M2 RuleConstraint | 声明式约束条件数据和消息模板 |
| `ruleId: SEM-TYPE-001/002` | M4 TypeAnalyzer → M2 AttrTypeSpec.expressionKind | 属性期望类型比对数据 |
| `suggestedFixes` | M5修复逻辑 → M2 RuleConstraint.suggestedFixes | 修复建议文本 |
| `ruleDocUrl` | RuleRepository → M2 RuleSource.docUrl | 规则文档链接 |

### 5.4 CLI异常场景

| 异常场景 | 退出码 | 说明 |
|---|---|---|
| 内置规则库JSON加载失败 | 2 | `resources/rules/`目录不存在或JSON格式错误，无法启动 |
| 自定义`--rule-dir`目录不存在 | 2 | 指定目录路径无效 |
| 自定义`--rule-dir`缺少必要JSON文件 | 1（降级运行） | 使用内置规则库继续运行，终端输出warning提示 |
| 自定义`--rule-dir`JSON格式错误 | 2 | 解析失败导致RuleRepository构建失败 |

### 5.5 零代码扩展

CLI通过`--rule-dir`指定外部规则库目录，实现完全零代码的自定义规则集：

| 扩展类型 | 方式 | 是否需要编码 |
|---|---|---|
| 新增元素 | 追加元素规则条目JSON | 否 |
| 新增属性 | 在optionalAttrs/attrTypes中追加 | 否 |
| 新增枚举值 | 在attrTypes.enumValues中追加 | 否 |
| 新增作用域 | 在scope/deviceSupport矩阵中追加 | 否 |
| 新增检测逻辑 | 在constraints数组中追加RuleConstraint（含声明式condition） | 否 |
| 复杂约束（如Trigger链结构） | 编写Analyzer并注册到M4引擎 | 是 |

## 6. 设计要点

- **纯数据无逻辑**：RuleRepository仅提供数据查询，不做任何判断（如"属性是否合法"的判断逻辑归M4语义分析模块）
- **声明式定义**：规则以JSON声明式定义，新增元素只需追加JSON条目，无需修改代码
- **函数签名库独立**：函数签名库归M0管理，不归M2；M2只存储RuleConstraint数据，执行归M0 RuleDslEvaluator
- **类型安全**：数据模型使用强类型Java类，而非Map传递；POJO使用@Data/@Builder注解简化
- **单一数据源**：所有模块统一通过RuleRepository接口访问，避免规则数据散落各处
- **Optional防空**：单元素查询方法返回Optional<T>，避免null引用风险
- **工具类规范**：RuleCacheManager遵循工具类模式（静态方法、私有构造函数）
- **开放式类型**：AttrTypeSpec.type为开放式字符串，支持未来新增类型无需修改数据模型
