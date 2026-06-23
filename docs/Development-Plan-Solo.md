# 单人开发计划 — M2 规则库优先

## 当前状态

- 项目结构已建立（Gradle + IntelliJ Plugin框架），但 **无任何Java源码**
- `feature/analysis/build.gradle` 已配置 GSON 2.9.0 + dom4j 2.1.3 + Lombok 1.18.22 + JUnit 5
- `resources/rules/` 目录尚未创建，无规则JSON文件
- 官方规范文档缓存在 `docs/themes_engine_next/raw_markdown/`（82个页面）

## 开发顺序总览

```
Step 1: 跨模块共享基础设施（Phase 0）
        → 所有模块的数据模型 + 核心接口定义

Step 2: M2 Core层 — 数据模型 + JSON加载 + RuleRepository实现
        → 纯数据层，无上游依赖，可立即开工

Step 3: M2 规则JSON文件编写
        → 从官方规范文档提取规则数据，逐元素编写JSON

Step 4: M1 文件识别（M2完成后顺手完成）
        → 极小模块，依赖M2的getRootElementNames()

Step 5: M2单元测试 + 集成验证
        → 确保规则库数据完整性和查询接口正确性
```

---

## Step 1: 跨模块共享基础设施

**目标**：定义所有模块间共享的数据模型和接口，消除后续开发的阻塞。

**预计耗时**：2-3天

### 1.1 创建包结构

```
feature/analysis/src/main/java/com/huawei/theme/analysis/
├── core/
│   ├── diagnostic/          ← Diagnostic数据模型（跨模块共享）
│   │   ├── Diagnostic.java
│   │   ├── DiagnosticSeverity.java
│   │   └── TextRange.java
│   ├── rulelibrary/         ← M2: 规则库
│   │   ├── model/           ← 数据模型
│   │   │   ├── DslElementRule.java
│   │   │   ├── AttrTypeSpec.java
│   │   │   ├── RuleConstraint.java
│   │   │   ├── DslGlobalVar.java
│   │   │   └── RuleSource.java
│   │   ├── RuleRepository.java      ← 接口
│   │   ├── JsonRuleLoader.java      ← JSON加载实现
│   │   └── DefaultRuleRepository.java ← RuleRepository默认实现
│   ├── fileidentification/  ← M1: 文件识别（Step 4）
│   ├── ast/                 ← M3: AST节点体系（后续）
│   ├── expression/          ← M0: 表达式解析器（后续）
│   ├── ruledsl/             ← M0: 规则DSL解析器（后续）
│   ├── function/            ← M0: 函数签名库（后续）
│   ├── syntaxanalysis/      ← M3: 语法分析（后续）
│   ├── semanticanalysis/    ← M4: 语义分析（后续）
│   ├── quickfix/            ← M5: 修复逻辑（后续）
│   ├── batchinspection/     ← M7: 批量检查（后续）
│   └── cli/                 ← CLI入口（后续）
│
├── plugin/                  ← Plugin层（后续）
│   ├── psiadapter/
│   ├── navigation/
│   ├── ui/
│   ├── quickfixui/
│   └── language/
```

### 1.2 Diagnostic数据模型

**文件**：`core/diagnostic/Diagnostic.java`

```java
@Data
@Builder
public class Diagnostic {
    DiagnosticSeverity severity;
    String ruleId;
    String message;
    String filePath;
    int line;
    int column;
    List<String> suggestedFixes;
    String ruleDocUrl;
}
```

**文件**：`core/diagnostic/DiagnosticSeverity.java`

```java
public enum DiagnosticSeverity {
    ERROR, WARNING, INFO
}
```

**文件**：`core/diagnostic/TextRange.java`

```java
@Data
@Builder
public class TextRange {
    int startLine;
    int startColumn;
    int endLine;
    int endColumn;
}
```

### 1.3 M2 数据模型

详见 Step 2。

---

