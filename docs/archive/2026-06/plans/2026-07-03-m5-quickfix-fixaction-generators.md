# M5 修复策略生成（各类 FixAction）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 issue #24 实现至少 5 类 FixAction 修复策略生成器（insert_attr、remove_attr、replace_enum、clamp_value、fix_expression），从 Diagnostic + RuleConstraint.suggestedFixes 数据生成结构化 FixAction。

**Architecture:** M5 模块已有完整的数据模型和注册机制（FixAction、FixActionGenerator、FixActionRegistry、QuickFixProvider），但零个生产代码 Generator 实现。本计划分两阶段：(1) 修复前置障碍——M3诊断缺 astNode、FixActionRegistry 缺 init()；(2) 实现各类 FixActionGenerator。Generator 从 Diagnostic.astNode 获取定位信息，从 Diagnostic.suggestedFixes / RuleRepository 获取修复内容数据。

**Tech Stack:** Java 17, Lombok @Data/@Builder, JUnit 5, Gradle 8.2

---

## 文件结构总览

| 操作 | 文件路径 | 职责 |
|------|----------|------|
| **修改** | `core/syntaxanalysis/SyntaxChecker.java` | 补充 astNode 到 Diagnostic |
| **修改** | `core/syntaxanalysis/ExpressionSyntaxChecker.java` | 补充 astNode 到 Diagnostic |
| **修改** | `core/quickfix/FixActionRegistry.java` | 添加 init() 注册引导 |
| **创建** | `core/quickfix/SuggestedFixParser.java` | suggestedFixes 文本→FixActionIntent 解析 |
| **创建** | `core/quickfix/FixActionIntent.java` | 解析意图数据模型 |
| **创建** | `core/quickfix/generators/InsertAttrGenerator.java` | SEM-REQ-001 → insert_attr |
| **创建** | `core/quickfix/generators/RemoveAttrGenerator.java` | 约束驱动 → remove_attr |
| **创建** | `core/quickfix/generators/ReplaceEnumGenerator.java` | SEM-ENUM-001 → replace_enum |
| **创建** | `core/quickfix/generators/ClampValueGenerator.java` | SEM-ATTR-001 → clamp_value |
| **创建** | `core/quickfix/generators/FixExpressionGenerator.java` | SYN-EXPR-001 → fix_expression |
| **创建** | `core/quickfix/generators/ConstraintFixGenerator.java` | 通用约束 FixAction 桥接 |
| **修改** | `core/semanticanalysis/analyzers/BaseXmlAnalyzer.java` | 传递 suggestedFixes（可选） |
| **创建** | `test/quickfix/SuggestedFixParserTest.java` | 解析器测试 |
| **创建** | `test/quickfix/generators/InsertAttrGeneratorTest.java` | |
| **创建** | `test/quickfix/generators/RemoveAttrGeneratorTest.java` | |
| **创建** | `test/quickfix/generators/ReplaceEnumGeneratorTest.java` | |
| **创建** | `test/quickfix/generators/ClampValueGeneratorTest.java` | |
| **创建** | `test/quickfix/generators/FixExpressionGeneratorTest.java` | |
| **创建** | `test/quickfix/generators/ConstraintFixGeneratorTest.java` | |
| **修改** | `test/quickfix/FixActionRegistryTest.java` | 补充 init() 测试 |

基础路径前缀: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/`
测试路径前缀: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/`

---

## 前置知识：错误检测数据流

### Diagnostic 如何产出

M3/M4 分析管线产出的 Diagnostic 对象是 M5 的唯一输入。Diagnostic 包含以下 M5 所需字段：

| 字段 | M3产出 | M4硬编码产出 | M4约束产出 |
|------|--------|-------------|-----------|
| `ruleId` | SYN-001/003/004, SYN-EXPR-* | SEM-REQ-001, SEM-ENUM-001, SEM-TYPE-003, SEM-NEST-001, SEM-SCOPE-001 | ~42条约束ruleId |
| `astNode` | **缺失(null)** ← 需修复 | 元素节点或属性节点 | 元素节点 |
| `suggestedFixes` | 空 `[]` | 空 `List.of()` | **从 RuleConstraint.suggestedFixes 直传** |

### suggestedFixes 文本模式（75条）

M5 需将这些中文文本转为结构化 FixAction。主要模式：

| 模式 | 示例 | fixType |
|------|------|---------|
| `移除X属性` | "移除play属性" | `remove_attr` |
| `添加X属性` | "添加width属性" | `insert_attr` |
| `设置X=Y` | "设置scaleType=center_crop" | `set_value` |
| `设置X值在Y范围内` | "设置alpha值在0-255范围内" | `clamp_value` |
| `修改X为合法枚举值` | "修改category为合法枚举值" | `replace_enum` |
| `将X改为Y` | "将direction改为1" | `replace_value` |
| `添加<X/>声明` | "添加<MediaController/>声明" | `insert_element` |
| `添加X子元素` | "添加Trigger子元素" | `insert_child` |
| `移除X子标签` | "移除SinMotion子标签" | `remove_child` |
| `将X移至Y` | "将ParticleView移至根标签下" | `move_element` |

---

### Task 1: 修复M3诊断 astNode 缺失 + FixActionRegistry.init()

M3 的 SyntaxChecker 和 ExpressionSyntaxChecker 构建 Diagnostic 时手动提取 line/column 但**不设置 astNode**。M5 Generator 需要 astNode 来定位 TextRange。同时 FixActionRegistry 没有 init() 方法注册 Generator。

**Files:**
- Modify: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/syntaxanalysis/SyntaxChecker.java`
- Modify: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/syntaxanalysis/ExpressionSyntaxChecker.java`
- Modify: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/FixActionRegistry.java`
- Test: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/quickfix/FixActionRegistryTest.java`

- [ ] **Step 1: 修改 SyntaxChecker.diag() 方法，将 astNode 传入 Diagnostic**

当前 `diag()` 方法只手动设 line/column：
```java
private Diagnostic diag(String ruleId, DiagnosticSeverity severity, String message,
        String filePath, DslAstNode node) {
    Diagnostic.DiagnosticBuilder b = Diagnostic.builder()
            .severity(severity)
            .ruleId(ruleId)
            .message(message)
            .filePath(filePath)
            .line(node.getLine())
            .column(node.getColumn());
    // ...
    return b.build();
}
```

改为使用 `.astNode(node)` 自动设 line/column + astNode：
```java
private Diagnostic diag(String ruleId, DiagnosticSeverity severity, String message,
        String filePath, DslAstNode node) {
    Diagnostic.DiagnosticBuilder b = Diagnostic.builder()
            .severity(severity)
            .ruleId(ruleId)
            .message(message)
            .filePath(filePath)
            .astNode(node);
    Optional<RuleSource> src = ruleRepository.getRuleSource(ruleId);
    if (src.isPresent()) {
        b.ruleDocUrl(src.get().getDocUrl());
    }
    return b.build();
}
```

- [ ] **Step 2: 修改 ExpressionSyntaxChecker.diag() 方法，同上模式**

当前 `diag()` 只设 `attr.getLine()/attr.getColumn()`。改为 `.astNode(attr)`：

```java
private Diagnostic diag(String ruleId, DiagnosticSeverity severity, String message,
        String filePath, DslAttributeNode attr) {
    Diagnostic.DiagnosticBuilder b = Diagnostic.builder()
            .severity(severity)
            .ruleId(ruleId)
            .message(message)
            .filePath(filePath)
            .astNode(attr);
    Optional<RuleSource> src = ruleRepository.getRuleSource(ruleId);
    if (src.isPresent()) {
        b.ruleDocUrl(src.get().getDocUrl());
    }
    return b.build();
}
```

- [ ] **Step 3: 添加 FixActionRegistry.init() 方法**

在 `FixActionRegistry.java` 中添加 `init()` 方法，注册所有 Generator：

```java
public static void init() {
    if (initialized) {
        return;
    }
    initialized = true;
    register(new InsertAttrGenerator());
    register(new RemoveAttrGenerator());
    register(new ReplaceEnumGenerator());
    register(new ClampValueGenerator());
    register(new FixExpressionGenerator());
    register(new ConstraintFixGenerator());
}
```

