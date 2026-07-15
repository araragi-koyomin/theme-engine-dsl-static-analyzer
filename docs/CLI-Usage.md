---
module_ids: [CLI]
doc_kind: guide
status: stale
created: 2026-07-06
---
# DSL 静态分析器 — 命令行使用指南

dsl-analyzer 是华为主题引擎 DSL 的静态分析工具，能对 XML 格式的主题文件进行语法、语义和类型推断检查，并输出结构化诊断报告。

## 快速开始

```bash
# 构建
./gradlew :feature:analysis:buildFatJar

# 分析单个文件
java -jar dsl-analyzer.jar /path/to/theme.xml

# 分析整个目录（自动扫描所有 .xml 文件）
java -jar dsl-analyzer.jar /path/to/themes/

# 只看语法错误
java -jar dsl-analyzer.jar --syntax-only /path/to/theme.xml

# 输出 JSON 格式报告
java -jar dsl-analyzer.jar --format json --no-color /path/to/theme.xml

# 查看版本号
java -jar dsl-analyzer.jar --version

# 查看帮助
java -jar dsl-analyzer.jar --help
```

## 命令格式

```bash
java -jar dsl-analyzer.jar [options] <file-or-directory>
```

`<file-or-directory>` 是必填参数，指定要分析的 DSL 文件或包含 DSL 文件的目录路径。只能指定一个目标路径。

## 全部参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `<file-or-directory>` | 目标文件或目录路径（必填） | — |
| `--rule-dir <path>` | 自定义规则库目录 | 内置规则库 |
| `--no-type-check` | 禁用类型推断检查 | 类型推断默认启用 |
| `--syntax-only` | 仅执行语法分析，跳过语义和修复建议 | 全量分析 |
| `--semantic-only` | 仅执行语义分析，跳过语法诊断和修复建议 | 全量分析 |
| `--format <format>` | 输出格式：`terminal`、`json`、`markdown` | `terminal` |
| `--output <path>` | 将报告写入文件（适用于 json/markdown 格式） | 输出到终端 |
| `--no-color` | 禁用终端 ANSI 彩色输出 | 彩色输出 |
| `--verbose` | 启用详细输出模式 | 禁用 |
| `--quiet` | 仅输出 error 级别诊断（与 `--verbose` 互斥） | 输出所有级别 |
| `--config <path>` | 检查配置文件（JSON 格式） | — |
| `--version` | 显示版本号并退出 | — |
| `--help` / `-h` | 显示帮助信息并退出 | — |

## 分析模式

工具提供三种分析模式，控制哪些分析阶段被执行：

### 全量分析（默认）

执行全部三个阶段：语法分析 → 语义分析 → 修复建议。

```bash
java -jar dsl-analyzer.jar /path/to/theme.xml
```

### 仅语法分析（`--syntax-only`）

只执行语法分析阶段，快速检查 XML 结构和元素属性是否符合规则定义。跳过语义分析和修复建议。

适用场景：快速排查 XML 语法问题，或在语义分析耗时较长时先做一轮语法筛查。

```bash
java -jar dsl-analyzer.jar --syntax-only /path/to/theme.xml
```

### 仅语义分析（`--semantic-only`）

只执行语义分析阶段（符号引用、约束检查等），跳过语法诊断输出和修复建议生成。AST 构建仍会执行（语义分析需要 AST 作为输入）。

适用场景：确认 XML 语法无误后，聚焦语义层面的问题。

```bash
java -jar dsl-analyzer.jar --semantic-only /path/to/theme.xml
```

> **注意**：`--syntax-only` 和 `--semantic-only` 互斥，不能同时使用。同时指定会导致 exit 2 错误。

## 输出格式

### Terminal 格式（默认）

gcc/clang 风格，彩色输出：

```
/path/to/theme.xml:5:12: error: 必需属性 'name' 缺失 [SEM-REQ-001]
/path/to/theme.xml:8:4: warning: 属性值引用了未定义变量 'unknownVar' [SEM-REF-001]
```

