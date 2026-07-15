# suggestedFixes 结构化迁移实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 JSON 规则文件中的 suggestedFixes 从纯中文字符串格式迁移为 `{text, type, target, value?, range?}` 结构化对象格式，并同步更新 Java 数据模型、反序列化、Generator 消费链路和所有测试。

**Architecture:** 采用 C 方案——JSON 中每条 suggestedFixes 改为 `{text: "中文描述", type: "ADD_ATTR", target: "width", value?: "...", range?: "0-255"}` 结构化对象。text 字段仅供 UI 展示和文档阅读，Generator 代码只消费 type/target/value/range 字段，不再依赖 SuggestedFixParser 的正则匹配。SuggestedFixParser 保留但降级为"当 type 字段缺失或为 UNKNOWN 时的 fallback 解析器"。全量迁移 38 个 JSON 文件中的 66 条约束。

**Tech Stack:** Java 17, Gradle 8.2, Lombok, Gson, JUnit 5

---

## 新 JSON 格式规范

每条 suggestedFix 从字符串变为对象：

```json
// 旧格式（将被替换）
"suggestedFixes": ["移除play属性", "移除sound属性"]

// 新格式
"suggestedFixes": [
  {"text": "移除play属性", "type": "REMOVE_ATTR", "target": "play"},
  {"text": "移除sound属性", "type": "REMOVE_ATTR", "target": "sound"}
]
```

### 字段定义

| 字段 | 必填 | 说明 |
|------|------|------|
| `text` | ✅ | 中文描述，仅供 UI 展示，Generator 不消费 |
| `type` | ✅ | FixActionType 枚举名（全大写+下划线），Generator 直接消费 |
| `target` | ✅ | 操作目标名（属性名、标签名、变量名等） |
| `value` | ❌ | 单值场景：要设置的值（如 `"center_crop"`、`"true"`） |
| `range` | ❌ | 范围场景：值范围字符串（如 `"0-255"`、`"-10到7"`），仅 CLAMP_VALUE/REDUCE_VALUE 类型使用 |

### type 可选值（来自 FixActionType 枚举）

`REMOVE_ATTR`, `ADD_ATTR`, `SET_VALUE`, `CLAMP_VALUE`, `REPLACE_ENUM`, `REPLACE_VALUE`, `ADD_CHILD`, `REMOVE_CHILD`, `ADD_DECLARATION`, `MOVE_ELEMENT`, `REDUCE_VALUE`, `USE_ALTERNATIVE`, `DELETE_NODE`, `MODIFY_NAME_FORMAT`, `CONFIRM`, `DECLARE_OUTSIDE`, `UNKNOWN`

---

## 文件结构映射

| 文件 | 变更类型 | 职责 |
|------|---------|------|
| `RuleConstraint.java` | 修改 | suggestedFixes 类型从 `List<String>` → `List<SuggestedFix>` |
| `SuggestedFix.java` | 新建 | 结构化建议修复数据模型 |
| `Diagnostic.java` | 修改 | suggestedFixes 类型从 `List<String>` → `List<SuggestedFix>` |
| `JsonRuleLoader.java` | 修改 | 反序列化适配 + normalize 适配 |
| `ConstraintAnalyzer.java` | 修改 | buildDiagnostic 传递 SuggestedFix 列表 |
| `SuggestedFixParser.java` | 修改 | 新增 `parse(List<SuggestedFix>)` 方法，从结构化字段直接读取；保留 `parse(List<String>)` 为 fallback |
| `ConstraintFixGenerator.java` | 修改 | 使用新 parse 方法 |
| `RemoveAttrGenerator.java` | 修改 | 使用新 parse 方法 |
| `ClampValueGenerator.java` | 修改 | 使用新 parse 方法 + range 字段 |
| `InsertAttrGenerator.java` | 修改 | 可从 SuggestedFix.target 获取属性名（不再从 message 解析） |
| `ReplaceEnumGenerator.java` | 修改 | 可从 SuggestedFix.target 获取属性名 |
| `FixExpressionGenerator.java` | 无变更 | 不依赖 suggestedFixes |
| 38 个 JSON 规则文件 | 修改 | suggestedFixes 格式迁移 |
| `SuggestedFixParserTest.java` | 修改 | 新增结构化解析测试 |
| `ConstraintFixGeneratorTest.java` | 修改 | 使用 SuggestedFix 对象构建诊断 |
| `RemoveAttrGeneratorTest.java` | 修改 | 使用 SuggestedFix 对象构建诊断 |
| `ClampValueGeneratorTest.java` | 修改 | 使用 SuggestedFix 对象构建诊断 |
| `ConstraintAnalyzerTest.java` | 修改 | 使用 SuggestedFix 对象构建诊断 |
| `DiagnosticTest.java` | 修改 | 使用 SuggestedFix 对象构建诊断 |
| `QuickFixIntegrationTest.java` | 修改 | 使用 SuggestedFix 对象构建诊断 |
| `InsertAttrGeneratorTest.java` | 可能修改 | 如果改用 SuggestedFix.target |

---

### Task 1: 新建 SuggestedFix 数据模型 + FixActionType enum 迁移

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/rulelibrary/model/SuggestedFix.java`
- Modify: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/FixAction.java`

- [ ] **Step 1: 创建 SuggestedFix.java**

```java
package com.huawei.theme.analysis.core.rulelibrary.model;

import lombok.Builder;
import lombok.Data;

import com.huawei.theme.analysis.core.quickfix.FixActionIntent;

@Data
@Builder
public class SuggestedFix {
    String text;
    FixActionIntent.FixActionType type;
    String target;
    String value;
    String range;
}
```