需要添加 `private static boolean initialized = false;` 字段。同时让 `register()` 方法在 initialized 之后也可调用（支持外部自定义 Generator 注册）。

- [ ] **Step 4: 运行现有测试确认不破坏**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.syntaxanalysis.*" --tests "com.huawei.theme.analysis.core.quickfix.*"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/syntaxanalysis/SyntaxChecker.java \
       feature/analysis/src/main/java/com/huawei/theme/analysis/core/syntaxanalysis/ExpressionSyntaxChecker.java \
       feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/FixActionRegistry.java
git commit -m "fix(m3+m5): populate astNode in M3 diagnostics; add FixActionRegistry.init()"
```

---

### Task 2: SuggestedFixes 文本解析器基础设施

约束驱动诊断的 suggestedFixes 是中文自由文本（如"移除play属性"、"设置alpha值在0-255范围内"）。需要解析器将其转为结构化 FixActionIntent，供 Generator 消费。

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/FixActionIntent.java`
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/SuggestedFixParser.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/quickfix/SuggestedFixParserTest.java`

- [ ] **Step 1: 创建 FixActionIntent 数据模型**

```java
package com.huawei.theme.analysis.core.quickfix;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FixActionIntent {
    FixActionType actionType;
    String targetName;
    String targetValue;
    String description;

    public enum FixActionType {
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
}
```

- [ ] **Step 2: 创建 SuggestedFixParser 解析器**

```java
package com.huawei.theme.analysis.core.quickfix;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SuggestedFixParser {

    private SuggestedFixParser() {}

    private static final Pattern REMOVE_ATTR = Pattern.compile("移除(.+)属性");
    private static final Pattern REMOVE_CHILD = Pattern.compile("移除(.+)子标签");
    private static final Pattern ADD_ATTR = Pattern.compile("添加(.+)属性(.*)");
    private static final Pattern ADD_ATTR_TO = Pattern.compile("为(.+)添加(.+)属性");
    private static final Pattern ADD_CHILD = Pattern.compile("添加(.+)(子元素|子标签)");
    private static final Pattern ADD_DECLARATION = Pattern.compile("添加<(.+?)(/?)>声明");
    private static final Pattern SET_VALUE_EQ = Pattern.compile("设置(\\w+)=(.+)");
    private static final Pattern SET_VALUE_RANGE = Pattern.compile("设置(.+)值在(.+)范围内");
    private static final Pattern SET_VALUE_LIMIT = Pattern.compile("设置(.+)值不超过(.+)");
    private static final Pattern SET_VALUE_TO = Pattern.compile("设置(.+)为(.+)");
    private static final Pattern MODIFY_TO_ENUM = Pattern.compile("修改(.+)为合法枚举值");
    private static final Pattern MODIFY_TO = Pattern.compile("修改(.+)为(.+)");
    private static final Pattern CHANGE_TO = Pattern.compile("将(.+)改为(.+)");
    private static final Pattern CHANGE_VALUE = Pattern.compile("更改(.+)为(.+)");
    private static final Pattern ADJUST_RANGE = Pattern.compile("将(.+)调整到(.+)范围内");
    private static final Pattern ADJUST_TO = Pattern.compile("将(.+)设置为(.+)");
    private static final Pattern CONVERT_TO = Pattern.compile("将(.+)转换为(.+)");
    private static final Pattern MOVE_TO = Pattern.compile("将(.+)移至(.+)");
    private static final Pattern REDUCE_TO = Pattern.compile("(减小|减少|压缩)(.+)(至|值)(.+)");
    private static final Pattern USE_ALT = Pattern.compile("使用(.+)(替代(.+))?");
    private static final Pattern DELETE_NODE = Pattern.compile("删除(.+)节点");
    private static final Pattern CONFIRM = Pattern.compile("确认(.+)");
    private static final Pattern DECLARE_OUTSIDE = Pattern.compile("在(.+)外声明<(.+)>.*");
    private static final Pattern MODIFY_NAME_FORMAT = Pattern.compile("修改(.+)的name属性为(.+)等标准格式");

    public static List<FixActionIntent> parse(List<String> suggestedFixes) {
        if (suggestedFixes == null || suggestedFixes.isEmpty()) {
            return List.of();
        }
        List<FixActionIntent> intents = new ArrayList<>();
        for (String fix : suggestedFixes) {
            intents.add(parseOne(fix));
        }
        return intents;
    }

    public static FixActionIntent parseOne(String text) {
        if (text == null || text.isBlank()) {
            return unknownIntent(text);
        }
        Matcher m;
        m = REMOVE_ATTR.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.REMOVE_ATTR)
                    .targetName(m.group(1))
                    .description(text)
                    .build();
        }
        m = REMOVE_CHILD.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.REMOVE_CHILD)
                    .targetName(m.group(1))
                    .description(text)
                    .build();
        }
        m = ADD_ATTR_TO.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.ADD_ATTR)
                    .targetName(m.group(2))
                    .targetValue(null)
                    .description(text)
                    .build();
        }
        m = ADD_ATTR.matcher(text);
        if (m.matches()) {
            String attrName = extractAttrName(m.group(1));
            String qualifier = m.group(2);
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.ADD_ATTR)
                    .targetName(attrName)
                    .targetValue(qualifier)
                    .description(text)
                    .build();
        }
        m = ADD_CHILD.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.ADD_CHILD)
                    .targetName(m.group(1))
                    .description(text)
                    .build();
        }
        m = ADD_DECLARATION.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.ADD_DECLARATION)
                    .targetName(m.group(1))
                    .description(text)
                    .build();
        }
        m = SET_VALUE_EQ.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.SET_VALUE)
                    .targetName(m.group(1))
                    .targetValue(m.group(2))
                    .description(text)
                    .build();
        }
        m = SET_VALUE_RANGE.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.CLAMP_VALUE)
                    .targetName(extractAttrName(m.group(1)))
                    .targetValue(m.group(2))
                    .description(text)
                    .build();
        }
        m = SET_VALUE_LIMIT.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.CLAMP_VALUE)
                    .targetName(extractAttrName(m.group(1)))
                    .targetValue(m.group(2))
                    .description(text)
                    .build();
        }
        m = SET_VALUE_TO.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.SET_VALUE)
                    .targetName(extractAttrName(m.group(1)))
                    .targetValue(m.group(2))
                    .description(text)
                    .build();
        }
        m = MODIFY_TO_ENUM.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.REPLACE_ENUM)
                    .targetName(m.group(1))
                    .description(text)
                    .build();
        }
        m = MODIFY_TO.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.REPLACE_VALUE)
                    .targetName(m.group(1))
                    .targetValue(m.group(2))
                    .description(text)
                    .build();
        }
        m = CHANGE_TO.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.REPLACE_VALUE)
                    .targetName(m.group(1))
                    .targetValue(m.group(2))
                    .description(text)
                    .build();
        }
        m = CHANGE_VALUE.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.REPLACE_VALUE)
                    .targetName(m.group(1))
                    .targetValue(stripParenthetical(m.group(2)))
                    .description(text)
                    .build();
        }
        m = ADJUST_RANGE.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.CLAMP_VALUE)
                    .targetName(m.group(1))
                    .targetValue(m.group(2))
                    .description(text)
                    .build();
        }
        m = ADJUST_TO.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.SET_VALUE)
                    .targetName(m.group(1))
                    .targetValue(m.group(2))
                    .description(text)
                    .build();
        }
        m = CONVERT_TO.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.REPLACE_VALUE)
                    .targetName(m.group(1))
                    .targetValue(m.group(2))
                    .description(text)
                    .build();
        }
        m = MOVE_TO.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.MOVE_ELEMENT)
                    .targetName(m.group(1))
                    .targetValue(m.group(2))
                    .description(text)
                    .build();
        }
        m = REDUCE_TO.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.REDUCE_VALUE)
                    .targetName(m.group(2))
                    .targetValue(m.group(4))
                    .description(text)
                    .build();
        }
        m = USE_ALT.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.USE_ALTERNATIVE)
                    .targetName(m.group(1))
                    .targetValue(m.group(3))
                    .description(text)
                    .build();
        }
        m = DELETE_NODE.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.DELETE_NODE)
                    .targetName(m.group(1))
                    .description(text)
                    .build();
        }
        m = CONFIRM.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.CONFIRM)
                    .targetName(m.group(1))
                    .description(text)
                    .build();
        }
        m = DECLARE_OUTSIDE.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.DECLARE_OUTSIDE)
                    .targetName(m.group(2))
                    .targetValue(m.group(1))
                    .description(text)
                    .build();
        }
        m = MODIFY_NAME_FORMAT.matcher(text);
        if (m.matches()) {
            return FixActionIntent.builder()
                    .actionType(FixActionIntent.FixActionType.MODIFY_NAME_FORMAT)
                    .targetName("name")
                    .targetValue(m.group(2))
                    .description(text)
                    .build();
        }
        return unknownIntent(text);
    }

    private static String extractAttrName(String raw) {
        int idx = raw.indexOf("指定");
        if (idx > 0) {
            return raw.substring(0, idx);
        }
        return raw;
    }

    private static String stripParenthetical(String value) {
        return value.replaceAll("\\([^)]*\\)", "");
    }

    private static FixActionIntent unknownIntent(String text) {
        return FixActionIntent.builder()
                .actionType(FixActionIntent.FixActionType.UNKNOWN)
                .description(text != null ? text : "")
                .build();
    }
}
```

- [ ] **Step 3: 编写 SuggestedFixParserTest 测试**

```java
package com.huawei.theme.analysis.core.quickfix;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SuggestedFixParserTest {

    @Test
    void parseRemoveAttr() {
        FixActionIntent intent = SuggestedFixParser.parseOne("移除play属性");
        assertEquals(FixActionIntent.FixActionType.REMOVE_ATTR, intent.getActionType());
        assertEquals("play", intent.getTargetName());
    }

    @Test
    void parseRemoveAttrCompound() {
        FixActionIntent intent = SuggestedFixParser.parseOne("移除persist/globalPersist/styleGlobalPersist属性");
        assertEquals(FixActionIntent.FixActionType.REMOVE_ATTR, intent.getActionType());
        assertEquals("persist/globalPersist/styleGlobalPersist", intent.getTargetName());
    }

    @Test
    void parseAddAttr() {
        FixActionIntent intent = SuggestedFixParser.parseOne("添加width属性");
        assertEquals(FixActionIntent.FixActionType.ADD_ATTR, intent.getActionType());
        assertEquals("width", intent.getTargetName());
    }

    @Test
    void parseAddAttrWithQualifier() {
        FixActionIntent intent = SuggestedFixParser.parseOne("添加src属性指定图片路径");
        assertEquals(FixActionIntent.FixActionType.ADD_ATTR, intent.getActionType());
        assertEquals("src", intent.getTargetName());
    }

    @Test
    void parseAddAttrTo() {
        FixActionIntent intent = SuggestedFixParser.parseOne("为Variable添加name和index属性");
        assertEquals(FixActionIntent.FixActionType.ADD_ATTR, intent.getActionType());
        assertEquals("name和index", intent.getTargetName());
    }

    @Test
    void parseSetValue() {
        FixActionIntent intent = SuggestedFixParser.parseOne("设置scaleType=center_crop");
        assertEquals(FixActionIntent.FixActionType.SET_VALUE, intent.getActionType());
        assertEquals("scaleType", intent.getTargetName());
        assertEquals("center_crop", intent.getTargetValue());
    }

    @Test
    void parseClampValue() {
        FixActionIntent intent = SuggestedFixParser.parseOne("设置alpha值在0-255范围内");
        assertEquals(FixActionIntent.FixActionType.CLAMP_VALUE, intent.getActionType());
        assertEquals("alpha", intent.getTargetName());
        assertEquals("0-255", intent.getTargetValue());
    }

    @Test
    void parseReplaceEnum() {
        FixActionIntent intent = SuggestedFixParser.parseOne("修改category为合法枚举值");
        assertEquals(FixActionIntent.FixActionType.REPLACE_ENUM, intent.getActionType());
        assertEquals("category", intent.getTargetName());
    }

    @Test
    void parseReplaceValue() {
        FixActionIntent intent = SuggestedFixParser.parseOne("将direction改为1");
        assertEquals(FixActionIntent.FixActionType.REPLACE_VALUE, intent.getActionType());
        assertEquals("direction", intent.getTargetName());
        assertEquals("1", intent.getTargetValue());
    }

    @Test
    void parseChangeValue() {
        FixActionIntent intent = SuggestedFixParser.parseOne("更改touchType为2(重力+滑动)或3(调距浏览)");
        assertEquals(FixActionIntent.FixActionType.REPLACE_VALUE, intent.getActionType());
        assertEquals("touchType", intent.getTargetName());
        assertEquals("2或3", intent.getTargetValue());
    }

    @Test
    void parseAddDeclaration() {
        FixActionIntent intent = SuggestedFixParser.parseOne("添加<MediaController/>声明");
        assertEquals(FixActionIntent.FixActionType.ADD_DECLARATION, intent.getActionType());
        assertEquals("MediaController/", intent.getTargetName());
    }

    @Test
    void parseAddChild() {
        FixActionIntent intent = SuggestedFixParser.parseOne("添加Trigger子元素");
        assertEquals(FixActionIntent.FixActionType.ADD_CHILD, intent.getActionType());
        assertEquals("Trigger", intent.getTargetName());
    }

    @Test
    void parseMoveTo() {
        FixActionIntent intent = SuggestedFixParser.parseOne("将ParticleView移至根标签下");
        assertEquals(FixActionIntent.FixActionType.MOVE_ELEMENT, intent.getActionType());
        assertEquals("ParticleView", intent.getTargetName());
        assertEquals("根标签下", intent.getTargetValue());
    }

    @Test
    void parseReduceValue() {
        FixActionIntent intent = SuggestedFixParser.parseOne("减小w/h值至120以内");
        assertEquals(FixActionIntent.FixActionType.REDUCE_VALUE, intent.getActionType());
    }

    @Test
    void parseUseAlternative() {
        FixActionIntent intent = SuggestedFixParser.parseOne("使用ImageSeries替代");
        assertEquals(FixActionIntent.FixActionType.USE_ALTERNATIVE, intent.getActionType());
        assertEquals("ImageSeries", intent.getTargetName());
    }

    @Test
    void parseSetLimit() {
        FixActionIntent intent = SuggestedFixParser.parseOne("设置delay值不超过3000");
        assertEquals(FixActionIntent.FixActionType.CLAMP_VALUE, intent.getActionType());
        assertEquals("delay", intent.getTargetName());
        assertEquals("3000", intent.getTargetValue());
    }

    @Test
    void parseListReturnsMultipleIntents() {
        List<FixActionIntent> intents = SuggestedFixParser.parse(
                List.of("移除play属性", "移除sound属性"));
        assertEquals(2, intents.size());
        assertEquals(FixActionIntent.FixActionType.REMOVE_ATTR, intents.get(0).getActionType());
        assertEquals(FixActionIntent.FixActionType.REMOVE_ATTR, intents.get(1).getActionType());
    }

    @Test
    void parseNullReturnsEmpty() {
        assertEquals(0, SuggestedFixParser.parse(null).size());
    }

    @Test
    void parseUnknownReturnsUnknown() {
        FixActionIntent intent = SuggestedFixParser.parseOne("一些无法识别的文本");
        assertEquals(FixActionIntent.FixActionType.UNKNOWN, intent.getActionType());
    }
}
```

- [ ] **Step 4: 运行测试确认解析器正确**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.quickfix.SuggestedFixParserTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/FixActionIntent.java \
       feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/SuggestedFixParser.java \
       feature/analysis/src/test/java/com/huawei/theme/analysis/core/quickfix/SuggestedFixParserTest.java
git commit -m "feat(m5): add SuggestedFixParser for parsing Chinese suggestedFixes text into FixActionIntent"
```