## Step 2: M2 Core层 — 数据模型 + JSON加载 + RuleRepository

**目标**：完成M2的全部Core层功能——规则条目的定义、JSON加载与查询服务。

**预计耗时**：3-4天

### 2.1 数据模型（5个POJO）

**`core/rulelibrary/model/DslElementRule.java`**

```java
@Data
@Builder
public class DslElementRule {
    String elementName;
    String category;                          // "root"/"view"/"layout"/"variable"/"control"/"command"/"animation"/"effect"/"three_d"/"trigger"
    List<String> requiredAttrs;
    List<String> optionalAttrs;
    Map<String, AttrTypeSpec> attrTypes;
    List<String> allowedParents;
    List<String> allowedChildren;
    String inherits;
    Map<String, Boolean> scope;               // 应用位置支持矩阵
    Map<String, Boolean> deviceSupport;        // 设备类型支持矩阵
    List<RuleConstraint> constraints;          // 声明式约束条件列表
}
```

**`core/rulelibrary/model/AttrTypeSpec.java`**

```java
@Data
@Builder
public class AttrTypeSpec {
    String type;                              // string/number/boolean/enum/expression/action/object/reference
    List<String> enumValues;
    List<String> aliases;
    boolean supportsExpression;
    String expressionKind;                    // "number"/"string"/"auto"
}
```

**`core/rulelibrary/model/RuleConstraint.java`**

```java
@Data
@Builder
public class RuleConstraint {
    String ruleId;
    String condition;
    String message;
    DiagnosticSeverity severity;
    List<String> suggestedFixes;
}
```

**`core/rulelibrary/model/DslGlobalVar.java`**

```java
@Data
@Builder
public class DslGlobalVar {
    String name;
    String type;
    String scope;                            // "global"/"local"/"context"
    String description;
    String accessPattern;
    List<RuleConstraint> constraints;
}
```

**`core/rulelibrary/model/RuleSource.java`**

```java
@Data
@Builder
public class RuleSource {
    String ruleId;
    String category;                         // SYN/SEM
    String description;
    String docUrl;
}
```

### 2.2 RuleRepository接口

**`core/rulelibrary/RuleRepository.java`**

```java
public interface RuleRepository {
    Optional<DslElementRule> getElementRule(String elementName);
    List<DslElementRule> getAllElementRules();
    List<String> getAllElementNames();
    List<String> getRootElementNames();
    Optional<AttrTypeSpec> getAttrTypeSpec(String elementName, String attrName);
    List<String> getAllowedParents(String elementName);
    List<String> getAllowedChildren(String elementName);
    List<RuleConstraint> getConstraints(String elementName);
    Optional<DslGlobalVar> getGlobalVar(String varName);
    List<DslGlobalVar> getAllGlobalVars();
    Optional<RuleSource> getRuleSource(String ruleId);
}
```

### 2.3 DefaultRuleRepository实现

**`core/rulelibrary/DefaultRuleRepository.java`**

内部使用 `Map<String, DslElementRule>` + `Map<String, DslGlobalVar>` + `Map<String, RuleSource>` 作为存储。

- 所有查询方法直接从Map中查找
- `getRootElementNames()` 返回scope中至少一个为true的根元素标签名集合：`["Lockscreen", "Wallpaper", "Widget", "ChargingSkin"]`
- `getConstraints()` 从DslElementRule.constraints字段获取
- 构造函数接收三个Map参数（由JsonRuleLoader提供）

### 2.4 JsonRuleLoader — JSON加载实现

**`core/rulelibrary/JsonRuleLoader.java`**

职责：从指定目录加载JSON文件，构建RuleRepository所需的三个Map。

**加载逻辑**：

1. 从 `rulesDir` 目录扫描所有 `.json` 文件
2. 按文件类型分类加载：
   - `elements/*.json` → 元素规则条目（DslElementRule）
   - `commands/*.json` → 命令规则条目（DslElementRule，category="command")
   - `global_vars.json` → 全局变量条目（DslGlobalVar）
   - `rule_sources.json` → 规则来源条目（RuleSource）
