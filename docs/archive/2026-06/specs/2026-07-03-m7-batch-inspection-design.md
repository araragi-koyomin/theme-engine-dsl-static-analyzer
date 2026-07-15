# M7 批量检查功能 - 设计规格文档

## 1. 实现范围

本次实现 M7 模块 Core 层的批量扫描引擎 + Terminal 彩色输出，**不包括** CLI 集成（CliMain 更新、CliConfig 参数扩展）和报告文件导出（Markdown/JSON 文件输出）。

| 组件 | 本次实现 | 暂不实现 |
|---|---|---|
| BatchInspectionResult 数据模型 | 是 | - |
| FileDiagnosticResult 数据模型 | 是 | - |
| BatchInspectionRunner 接口 | 是 | - |
| BatchInspectionRunnerImpl 实现 | 是 | - |
| TerminalFormatter 终端输出 | 是 | - |
| 测试类 | 是 | - |
| CliMain 管线串联 | - | 推后 |
| CliConfig 参数扩展 | - | 推后 |
| ReportExporter 接口 | - | 推后 |
| Markdown/JSON 报告导出 | - | 推后 |

## 2. 数据模型

### 2.1 BatchInspectionResult

```java
package com.huawei.theme.analysis.core.batchinspection.model;

@Data
@Builder
public class BatchInspectionResult {
    int totalFiles;
    int skippedFiles;
    int errorCount;
    int warningCount;
    int infoCount;
    List<FileDiagnosticResult> fileResults;
}
```

- `totalFiles`: 实际分析的 DSL 文件数量（经 M1 过滤后）
- `skippedFiles`: M1 过滤掉的非 DSL 文件数量
- `errorCount/warningCount/infoCount`: 所有文件诊断结果的汇总计数
- `fileResults`: 各文件的诊断结果列表

### 2.2 FileDiagnosticResult

```java
package com.huawei.theme.analysis.core.batchinspection.model;

@Data
@Builder
public class FileDiagnosticResult {
    String filePath;
    List<Diagnostic> diagnostics;
    List<FixAction> fixActions;
}
```

- `filePath`: 文件路径
- `diagnostics`: M4 语义分析产出的诊断列表
- `fixActions`: M5 QuickFixProvider 为该文件所有诊断生成的修复建议列表（可选）

## 3. BatchInspectionRunner 接口

```java
package com.huawei.theme.analysis.core.batchinspection;

public interface BatchInspectionRunner {
    BatchInspectionResult runOnFile(String filePath);
    BatchInspectionResult runOnDirectory(String directoryPath);
}
```

- `runOnFile`: 对单个文件执行完整管线（M1→M3→M4→M5）
- `runOnDirectory`: 递归遍历目录，M1 过滤 DSL 文件，对每个文件执行管线，汇总结果

## 4. BatchInspectionRunnerImpl 实现

### 4.1 构造器注入

```java
public class BatchInspectionRunnerImpl implements BatchInspectionRunner {
    private final DslFileMatcher fileMatcher;
    private final DslAstProvider astProvider;
    private final DiagnosticProvider diagnosticProvider;
    private final QuickFixProvider quickFixProvider;
    private final SymbolTableBuilder symbolTableBuilder;
    private final RuleRepository ruleRepository;

    public BatchInspectionRunnerImpl(
            DslFileMatcher fileMatcher,
            DslAstProvider astProvider,
            DiagnosticProvider diagnosticProvider,
            QuickFixProvider quickFixProvider,
            SymbolTableBuilder symbolTableBuilder,
            RuleRepository ruleRepository) {
        // 赋值
    }
}
```

### 4.2 runOnFile 流程

```
1. 读取文件内容（UTF-8）
2. M1 DslFileMatcher.isDslFile(filePath, content) → 非DSL则返回空结果
3. M3 DslAstProvider.getDslAst(filePath, content) → DslFileNode
4. M4 DiagnosticProvider.analyze(ast, ruleRepo, symbolTableBuilder) → List<Diagnostic>
5. M5 QuickFixProvider.getFixActions(diagnostics) → List<FixAction>
6. 组装 FileDiagnosticResult
7. 组装 BatchInspectionResult（单文件版）
```

### 4.3 runOnDirectory 流程

```
1. 递归遍历目录，收集所有 .xml 文件路径
2. 对每个文件：读取内容 → M1 过滤 → 非 DSL 计入 skippedFiles
3. 对 DSL 文件执行与 runOnFile 相同的单文件管线
4. 汇总所有 FileDiagnosticResult → BatchInspectionResult
5. 计算 errorCount/warningCount/infoCount 汇总统计
```

### 4.4 错误处理

- 文件读取失败（IOException）：该文件产出空 diagnostics 列表，不中断整个扫描
- 目录不存在或不可读：抛出 BatchInspectionException，CLI 退出码=2（但 CLI 部分暂不实现）
- 规则库/函数签名库为 null：抛出 BatchInspectionException

### 4.5 BatchInspectionException

```java
package com.huawei.theme.analysis.core.batchinspection;

public class BatchInspectionException extends RuntimeException {
    public BatchInspectionException(String message) { super(message); }
    public BatchInspectionException(String message, Throwable cause) { super(message, cause); }
}
```

遵循 AGENTS.md §4.4：不使用受检异常。

## 5. TerminalFormatter

### 5.1 输出格式