---

### Task 3: InsertAttrGenerator (SEM-REQ-001 → insert_attr)

RequiredAttrAnalyzer 产出 SEM-REQ-001 诊断时 `astNode=elementNode`，message="缺失必填属性: name"。Generator 需从 RuleRepository 获取 defaultValue 来构造替换文本。

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/generators/InsertAttrGenerator.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/quickfix/generators/InsertAttrGeneratorTest.java`

- [ ] **Step 1: 编写 InsertAttrGenerator 测试**

```java
package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionGenerator;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

class InsertAttrGeneratorTest {

    @Test
    void generatesInsertAttrForMissingRequired() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Var");
        elementNode.setLine(10);
        elementNode.setColumn(5);
        elementNode.setAttributes(List.of());

        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-REQ-001")
                .message("缺失必填属性: name")
                .filePath("theme.xml")
                .astNode(elementNode)
                .build();

        RuleRepository mockRepo = new MockRuleRepository();
        InsertAttrGenerator gen = new InsertAttrGenerator(mockRepo);
        List<FixAction> actions = gen.generate(diag);

        assertEquals(1, actions.size());
        assertEquals("insert_attr", actions.get(0).getFixType());
        assertEquals("name", actions.get(0).getReplacementText().substring(0, 4));
        assertTrue(actions.get(0).getDescription().contains("name"));
    }

    private static class MockRuleRepository implements RuleRepository {
        @Override
        public Optional<DslElementRule> getElementRule(String elementName) {
            if ("Var".equals(elementName)) {
                return Optional.of(DslElementRule.builder()
                        .elementName("Var")
                        .requiredAttrs(List.of("name"))
                        .attrTypes(java.util.Map.of("name",
                                AttrTypeSpec.builder().type("string").defaultValue(null).build()))
                        .build());
            }
            return Optional.empty();
        }
        // ... 其他方法返回空/默认值（省略完整实现，测试时需要补充）
    }
}
```

- [ ] **Step 2: 实现 InsertAttrGenerator**

```java
package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionGenerator;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;