注意：SuggestedFix 放在 `rulelibrary/model` 包（与 RuleConstraint 同包），因为它是 JSON 规则数据的一部分。但它的 `type` 字段引用了 `quickfix.FixActionIntent.FixActionType`——这打破了 rulelibrary → quickfix 的依赖方向（rulelibrary 是底层，quickfix 是上层）。

**依赖方向解决方案：** 将 `FixActionType` 枚举从 `FixActionIntent` 中提取出来，放到独立的 `rulelibrary.model.FixType` 或 `shared.model.FixType` 包中，让 rulelibrary 和 quickfix 都依赖它。

- [ ] **Step 2: 创建 FixType.java（独立枚举）**

```java
package com.huawei.theme.analysis.core.shared.model;

public enum FixType {
    REMOVE_ATTR,
    ADD_ATTR,
    SET_VALUE,
    CLAMP_VALUE,
    REPLACE_ENUM,
    REPLACE_VALUE,
    ADD_CHILD,
    REMOVE_CHILD,
    ADD_DECLARATION,
    MOVE_ELEMENT,
    REDUCE_VALUE,
    USE_ALTERNATIVE,
    DELETE_NODE,
    MODIFY_NAME_FORMAT,
    CONFIRM,
    DECLARE_OUTSIDE,
    UNKNOWN
}
```

放在 `shared.model` 包，因为它是 rulelibrary 和 quickfix 共享的类型。

- [ ] **Step 3: 修改 FixActionIntent.java — 使用 FixType 替代内部枚举**

将 `FixActionIntent.FixActionType` 改为引用 `FixType`：

```java
package com.huawei.theme.analysis.core.quickfix;

import lombok.Builder;
import lombok.Data;

import com.huawei.theme.analysis.core.shared.model.FixType;

@Data
@Builder
public class FixActionIntent {
    FixType actionType;
    String targetName;
    String targetValue;
    String description;
}
```

删除 FixActionIntent 内部的 `FixActionType` 枚举定义。全局替换所有 `FixActionIntent.FixActionType` → `FixType`，所有 `FixActionType.xxx` → `FixType.xxx`。

- [ ] **Step 4: 修改 FixAction.java — fixType 使用 FixType enum 替代 String**

```java
package com.huawei.theme.analysis.core.quickfix;

import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Data;

import com.huawei.theme.analysis.core.shared.model.FixType;
import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;

@Data
@Builder
public class FixAction {
    FixType fixType;
    TextRange targetRange;
    String replacementText;
    @Builder.Default
    List<CandidateItem> candidates = Collections.emptyList();
    String description;
}
```

这意味着 ConstraintFixGenerator 的 `mapFixType()` switch 方法可以删除——不再需要把 FixType 映射为 String，直接传递 FixType 即可。

- [ ] **Step 5: 修改 SuggestedFix.java — 使用 FixType**

```java
package com.huawei.theme.analysis.core.rulelibrary.model;

import lombok.Builder;
import lombok.Data;

import com.huawei.theme.analysis.core.shared.model.FixType;

@Data
@Builder
public class SuggestedFix {
    String text;
    FixType type;
    String target;
    String value;
    String range;
}
```

- [ ] **Step 6: 全局替换 FixActionIntent.FixActionType → FixType**

涉及文件（按 grep 结果）：
- SuggestedFixParser.java
- SuggestedFixParserTest.java
- ConstraintFixGenerator.java
- ConstraintFixGeneratorTest.java
- RemoveAttrGenerator.java
- RemoveAttrGeneratorTest.java
- ClampValueGenerator.java
- ClampValueGeneratorTest.java
- QuickFixIntegrationTest.java

所有 `FixActionIntent.FixActionType.xxx` → `FixType.xxx`
所有 `FixActionIntent.FixActionType` → `FixType`

- [ ] **Step 7: 删除 ConstraintFixGenerator.mapFixType()**

ConstraintFixGenerator 的 `intentToFixAction()` 中，`fixType = mapFixType(intent.getActionType())` 改为 `fixType = intent.getActionType()`，直接传递 FixType。删除整个 `mapFixType()` 方法。

- [ ] **Step 8: 修改所有 Generator 的 FixAction.builder().fixType(...) 调用**

原来 `fixType("insert_attr")` → `fixType(FixType.ADD_ATTR)` 等：
- InsertAttrGenerator: `fixType("insert_attr")` → `fixType(FixType.ADD_ATTR)`
- RemoveAttrGenerator: `fixType("remove_attr")` → `fixType(FixType.REMOVE_ATTR)`
- ReplaceEnumGenerator: `fixType("replace_enum")` → `fixType(FixType.REPLACE_ENUM)`
- ClampValueGenerator: `fixType("clamp_value")` → `fixType(FixType.CLAMP_VALUE)`
- FixExpressionGenerator: `fixType("fix_expression")` → `fixType(FixType.SET_VALUE)`（表达式修正本质是设置值）

注意：`fix_expression` 不在 FixType 枚举中，需要决定用什么。选项：1) 在 FixType 中新增 `FIX_EXPRESSION`；2) 用 `SET_VALUE` 代替。考虑到 FixExpressionGenerator 是唯一处理 SYN-EXPR-001 的专用 Generator，新增 `FIX_EXPRESSION` 更精确。

- [ ] **Step 9: 在 FixType enum 中新增 FIX_EXPRESSION**

