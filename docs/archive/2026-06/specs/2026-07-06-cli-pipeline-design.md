# CLI管线集成与输出格式设计文档

## 概述

合并开发 #55（CLI-BatchInspectionRunner管线集成）与 #56（CLI输出格式+退出码），#57（End-to-end验收）独立执行。

## 1. 架构概述与管线编排

### 1.1 核心变更

将 `CliMain` 从"打印config→exit 0"升级为完整的M7管线编排器。

### 1.2 新增/修改的关键类

```
core/cli/
  CliConfig.java          ← 扩展：新增 --format/--output/--no-color/--quiet/--syntax-only/--semantic-only/--version
  CliMain.java            ← 重构：管线编排（创建依赖链→执行扫描→导出报告→计算退出码）
  InspectionConfig.java   ← 扩展：新增 PipelineMode 枚举 + format/noColor/quiet/outputPath 字段
  PipelineMode.java       ← 新增：FULL/SYNTAX_ONLY/SEMANTIC_ONLY 枚举

core/batchinspection/
  BatchInspectionRunnerImpl.java  ← 重构：构造参数新增 InspectionConfig，内部根据模式决定执行阶段
  BatchInspectionResult.java      ← 无变更
```

### 1.3 CliMain管线编排流程

```
CliMain.run(args)
  ├─ CliConfig.fromArgs(args) → 解析所有CLI参数
  ├─ 参数互斥验证 (--verbose/--quiet, --syntax-only/--semantic-only)
  ├─ --rule-dir 验证（目录不存在/缺JSON/格式错误）
  ├─ 加载规则库 (JsonRuleLoader → ConfigAwareRuleRepository)
  ├─ 创建依赖链:
  │   RuleRepository → CliDslFileMatcher → CliDslAstProvider
  │   → DiagnosticProvider → QuickFixProvider → SymbolTableBuilder
  │   → BatchInspectionRunnerImpl(所有依赖 + InspectionConfig)
  ├─ 执行扫描: runner.runOnFile() / runner.runOnDirectory()
  ├─ 导出报告: ReportExporter (terminal/json/markdown)
  │   (或 exportToFile 若指定 --output)
  ├─ 计算退出码: ExitCodeCalculator.compute(result)
  └─ System.exit(exitCode)
```

### 1.4 PipelineMode对Runner执行阶段的影响

| 模式 | M3语法分析 | M4语义分析 | M5修复逻辑 |
|------|-----------|-----------|-----------|
| FULL | 执行 | 执行 | 执行 |
| SYNTAX_ONLY | 执行 | 跳过 | 跳过 |
| SEMANTIC_ONLY | 跳过语法错误收集 | 执行(需AST) | 执行 |

SEMANTIC_ONLY仍需M3构建AST（M4依赖AST输入），但跳过M3语法错误收集。--no-type-check控制M4内TypeAnalyzer是否启用（已在AnalyzerRegistry层面处理）。

## 2. 降级运行与错误处理

### 2.1 降级场景

| # | 场景 | 处理策略 | 退出码影响 |
|---|------|---------|-----------|
| 1 | 单文件读取/解析失败 | 跳过该文件+计入skippedFiles+输出警告 | exit 1（如果有error诊断），否则正常 |
| 2 | 单个Analyzer执行异常（如ConstraintAnalyzer） | 跳过该Analyzer的诊断+其余Analyzer继续+输出警告 | exit 1（如果有error诊断） |
| 3 | SymbolTable构建异常 | 跳过整个M4语义分析+空诊断+输出警告 | exit 1（如果有error诊断） |
| 4 | M5修复生成异常 | 空FixAction+警告 | 不影响退出码 |

**降级粒度**：场景#2为per-Analyzer降级，需修改 `DiagnosticProviderImpl` 的analyze循环，对单个Analyzer调用加try-catch。场景#3为SymbolTable构建失败，此时整个M4无法执行，退化为空诊断。

### 2.2 降级实现方式

**降级实现分为两层**：

#### Runner层（BatchInspectionRunnerImpl.analyzeFile()）

```java
private FileDiagnosticResult analyzeFile(String filePath, String content) {
    DslFileNode ast;
    try {
        ast = astProvider.getDslAst(filePath, content);
    } catch (Exception e) {
        LOGGER.w("M3 AST构建失败: " + filePath + " - " + e.getMessage());
        return FileDiagnosticResult.builder()
            .filePath(filePath).diagnostics(List.of()).fixActions(List.of())
            .build();
    }

    List<Diagnostic> diagnostics = List.of();
    if (pipelineMode != PipelineMode.SYNTAX_ONLY) {
        try {
            diagnostics = diagnosticProvider.analyze(ast, ruleRepository, symbolTableBuilder);
        } catch (Exception e) {
            LOGGER.w("M4语义分析失败: " + filePath + " - " + e.getMessage());
        }
    }

    List<FixAction> fixActions = List.of();
    if (pipelineMode == PipelineMode.FULL && !diagnostics.isEmpty()) {
        try {
            fixActions = quickFixProvider.getFixActions(diagnostics);
        } catch (Exception e) {
            LOGGER.w("M5修复生成失败: " + filePath + " - " + e.getMessage());
        }
    }

    return FileDiagnosticResult.builder()
        .filePath(filePath).diagnostics(diagnostics).fixActions(fixActions)
        .build();
}
```