public class InsertAttrGenerator implements FixActionGenerator {

    private static final String RULE_ID = "SEM-REQ-001";
    private final RuleRepository ruleRepository;

    public InsertAttrGenerator(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Override
    public String getRuleId() {
        return RULE_ID;
    }

    @Override
    public List<FixAction> generate(Diagnostic diagnostic) {
        DslAstNode astNode = diagnostic.getAstNode();
        if (!(astNode instanceof DslElementNode elementNode)) {
            return Collections.emptyList();
        }
        String tagName = elementNode.getTagName();
        Optional<DslElementRule> ruleOpt = ruleRepository.getElementRule(tagName);
        if (ruleOpt.isEmpty()) {
            return Collections.emptyList();
        }

        String missingAttr = extractMissingAttrName(diagnostic.getMessage());
        if (missingAttr == null) {
            return Collections.emptyList();
        }

        Optional<AttrTypeSpec> specOpt = ruleRepository.getAttrTypeSpec(tagName, missingAttr);
        String defaultValue = specOpt.map(s -> s.getDefaultValue()).orElse(null);

        String replacementText = buildAttrInsertion(missingAttr, defaultValue);
        TextRange targetRange = buildTargetRange(elementNode);

        return List.of(FixAction.builder()
                .fixType("insert_attr")
                .targetRange(targetRange)
                .replacementText(replacementText)
                .description("添加必填属性: " + missingAttr)
                .build());
    }

    private String extractMissingAttrName(String message) {
        String prefix = "缺失必填属性: ";
        if (message != null && message.startsWith(prefix)) {
            return message.substring(prefix.length());
        }
        return null;
    }

    private String buildAttrInsertion(String attrName, String defaultValue) {
        if (defaultValue != null) {
            return attrName + "=\"" + defaultValue + "\"";
        }
        return attrName + "=\"\"";
    }

    private TextRange buildTargetRange(DslElementNode elementNode) {
        return TextRange.builder()
                .startLine(elementNode.getLine())
                .startColumn(elementNode.getColumn())
                .endLine(elementNode.getLine())
                .endColumn(elementNode.getColumn())
                .build();
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.quickfix.generators.InsertAttrGeneratorTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/generators/InsertAttrGenerator.java \
       feature/analysis/src/test/java/com/huawei/theme/analysis/core/quickfix/generators/InsertAttrGeneratorTest.java
git commit -m "feat(m5): add InsertAttrGenerator for SEM-REQ-001"
```

---

### Task 4: RemoveAttrGenerator (约束驱动诊断 → remove_attr)

这是覆盖面最广的 Generator——~18 条约束诊断的 suggestedFixes 包含"移除X属性"模式。RemoveAttrGenerator 通过 SuggestedFixParser 解析 suggestedFixes，对每个 REMOVE_ATTR intent 生成 FixAction。

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/generators/RemoveAttrGenerator.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/quickfix/generators/RemoveAttrGeneratorTest.java`

- [ ] **Step 1: 编写 RemoveAttrGenerator 测试**

```java
package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionIntent;
import com.huawei.theme.analysis.core.quickfix.SuggestedFixParser;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

class RemoveAttrGeneratorTest {

    @Test
    void generatesRemoveAttrFromSuggestedFixes() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("VideoCommand");
        elementNode.setLine(8);
        elementNode.setColumn(3);
        DslAttributeNode playAttr = new DslAttributeNode();
        playAttr.setName("play");
        playAttr.setLine(8);
        playAttr.setColumn(10);
        DslAttributeValueNode val = new DslAttributeValueNode();
        val.setRawValue("true");
        val.setLiteral(true);
        playAttr.setValue(val);
        elementNode.setAttributes(List.of(playAttr));

        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-CMD-001")
                .message("VideoCommand sound和play互斥")
                .filePath("theme.xml")
                .astNode(elementNode)
                .suggestedFixes(List.of("移除play属性", "移除sound属性"))
                .build();

        RemoveAttrGenerator gen = new RemoveAttrGenerator();
        List<FixAction> actions = gen.generate(diag);

        assertEquals(2, actions.size());
        assertEquals("remove_attr", actions.get(0).getFixType());
        assertTrue(actions.get(0).getDescription().contains("play"));
        assertEquals("remove_attr", actions.get(1).getFixType());
        assertTrue(actions.get(1).getDescription().contains("sound"));
    }

    @Test
    void skipsNonRemoveAttrIntents() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Image");
        elementNode.setLine(5);
        elementNode.setColumn(1);

        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-IMG-SRC")
                .message("Image src和srcExp至少需要一个")
                .filePath("theme.xml")
                .astNode(elementNode)
                .suggestedFixes(List.of("添加src属性指定图片路径"))
                .build();

        RemoveAttrGenerator gen = new RemoveAttrGenerator();
        List<FixAction> actions = gen.generate(diag);

        assertTrue(actions.isEmpty());
    }
}
```

- [ ] **Step 2: 实现 RemoveAttrGenerator**

```java
package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionGenerator;
import com.huawei.theme.analysis.core.quickfix.FixActionIntent;
import com.huawei.theme.analysis.core.quickfix.SuggestedFixParser;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;

public class RemoveAttrGenerator implements FixActionGenerator {

    private static final String RULE_ID = "SEM-CMD-001";

    @Override
    public String getRuleId() {
        return RULE_ID;
    }

    @Override
    public List<FixAction> generate(Diagnostic diagnostic) {
        DslAstNode astNode = diagnostic.getAstNode();
        if (!(astNode instanceof DslElementNode elementNode)) {
            return Collections.emptyList();
        }
        List<String> suggestedFixes = diagnostic.getSuggestedFixes();
        if (suggestedFixes == null || suggestedFixes.isEmpty()) {
            return Collections.emptyList();
        }

        List<FixActionIntent> intents = SuggestedFixParser.parse(suggestedFixes);
        List<FixAction> actions = new ArrayList<>();
        for (FixActionIntent intent : intents) {
            if (intent.getActionType() == FixActionIntent.FixActionType.REMOVE_ATTR) {
                DslAttributeNode targetAttr = findAttribute(elementNode, intent.getTargetName());
                if (targetAttr != null) {
                    actions.add(buildRemoveAttrAction(targetAttr, intent));
                } else {
                    actions.add(buildRemoveAttrActionFromName(elementNode, intent));
                }
            }
        }
        return actions;
    }

    private DslAttributeNode findAttribute(DslElementNode elementNode, String attrNameOrCompound) {
        String firstAttr = attrNameOrCompound.split("/")[0];
        if (elementNode.getAttributes() != null) {
            for (DslAttributeNode attr : elementNode.getAttributes()) {
                if (attr.getName().equals(firstAttr)) {
                    return attr;
                }
            }
        }
        return null;
    }

    private FixAction buildRemoveAttrAction(DslAttributeNode attr, FixActionIntent intent) {
        return FixAction.builder()
                .fixType("remove_attr")
                .targetRange(TextRange.builder()
                        .startLine(attr.getLine())
                        .startColumn(attr.getColumn())
                        .endLine(attr.getLine())
                        .endColumn(attr.getColumn() + attr.getName().length()
                                + (attr.getValue() != null ? attr.getValue().getRawValue().length() + 3 : 0))
                        .build())
                .replacementText("")
                .description(intent.getDescription())
                .build();
    }

    private FixAction buildRemoveAttrActionFromName(DslElementNode elementNode, FixActionIntent intent) {
        return FixAction.builder()
                .fixType("remove_attr")
                .targetRange(TextRange.builder()
                        .startLine(elementNode.getLine())
                        .startColumn(elementNode.getColumn())
                        .endLine(elementNode.getLine())
                        .endColumn(elementNode.getColumn())
                        .build())
                .replacementText("")
                .description(intent.getDescription())
                .build();
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.quickfix.generators.RemoveAttrGeneratorTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/generators/RemoveAttrGenerator.java \
       feature/analysis/src/test/java/com/huawei/theme/analysis/core/quickfix/generators/RemoveAttrGeneratorTest.java
git commit -m "feat(m5): add RemoveAttrGenerator for constraint-driven remove_attr fixes"
```

---

### Task 5: ConstraintFixGenerator (通用约束 FixAction 桥接)

RemoveAttrGenerator 只处理 SEM-CMD-001 的"移除X属性"意图，但 ~42 条约束诊断涵盖多种意图类型（ADD_ATTR、SET_VALUE、CLAMP_VALUE、REPLACE_ENUM、ADD_CHILD 等）。需要一个通用 Generator 将 suggestedFixes 解析为多种 FixAction。

**设计决策**：ConstraintFixGenerator 注册一个特殊 ruleId `"*"`，QuickFixProviderImpl 在没有精确匹配时fallback到这个通配 Generator。或者，让 ConstraintFixGenerator 对每个约束 ruleId 动态匹配。

**实际方案**：修改 FixActionRegistry.getGenerator() 支持精确匹配 + 通配 fallback。ConstraintFixGenerator 作为一个通用 Generator，对所有约束诊断（通过 suggestedFixes 非空判断）生效。

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/generators/ConstraintFixGenerator.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/quickfix/generators/ConstraintFixGeneratorTest.java`
- Modify: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/FixActionRegistry.java`
- Modify: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/QuickFixProviderImpl.java`

- [ ] **Step 1: 修改 FixActionRegistry 支持通配 fallback**

在 `FixActionRegistry.java` 中添加 fallback Generator 支持：

```java
private static FixActionGenerator fallbackGenerator;

public static void setFallback(FixActionGenerator generator) {
    fallbackGenerator = generator;
}

public static Optional<FixActionGenerator> getGenerator(String ruleId) {
    FixActionGenerator exact = generators.get(ruleId);
    if (exact != null) {
        return Optional.of(exact);
    }
    return Optional.ofNullable(fallbackGenerator);
}
```

- [ ] **Step 2: 修改 QuickFixProviderImpl 使用 FixActionRegistry（含 fallback）**

当前实现已委托给 FixActionRegistry.getGenerator()，无需改动，只需确保 fallback 机制生效。

- [ ] **Step 3: 编写 ConstraintFixGeneratorTest**

```java
package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionGenerator;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

class ConstraintFixGeneratorTest {

    @Test
    void generatesAddAttrFromSuggestedFixes() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Image");
        elementNode.setLine(5);
        elementNode.setColumn(1);
        elementNode.setAttributes(List.of());

        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-IMG-SRC")
                .message("Image src和srcExp至少需要一个")
                .filePath("theme.xml")
                .astNode(elementNode)
                .suggestedFixes(List.of("添加src属性指定图片路径", "添加srcExp属性指定图片源表达式"))
                .build();

        ConstraintFixGenerator gen = new ConstraintFixGenerator();
        List<FixAction> actions = gen.generate(diag);

        assertEquals(2, actions.size());
        assertEquals("insert_attr", actions.get(0).getFixType());
        assertTrue(actions.get(0).getDescription().contains("src"));
    }

    @Test
    void generatesSetValueFromSuggestedFixes() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Image");
        elementNode.setLine(12);
        elementNode.setColumn(3);

        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-ATTR-005")
                .message("Image isBackground=true时需设置scaleType=center_crop")
                .filePath("theme.xml")
                .astNode(elementNode)
                .suggestedFixes(List.of("设置scaleType=center_crop"))
                .build();

        ConstraintFixGenerator gen = new ConstraintFixGenerator();
        List<FixAction> actions = gen.generate(diag);

        assertEquals(1, actions.size());
        assertEquals("set_value", actions.get(0).getFixType());
        assertEquals("scaleType=\"center_crop\"", actions.get(0).getReplacementText());
    }

    @Test
    void skipsWhenNoSuggestedFixes() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Var");
        elementNode.setLine(1);
        elementNode.setColumn(1);

        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-REQ-001")
                .message("缺失必填属性: name")
                .filePath("theme.xml")
                .astNode(elementNode)
                .suggestedFixes(List.of())
                .build();

        ConstraintFixGenerator gen = new ConstraintFixGenerator();
        assertTrue(gen.generate(diag).isEmpty());
    }
}
```

- [ ] **Step 4: 实现 ConstraintFixGenerator**

```java
package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionGenerator;
import com.huawei.theme.analysis.core.quickfix.FixActionIntent;
import com.huawei.theme.analysis.core.quickfix.SuggestedFixParser;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;