用 `--no-color` 禁用彩色（适用于 CI 环境或管道输出）：

```bash
java -jar dsl-analyzer.jar --no-color /path/to/theme.xml
```

### JSON 格式

结构化 JSON 报告，适合工具链集成或进一步处理：

```bash
java -jar dsl-analyzer.jar --format json --no-color /path/to/theme.xml
```

输出示例：

```json
{
  "totalFiles": 1,
  "skippedFiles": 0,
  "errorCount": 1,
  "warningCount": 1,
  "infoCount": 0,
  "fileResults": [
    {
      "filePath": "/path/to/theme.xml",
      "diagnostics": [
        {
          "severity": "ERROR",
          "ruleId": "SEM-REQ-001",
          "message": "必需属性 'name' 缺失",
          "filePath": "/path/to/theme.xml",
          "line": 5,
          "column": 12
        }
      ]
    }
  ]
}
```

### Markdown 格式

按严重级别分组的可读报告，适合代码审查或文档记录：

```bash
java -jar dsl-analyzer.jar --format markdown /path/to/theme.xml
```

### 写入文件

使用 `--output` 将报告写入文件（与 `--format json` 或 `--format markdown` 搭配）：

```bash
java -jar dsl-analyzer.jar --format json --output report.json /path/to/theme.xml
java -jar dsl-analyzer.jar --format markdown --output report.md /path/to/themes/
```

## 退出码

| 退出码 | 含义 |
|--------|------|
| `0` | 分析正常完成，无 error 级别诊断 |
| `1` | 分析完成，但存在 error 级别诊断 |
| `2` | 执行异常（参数错误、路径不存在、规则库加载失败、运行时异常等） |

在 CI/CD 管道中，可以根据退出码判断是否阻断流程：

```bash
java -jar dsl-analyzer.jar /path/to/theme.xml
if [ $? -eq 1 ]; then
    echo "发现 error 级别问题，阻断发布流程"
    exit 1
fi
```

## 自定义规则库

默认使用内置规则库。如需使用自定义规则库，指定 `--rule-dir`：

```bash
java -jar dsl-analyzer.jar --rule-dir /path/to/custom-rules /path/to/theme.xml
```

### --rule-dir 行为说明

| 场景 | 行为 | 退出码 |
|------|------|--------|
| 目录不存在 | 报错退出 | 2 |
| 目录存在但无规则 JSON 文件 | 降级到内置规则库 + 警告 | 0 或 1 |
| 目录有格式错误的 JSON | 报错退出 | 2 |
| 目录有有效规则 JSON | 使用自定义规则库 | 0 或 1 |

自定义规则库目录结构要求：

```
custom-rules/
  elements/
    Lockscreen.json
    Wallpaper.json
    ...
  global_vars.json
  rule_sources.json
```

## 检查配置文件

`--config <path>` 指定 JSON 格式的检查配置，支持三个维度的控制：

1. **规则子集**：通过 `enabledRuleIds` 或 `disabledRuleIds` 启用/禁用特定规则
2. **严重级别覆盖**：通过 `severityOverrides` 将特定规则的级别从原值改为新值
3. **根元素列表**：通过 `rootElementNames` 覆盖内置的根元素集合

配置文件示例：

```json
{
  "rootElementNames": ["Lockscreen", "Wallpaper"],
  "enabledRuleIds": ["SYN-001", "SYN-002", "SEM-REF-001"],
  "severityOverrides": {
    "SYN-003": "warning",
    "SEM-CMD-001": "info"
  }
}
```

或使用禁用模式：

```json
{
  "disabledRuleIds": ["SYN-005"],
  "severityOverrides": {
    "SYN-003": "warning"
  }
}
```

配置文件约束：

- `enabledRuleIds` 与 `disabledRuleIds` 不能同时指定
- `severityOverrides` 的值只能是 `error`、`warning`、`info`
- 所有字段均为可选，缺失字段使用默认行为

## 互斥参数