3. GSON反序列化为对应POJO
4. 构建 `Map<String, DslElementRule>`、`Map<String, DslGlobalVar>`、`Map<String, RuleSource>`
5. 调用 `buildRuleRepository()` 构建DefaultRuleRepository实例

**JSON文件组织**：

```
resources/rules/
├── elements/
│   ├── view/               ← 视图元素规则
│   │   ├── Text.json
│   │   ├── Image.json
│   │   ├── Video.json
│   │   ├── Time.json
│   │   ├── DateTime.json
│   │   ├── CountDownTime.json
│   │   ├── ImageNumber.json
│   │   ├── ImageSeries.json
│   │   ├── SourceImage.json
│   │   ├── Mask.json
│   │   ├── GroupImage.json
│   │   ├── Swiper.json
│   │   ├── figures/        ← 几何图形
│   │   │   ├── Arc.json
│   │   │   ├── Circle.json
│   │   │   ├── Ellipse.json
│   │   │   ├── Line.json
│   │   │   ├── Rectangle.json
│   │   │   └── PathUtil.json
│   ├── layout/             ← 布局容器元素规则
│   │   └── Group.json
│   ├── control/            ← 控件元素规则
│   │   └── Button.json
│   ├── variable/           ← 变量元素规则（与官方规范§2.4分类一致）
│   │   ├── Var.json
│   │   ├── GlobalVariable.json
│   │   ├── VarArray.json
│   │   ├── Array.json
│   ├── root/               ← 根元素规则
│   │   ├── Lockscreen.json
│   │   ├── Wallpaper.json
│   │   ├── Widget.json
│   │   ├── ChargingSkin.json
│   ├── animation/          ← 动画元素规则
│   │   ├── AlphaAnimation.json
│   │   ├── PositionAnimation.json
│   │   ├── RotationAnimation.json
│   │   ├── SizeAnimation.json
│   │   ├── SourceAnimation.json
│   │   ├── VariableAnimation.json
│   ├── effect/             ← 特效元素规则
│   │   ├── MeshImageTrans.json
│   │   ├── MeshImagesInMotion.json
│   │   ├── ParticleView.json
│   │   ├── DropPhysicalView.json
│   │   ├── CollisionWorld.json
│   │   ├── Fluids.json
│   ├── three_d/            ← 3D元素规则
│   │   ├── StereoView.json
│   │   ├── MultiLayer.json
│   │   ├── Scene3D.json
│   ├── trigger/            ← Trigger规则
│   │   └── Trigger.json
├── commands/
│   ├── Command.json
│   ├── VariableCommand.json
│   ├── VideoCommand.json
│   ├── SoundCommand.json
│   ├── VisibilityCommand.json
│   ├── IntentCommand.json
│   ├── ExternCommand.json
│   ├── GroupCommand.json
│   ├── GroupCommands.json
│   ├── CycleCommand.json
│   ├── StyleCommand.json
│   ├── RefreshWeatherCommand.json
│   ├── RefreshHealthyCommand.json
│   ├── KeepScreenOnCommand.json
│   ├── CollaborationCommand.json
│   ├── CollaborationSendCommand.json
│   ├── CollaborationDisconnectCommand.json
│   ├── EmotionCommand.json
│   ├── VibrateCommand.json
│   ├── VoiceCommand.json
│   ├── ScenarioIntentCommand.json
│   ├── SwingCommand.json
│   ├── CardInteractionCommand.json
├── global_vars.json        ← 全局变量目录（单文件）
├── rule_sources.json       ← 规则来源映射（单文件）
```

**GSON反序列化注意事项**：