```java
public enum FixType {
    REMOVE_ATTR,
    ADD_ATTR,
    SET_VALUE,
    CLAMP_VALUE,
    REPLACE_ENUM,
    REPLACE_VALUE,
    ADD_CHILD,
    REMOVE_CHILD,
    ADD_DECLARATION,
    MOVE_ELEMENT,
    REDUCE_VALUE,
    USE_ALTERNATIVE,
    DELETE_NODE,
    MODIFY_NAME_FORMAT,
    CONFIRM,
    DECLARE_OUTSIDE,
    FIX_EXPRESSION,
    UNKNOWN
}
```

- [ ] **Step 10: 运行编译验证**

Run: `./gradlew :feature:analysis:compileJava`
Expected: BUILD SUCCESSFUL（有编译错误则修复）

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat(M5): create SuggestedFix model, FixType enum; migrate FixAction/FixActionIntent from String to FixType"
```

---

### Task 2: 修改 RuleConstraint + Diagnostic — suggestedFixes 类型迁移

**Files:**
- Modify: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/rulelibrary/model/RuleConstraint.java`
- Modify: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/diagnostic/Diagnostic.java`

- [ ] **Step 1: 修改 RuleConstraint.java**

将 `suggestedFixes` 从 `List<String>` 改为 `List<SuggestedFix>`：

```java
package com.huawei.theme.analysis.core.rulelibrary.model;

import java.util.Collections;
import java.util.List;

import lombok.Builder;
import lombok.Data;

import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

@Data
@Builder
public class RuleConstraint {
    String ruleId;
    String condition;
    String message;
    DiagnosticSeverity severity;
    @Builder.Default List<SuggestedFix> suggestedFixes = Collections.emptyList();
}
```

注意：需要添加 `import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;`（同包，无需显式 import）

- [ ] **Step 2: 修改 Diagnostic.java**

将 `suggestedFixes` 从 `List<String>` 改为 `List<SuggestedFix>`：

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
    DslAstNode astNode;

    @Builder.Default
    List<SuggestedFix> suggestedFixes = Collections.emptyList();
    String ruleDocUrl;
}
```

添加 import: `import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;`

注意：Diagnostic 在 `shared.diagnostic` 包，SuggestedFix 在 `rulelibrary.model` 包——这是 shared → rulelibrary 的依赖。这合理吗？Diagnostic 是输出模型，需要携带修复建议信息，对 rulelibrary 的依赖是合理的（shared 本身就是各模块共享的定义层）。

- [ ] **Step 3: 运行编译验证**

Run: `./gradlew :feature:analysis:compileJava`
Expected: 编译错误——所有使用 `Diagnostic.builder().suggestedFixes(List.of("xxx"))` 的测试和代码都需要改为 `List<SuggestedFix>`。先记录错误数量，下一步统一修复。

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(M5): migrate RuleConstraint.suggestedFixes and Diagnostic.suggestedFixes from List<String> to List<SuggestedFix>"
```

---

### Task 3: 修改 JsonRuleLoader — 反序列化适配

**Files:**
- Modify: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/rulelibrary/JsonRuleLoader.java`

- [ ] **Step 1: 为 SuggestedFix 注册 Gson 反序列化适配器**

Gson 需要处理 `FixType` 枚举的反序列化。JSON 中 `type` 字段存的是 `"ADD_ATTR"` 等字符串，需要映射为 `FixType.ADD_ATTR` 枚举。

创建 `FixTypeAdapter.java`：

```java
package com.huawei.theme.analysis.core.shared.diagnostic.adapter;

import com.google.gson.*;
import com.huawei.theme.analysis.core.shared.model.FixType;

import java.lang.reflect.Type;

public class FixTypeAdapter implements JsonSerializer<FixType>, JsonDeserializer<FixType> {

    @Override
    public JsonElement serialize(FixType src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.name());
    }

    @Override
    public FixType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        String name = json.getAsString();
        try {
            return FixType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return FixType.UNKNOWN;
        }
    }
}
```

- [ ] **Step 2: 在 JsonRuleLoader.createGson() 中注册 FixTypeAdapter**

```java
private static Gson createGson() {
    return new GsonBuilder()
            .registerTypeAdapter(DiagnosticSeverity.class, new DiagnosticSeverityAdapter())
            .registerTypeAdapter(FixType.class, new FixTypeAdapter())
            .setPrettyPrinting()
            .create();
}
```

添加 import: `import com.huawei.theme.analysis.core.shared.model.FixType;` 和 `import com.huawei.theme.analysis.core.shared.diagnostic.adapter.FixTypeAdapter;`

- [ ] **Step 3: 修改 normalizeConstraints 方法**

```java
private void normalizeConstraints(DslElementRule rule) {
    for (RuleConstraint constraint : rule.getConstraints()) {
        if (constraint.getSuggestedFixes() == null) {
            constraint.setSuggestedFixes(Collections.emptyList());
        }
        for (SuggestedFix fix : constraint.getSuggestedFixes()) {
            if (fix.getType() == null) {
                fix.setType(FixType.UNKNOWN);
            }
        }
    }
}
```

注意：`SuggestedFix` 现在是对象而非字符串，null 安全需要检查 type 字段。同时也需要修改 `normalizeGlobalVar` 中相同的逻辑。

- [ ] **Step 4: 修改 normalizeGlobalVar 方法**

```java
private void normalizeGlobalVar(DslGlobalVar var) {
    if (var.getConstraints() == null) {
        var.setConstraints(Collections.emptyList());
    }
    for (RuleConstraint constraint : var.getConstraints()) {
        if (constraint.getSuggestedFixes() == null) {
            constraint.setSuggestedFixes(Collections.emptyList());
        }
        for (SuggestedFix fix : constraint.getSuggestedFixes()) {
            if (fix.getType() == null) {
                fix.setType(FixType.UNKNOWN);
            }
        }
    }
}
```

