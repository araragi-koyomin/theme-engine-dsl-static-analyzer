# ReportExporter 报告导出设计文档

## 1. 概述

实现 M7 批量检查与报告模块的 ReportExporter 功能（issue #26），将 `BatchInspectionResult` 转换为 JSON、Markdown、Terminal 三种格式输出，并提供退出码计算（0/1/2）。

## 2. 设计决策

| 决策项 | 选择 | 理由 |
|---|---|---|
| TerminalFormatter 整合 | 委派模式 | ReportExporterImpl 内部委派 TerminalFormatter，TerminalFormatter 保持独立不变 |
| JSON 序列化 | GSON + 手动 JsonObject 构建 | 复用项目已有 GSON；字段名不匹配需手动构建（errorCount→errors 等） |
| 报告内容范围 | 仅 SuggestedFix 文本 | 与架构文档定义一致，不含 FixAction 详情 |
| ruleDocUrl | 不包含 | 用户确认 JSON/Markdown/Terminal 输出均不含 ruleDocUrl |
| 退出码归属 | ExitCodeCalculator 独立工具类 | 退出码计算是 CLI 入口职责，不属于格式转换 |
| 实现方式 | 单一 ReportExporterImpl | 与架构文档接口签名一致，与项目现有单实现类模式一致 |

## 3. 新增类

所有新增类在 `core/batchinspection` 包下：

| 类 | 类型 | 职责 |
|---|---|---|
| `ReportExporter` | 接口 | 架构文档定义的4方法签名 |
| `ReportExporterImpl` | 实现类 | 委派 TerminalFormatter + JSON/Markdown 格式化 + 文件写入 |
| `JsonReportSerializer` | 工具类 | GSON JsonObject 构建 + 序列化逻辑，分离 JSON 格式化细节 |
| `ExitCodeCalculator` | 工具类（静态） | BatchInspectionResult → 退出码 0/1/2 |

## 4. 接口签名

### ReportExporter

```java
public interface ReportExporter {
    String exportMarkdown(BatchInspectionResult result);   // result must not be null
    String exportJson(BatchInspectionResult result);       // result must not be null
    String exportTerminal(BatchInspectionResult result);   // result must not be null
    void exportToFile(BatchInspectionResult result, String format, String outputPath); // result, format, outputPath must not be null
}
```

所有方法参数在实现中使用 `Objects.requireNonNull` 校验，null 参数抛 `NullPointerException`。

### ExitCodeCalculator

```java
public final class ExitCodeCalculator {
    private ExitCodeCalculator() {}

    public static int compute(BatchInspectionResult result) {
        if (result.getErrorCount() > 0) return 1;
        return 0;
    }

    public static int computeFromException(Throwable e) {
        return 2;
    }
}
```

## 5. ReportExporterImpl 实现

### 5.1 构造函数

```java
public class ReportExporterImpl implements ReportExporter {
    private final TerminalFormatter terminalFormatter;
    private final JsonReportSerializer jsonSerializer;

    public ReportExporterImpl(TerminalFormatter terminalFormatter) {
        this.terminalFormatter = Objects.requireNonNull(terminalFormatter, "terminalFormatter must not be null");
        this.jsonSerializer = new JsonReportSerializer();
    }
}
```

接收 TerminalFormatter 实例便于测试注入和 CLI noColor 控制。

### 5.2 exportTerminal

```java
@Override
public String exportTerminal(BatchInspectionResult result) {
    return terminalFormatter.formatFullReport(result);
}
```

### 5.3 exportJson

```java
@Override
public String exportJson(BatchInspectionResult result) {
    return jsonSerializer.serialize(result);
}
```

### 5.4 exportMarkdown

内联 StringBuilder 格式化，按严重级别分组，每组内按文件聚合。

### 5.5 exportToFile

```java
@Override
public void exportToFile(BatchInspectionResult result, String format, String outputPath) {
    String content;
    switch (format.toLowerCase()) {
        case "json":     content = exportJson(result);      break;
        case "markdown": content = exportMarkdown(result);  break;
        case "md":       content = exportMarkdown(result);  break;
        case "terminal": content = exportTerminal(result);  break;
        default: throw new BatchInspectionException("Unsupported format: " + format);
    }
    try {
        Files.writeString(Path.of(outputPath), content, StandardCharsets.UTF_8);
    } catch (IOException e) {
        throw new BatchInspectionException("Failed to write report to: " + outputPath, e);
    }
}
```

## 6. JSON 输出格式