- DslElementRule的scope/deviceSupport字段：JSON中为 `Map<String, Boolean>`，GSON可直接反序列化
- AttrTypeSpec的enumValues/aliases字段：为null时反序列化为空List（需自定义TypeAdapter或在POJO中设默认值）
- RuleConstraint的severity字段：JSON中为字符串"error"/"warning"/"info"，需映射为DiagnosticSeverity枚举（自定义TypeAdapter）
- DslGlobalVar的constraints字段：为null时反序列化为空List

---

## Step 3: M2 规则JSON文件编写

**目标**：从官方规范文档提取规则数据，编写所有元素/命令/全局变量/规则来源的JSON文件。

**预计耗时**：5-8天（数据量大，需逐元素审阅官方规范）

### 3.1 编写顺序（按重要性排列）

| 批次 | 内容 | 文件数 | 优先级理由 |
|---|---|---|---|
| **Batch 1** | 根元素(Lockscreen/Wallpaper/Widget/ChargingSkin) + Var + VarArray + Array + 全局变量 | 7+1 | M1文件识别依赖根元素；M4符号表依赖Var+全局变量；VarArray/Array归类为变量元素(§2.4) |
| **Batch 2** | 视图元素(Text/Image/Video/Time/DateTime) | 5 | 最常用元素，语法+语义检测覆盖面广 |
| **Batch 3** | 命令元素(Command/VariableCommand/VideoCommand/SoundCommand) + Trigger | 4+1 | Trigger/Command链分析核心，含声明式约束 |
| **Batch 4** | Group + Button + SourceImage + ImageNumber/ImageSeries | 5 | 布局容器+控件+特殊视图 |
| **Batch 5** | 几何图形(Arc/Circle/Ellipse/Line/Rectangle/PathUtil) | 6 | 规则相对简单 |
| **Batch 6** | 动画元素(6个) + GlobalVariable | 7 | 动画规则结构化；GlobalVariable是变量占位概念 |
| **Batch 7** | 特效(6个) + 3D(3个) + Swiper + Mask + GroupImage + CountDownTime | 11 | 边缘元素 |
| **Batch 8** | 命令(剩余19个) | 19 | 规则简单但数量多 |
| **Batch 9** | 规则来源映射(rule_sources.json) | 1 | 收尾，汇总所有ruleId的文档来源 |

### 3.2 每个JSON文件的编写流程

1. 打开对应的官方规范文档（`docs/themes_engine_next/raw_markdown/`）
2. 提取元素信息：标签名、属性列表、属性类型、必填/选填
3. 对照 DSL-Rule-Spec.md 确认scope矩阵、约束条件
4. 对照 DSL-Rule-Spec.md §5 确认错误检测规则ID
5. 编写JSON文件，确保格式与 DSL-Rule-Spec §6 Schema一致
6. 在本地用GSON验证JSON可正确反序列化

### 3.3 Batch 1 详解（优先启动）

#### 3.3.1 `resources/rules/elements/root/Lockscreen.json`

参照 DSL-Rule-Spec §1.2 + `themes-engine-next-lock-*.md`：

```json
{
  "element": "Lockscreen",
  "category": "root",
  "requiredAttrs": [],
  "optionalAttrs": ["frameRate", "screenWidth"],
  "attrTypes": {
    "frameRate": {"type": "number", "supportsExpression": false},
    "screenWidth": {"type": "number", "supportsExpression": false}
  },
  "allowedParents": [],
  "allowedChildren": ["Var", "GlobalVariable", "VarArray", "Group", "Text", "Image", "Video", "Time", "DateTime", "CountDownTime", "ImageNumber", "ImageSeries", "SourceImage", "Mask", "GroupImage", "Arc", "Circle", "Ellipse", "Line", "Rectangle", "PathUtil", "Swiper", "Button", "Unlocker", "Slider", "ExternalCommands", "Array", "StereoView", "MultiLayer", "AlphaAnimation", "PositionAnimation", "RotationAnimation", "SizeAnimation", "SourceAnimation", "VariableAnimation", "MeshImageTrans", "MeshImagesInMotion", "ParticleView", "DropPhysicalView", "CollisionWorld", "Fluids"],
  "inherits": null,
  "scope": {
    "Lockscreen": true,
    "Wallpaper": false,
    "LongTake": false,
    "Widget": false,
    "ChargingSkin": false
  },
  "deviceSupport": {
    "barPhone": true,
    "foldable": true,
    "tablet": true
  },
  "constraints": []
}
```