- [ ] **Step 5: 运行编译验证**

Run: `./gradlew :feature:analysis:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(M5): add FixTypeAdapter for Gson deserialization; update JsonRuleLoader normalize for SuggestedFix objects"
```

---

### Task 4: 修改 ConstraintAnalyzer — 传递 SuggestedFix 列表

**Files:**
- Modify: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/semanticanalysis/ConstraintAnalyzer.java`

- [ ] **Step 1: 修改 buildDiagnostic 方法**

`.suggestedFixes(constraint.getSuggestedFixes())` 行不需要改——类型已经从 `List<String>` 变为 `List<SuggestedFix>`，Gson 反序列化后 constraint.getSuggestedFixes() 返回的就是 `List<SuggestedFix>`。只要 Diagnostic.suggestedFixes 也是 `List<SuggestedFix>`，这行代码自然兼容。

确认 ConstraintAnalyzer.java 第 108 行 `.suggestedFixes(constraint.getSuggestedFixes())` 无需修改。

- [ ] **Step 2: 运行编译验证**

Run: `./gradlew :feature:analysis:compileJava`
Expected: 编译错误集中在测试代码（Diagnostic.builder().suggestedFixes(...) 的参数类型变化）。主代码应该编译成功。

- [ ] **Step 3: Commit（如有变更）**

如果 ConstraintAnalyzer.java 不需要修改，跳过此 commit。

---

### Task 5: 修改 SuggestedFixParser — 新增结构化解析方法

**Files:**
- Modify: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/SuggestedFixParser.java`

- [ ] **Step 1: 新增 parse(List<SuggestedFix>) 方法**

```java
public static List<FixActionIntent> parse(List<SuggestedFix> suggestedFixes) {
    if (suggestedFixes == null) {
        return new ArrayList<>();
    }
    List<FixActionIntent> intents = new ArrayList<>();
    for (SuggestedFix fix : suggestedFixes) {
        if (fix == null) {
            continue;
        }
        if (fix.getType() != null && fix.getType() != FixType.UNKNOWN) {
            intents.add(FixActionIntent.builder()
                    .actionType(fix.getType())
                    .targetName(fix.getTarget())
                    .targetValue(fix.getValue() != null ? fix.getValue() : fix.getRange())
                    .description(fix.getText())
                    .build());
        } else {
            intents.add(parseSingle(fix.getText()));
        }
    }
    return intents;
}
```

核心逻辑：
- 如果 `type` 字段有效（非 null、非 UNKNOWN），直接从结构化字段构建 FixActionIntent
- 如果 `type` 为 null/UNKNOWN，fallback 到 `parseSingle(text)` 正则解析
- `targetValue` 优先取 `value` 字段，其次取 `range` 字段

- [ ] **Step 2: 保留 parse(List<String>) 但标记为 @Deprecated**

原有的 `parse(List<String>)` 方法保留用于旧格式 fallback（任何 Diagnostic 如果 suggestedFixes 是纯字符串列表），但标记 `@Deprecated` 表示将移除。

- [ ] **Step 3: 运行编译验证**

Run: `./gradlew :feature:analysis:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(M5): add SuggestedFixParser.parse(List<SuggestedFix>) for structured format; deprecate parse(List<String>)"
```

---

### Task 6: 修改 Generator 消费链路 — 使用新 parse 方法

**Files:**
- Modify: `ConstraintFixGenerator.java`
- Modify: `RemoveAttrGenerator.java`
- Modify: `ClampValueGenerator.java`

- [ ] **Step 1: ConstraintFixGenerator — 改用新 parse**

```java
@Override
public List<FixAction> generate(Diagnostic diagnostic) {
    List<SuggestedFix> suggestedFixes = diagnostic.getSuggestedFixes();
    if (suggestedFixes == null || suggestedFixes.isEmpty()) {
        return Collections.emptyList();
    }
    DslAstNode astNode = diagnostic.getAstNode();
    if (!(astNode instanceof DslElementNode elementNode)) {
        return Collections.emptyList();
    }
    List<FixActionIntent> intents = SuggestedFixParser.parse(suggestedFixes);
    List<FixAction> actions = new ArrayList<>();
    for (FixActionIntent intent : intents) {
        actions.add(intentToFixAction(intent, elementNode));
    }
    return actions;
}
```

变更点：`List<String> suggestedFixes` → `List<SuggestedFix> suggestedFixes`，`SuggestedFixParser.parse(suggestedFixes)` 调用新方法（参数类型变了，自动路由到 `parse(List<SuggestedFix>)`）。

同时删除 `mapFixType()` 方法，`intentToFixAction()` 中改为直接使用 `intent.getActionType()` 作为 FixType：

```java
private FixAction intentToFixAction(FixActionIntent intent, DslElementNode elementNode) {
    FixType fixType = intent.getActionType();
    TextRange targetRange = buildRange(intent, elementNode);
    String replacementText = buildReplacement(intent);
    String description = intent.getDescription();
    return FixAction.builder()
            .fixType(fixType)
            .targetRange(targetRange)
            .replacementText(replacementText)
            .candidates(Collections.emptyList())
            .description(description)
            .build();
}
```

- [ ] **Step 2: RemoveAttrGenerator — 改用新 parse**

```java
List<SuggestedFix> suggestedFixes = diagnostic.getSuggestedFixes();
if (suggestedFixes == null || suggestedFixes.isEmpty()) {
    return Collections.emptyList();
}
List<FixActionIntent> intents = SuggestedFixParser.parse(suggestedFixes);
```

