---
module_ids: [M5]
doc_kind: architecture
status: active
created: 2026-06-17
---
# M5 修复逻辑模块 - 架构设计

## 1. 模块职责

对每种诊断类型提供修复策略生成（纯文本操作描述FixAction）。Core层产出FixAction，不依赖PsiElement，不包含UI交互。

**单一职责**：修复策略生成。

**Core/Plugin拆分**：Core层M5产出FixAction（纯文本操作描述+TextRange定位）；Plugin层M5-UI桥接为IntentionAction（归M6模块）。

## 2. 三层划分

| 层级 | 功能 | 说明 |
|---|---|---|
| **Core** | 无需确认类FixAction生成 | MVP必交 |
| **Extension** | 需确认类FixAction（候选列表+diff预览描述） | 正式版本 |
| **Optional** | 批量修复描述（同类型问题一键修复） | 后续迭代 |

## 3. 核心组件

### 3.1 FixAction数据模型

```java
@Data
@Builder
public class FixAction {
    String fixType;            // "close_tag"/"add_quotes"/"insert_attr"/"replace_element"/...
    TextRange targetRange;     // 目标文本范围（起止行列）
    String replacementText;    // 替换文本内容
    List<CandidateItem> candidates;  // 需确认类修复的候选列表
    String description;        // 修复描述文本
}
```

### 3.2 TextRange定位模型

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

**关键设计**：TextRange使用行列号定位，不依赖PsiElement。Plugin层M5-UI通过PSI Adapter将TextRange映射为PSI offset范围。

### 3.3 CandidateItem候选模型

```java
@Data
@Builder
public class CandidateItem {
    String description;              // 简要描述，如 "替换为 <Theme>"
    String previewText;              // 预览文本（修复后的代码片段）
    double similarityScore;          // 相似度分数（排序用）
}
```

候选来源：从M4 SimilarityMatcher获取。

### 3.4 QuickFixProvider（接口）

```java
public interface QuickFixProvider {
    List<FixAction> getFixActions(Diagnostic diagnostic);
    List<FixAction> getFixActions(List<Diagnostic> diagnostics);
}
```

**纯字符串参数**：输入为Diagnostic（filePath+line+column定位），输出为FixAction（TextRange定位）。

### 3.5 无需确认类FixAction（Core层）

| FixAction | 对应诊断 | 修复策略 | fixType |
|---|---|---|---|
| 补闭合标签 | XML标签未闭合 | 补闭合标签 | `close_tag` |
| 删除多余结束标签 | 多余结束标签 | 删除多余结束标签 | `remove_end_tag` |
| 补属性引号 | 属性引号缺失 | 补属性引号 | `add_quotes` |
| 插入必填属性占位值 | 必填属性缺失 | 插入必填属性默认值 | `insert_attr` |
| 类型归一化 | 属性值类型错误 | 数字/布尔/表达式格式修正 | `normalize_format` |
| 表达式语法修正 | `-#varName`模式 | `-1*#varName` | `fix_expression` |
| 移除互斥属性 | 禁止属性组合 | 移除互斥属性之一 | `remove_attr` |

### 3.6 需确认类FixAction（Extension层）

| FixAction | 对应诊断 | 修复策略 | fixType |
|---|---|---|---|
| 替换为最接近合法组件名 | 未知元素 | 编辑距离匹配候选列表 | `replace_element` |
| 替换为别名属性/删除属性 | 未知属性 | 候选列表+diff预览描述 | `replace_attr` |
| 替换为最接近合法枚举值 | 枚举值不合法 | 候选列表 | `replace_enum` |
| clamp到合法范围 | alpha超出0-255 | diff预览描述 | `clamp_value` |

**候选列表来源**：M4 SimilarityMatcher → CandidateItem列表 → 存入FixAction.candidates。

### 3.7 批量修复描述（Optional层）

```java
public interface BatchQuickFixProvider {
    List<FixAction> getBatchFixActions(String ruleId, List<Diagnostic> diagnostics);
}
```

批量检查后，对同类型问题一键修复：M7面板中"Fix All"按钮触发。

### 3.8 FixActionRegistry

每种诊断类型注册对应的FixAction生成器：