#### 3.3.2 `resources/rules/elements/root/Wallpaper.json`

同Lockscreen结构，scope改为Wallpaper=true，其余false。继承锁屏除解锁交互外的所有功能（allowedChildren不含Button/Unlocker/Slider/ExternalCommands）。

#### 3.3.3 `resources/rules/elements/root/Widget.json`

必填属性：screenWidth, screenHeight。scope: Widget=true。

#### 3.3.4 `resources/rules/elements/root/ChargingSkin.json`

scope: ChargingSkin=true。allowedChildren不含交互控件。

#### 3.3.5 `resources/rules/elements/variable/Var.json`

参照 DSL-Rule-Spec §2.4 + `themes-engine-next-base-var-*.md`：

```json
{
  "element": "Var",
  "category": "variable",
  "requiredAttrs": ["name"],
  "optionalAttrs": ["expression", "type", "threshold", "persist", "index", "values", "size", "const"],
  "attrTypes": {
    "name": {"type": "string", "supportsExpression": false},
    "expression": {"type": "string", "supportsExpression": true, "expressionKind": "auto"},
    "type": {"type": "string", "enumValues": ["number", "string", "number[]", "string[]"], "supportsExpression": false},
    "threshold": {"type": "number", "supportsExpression": true, "expressionKind": "number"},
    "persist": {"type": "string", "enumValues": ["true", "false"], "supportsExpression": false},
    "index": {"type": "string", "supportsExpression": false},
    "values": {"type": "string", "supportsExpression": false},
    "size": {"type": "number", "supportsExpression": false},
    "const": {"type": "string", "enumValues": ["true", "false"], "supportsExpression": false}
  },
  "allowedParents": ["Lockscreen", "Wallpaper", "Widget", "ChargingSkin", "Group"],
  "allowedChildren": ["Trigger", "VariableAnimation"],
  "inherits": null,
  "scope": {
    "Lockscreen": true,
    "Wallpaper": true,
    "LongTake": true,
    "Widget": true,
    "ChargingSkin": true
  },
  "deviceSupport": {
    "barPhone": true,
    "foldable": true,
    "tablet": true
  },
  "constraints": [
    {
      "ruleId": "SEM-PERSIST-001",
      "condition": "element.attrs['persist'] != null AND element.attrs['type'] IN ['time','date','week']",
      "message": "禁止对时间/日期/星期变量使用persist/globalPersist/styleGlobalPersist",
      "severity": "error",
      "suggestedFixes": ["移除persist属性"]
    }
  ]
}
```

#### 3.3.6 `resources/rules/global_vars.json`

参照 DSL-Rule-Spec §2.5 + `themes-engine-next-base-globalvar-*.md`：

```json
{
  "globalVars": [
    {"name": "battery_level", "type": "number", "scope": "global", "description": "当前电量1-100", "accessPattern": "#battery_level", "constraints": []},
    {"name": "battery_state", "type": "number", "scope": "global", "description": "电量状态: Normal(0),Charging(1),BatteryLow(2),BatteryFull(3)", "accessPattern": "#battery_state", "constraints": []},
    {"name": "screen_width", "type": "number", "scope": "global", "description": "虚拟屏幕宽度", "accessPattern": "#screen_width", "constraints": []},
    {"name": "screen_height", "type": "number", "scope": "global", "description": "虚拟屏幕高度", "accessPattern": "#screen_height", "constraints": []},
    {"name": "touch_x", "type": "number", "scope": "global", "description": "当前触摸点X坐标", "accessPattern": "#touch_x", "constraints": []},
    {"name": "touch_y", "type": "number", "scope": "global", "description": "当前触摸点Y坐标", "accessPattern": "#touch_y", "constraints": []}
  ]
}
```