变更点同上：参数类型从 `List<String>` → `List<SuggestedFix>`。

- [ ] **Step 3: ClampValueGenerator — 改用新 parse + range 字段**

```java
List<SuggestedFix> suggestedFixes = diagnostic.getSuggestedFixes();
if (suggestedFixes == null || suggestedFixes.isEmpty()) {
    return Collections.emptyList();
}
List<FixActionIntent> intents = SuggestedFixParser.parse(suggestedFixes);
FixActionIntent clampIntent = null;
for (FixActionIntent intent : intents) {
    if (intent.getActionType() == FixType.CLAMP_VALUE) {
        clampIntent = intent;
        break;
    }
}
if (clampIntent == null) {
    return Collections.emptyList();
}
String attrName = clampIntent.getTargetName();
String upperBound = extractUpperBound(clampIntent.getTargetValue());
```

`clampIntent.getTargetValue()` 会从 SuggestedFix 的 `range` 字段获取（因为 parse 方法中 `targetValue = value != null ? value : range`），所以 `extractUpperBound` 逻辑不变。

- [ ] **Step 4: 运行编译验证**

Run: `./gradlew :feature:analysis:compileJava`
Expected: 编译错误仍在测试代码中。主代码应该编译成功。

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(M5): update Generator chain to consume List<SuggestedFix>; remove ConstraintFixGenerator.mapFixType()"
```

---

### Task 7: 修改所有测试代码 — 适配 SuggestedFix 对象

**Files:**
- Modify: 所有使用 `Diagnostic.builder().suggestedFixes(List.of("xxx"))` 的测试文件

涉及文件列表（按 grep 结果）：
1. `QuickFixIntegrationTest.java`
2. `DiagnosticTest.java`
3. `RemoveAttrGeneratorTest.java`
4. `ConstraintFixGeneratorTest.java`
5. `ClampValueGeneratorTest.java`
6. `ConstraintAnalyzerTest.java`

- [ ] **Step 1: 在 QuickFixIntegrationTest.java 中替换 suggestedFixes 构建**

```java
// 旧：
.suggestedFixes(List.of("移除play属性", "移除sound属性"))

// 新：
.suggestedFixes(List.of(
    SuggestedFix.builder().text("移除play属性").type(FixType.REMOVE_ATTR).target("play").build(),
    SuggestedFix.builder().text("移除sound属性").type(FixType.REMOVE_ATTR).target("sound").build()
))
```

添加 import: `import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;` 和 `import com.huawei.theme.analysis.core.shared.model.FixType;`

- [ ] **Step 2: 同样修改 DiagnosticTest.java**

```java
// 旧：
.suggestedFixes(List.of("移除values属性", "移除size属性"))

// 新：
.suggestedFixes(List.of(
    SuggestedFix.builder().text("移除values属性").type(FixType.REMOVE_ATTR).target("values").build(),
    SuggestedFix.builder().text("移除size属性").type(FixType.REMOVE_ATTR).target("size").build()
))
```

- [ ] **Step 3: 修改 RemoveAttrGeneratorTest.java**

所有 5 个测试中的 `.suggestedFixes(...)` 替换。例如：

```java
// 旧：
.suggestedFixes(List.of("移除play属性", "移除sound属性"))
// 新：
.suggestedFixes(List.of(
    SuggestedFix.builder().text("移除play属性").type(FixType.REMOVE_ATTR).target("play").build(),
    SuggestedFix.builder().text("移除sound属性").type(FixType.REMOVE_ATTR).target("sound").build()
))

// 旧：
.suggestedFixes(List.of("添加src属性指定图片路径"))
// 新：
.suggestedFixes(List.of(
    SuggestedFix.builder().text("添加src属性指定图片路径").type(FixType.ADD_ATTR).target("src").build()
))

// 旧：
.suggestedFixes(List.of("移除play属性"))
// 新：
.suggestedFixes(List.of(
    SuggestedFix.builder().text("移除play属性").type(FixType.REMOVE_ATTR).target("play").build()
))

// 旧：
.suggestedFixes(null)
// 新：保持 null（测试空值回退）
.suggestedFixes(null)

// 旧：
.suggestedFixes(List.of("移除sound属性"))
// 新：
.suggestedFixes(List.of(
    SuggestedFix.builder().text("移除sound属性").type(FixType.REMOVE_ATTR).target("sound").build()
))
```

- [ ] **Step 4: 修改 ConstraintFixGeneratorTest.java**

5 个测试同理替换。

```java
// test 1: 添加src属性指定图片路径 + 添加srcExp属性指定图片源表达式
.suggestedFixes(List.of(
    SuggestedFix.builder().text("添加src属性指定图片路径").type(FixType.ADD_ATTR).target("src").build(),
    SuggestedFix.builder().text("添加srcExp属性指定图片源表达式").type(FixType.ADD_ATTR).target("srcExp").build()
))

// test 2: 设置scaleType=center_crop
.suggestedFixes(List.of(
    SuggestedFix.builder().text("设置scaleType=center_crop").type(FixType.SET_VALUE).target("scaleType").value("center_crop").build()
))

// test 3: 设置alpha值在0-255范围内
.suggestedFixes(List.of(
    SuggestedFix.builder().text("设置alpha值在0-255范围内").type(FixType.CLAMP_VALUE).target("alpha").range("0-255").build()
))

// test 4: 空列表
.suggestedFixes(Collections.emptyList())