### 6.1 单文件场景（fileResults.size() == 1）

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
      "suggestedFixes": ["声明Var name=\"steps_value\""]
    }
  ],
  "summary": { "errors": 1, "warnings": 0, "info": 0 }
}
```

### 6.2 多文件场景（fileResults.size() > 1）

```json
{
  "files": [
    {
      "file": "theme.xml",
      "diagnostics": [
        { "severity": "error", "line": 15, "col": 3, "ruleId": "SEM-REF-001", "message": "...", "suggestedFixes": ["..."] }
      ],
      "summary": { "errors": 1, "warnings": 0, "info": 0 }
    },
    {
      "file": "layout.xml",
      "diagnostics": [],
      "summary": { "errors": 0, "warnings": 2, "info": 1 }
    }
  ],
  "summary": { "totalFiles": 2, "skippedFiles": 1, "errors": 1, "warnings": 2, "info": 1 }
}
```

### 6.3 JSON 字段规则

| 字段 | 规则 |
|---|---|
| severity | 小写字符串 "error"/"warning"/"info"（复用 DiagnosticSeverityAdapter） |
| suggestedFixes | SuggestedFix.text 的字符串数组；空列表输出 `[]` |
| ruleDocUrl | 不包含 |
| null filePath | 保留字段，输出 null |

### 6.4 JsonReportSerializer 实现

不直接序列化 `BatchInspectionResult`/`FileDiagnosticResult`/`Diagnostic`，因为模型字段名与 JSON 输出字段名不一致（errorCount→errors, filePath→file 等），且单/多文件输出结构不同。

使用 GSON `JsonObject` 手动构建 JSON 结构树：
- 创建 GsonBuilder 注册 DiagnosticSeverityAdapter
- 逐层构建 JsonObject（全局 → 文件 → 诊断 → 字段）
- 判断 fileResults.size() 决定顶层结构（单文件用 `file`/`diagnostics`/`summary`，多文件用 `files`/`summary`）

## 7. Markdown 输出格式

```markdown
# DSL 诊断报告

## Error 级别问题

### theme.xml

- **SEM-REF-001** (line 15, col 3): 引用未定义变量 #steps_value
  - 建议修复: 声明Var name="steps_value"
- **SYN-003** (line 3, col 5): 未知元素标签 'UnknownTag'
  - 建议修复: 替换为合法元素标签

## Warning 级别问题

(若无则输出"无 Warning 级别问题")

## Info 级别问题

(若无则输出"无 Info 级别问题")

---

## 汇总

| 文件 | Error | Warning | Info |
|------|-------|---------|------|
| theme.xml | 2 | 0 | 0 |
| layout.xml | 0 | 1 | 1 |

**总计**: 2 files, 1 skipped, 2 errors, 1 warnings, 1 info
```

### 7.1 格式化规则

- 按严重级别分组：ERROR → WARNING → INFO
- 每组内按文件聚合，同文件内按行号排序
- 无某级别问题时输出"无 XX 级别问题"
- 汇总表包含文件级统计 + 全局总计
- suggestedFixes 每条以 "- 建议修复: " 开头
- 不含 ruleDocUrl

## 8. Terminal 输出格式

完全委派给 `TerminalFormatter.formatFullReport(result)`，不新增逻辑。

Terminal 输出格式：
```
theme.xml:15:3: error: 引用未定义变量 #steps_value [SEM-REF-001]
  建议修复: 声明Var name="steps_value"

1 errors, 0 warnings, 0 info
```

ANSI 彩色由 TerminalFormatter 的 noColor 参数控制。

## 9. 退出码语义

| 退出码 | 含义 | 触发场景 |
|---|---|---|
| 0 | 无 error 级诊断 | 所有诊断均为 warning/info 级别 |
| 1 | 有 error 级诊断 | 至少一条 error 级诊断 |
| 2 | 执行异常 | 文件不存在、规则库加载失败、写报告文件失败 |

计算逻辑在 `ExitCodeCalculator.compute(BatchInspectionResult)` 和 `ExitCodeCalculator.computeFromException(Throwable)`。

## 10. 测试策略

| 测试类 | 测试内容 |
|---|---|
| `ReportExporterImplTest` | exportJson/exportMarkdown/exportTerminal 各至少1测试方法（单文件+多文件场景）；exportToFile 正常写入和异常写入 |
| `JsonReportSerializerTest` | JSON 序列化细节：字段名正确、severity 小写、空 suggestedFixes 为 `[]`、null filePath 保留、单/多文件结构切换 |
| `ExitCodeCalculatorTest` | compute: errorCount=0 → 0, errorCount>0 → 1; computeFromException → 2 |

验收标准（issue #26）：三种格式各1测试。

## 11. 不修改的现有类

- `TerminalFormatter` — 保持原样
- `BatchInspectionResult` / `FileDiagnosticResult` / `Diagnostic` — 保持原样，纯消费
- `BatchInspectionRunnerImpl` — 保持原样
- `BatchInspectionException` — 保持原样

## 12. 文件清单

新增源文件：

```
feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/
├── ReportExporter.java                    (接口)
├── ReportExporterImpl.java                (实现类)
├── JsonReportSerializer.java              (JSON序列化工具类)
├── ExitCodeCalculator.java                (退出码工具类)

feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/
├── ReportExporterImplTest.java            (实现类测试)
├── JsonReportSerializerTest.java          (JSON序列化测试)
├── ExitCodeCalculatorTest.java            (退出码测试)
```