```java
public interface FixActionGenerator {
    String getRuleId();
    List<FixAction> generate(Diagnostic diagnostic);
}
```

注册机制：

```java
public class FixActionRegistry {
    private FixActionRegistry() {}

    public static void register(FixActionGenerator generator);
    public static Optional<FixActionGenerator> getGenerator(String ruleId);
}
```

## 4. 模块依赖

| 上游依赖 | 说明 |
|---|---|
| M2 规则库 | RuleConstraint.suggestedFixes + DslElementRule默认值 + 枚举候选 |
| M4 语义分析 | Diagnostic诊断数据 + SymbolTable + SimilarityMatcher候选列表 |

| 下游消费 | 提供接口 | 说明 |
|---|---|---|
| M7 批量检查 | `QuickFixProvider.getFixActions()` | 批量扫描后修复建议输出 |
| M5-UI (Plugin层) | FixAction → IntentionAction桥接 | 归M6模块，通过PSI Adapter将TextRange映射为PSI范围 |
| CLI入口 | FixAction → suggestedFixes文本 | CLI输出修复建议描述文本 |

## 5. CLI相关

### 5.1 CLI命令调用

M5为CLI管线提供修复建议输出：

```
java -jar dsl-analyzer.jar [options] <file-or-directory>
```

M5在CLI管线中的位置：

```
文件输入 → M1 → M3 → M4(Diagnostic列表) → M5(FixAction列表) → M7(组合报告输出)
```

### 5.2 CLI参数与M5的关系

| 参数 | 影响范围 | M5相关说明 |
|---|---|---|
| `--verbose` | 详细输出 | 开启时CLI输出包含FixAction详情（fixType、targetRange、replacementText） |
| `--quiet` | 只输出error级别 | 不影响M5，FixAction基于Diagnostic生成，Diagnostic过滤归CLI入口 |

### 5.3 CLI输出中M5的贡献

**JSON输出**（--format json）：

```json
{
  "file": "theme.xml",
  "diagnostics": [
    {
      "severity": "error",
      "line": 15,
      "col": 3,
      "ruleId": "SEM-REF-001",
      "message": "引用未定义变量 #steps_value",
      "suggestedFixes": ["声明Var name=\"steps_value\""],
      "fixActions": [
        {"fixType": "insert_attr", "targetRange": {"startLine":5,"startColumn":1,"endLine":5,"endColumn":1}, "replacementText": "<Var name=\"steps_value\" expression=\"0\"/>"}
      ]
    }
  ]
}
```

**终端彩色输出**（--format terminal）：

```
theme.xml:15:3: error: 引用未定义变量 #steps_value [SEM-REF-001]
  建议修复: 声明Var name="steps_value"
  可执行修复: insert_attr → <Var name="steps_value" expression="0"/> (line 5)
```

**Markdown报告**（--format markdown --output report.md）：FixAction作为修复建议部分嵌入报告。

### 5.4 CLI与Plugin的修复交付差异

| 维度 | CLI输出 | Plugin交互 |
|---|---|---|
| 修复形态 | suggestedFixes文本 + FixAction详情（JSON/terminal） | IntentionAction UI交互（Alt+Enter触发） |
| 执行方式 | 用户手动根据建议修改代码 | WriteCommandAction自动执行文本替换 |
| 确认类修复 | 输出候选列表文本，用户自行选择 | CandidateSelectionDialog下拉+diff预览 |
| 批量修复 | 报告中同类型问题汇总 | 面板"Fix All"按钮一键修复 |

## 6. 设计要点

- **Core层纯文本操作**：FixAction使用TextRange(startLine/startColumn/endLine/endColumn)定位+replacementText文本，不依赖PsiElement
- **Core/Plugin拆分**：Core层M5只生成FixAction描述；Plugin层M5-UI桥接为IntentionAction归M6模块
- **修复与诊断分离**：M4只描述问题，M5只生成修复策略
- **最小修改原则**：FixAction仅做最小必要文本替换
- **候选来源解耦**：CandidateItem来自M4 SimilarityMatcher，M5只负责组织候选
- **FixActionRegistry注册机制**：新增修复类型只需实现FixActionGenerator并注册
- **CLI输出修复建议文本**：CLI不执行修复，只输出suggestedFixes和FixAction详情供用户参考