// test 5: 移除play属性 + 移除sound属性
.suggestedFixes(List.of(
    SuggestedFix.builder().text("移除play属性").type(FixType.REMOVE_ATTR).target("play").build(),
    SuggestedFix.builder().text("移除sound属性").type(FixType.REMOVE_ATTR).target("sound").build()
))
```

同时修改断言——`assertEquals("remove_attr", actions.get(0).getFixType())` → `assertEquals(FixType.REMOVE_ATTR, actions.get(0).getFixType())`，`assertEquals("insert_attr", ...)` → `assertEquals(FixType.ADD_ATTR, ...)`，`assertEquals("clamp_value", ...)` → `assertEquals(FixType.CLAMP_VALUE, ...)` 等。

- [ ] **Step 5: 修改 ClampValueGeneratorTest.java**

7 个测试同理替换。重点：
- `设置alpha值在0-255范围内` → `SuggestedFix.builder().text("...").type(FixType.CLAMP_VALUE).target("alpha").range("0-255").build()`
- `移除alpha属性` → `SuggestedFix.builder().text("移除alpha属性").type(FixType.REMOVE_ATTR).target("alpha").build()`（此测试验证非 CLAMP_VALUE intent 被过滤）

断言也需更新：`assertEquals("clamp_value", ...)` → `assertEquals(FixType.CLAMP_VALUE, ...)`。

- [ ] **Step 6: 修改 ConstraintAnalyzerTest.java**

所有使用 `.suggestedFixes(List.of("xxx"))` 的测试行替换。

- [ ] **Step 7: 修改 SuggestedFixParserTest.java — 新增结构化解析测试**

保留原有 19 个正则解析测试（它们测试 parseSingle 和 parse(List<String>) fallback）。

新增测试：

```java
@Test
void parseStructuredFixes() {
    List<SuggestedFix> fixes = List.of(
        SuggestedFix.builder().text("移除play属性").type(FixType.REMOVE_ATTR).target("play").build(),
        SuggestedFix.builder().text("添加width属性").type(FixType.ADD_ATTR).target("width").build()
    );
    List<FixActionIntent> intents = SuggestedFixParser.parse(fixes);
    assertEquals(2, intents.size());
    assertEquals(FixType.REMOVE_ATTR, intents.get(0).getActionType());
    assertEquals("play", intents.get(0).getTargetName());
    assertEquals(FixType.ADD_ATTR, intents.get(1).getActionType());
    assertEquals("width", intents.get(1).getTargetName());
}

@Test
void parseStructuredWithRange() {
    List<SuggestedFix> fixes = List.of(
        SuggestedFix.builder().text("设置alpha值在0-255范围内").type(FixType.CLAMP_VALUE).target("alpha").range("0-255").build()
    );
    List<FixActionIntent> intents = SuggestedFixParser.parse(fixes);
    assertEquals(FixType.CLAMP_VALUE, intents.get(0).getActionType());
    assertEquals("alpha", intents.get(0).getTargetName());
    assertEquals("0-255", intents.get(0).getTargetValue());
}

@Test
void parseStructuredWithUnknownFallsBackToRegex() {
    List<SuggestedFix> fixes = List.of(
        SuggestedFix.builder().text("移除play属性").type(FixType.UNKNOWN).build()
    );
    List<FixActionIntent> intents = SuggestedFixParser.parse(fixes);
    assertEquals(FixType.REMOVE_ATTR, intents.get(0).getActionType());
    assertEquals("play", intents.get(0).getTargetName());
}

@Test
void parseStructuredWithNullTypeFallsBackToRegex() {
    List<SuggestedFix> fixes = List.of(
        SuggestedFix.builder().text("移除play属性").type(null).build()
    );
    List<FixActionIntent> intents = SuggestedFixParser.parse(fixes);
    assertEquals(FixType.REMOVE_ATTR, intents.get(0).getActionType());
}
```

- [ ] **Step 8: 运行全量测试验证**

Run: `./gradlew :feature:analysis:test`
Expected: ALL TESTS PASS

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat(M5): update all test code to use SuggestedFix objects; add structured parse tests"
```

---

### Task 8: JSON 规则文件全量迁移（38 个文件）

这是工作量最大的任务。需要将 38 个 JSON 文件中的 66 条约束的 suggestedFixes 从字符串数组改为结构化对象数组。

按扫描结果分类处理。为避免上下文过长，将分 3 批 subagent 执行。

**迁移规则：**

