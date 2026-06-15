    # M2 规则库模块 - 架构设计

## 1. 模块职责

以纯数据形式定义所有DSL元素的约束规则，供其他模块查询使用。不包含任何逻辑判断或分析行为。

**单一职责**：规则条目的定义、存储与查询服务。

## 2. 三层划分

| 层级 | 功能 | 说明 |
|---|---|---|
| **Core** | 规则条目数据模型 + JSON加载 + 查询API | MVP必交 |
| **Extension** | 规则库缓存管理 + 热更新机制 | 性能与维护增强 |
| **Optional** | 规则条目编辑界面（IDEA Settings内可视化维护规则） | 后续迭代 |

## 3. 核心组件

### 3.1 规则条目数据模型

每条规则条目定义一个DSL元素的完整约束：

```java
@Data
@Builder
public class DslElementRule {
    String elementName;              // 合法标签名
    List<String> requiredAttrs;      // 必填属性列表
    List<String> optionalAttrs;      // 可选属性列表
    Map<String, AttrTypeSpec> attrTypes;  // 各属性的类型规范
    List<String> allowedParents;     // 允许的父元素列表
    List<String> allowedChildren;    // 允许的子元素列表
    String inherits;                 // 继承的父元素名称（null表示无继承）
}

@Data
@Builder
public class AttrTypeSpec {
    String type;                     // 属性类型（详见下方类型说明）
    List<String> enumValues;         // 枚举类型的合法取值集合（仅enum类型有效）
    List<String> aliases;            // 属性别名列表
}
```

**`type` 字段说明**：

开发阶段需阅览 `docs/DSL-Rule-Spec.md` 及 `docs/themes_engine_next/raw_markdown/` 下的官方规范文档，将DSL规范中描述的属性类型进行正确转化映射。目前已知的类型包括且不限于：

| 类型 | 说明 | 示例属性 |
|---|---|---|
| `string` | 字符串值 | name, src |
| `number` | 数值（整数或浮点） | x, y, w, h, alpha |
| `boolean` | 布尔值（true/false） | loop, clip, const |
| `enum` | 枚举值（需配合enumValues） | type, action, visibility |
| `expression` | DSL表达式（数值表达式或字符串表达式） | expression, srcExp |
| `action` | 动作/命令类型 | IntentCommand action |
| `object` | 对象引用 | Command target |
| `reference` | 变量/元素引用（#varName / @varName） | threshold, condition |

> **注意**：上述类型列表不完整。开发阶段必须逐一审阅官方规范文档，确定每个属性的实际类型，并做出可能的拓展。`AttrTypeSpec.type` 字段设计为开放式字符串而非硬编码枚举，以支持未来新增类型无需修改数据模型。

### 3.2 规则来源标识

```java
@Data
@Builder
public class RuleSource {
    String ruleId;                   // 格式: [类别]-[编号]，如 SYN-001, SEM-003
    String category;                 // SYN(语法) | SEM(语义)
    String description;              // 规则描述
    String docUrl;                   // 文档URL: https://dsl-docs.example.com/rules/{ruleId}
}
```

### 3.3 RuleRepository（接口）

```java
public interface RuleRepository {
    Optional<DslElementRule> getElementRule(String elementName);
    List<DslElementRule> getAllElementRules();
    List<String> getAllElementNames();
    List<String> getRootElementNames();
    Optional<AttrTypeSpec> getAttrTypeSpec(String elementName, String attrName);
    List<String> getAllowedParents(String elementName);
    List<String> getAllowedChildren(String elementName);
    Optional<RuleSource> getRuleSource(String ruleId);
}
```

### 3.4 JsonRuleLoader（Core层实现）

从JSON文件加载规则数据：

- 规则文件存放于插件resources目录
- 加载时机：插件初始化时预加载
- 数据格式：与 `DSL-Rule-Spec.md` 第6章定义的规则条目Schema一致，包括元素规则条目（6.1）、命令规则条目（6.2）、全局变量条目（6.3）

### 3.5 RuleCacheManager（Extension层）

- 规则库数据预加载并缓存到内存
- 工具类设计：私有构造函数，静态方法访问缓存
- 提供`invalidateCache()`静态方法，支持规则文件变更时重建缓存
- 缓存结构：`Map<String, DslElementRule>` + `Map<String, RuleSource>`

### 3.6 RuleEditorUI（Optional层）

IDEA Settings页面内嵌规则编辑器：

- 表格形式展示所有规则条目
- 支持新增/编辑/删除规则条目
- 编辑后自动触发缓存重建

## 4. 模块依赖

| 上游依赖 | 说明 |
|---|---|
| 无 | 纯数据层，不依赖其他模块 |

| 下游消费 | 提供接口 |
|---|---|
| M1 文件识别 | `getRootElementNames()` 获取合法根元素集合 |
| M3 语法分析 | `getElementRule()` 获取语法验证规则 |
| M4 语义分析 | `getElementRule()` + `getAttrTypeSpec()` 获取语义约束 |
| M5 Quick Fix | `getElementRule()` + `getAttrTypeSpec()` 获取修复建议数据 |
| M7 批量检查 | `getAllElementRules()` 获取全量规则用于批量扫描 |

## 5. 设计要点

- **纯数据无逻辑**：RuleRepository仅提供数据查询，不做任何判断（如"属性是否合法"的判断逻辑归M4语义分析模块）
- **声明式定义**：规则以JSON声明式定义，新增元素只需追加JSON条目，无需修改代码
- **类型安全**：数据模型使用强类型Java类，而非Map传递，保证字段访问的类型安全；POJO使用@Data/@Builder注解简化
- **单一数据源**：所有模块统一通过RuleRepository接口访问，避免规则数据散落各处
- **Optional防空**：单元素查询方法返回Optional<T>，避免null引用风险
- **工具类规范**：RuleCacheManager遵循工具类模式（静态方法、私有构造函数）