gcc/clang 风格，每条诊断一行：

```
theme.xml:15:3: error: 引用未定义变量 #steps_value [SEM-REF-001]
  建议修复: 声明Var name="steps_value"
```

汇总行：

```
3 errors, 0 warnings, 0 info
```

### 5.2 ANSI 颜色规则

| 严重级别 | ANSI 颜色 | 代码 |
|---|---|---|
| ERROR | 红色 | `\u001B[31m` |
| WARNING | 黄色 | `\u001B[33m` |
| INFO | 蓝色 | `\u001B[34m` |
| 重置 | 默认色 | `\u001B[0m` |

`noColor=true` 时禁用 ANSI 颜色输出。

### 5.3 接口设计

```java
package com.huawei.theme.analysis.core.batchinspection;

public class TerminalFormatter {
    private final boolean noColor;

    public TerminalFormatter(boolean noColor) { this.noColor = noColor; }

    public String formatDiagnostic(Diagnostic diagnostic);
    public String formatSuggestedFixes(List<SuggestedFix> fixes);
    public String formatSummary(BatchInspectionResult result);
    public String formatFileResult(FileDiagnosticResult result);
    public String formatFullReport(BatchInspectionResult result);
}
```

- `formatDiagnostic`: 单条诊断行（含 ANSI 颜色）
- `formatSuggestedFixes`: 修复建议行（"建议修复: ..."）
- `formatSummary`: 汇总统计行
- `formatFileResult`: 单文件所有诊断输出（含汇总）
- `formatFullReport`: 完整报告（遍历所有文件 + 全局汇总）

### 5.4 诊断输出顺序

按严重级别排序：ERROR → WARNING → INFO，每个级别内按文件路径+行号排序。

## 6. 文件组织

```
feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/
├── BatchInspectionRunner.java              // 接口
├── BatchInspectionRunnerImpl.java          // 实现
├── BatchInspectionException.java           // 异常类
├── TerminalFormatter.java                  // 终端输出格式化
├── model/
│   ├── BatchInspectionResult.java          // 汇总结果数据模型
│   └── FileDiagnosticResult.java           // 单文件结果数据模型
```

测试目录：

```
feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/
├── BatchInspectionRunnerImplTest.java      // 管线编排逻辑测试
├── TerminalFormatterTest.java              // 输出格式测试
└── model/
│   ├── BatchInspectionResultTest.java      // 数据模型测试
│   └── FileDiagnosticResultTest.java       // 数据模型测试
```

## 7. 依赖关系

| 上游依赖 | 接口 | 消费方式 |
|---|---|---|
| M1 文件识别 | `DslFileMatcher.isDslFile(filePath, content)` | 过滤扫描范围 |
| M2 规则库 | `RuleRepository` | 传递给 M4 DiagnosticProvider 和 M3 AstBuilder |
| M3 语法分析 | `DslAstProvider.getDslAst(filePath, content)` | 构建 AST |
| M4 语义分析 | `DiagnosticProvider.analyze(ast, ruleRepo, symbolTableBuilder)` | 产出诊断 |
| M4 符号表 | `SymbolTableBuilder.buildGlobal/build` | 传递给 M4 DiagnosticProvider |
| M5 修复逻辑 | `QuickFixProvider.getFixActions(diagnostics)` | 生成修复建议 |

**关键约束**：Core 层禁止 import com.intellij.*。所有参数使用纯字符串/基本类型。

## 8. 测试策略

### 8.1 BatchInspectionRunnerImplTest

- **构造器验证**：null 参数抛出异常
- **单文件管线**：Mock 上游接口，验证 M1→M3→M4→M5 调用链正确
- **M1 过滤**：非 DSL 文件返回空结果，totalFiles=0, skippedFiles=1
- **目录扫描**：Mock 文件遍历，验证多文件汇总统计正确
- **文件读取失败**：IOException 时该文件诊断为空，不中断整体
- **诊断计数**：验证 errorCount/warningCount/infoCount 汇总正确

### 8.2 TerminalFormatterTest

- **单条诊断格式**：验证 "filePath:line:col: severity: message [ruleId]" 格式
- **ANSI 颜色**：验证 ERROR=红色、WARNING=黄色、INFO=蓝色包裹
- **noColor 模式**：验证禁用 ANSI 颜色后输出无颜色代码
- **汇总统计格式**：验证 "X errors, Y warnings, Z info" 格式
- **完整报告格式**：验证多文件报告输出顺序和格式
- **修复建议输出**：验证 suggestedFixes 格式化

### 8.3 数据模型测试

- Lombok @Data/@Builder 注解生成代码，验证基本构建和 getter/setter

## 9. 与架构文档对齐检查

| 架构文档定义 | 本次实现 | 对齐状态 |
|---|---|---|
| BatchInspectionRunner 接口签名 | 完全一致 | OK |
| BatchInspectionResult 字段 | 完全一致 | OK |
| FileDiagnosticResult 字段 | 扩展 fixActions | OK（M5 集成） |
| ReportExporter 接口 | 暂不实现 | 延后 |
| Terminal 输出格式 | 完全一致 | OK |
| ANSI 颜色规则 | 完全一致 | OK |
| 退出码语义 | CLI 部分推后 | 延后 |
| Core 层无 IDEA 依赖 | 严格遵守 | OK |