#### Analyzer层（DiagnosticProviderImpl.analyze循环）

场景#2（per-Analyzer降级）修改 `DiagnosticProviderImplInner.analyze()` 方法：

```java
private void analyze(DslElementNode elementNode, SymbolTable symbolTable) {
    for (var analyzer : AnalyzerRegistry.getAnalyzers()) {
        try {
            var list = analyzer.analyze(elementNode,
                    new DslContext(ruleRepo, symbolTable, root.getFilePath(), root));
            diagnostics.addAll(list);
        } catch (Exception e) {
            LOGGER.w("Analyzer " + analyzer.getClass().getSimpleName()
                    + " 执行异常: " + e.getMessage());
        }
    }
    for (var child : elementNode.getChildElements()) {
        try {
            analyze(child, symbolTableBuilder.build(elementNode, symbolTable, ruleRepo));
        } catch (Exception e) {
            LOGGER.w("SymbolTable构建异常: " + e.getMessage());
            break;  // SymbolTable失败→无法继续递归子元素
        }
    }
}
```

**SymbolTable异常处理**：子元素的SymbolTable构建失败时，停止递归该分支（break），但同一层级其他Analyzer结果仍然保留。全局SymbolTable构建失败（构造函数中`buildGlobal()`异常）则整个M4退化为空诊断，由Runner层catch处理。

### 2.3 --rule-dir验证（在CliMain中处理）

| 场景 | 条件 | 处理 | 退出码 |
|------|------|------|--------|
| 目录不存在 | `!new File(ruleDir).exists()` | 输出错误信息 | 2 |
| 缺少必要JSON | JsonRuleLoader成功加载但`ruleRepo.getAllElementNames()`为空 | 降级回退内置规则+警告 | 1 |
| JSON格式错误 | JsonRuleLoader抛出RuleLoadException | 输出错误信息 | 2 |

**内置规则回退逻辑**：缺必要JSON时，用默认内置规则路径重新加载一次JsonRuleLoader，成功则降级运行（exit 1），失败则exit 2。

### 2.4 降级警告输出

使用 `CliOutputFormatter.formatWarning()`（需新增此方法），在Terminal模式下黄色ANSI输出。

## 3. CLI参数扩展与输出格式

### 3.1 CliConfig新增字段

```java
@Data @Builder
public class CliConfig {
    // 已有
    String ruleDir;
    @Builder.Default boolean typeCheck = true;
    boolean verbose;
    boolean helpRequested;
    String targetPath;
    String configPath;

    // 新增
    @Builder.Default String format = "terminal";  // json / markdown / terminal
    String outputPath;                              // --output <path>，仅md/json
    @Builder.Default boolean noColor = false;       // --no-color
    boolean quiet;                                  // --quiet（与verbose互斥）
    boolean syntaxOnly;                             // --syntax-only
    boolean semanticOnly;                           // --semantic-only（与syntaxOnly互斥）
    boolean versionRequested;                       // --version
}
```

### 3.2 参数互斥验证（在CliMain.run()中）

- `--verbose` + `--quiet` → exit 2（错误信息："--verbose and --quiet are mutually exclusive"）
- `--syntax-only` + `--semantic-only` → exit 2（错误信息："--syntax-only and --semantic-only are mutually exclusive"）
- `--format terminal` + `--output <path>` → 警告（terminal格式默认输出到stdout，--output被忽略）

### 3.3 InspectionConfig扩展（供Runner消费）

```java
@Data @Builder
public class InspectionConfig {
    // 已有: rootElementNames, enabledRuleIds, disabledRuleIds, severityOverrides
    // 新增
    PipelineMode pipelineMode;    // FULL/SYNTAX_ONLY/SEMANTIC_ONLY
    boolean typeCheck;            // --no-type-check 对应
    boolean noColor;              // 传递给TerminalFormatter
    boolean verbose;              // verbose模式
    boolean quiet;                // quiet模式（只输出ERROR级别）
}
```

### 3.4 PipelineMode枚举