public class ConstraintFixGenerator implements FixActionGenerator {

    private static final String FALLBACK_RULE_ID = "*";

    @Override
    public String getRuleId() {
        return FALLBACK_RULE_ID;
    }

    @Override
    public List<FixAction> generate(Diagnostic diagnostic) {
        List<String> suggestedFixes = diagnostic.getSuggestedFixes();
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

    private FixAction intentToFixAction(FixActionIntent intent, DslElementNode elementNode) {
        String fixType = mapFixType(intent.getActionType());
        TextRange range = buildRange(intent, elementNode);
        String replacement = buildReplacement(intent);
        return FixAction.builder()
                .fixType(fixType)
                .targetRange(range)
                .replacementText(replacement)
                .description(intent.getDescription())
                .candidates(intent.getActionType() == FixActionIntent.FixActionType.REPLACE_ENUM
                        ? buildEnumCandidates(intent) : Collections.emptyList())
                .build();
    }

    private String mapFixType(FixActionIntent.FixActionType actionType) {
        switch (actionType) {
            case REMOVE_ATTR: return "remove_attr";
            case ADD_ATTR: return "insert_attr";
            case SET_VALUE: return "set_value";
            case CLAMP_VALUE: return "clamp_value";
            case REPLACE_ENUM: return "replace_enum";
            case REPLACE_VALUE: return "replace_value";
            case ADD_CHILD: return "insert_child";
            case REMOVE_CHILD: return "remove_child";
            case ADD_DECLARATION: return "insert_element";
            case MOVE_ELEMENT: return "move_element";
            case REDUCE_VALUE: return "reduce_value";
            case USE_ALTERNATIVE: return "use_alternative";
            case DELETE_NODE: return "delete_node";
            default: return "unknown";
        }
    }

    private TextRange buildRange(FixActionIntent intent, DslElementNode elementNode) {
        if (intent.getActionType() == FixActionIntent.FixActionType.REMOVE_ATTR) {
            DslAttributeNode attr = findAttr(elementNode, intent.getTargetName());
            if (attr != null) {
                return TextRange.builder()
                        .startLine(attr.getLine())
                        .startColumn(attr.getColumn())
                        .endLine(attr.getLine())
                        .endColumn(attr.getColumn() + attr.getName().length() + 3
                                + (attr.getValue() != null && attr.getValue().getRawValue() != null
                                ? attr.getValue().getRawValue().length() : 0))
                        .build();
            }
        }
        return TextRange.builder()
                .startLine(elementNode.getLine())
                .startColumn(elementNode.getColumn())
                .endLine(elementNode.getLine())
                .endColumn(elementNode.getColumn())
                .build();
    }

    private String buildReplacement(FixActionIntent intent) {
        if (intent.getActionType() == FixActionIntent.FixActionType.REMOVE_ATTR
                || intent.getActionType() == FixActionIntent.FixActionType.REMOVE_CHILD
                || intent.getActionType() == FixActionIntent.FixActionType.DELETE_NODE) {
            return "";
        }
        if (intent.getActionType() == FixActionIntent.FixActionType.SET_VALUE
                || intent.getActionType() == FixActionIntent.FixActionType.CLAMP_VALUE) {
            if (intent.getTargetValue() != null) {
                return intent.getTargetName() + "=\"" + intent.getTargetValue() + "\"";
            }
        }
        if (intent.getActionType() == FixActionIntent.FixActionType.ADD_ATTR) {
            String val = intent.getTargetValue() != null ? intent.getTargetValue() : "";
            return intent.getTargetName() + "=\"" + val + "\"";
        }
        if (intent.getActionType() == FixActionIntent.FixActionType.ADD_CHILD
                || intent.getActionType() == FixActionIntent.FixActionType.ADD_DECLARATION) {
            return "<" + intent.getTargetName() + "/>";
        }
        return "";
    }

    private DslAttributeNode findAttr(DslElementNode elementNode, String targetName) {
        String firstAttr = targetName.split("/")[0];
        if (elementNode.getAttributes() != null) {
            for (DslAttributeNode attr : elementNode.getAttributes()) {
                if (attr.getName().equals(firstAttr)) {
                    return attr;
                }
            }
        }
        return null;
    }

    private List<com.huawei.theme.analysis.core.quickfix.CandidateItem> buildEnumCandidates(FixActionIntent intent) {
        return Collections.emptyList();
    }
}
```

- [ ] **Step 5: 运行测试**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.quickfix.generators.ConstraintFixGeneratorTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/generators/ConstraintFixGenerator.java \
       feature/analysis/src/test/java/com/huawei/theme/analysis/core/quickfix/generators/ConstraintFixGeneratorTest.java \
       feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/FixActionRegistry.java \
       feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/QuickFixProviderImpl.java
git commit -m "feat(m5): add ConstraintFixGenerator as fallback for all constraint-driven diagnostics"
```

---

### Task 6: ReplaceEnumGenerator (SEM-ENUM-001 → replace_enum)

EnumValueAnalyzer 产出 SEM-ENUM-001 诊断时 `astNode=DslAttributeNode`，message 包含属性名和非法值。Generator 需从 RuleRepository 获取 AttrTypeSpec.enumValues 构建 CandidateItem 列表。

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/generators/ReplaceEnumGenerator.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/quickfix/generators/ReplaceEnumGeneratorTest.java`

- [ ] **Step 1: 编写 ReplaceEnumGenerator 测试**

```java
package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.theme.analysis.core.quickfix.CandidateItem;
import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

class ReplaceEnumGeneratorTest {