| 原始中文文本 | 新格式 |
|-------------|--------|
| `"移除X属性"` | `{"text": "移除X属性", "type": "REMOVE_ATTR", "target": "X"}` |
| `"移除X/Y/Z属性"` | `{"text": "移除X/Y/Z属性", "type": "REMOVE_ATTR", "target": "X/Y/Z"}` |
| `"添加X属性"` | `{"text": "添加X属性", "type": "ADD_ATTR", "target": "X"}` |
| `"添加X属性指定Y"` | `{"text": "添加X属性指定Y", "type": "ADD_ATTR", "target": "X"}` |
| `"设置X=Y"` | `{"text": "设置X=Y", "type": "SET_VALUE", "target": "X", "value": "Y"}` |
| `"设置X值在Z范围内"` | `{"text": "设置X值在Z范围内", "type": "CLAMP_VALUE", "target": "X", "range": "Z"}` |
| `"设置X值不超过Y"` | `{"text": "设置X值不超过Y", "type": "CLAMP_VALUE", "target": "X", "value": "Y"}` |
| `"修改X为合法枚举值"` | `{"text": "修改X为合法枚举值", "type": "REPLACE_ENUM", "target": "X"}` |
| `"将X改为Y"` | `{"text": "将X改为Y", "type": "REPLACE_VALUE", "target": "X", "value": "Y"}` |
| `"更改X为Y"` | `{"text": "更改X为Y", "type": "REPLACE_VALUE", "target": "X", "value": "Y"}` |
| `"添加X子元素"` | `{"text": "添加X子元素", "type": "ADD_CHILD", "target": "X"}` |
| `"添加<X/>声明"` | `{"text": "添加<X/>声明", "type": "ADD_DECLARATION", "target": "X"}` |
| `"将X移至Y"` | `{"text": "将X移至Y", "type": "MOVE_ELEMENT", "target": "X", "value": "Y"}` |
| `"减小X值至Y以内"` | `{"text": "减小X值至Y以内", "type": "REDUCE_VALUE", "target": "X", "range": "Y"}` |
| `"使用X替代"` | `{"text": "使用X替代", "type": "USE_ALTERNATIVE", "target": "X"}` |
| `"使用X替代Y"` | `{"text": "使用X替代Y", "type": "USE_ALTERNATIVE", "target": "X", "value": "Y"}` |
| `"删除X节点"` | `{"text": "删除X节点", "type": "DELETE_NODE", "target": "X"}` |
| `"将X值调整到合法范围内"` | `{"text": "将X值调整到合法范围内", "type": "CLAMP_VALUE", "target": "X"}` （无具体 range，range 从 condition/message 推导） |
| `"移除X子标签"` | `{"text": "移除X子标签", "type": "REMOVE_CHILD", "target": "X"}` |
| `"为X添加Y属性"` | `{"text": "为X添加Y属性", "type": "ADD_ATTR", "target": "Y"}` |

**特殊条目处理：**

| 原始文本 | type | 特殊处理说明 |
|---------|------|------------|
| `"移除isFullScreenNode或移除isTransparent"` | REMOVE_ATTR | 拆为两条：`{type:REMOVE_ATTR, target:"isFullScreenNode"}` + `{type:REMOVE_ATTR, target:"isTransparent"}` |
| `"移除align属性或移除isBackground属性"` | REMOVE_ATTR | 拆为两条 |
| `"移除persist/globalPersist/styleGlobalPersist属性"` | REMOVE_ATTR | 合并为一条：target="persist/globalPersist/styleGlobalPersist" |
| `"为最后一个Image添加hybridMode属性"` | ADD_ATTR | target="hybridMode"（省略位置限定词"最后一个Image"，因为 Generator 会从 AST 上下文判断） |
| `"移除前面视图的hybridMode属性"` | REMOVE_ATTR | target="hybridMode"（同上，位置限定词从上下文判断） |
| `"修改collaborationId为4位A-Z/a-z/0-9字符串"` | MODIFY_NAME_FORMAT | 需在 FixType 中确认有 MODIFY_NAME_FORMAT；target="collaborationId"，value="4位A-Z/a-z/0-9" |
| `"修改Var的name属性为X等标准格式"` | MODIFY_NAME_FORMAT | target="name"，value="X" |
| `"确认MediaController指定华为音乐包名"` | CONFIRM | target="MediaController" |
| `"将视频转换为mp4格式"` | UNKNOWN | 外部操作，无法用 DSL 代码修复；保留为 type=UNKNOWN |
| `"压缩音频文件至1MB以内"` | UNKNOWN | 同上 |
| `"添加<Trigger action='pause'>子元素并在其中停止所有动画"` | ADD_CHILD | target="Trigger"，text 保留完整描述 |
| `"在Calendar外声明<Var .../>"` | DECLARE_OUTSIDE | target="Var"，value 包含 XML snippet |
| `"将duration设置为1000或更大"` | SET_VALUE | target="duration"，value="1000" |
| `"将repeat设置为-1或更大"` | SET_VALUE | target="repeat"，value="-1" |
| `"减小number值"` | REDUCE_VALUE | target="number" |
| `"减少Slice数量至64个以内"` | REDUCE_VALUE | target="Slice"，range="64" |
| `"减少VariablePoint数量至2个以内"` | REDUCE_VALUE | target="VariablePoint"，range="2" |

- [ ] **Step 1: 执行第一批 JSON 迁移（12 个文件）**

Subagent 迁移以下文件的 suggestedFixes：
- Text.json
- Video.json
- VideoCommand.json
- Group.json
- Image.json
- ImageNumber.json
- SourceImage.json
- Var.json
- ExternCommand.json
- Slice.json
- Healthy.json
- SensorBinder.json

- [ ] **Step 2: 执行第二批 JSON 迁移（14 个文件）**

- CollaborationCommands.json
- MultiLayer.json
- StereoView.json
- Layer.json
- Swiper.json
- Button.json
- Calendar.json
- Weather.json
- MediaCommand.json
- MediaIcon.json
- MediaController.json
- BluetoothBattery.json
- VariablePoint.json
- VariableFramerate.json

- [ ] **Step 3: 执行第三批 JSON 迁移（12 个文件）**

- VibrateCommand.json
- Wave.json
- SoundCommand.json
- StyleCommand.json
- VariableCommand.json
- GroupImage.json
- Translation.json
- MeshImage.json
- ParticleView.json
- ExternalCommands.json
- Trigger.json
- StoryBoard.json

- [ ] **Step 4: 运行全量测试验证**