```java
public enum PipelineMode {
    FULL,           // 全管线 M3+M4+M5
    SYNTAX_ONLY,    // 仅M3语法分析
    SEMANTIC_ONLY   // 仅M4语义分析（仍需M3构建AST）
}
```

### 3.5 输出格式处理（在CliMain中）

```java
TerminalFormatter formatter = new TerminalFormatter(config.isNoColor());
ReportExporter exporter = new ReportExporterImpl(formatter);
String output;
switch (config.getFormat()) {
    case "json":     output = exporter.exportJson(result); break;
    case "markdown": output = exporter.exportMarkdown(result); break;
    default:         output = exporter.exportTerminal(result); break;
}

if (config.getOutputPath() != null) {
    exporter.exportToFile(result, config.getFormat(), config.getOutputPath());
} else {
    System.out.println(output);
}
```

### 3.6 --verbose模式输出

在标准诊断输出后追加：
- AST节点数统计
- 各阶段耗时（M3解析、M4分析、M5修复）
- 符号表内容摘要
- 函数签名匹配详情

### 3.7 --quiet模式

ReportExporter的Terminal输出只包含ERROR级别诊断。

### 3.8 --version

输出版本号字符串后exit 0，不执行分析。版本号定义为常量 `CliMain.VERSION = "0.1.0"`。

## 4. 测试策略与验收标准

### 4.1 开发Phase与对应测试

| Phase | 内容 | 测试项 |
|-------|------|--------|
| P1 | InspectionConfig + PipelineMode + CliConfig扩展 | CliConfig.fromArgs()所有新参数 + 互斥验证 |
| P2 | CliMain管线编排 + Runner模式注入 | 单文件/目录扫描 + 3种模式 |
| P3 | ReportExporter + ExitCodeCalculator接入CliMain | 3种格式输出 + 退出码0/1/2 + --verbose/--quiet |
| P4 | 降级运行 | 3种降级场景 |
| P5 | --rule-dir验证 | 3种验证场景 |

### 4.2 #55+#56合并测试矩阵

| 测试类别 | 测试项 |
|---------|--------|
| 参数解析 | CliConfig.fromArgs() 所有新参数 + 互斥验证 |
| 管线编排 | CliMain→Runner→Exporter完整管线 |
| 单文件扫描 | runner.runOnFile() 正常DSL文件 + 非DSL文件跳过 |
| 目录扫描 | runner.runOnDirectory() 含DSL过滤 |
| 模式开关 | --syntax-only 仅语法诊断 / --semantic-only 仅语义诊断 / --no-type-check 无类型诊断 |
| Terminal输出 | gcc风格格式 + ANSI彩色 + --no-color |
| JSON输出 | 单文件schema + 多文件schema |
| Markdown输出 | 按severity分组 + summary |
| 退出码 | 0(无error) / 1(有error) / 2(异常) |
| --output文件导出 | 写入指定路径 |
| --verbose/--quiet | verbose详细输出 / quiet只输出ERROR |
| --version | 显示版本号exit 0 |
| 降级-单文件失败 | 跳过+计入skippedFiles+警告 |
| 降级-per-Analyzer异常 | 跳过该Analyzer+其余继续+警告 |
| 降级-SymbolTable异常 | 跳过M4或停止递归+警告 |
| 降级-M5修复异常 | 空FixAction+警告 |
| --rule-dir验证 | 目录不存在→exit2 / 缺JSON→exit1降级 / 格式错误→exit2 |

### 4.3 #57独立验收标准

- `java -jar dsl-analyzer.jar theme.xml` 完整运行并输出诊断结果
- `java -jar dsl-analyzer.jar ./themes/` 目录扫描运行
- Core层无 `com.intellij` import（编译期扫描通过）
- 退出码0/1/2场景全部验证
- 100文件性能 ≤5s

### 4.4 测试文件位置

- `feature/analysis/src/test/java/com/huawei/theme/analysis/core/cli/`
- `feature/analysis/src/test/java/com/huawei/theme/analysis/core/batchinspection/`

### 4.5 测试命名惯例

`CliMainIntegrationTest`, `CliConfigExtendedTest`, `BatchInspectionRunnerModeTest`, `ReportExporterCliTest`, `CliDegradationTest`

## 5. Issue关系

| Issue | 角色 | 状态 |
|-------|------|------|
| #67 | 上游（M7接口+数据模型） | 代码已实现，后续关闭 |
| #55 | 管线集成（合并开发） | 本次实现 |
| #56 | 输出格式+退出码（合并开发） | 本次实现 |
| #57 | End-to-end验收 | #55/#56完成后独立执行 |

## 6. 不在本次范围内

- Plugin层Adapter/进度条/Dispatcher事件
- M5 QuickFixProvider的Extension层功能
- 规则编辑器UI接口
- 自定义报告模板
- 函数签名库热更新