以下参数组合互斥，同时使用会导致 exit 2：

| 互斥组合 | 原因 |
|----------|------|
| `--verbose` + `--quiet` | 详细输出和精简输出逻辑矛盾 |
| `--syntax-only` + `--semantic-only` | 两种模式指向不同分析阶段 |

## 降级运行

分析过程中部分阶段失败时，工具会降级运行而非直接崩溃：

| 失败场景 | 降级行为 |
|----------|----------|
| 单个文件读取失败 | 跳过该文件，继续分析其余文件 |
| 单个 Analyzer 执行异常 | 跳过该 Analyzer，其余 Analyzer 继续运行，输出警告 |
| SymbolTable 构建异常 | 跳过该子树的语义分析，输出警告 |
| AST 构建失败 | 跳过该文件的所有后续分析阶段 |
| 自定义规则库为空 | 降级到内置规则库，输出警告 |

降级运行不会产生 exit 2，但相关问题会被记录为 WARNING 级别诊断。

## 常见用法示例

### 基本分析

```bash
java -jar dsl-analyzer.jar theme.xml
```

### CI 集成（JSON 报告 + 退出码判断）

```bash
java -jar dsl-analyzer.jar --format json --no-color --output report.json themes/
```

### 快速语法筛查

```bash
java -jar dsl-analyzer.jar --syntax-only --quiet themes/
```

### 使用自定义规则 + 配置文件

```bash
java -jar dsl-analyzer.jar --rule-dir /custom-rules --config inspection.json theme.xml
```

### 禁用类型推断

```bash
java -jar dsl-analyzer.jar --no-type-check theme.xml
```

### 生成 Markdown 审查报告

```bash
java -jar dsl-analyzer.jar --format markdown --output review.md themes/
```

## 错误输出示例

缺少目标路径：

```bash
$ java -jar dsl-analyzer.jar
Error: No target path provided
```

路径不存在：

```bash
$ java -jar dsl-analyzer.jar /nonexistent/theme.xml
Error: Path not found: /nonexistent/theme.xml
```

互斥参数：

```bash
$ java -jar dsl-analyzer.jar --verbose --quiet theme.xml
Error: --verbose and --quiet are mutually exclusive
```

```bash
$ java -jar dsl-analyzer.jar --syntax-only --semantic-only theme.xml
Error: --syntax-only and --semantic-only are mutually exclusive
```

规则库目录不存在：

```bash
$ java -jar dsl-analyzer.jar --rule-dir /nonexistent/rules theme.xml
Error: Rule directory not found: /nonexistent/rules
```

## 后续版本计划

以下功能将在后续版本完善：

| 功能 | CLI 参数 | 当前状态 | 跟踪 Issue |
|------|----------|----------|------------|
| 详细输出（AST统计、耗时、符号表摘要） | `--verbose` | 参数已解析，详细内容待实现 | #76 |
| 仅输出 ERROR 级别诊断 | `--quiet` | 参数已解析，过滤逻辑待实现 | #77 |

## 打包内容

| 内容 | 说明 |
|------|------|
| core 层全部类 | cli, expression, ruledsl, function, fileidentification, rulelibrary, syntaxanalysis, semanticanalysis, quickfix, batchinspection, shared |
| ANTLR4 生成代码 | DslExpression / DslRuleCondition 的 Parser + Lexer + Visitor |
| 规则库资源 | rules/ 目录下所有元素规则 JSON + global_vars.json + rule_sources.json |
| 函数签名库资源 | functions/ 目录下 dsl_functions.json |
| GSON 2.9.0 | JSON 反序列化运行时依赖 |
| ANTLR4 runtime 4.13.1 | 表达式与规则 DSL 解析运行时依赖 |

| 排除内容 | 说明 |
|----------|------|
| plugin 层全部类 | PSI Adapter, UI, quickfixui, navigation, language |
| IntelliJ Platform SDK | IDEA 插件框架依赖 |
| Lombok | 编译期注解处理器，运行时不需要 |