    @Test
    void generatesReplaceEnumWithCandidates() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Text");
        elementNode.setLine(10);
        elementNode.setColumn(3);
        DslAttributeNode categoryAttr = new DslAttributeNode();
        categoryAttr.setName("category");
        categoryAttr.setLine(10);
        categoryAttr.setColumn(15);
        DslAttributeValueNode val = new DslAttributeValueNode();
        val.setRawValue("InvalidValue");
        val.setLiteral(true);
        categoryAttr.setValue(val);
        elementNode.setAttributes(List.of(categoryAttr));

        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-ENUM-001")
                .message("枚举值错误: category=InvalidValue, 合法值: [Normal, Charging, BatteryLow, BatteryFull]")
                .filePath("theme.xml")
                .astNode(categoryAttr)
                .build();

        RuleRepository mockRepo = createMockRepoWithEnumValues(
                "Text", "category", List.of("Normal", "Charging", "BatteryLow", "BatteryFull"));
        ReplaceEnumGenerator gen = new ReplaceEnumGenerator(mockRepo);
        List<FixAction> actions = gen.generate(diag);

        assertEquals(1, actions.size());
        assertEquals("replace_enum", actions.get(0).getFixType());
        assertEquals(4, actions.get(0).getCandidates().size());
        assertEquals("Normal", actions.get(0).getCandidates().get(0).getPreviewText());
    }

    private RuleRepository createMockRepoWithEnumValues(String element, String attr, List<String> enumValues) {
        // 返回包含指定 enumValues 的 MockRuleRepository
        // 实现省略，与 InsertAttrGeneratorTest 的 MockRuleRepository 模式相同
        return null; // TODO: 在实现时补充完整 MockRuleRepository
    }
}
```

- [ ] **Step 2: 实现 ReplaceEnumGenerator**

```java
package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.huawei.theme.analysis.core.quickfix.CandidateItem;
import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionGenerator;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;

public class ReplaceEnumGenerator implements FixActionGenerator {

    private static final String RULE_ID = "SEM-ENUM-001";
    private final RuleRepository ruleRepository;