完整全局变量列表需从官方文档提取，约30+条目。

---

## Step 4: M1 文件识别

**目标**：完成DSL文件识别模块，极小工作量。

**预计耗时**：0.5-1天

**依赖**：M2 RuleRepository.getRootElementNames()

### 4.1 DslFileMatcher接口

**`core/fileidentification/DslFileMatcher.java`**

```java
public interface DslFileMatcher {
    boolean isDslFile(String filePath, String content);
}
```

### 4.2 DslFileIdentifier实现

**`core/fileidentification/DslFileIdentifier.java`**

逻辑：
1. 检查filePath扩展名是否为 `.xml`
2. 解析content前N行，提取根元素标签名
3. 从RuleRepository.getRootElementNames()获取合法根元素集合
4. 根元素标签名在集合中 → 返回true

---

## Step 5: M2 单元测试 + 集成验证

**目标**：确保规则库数据完整性和查询接口正确性。

**预计耗时**：2-3天

### 5.1 测试文件位置

```
feature/analysis/src/test/java/com/huawei/theme/analysis/core/rulelibrary/
├── DslElementRuleTest.java       ← 数据模型POJO测试
├── JsonRuleLoaderTest.java       ← JSON加载测试
├── DefaultRuleRepositoryTest.java ← RuleRepository查询接口测试
```

### 5.2 测试内容

| 测试类 | 测试要点 |
|---|---|
| DslElementRuleTest | POJO构造+Builder+字段完整性 |
| JsonRuleLoaderTest | JSON文件扫描+反序列化+空目录/缺文件/格式错误异常场景 |
| DefaultRuleRepositoryTest | 全部接口方法：getElementRule(存在/不存在)、getAllElementNames、getRootElementNames、getAttrTypeSpec(存在/不存在)、getConstraints(有约束/无约束)、getGlobalVar、getAllGlobalVars |

### 5.3 验证要点

- 每个已编写的元素规则JSON都能被正确加载
- scope矩阵数据正确（Lockscreen/Wallpaper/Widget/ChargingSkin/LongTake）
- constraints声明式条件字符串语法正确（符合DslRuleCondition.g4规则DSL语法）
- global_vars.json中全局变量条目完整
- null字段安全处理（enumValues为null→空List，constraints为null→空List）

---

## 总体时间估算

| Step | 内容 | 耗时 |
|---|---|---|
| Step 1 | 共享基础设施（包结构+Diagnostic模型+接口定义） | 2-3天 |
| Step 2 | M2 Core层（5个POJO+RuleRepository接口+DefaultRuleRepository+JsonRuleLoader） | 3-4天 |
| Step 3 | 规则JSON编写（Batch 1优先，后续批次可渐进） | 5-8天 |
| Step 4 | M1 文件识别（顺手完成） | 0.5-1天 |
| Step 5 | M2 单元测试 + 验证 | 2-3天 |
| **合计** | | **13-17天** |

Step 3可与Step 5并行——编写一批JSON后立即编写该批次的加载测试，边写边验证。

---

## 后续衔接（队友回来后）

完成M2后，三条并行线可立即启动：

| 人员 | 可启动任务 | 依赖M2的接口 |
|---|---|---|
| Person A | M0 → M3 | RuleRepository.getElementRule() + getAttrTypeSpec() |
| Person B | M4-A（模式匹配Analyzer） | RuleRepository全部接口 |
| Person C | M4-B（TypeInference） | RuleRepository.getAttrTypeSpec() + getGlobalVar() |