Run: `./gradlew :feature:analysis:test`
Expected: ALL TESTS PASS（JSON 反序列化 + Generator 链路全部走通）

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(M5): migrate all 38 JSON rule files from string suggestedFixes to structured {text, type, target, value?, range?} format"
```

---

### Task 9: 清理遗留代码 — 移除 parse(List<String>) 和相关遗留

**Files:**
- Modify: `SuggestedFixParser.java`
- Modify: `BaseXmlAnalyzer.java`（如果它使用 suggestedFixes）

- [ ] **Step 1: 检查 BaseXmlAnalyzer.java 第 51 行**

```java
.suggestedFixes(List.of())
```

改为：
```java
.suggestedFixes(Collections.emptyList())
```

类型从 `List<String>` 的 `List.of()` 变为 `List<SuggestedFix>` 的 `Collections.emptyList()`，两者都兼容。

- [ ] **Step 2: 从 SuggestedFixParser 中移除 parse(List<String>) 方法和所有旧正则**

删除：
- 18 个 Pattern 常量声明（REMOVE_ATTR, REMOVE_CHILD, ...）
- `parse(List<String>)` 方法（已 deprecated）
- `parseSingle(String)` 方法
- `extractAttrName()` 辅助方法
- `stripParenthetical()` 辅助方法
- `unknownIntent()` 辅助方法

保留：
- `parse(List<SuggestedFix>)` 方法

SuggestedFixParser 简化为：

```java
package com.huawei.theme.analysis.core.quickfix;

import java.util.ArrayList;
import java.util.List;

import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.model.FixType;

public final class SuggestedFixParser {

    private SuggestedFixParser() {}

    public static List<FixActionIntent> parse(List<SuggestedFix> suggestedFixes) {
        if (suggestedFixes == null) {
            return new ArrayList<>();
        }
        List<FixActionIntent> intents = new ArrayList<>();
        for (SuggestedFix fix : suggestedFixes) {
            if (fix == null) {
                continue;
            }
            if (fix.getType() != null && fix.getType() != FixType.UNKNOWN) {
                intents.add(FixActionIntent.builder()
                        .actionType(fix.getType())
                        .targetName(fix.getTarget())
                        .targetValue(fix.getValue() != null ? fix.getValue() : fix.getRange())
                        .description(fix.getText())
                        .build());
            }
        }
        return intents;
    }
}
```

注意：移除 fallback 到正则的逻辑——所有 JSON 都已迁移为结构化格式，不再需要正则 fallback。如果某条 JSON 的 type 为 UNKNOWN，parse 会跳过它（不生成 intent），这是合理行为——UNKNOWN 类型的修复建议无法自动化执行。

- [ ] **Step 3: 移除 SuggestedFixParserTest 中所有正则解析测试**

删除 19 个旧测试（parseRemoveAttr, parseRemoveAttrCompound, ...），只保留新增的结构化解析测试。

- [ ] **Step 4: 运行全量测试验证**

Run: `./gradlew :feature:analysis:test`
Expected: ALL TESTS PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(M5): remove SuggestedFixParser regex-based parsing; clean up legacy parse(List<String>) and pattern constants"
```

---

### Task 10: 最终验证 + 全量回归测试

- [ ] **Step 1: 运行全项目测试**

Run: `./gradlew test`
Expected: ALL TESTS PASS

- [ ] **Step 2: 验证 JSON 反序列化链路**

创建一个快速验证脚本，确认 JsonRuleLoader 能正确加载迁移后的 JSON 规则文件，SuggestedFix 对象的 type/target/value/range 字段正确填充。

可通过现有 ConstraintAnalyzerTest 间接验证——如果约束分析器能正常读取 suggestedFixes 并传递到 Diagnostic，说明 JSON 反序列化正确。

- [ ] **Step 3: 验证 Generator 链路端到端**

通过 QuickFixIntegrationTest 验证：Diagnostic → QuickFixProviderImpl → FixActionRegistry → Generator → FixAction 全链路走通。

- [ ] **Step 4: 最终 Commit**

```bash
git add -A
git commit -m "feat(M5): complete suggestedFixes structured migration - all 38 JSON files migrated, FixType enum unified, SuggestedFixParser regex removed"
```

---

## 自检清单

1. **Spec coverage:** 每个需求是否都有对应 Task？
   - C 方案实现（text + 结构化字段）→ Task 1-6
   - 全量 JSON 迁移 → Task 8
   - 遗留清理 → Task 9
   - 验证 → Task 10

2. **Placeholder scan:** 无 TBD/TODO/implement later 等。

3. **Type consistency:**
   - FixType enum 定义在 Task 1 Step 2，被 SuggestedFix (Task 1 Step 5)、FixActionIntent (Task 1 Step 3)、FixAction (Task 1 Step 4)、RuleConstraint (Task 2 Step 1)、Diagnostic (Task 2 Step 2)、SuggestedFixParser (Task 5)、所有 Generator (Task 6)、所有测试 (Task 7) 一致使用。
   - SuggestedFix 定义在 Task 1 Step 1/5，被 RuleConstraint、Diagnostic、ConstraintAnalyzer、SuggestedFixParser、所有 Generator 一致使用。
   - FixTypeAdapter 定义在 Task 3 Step 1，在 JsonRuleLoader 中注册（Task 3 Step 2）。

4. **潜在遗漏：**
   - FixExpressionGenerator 的 `fixType("fix_expression")` → 需在 FixType 中新增 `FIX_EXPRESSION`（Task 1 Step 9）
   - BaseXmlAnalyzer.java 的 `.suggestedFixes(List.of())` → 需类型适配（Task 9 Step 1）
   - 所有使用 `FixActionIntent.FixActionType.xxx` 的断言 → 需改为 `FixType.xxx`（Task 1 Step 6）
   - 所有使用 `"insert_attr"` 等 String fixType 的断言 → 需改为 `FixType.ADD_ATTR` 等（Task 7）