    public ReplaceEnumGenerator(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Override
    public String getRuleId() {
        return RULE_ID;
    }

    @Override
    public List<FixAction> generate(Diagnostic diagnostic) {
        DslAstNode astNode = diagnostic.getAstNode();
        if (!(astNode instanceof DslAttributeNode attrNode)) {
            if (astNode instanceof DslElementNode elementNode) {
                attrNode = findAttrFromMessage(elementNode, diagnostic.getMessage());
                if (attrNode == null) {
                    return Collections.emptyList();
                }
            } else {
                return Collections.emptyList();
            }
        }

        DslElementNode parentElement = findParentElement(astNode);
        if (parentElement == null) {
            return Collections.emptyList();
        }
        String tagName = parentElement.getTagName();
        String attrName = attrNode.getName();

        Optional<AttrTypeSpec> specOpt = ruleRepository.getAttrTypeSpec(tagName, attrName);
        if (specOpt.isEmpty() || specOpt.get().getEnumValues() == null || specOpt.get().getEnumValues().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> enumValues = specOpt.get().getEnumValues();
        List<CandidateItem> candidates = new ArrayList<>();
        for (String value : enumValues) {
            candidates.add(CandidateItem.builder()
                    .description("替换为 " + value)
                    .previewText(attrName + "=\"" + value + "\"")
                    .similarityScore(1.0)
                    .build());
        }

        return List.of(FixAction.builder()
                .fixType("replace_enum")
                .targetRange(TextRange.builder()
                        .startLine(attrNode.getLine())
                        .startColumn(attrNode.getColumn())
                        .endLine(attrNode.getLine())
                        .endColumn(attrNode.getColumn() + attrNode.getName().length()
                                + (attrNode.getValue() != null && attrNode.getValue().getRawValue() != null
                                ? attrNode.getValue().getRawValue().length() + 3 : 0))
                        .build())
                .replacementText(attrName + "=\"" + enumValues.get(0) + "\"")
                .candidates(candidates)
                .description("替换枚举值: " + attrName)
                .build());
    }

    private DslElementNode findParentElement(DslAstNode node) {
        DslAstNode parent = node;
        while (parent != null && !(parent instanceof DslElementNode)) {
            if (parent instanceof DslAttributeNode attr) {
                parent = null;
            } else {
                break;
            }
        }
        if (node instanceof DslAttributeNode attr && attr.getValue() != null) {
            return null;
        }
        return null;
    }

    private DslAttributeNode findAttrFromMessage(DslElementNode elementNode, String message) {
        String prefix = "枚举值错误: ";
        if (message == null || !message.startsWith(prefix)) {
            return null;
        }
        String rest = message.substring(prefix.length());
        int eqIdx = rest.indexOf('=');
        if (eqIdx < 0) {
            return null;
        }
        String attrName = rest.substring(0, eqIdx);
        if (elementNode.getAttributes() != null) {
            for (DslAttributeNode attr : elementNode.getAttributes()) {
                if (attr.getName().equals(attrName)) {
                    return attr;
                }
            }
        }
        return null;
    }
}
```

**注意**：ReplaceEnumGenerator 需要 DslAttributeNode 的 parent 指针来获取 tagName。当前 DslAttributeNode 没有 parent 字段。Generator 从 `astNode` 判断类型——如果 Diagnostic.astNode 是 DslAttributeNode，需要通过遍历父元素来获取 tagName。此场景下 SEM-ENUM-001 的 astNode 是 DslAttributeNode，但 Diagnostic.message 包含属性名信息，可从 message 解析。后续需考虑在 Diagnostic 中携带额外上下文（如 tagName）或在 DslAttributeNode 上添加 parent 引用。

**替代方案**：在 ReplaceEnumGenerator 中，通过 Diagnostic.message 解析出 tagName 和 attrName，再用 RuleRepository.getAttrTypeSpec(tagName, attrName) 获取 enumValues。message 格式为 "枚举值错误: category=InvalidValue, 合法值: [Normal, ...]"，从中解析出 attrName 和 elementTagName（需要额外信息）。

**最终方案**：由于 SEM-ENUM-001 的 astNode 是 DslAttributeNode（无 parent），但 message 包含完整信息。同时 DslElementNode 的 attributes 列表中的 DslAttributeNode 可以通过遍历所有元素来反向查找 parent。简化方案：在 Generator 中接受一个辅助方法，从 Diagnostic.filePath + line/column 反推。但 Core 层无文件内容访问。

**最简方案**：修改 EnumValueAnalyzer 在 Diagnostic 中携带额外信息。但为避免改动 M4，采用另一方案：让 ReplaceEnumGenerator 从 `astNode` 类型判断——如果是 DslAttributeNode，从 message 解析 attrName，然后需要 tagName。可以在 Diagnostic.message 中提取，但 message 格式不含 tagName。

**折中方案**：在 Diagnostic 中添加一个 `contextTag` 字段存储父元素 tagName。但改动 Diagnostic 模型过大。

**最终决定**：给 DslAttributeNode 添加 parent 引用（指向所属 DslElementNode）。这是一个合理的 AST 结构补全，不影响现有逻辑。

- [ ] **Step 2a: 给 DslAttributeNode 添加 parent 字段**

修改 `DslAttributeNode.java`，添加 `DslAstNode parent` 字段（@EqualsAndHashCode exclude, @ToString exclude）。修改 `AstBuilder.AstContentHandler.startElement()` 中构建 DslAttributeNode 时设置 `attr.setParent(node)`。

- [ ] **Step 3: 运行测试**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.quickfix.generators.ReplaceEnumGeneratorTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/generators/ReplaceEnumGenerator.java \
       feature/analysis/src/test/java/com/huawei/theme/analysis/core/quickfix/generators/ReplaceEnumGeneratorTest.java \
       feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/ast/DslAttributeNode.java \
       feature/analysis/src/main/java/com/huawei/theme/analysis/core/syntaxanalysis/AstBuilder.java
git commit -m "feat(m5): add ReplaceEnumGenerator for SEM-ENUM-001; add parent field to DslAttributeNode"
```

---

### Task 7: ClampValueGenerator (SEM-ATTR-001 → clamp_value)

SEM-ATTR-001 诊断由 ConstraintAnalyzer 产出（alpha<0 OR alpha>255），astNode=DslElementNode，suggestedFixes=["设置alpha值在0-255范围内"]。ClampValueGenerator 从 suggestedFixes 解析出属性名和范围，构造 clamp 修复。

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/generators/ClampValueGenerator.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/quickfix/generators/ClampValueGeneratorTest.java`

- [ ] **Step 1: 编写 ClampValueGenerator 测试**

```java
package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

class ClampValueGeneratorTest {

    @Test
    void generatesClampValueFromSuggestedFixes() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Image");
        elementNode.setLine(10);
        elementNode.setColumn(3);
        DslAttributeNode alphaAttr = new DslAttributeNode();
        alphaAttr.setName("alpha");
        alphaAttr.setLine(10);
        alphaAttr.setColumn(15);
        DslAttributeValueNode val = new DslAttributeValueNode();
        val.setRawValue("300");
        val.setLiteral(true);
        alphaAttr.setValue(val);
        elementNode.setAttributes(List.of(alphaAttr));

        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-ATTR-001")
                .message("alpha值超出0-255范围")
                .filePath("theme.xml")
                .astNode(elementNode)
                .suggestedFixes(List.of("设置alpha值在0-255范围内"))
                .build();

        ClampValueGenerator gen = new ClampValueGenerator();
        List<FixAction> actions = gen.generate(diag);

        assertEquals(1, actions.size());
        assertEquals("clamp_value", actions.get(0).getFixType());
        assertEquals("alpha=\"255\"", actions.get(0).getReplacementText());
    }
}
```

- [ ] **Step 2: 实现 ClampValueGenerator**

```java
package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.Collections;
import java.util.List;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionGenerator;
import com.huawei.theme.analysis.core.quickfix.FixActionIntent;
import com.huawei.theme.analysis.core.quickfix.SuggestedFixParser;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;

public class ClampValueGenerator implements FixActionGenerator {

    private static final String RULE_ID = "SEM-ATTR-001";

    @Override
    public String getRuleId() {
        return RULE_ID;
    }

    @Override
    public List<FixAction> generate(Diagnostic diagnostic) {
        DslAstNode astNode = diagnostic.getAstNode();
        if (!(astNode instanceof DslElementNode elementNode)) {
            return Collections.emptyList();
        }
        List<String> suggestedFixes = diagnostic.getSuggestedFixes();
        if (suggestedFixes == null || suggestedFixes.isEmpty()) {
            return Collections.emptyList();
        }

        List<FixActionIntent> intents = SuggestedFixParser.parse(suggestedFixes);
        for (FixActionIntent intent : intents) {
            if (intent.getActionType() == FixActionIntent.FixActionType.CLAMP_VALUE) {
                String attrName = intent.getTargetName();
                String range = intent.getTargetValue();
                String clampedValue = extractUpperBound(range);
                DslAttributeNode attr = findAttribute(elementNode, attrName);
                if (attr != null) {
                    return List.of(FixAction.builder()
                            .fixType("clamp_value")
                            .targetRange(buildAttrRange(attr))
                            .replacementText(attrName + "=\"" + clampedValue + "\"")
                            .description(intent.getDescription())
                            .build());
                }
            }
        }
        return Collections.emptyList();
    }

    private String extractUpperBound(String range) {
        if (range == null) {
            return "0";
        }
        String[] parts = range.split("-");
        if (parts.length == 2) {
            return parts[1];
        }
        return range;
    }

    private DslAttributeNode findAttribute(DslElementNode elementNode, String attrName) {
        if (elementNode.getAttributes() != null) {
            for (DslAttributeNode attr : elementNode.getAttributes()) {
                if (attr.getName().equals(attrName)) {
                    return attr;
                }
            }
        }
        return null;
    }

    private TextRange buildAttrRange(DslAttributeNode attr) {
        return TextRange.builder()
                .startLine(attr.getLine())
                .startColumn(attr.getColumn())
                .endLine(attr.getLine())
                .endColumn(attr.getColumn() + attr.getName().length() + 3
                        + (attr.getValue() != null && attr.getValue().getRawValue() != null
                        ? attr.getValue().getRawValue().length() : 0))
                .build();
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.quickfix.generators.ClampValueGeneratorTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/generators/ClampValueGenerator.java \
       feature/analysis/src/test/java/com/huawei/theme/analysis/core/quickfix/generators/ClampValueGeneratorTest.java
git commit -m "feat(m5): add ClampValueGenerator for SEM-ATTR-001 alpha range clamping"
```

---

### Task 8: FixExpressionGenerator (SYN-EXPR-001 → fix_expression)

ExpressionSyntaxChecker 产出 SYN-EXPR-001 诊断（"-#varName" 模式），astNode=DslAttributeNode（修复 Task 1 后），message="数值表达式使用 -#var 语法: {rawValue}"。Generator 将 `-#varName` 转换为 `-1*#varName`。

**Files:**
- Create: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/generators/FixExpressionGenerator.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/quickfix/generators/FixExpressionGeneratorTest.java`

- [ ] **Step 1: 编写 FixExpressionGenerator 测试**

```java
package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

class FixExpressionGeneratorTest {

    @Test
    void generatesFixExpressionForUnaryMinusVar() {
        DslAttributeNode attr = new DslAttributeNode();
        attr.setName("x");
        attr.setLine(5);
        attr.setColumn(10);
        DslAttributeValueNode val = new DslAttributeValueNode();
        val.setRawValue("-#steps");
        val.setLiteral(false);
        attr.setValue(val);

        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SYN-EXPR-001")
                .message("数值表达式使用 -#var 语法: -#steps")
                .filePath("theme.xml")
                .astNode(attr)
                .build();

        FixExpressionGenerator gen = new FixExpressionGenerator();
        List<FixAction> actions = gen.generate(diag);

        assertEquals(1, actions.size());
        assertEquals("fix_expression", actions.get(0).getFixType());
        assertEquals("-1*#steps", actions.get(0).getReplacementText());
    }

    @Test
    void returnsEmptyWhenNoMatch() {
        DslAttributeNode attr = new DslAttributeNode();
        attr.setName("x");
        attr.setLine(5);
        attr.setColumn(10);

        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SYN-EXPR-001")
                .message("数值表达式使用 -#var 语法: #steps+1")
                .filePath("theme.xml")
                .astNode(attr)
                .build();

        FixExpressionGenerator gen = new FixExpressionGenerator();
        List<FixAction> actions = gen.generate(diag);

        assertEquals(0, actions.size());
    }
}
```

- [ ] **Step 2: 实现 FixExpressionGenerator**

```java
package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionGenerator;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;

public class FixExpressionGenerator implements FixActionGenerator {

    private static final String RULE_ID = "SYN-EXPR-001";
    private static final Pattern UNARY_MINUS_VAR = Pattern.compile("-#(\\w+)");

    @Override
    public String getRuleId() {
        return RULE_ID;
    }

    @Override
    public List<FixAction> generate(Diagnostic diagnostic) {
        DslAstNode astNode = diagnostic.getAstNode();
        if (!(astNode instanceof DslAttributeNode attrNode)) {
            return Collections.emptyList();
        }
        DslAttributeValueNode valueNode = attrNode.getValue();
        if (valueNode == null || valueNode.getRawValue() == null) {
            return Collections.emptyList();
        }
        String rawValue = valueNode.getRawValue();
        Matcher m = UNARY_MINUS_VAR.matcher(rawValue);
        if (!m.find()) {
            return Collections.emptyList();
        }

        String fixedValue = m.replaceAll("-1*#$1");

        return List.of(FixAction.builder()
                .fixType("fix_expression")
                .targetRange(TextRange.builder()
                        .startLine(attrNode.getLine())
                        .startColumn(attrNode.getColumn())
                        .endLine(attrNode.getLine())
                        .endColumn(attrNode.getColumn() + attr.getName().length() + 3 + rawValue.length())
                        .build())
                .replacementText(attrNode.getName() + "=\"" + fixedValue + "\"")
                .description("将 -#var 修正为 -1*#var")
                .build());
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.quickfix.generators.FixExpressionGeneratorTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/generators/FixExpressionGenerator.java \
       feature/analysis/src/test/java/com/huawei/theme/analysis/core/quickfix/generators/FixExpressionGeneratorTest.java
git commit -m "feat(m5): add FixExpressionGenerator for SYN-EXPR-001 -#var to -1*#var"
```

---

### Task 9: FixActionRegistry.init() 注册 + 集成测试

更新 FixActionRegistry.init() 注册所有 Generator，编写集成测试验证整条管线（Diagnostic → QuickFixProvider → FixAction）。

**Files:**
- Modify: `feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/FixActionRegistry.java`
- Create: `feature/analysis/src/test/java/com/huawei/theme/analysis/core/quickfix/QuickFixIntegrationTest.java`

- [ ] **Step 1: 更新 FixActionRegistry.init()**

```java
public static void init(RuleRepository ruleRepository) {
    if (initialized) {
        return;
    }
    initialized = true;
    register(new InsertAttrGenerator(ruleRepository));
    register(new ReplaceEnumGenerator(ruleRepository));
    register(new ClampValueGenerator());
    register(new FixExpressionGenerator());
    register(new RemoveAttrGenerator());
    setFallback(new ConstraintFixGenerator());
}
```

注意：InsertAttrGenerator 和 ReplaceEnumGenerator 需要 RuleRepository 参数。init() 需接受 RuleRepository。

- [ ] **Step 2: 编写 QuickFixIntegrationTest 集成测试**

```java
package com.huawei.theme.analysis.core.quickfix;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.huawei.theme.analysis.core.quickfix.generators.*;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

class QuickFixIntegrationTest {

    @BeforeEach
    void setup() {
        FixActionRegistry.clear();
        // 使用 MockRuleRepository 初始化
        FixActionRegistry.init(createMockRepo());
    }

    @AfterEach
    void teardown() {
        FixActionRegistry.clear();
    }

    @Test
    void semReq001ProducesInsertAttr() {
        DslElementNode node = new DslElementNode();
        node.setTagName("Var");
        node.setLine(5);
        node.setColumn(1);
        node.setAttributes(List.of());

        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-REQ-001")
                .message("缺失必填属性: name")
                .filePath("theme.xml")
                .astNode(node)
                .build();

        QuickFixProvider provider = new QuickFixProviderImpl();
        List<FixAction> actions = provider.getFixActions(diag);
        assertFalse(actions.isEmpty());
        assertEquals("insert_attr", actions.get(0).getFixType());
    }

    @Test
    void constraintFallbackProducesFixAction() {
        DslElementNode node = new DslElementNode();
        node.setTagName("VideoCommand");
        node.setLine(8);
        node.setColumn(3);

        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-CMD-001")
                .message("VideoCommand sound和play互斥")
                .filePath("theme.xml")
                .astNode(node)
                .suggestedFixes(List.of("移除play属性", "移除sound属性"))
                .build();

        QuickFixProvider provider = new QuickFixProviderImpl();
        List<FixAction> actions = provider.getFixActions(diag);
        assertFalse(actions.isEmpty());
    }

    private RuleRepository createMockRepo() {
        // 返回包含 Var.requiredAttrs=["name"], Text.attrTypes["category"].enumValues=[...] 的 MockRuleRepository
        return null; // TODO: 补充完整 MockRuleRepository
    }
}
```

- [ ] **Step 3: 运行所有 quickfix 测试**

Run: `./gradlew :feature:analysis:test --tests "com.huawei.theme.analysis.core.quickfix.*"`
Expected: ALL PASS

- [ ] **Step 4: 运行全项目测试确认不破坏**

Run: `./gradlew :feature:analysis:test`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/FixActionRegistry.java \
       feature/analysis/src/test/java/com/huawei/theme/analysis/core/quickfix/QuickFixIntegrationTest.java
git commit -m "feat(m5): integrate all generators into FixActionRegistry.init() with integration tests"
```

---

## 自审查清单

| # | 检查项 | 状态 |
|---|--------|------|
| 1 | Issue #24验收"至少3类修复策略+测试" | ✅ 覆盖5类（insert_attr, remove_attr, replace_enum, clamp_value, fix_expression）+ 1通用桥接（ConstraintFixGenerator） |
| 2 | Placeholder扫描（TBD/TODO/实现省略） | ⚠️ MockRuleRepository 实现需在实际编码时补充完整 |
| 3 | 类型一致性 | ⚠️ ReplaceEnumGenerator 中 findParentElement 方法需重新设计（依赖 DslAttributeNode.parent 字段添加） |
| 4 | FixActionRegistry.init() 需要 RuleRepository 参数 | ✅ 已在 Task 9 Step 1 中体现 |
| 5 | M3 diagnostics astNode 修复 | ✅ Task 1 覆盖 SyntaxChecker + ExpressionSyntaxChecker |
| 6 | suggestedFixes 解析器 | ✅ Task 2 覆盖 18 种中文文本模式 |
| 7 | fallback Generator 机制 | ✅ Task 5 覆盖 FixActionRegistry.setFallback() + ConstraintFixGenerator |

## 未覆盖项（超出 MVP 范围，后续迭代）

- SYN-003 → replace_element（需 SimilarityMatcher，不存在）
- SYN-004 → replace_attr（需 SimilarityMatcher + aliases）
- M3 SAX XML 解析错误 → close_tag / add_quotes（当前不产出 Diagnostic，仅设 hasError=true）
- SEM-NEST-001 → 嵌套结构修复（需移动元素，复杂文本操作）
- SEM-SCOPE-001 → 作用域修复（需移动元素到合法位置）
- 批量修复（BatchQuickFixProvider，Optional 层）
