# dsl-analyzer.jar 使用说明

## 构建

```bash
./gradlew :feature:analysis:buildFatJar
```

构建产物位于 `feature/analysis/build/cli/dsl-analyzer.jar`。

## 运行

```bash
java -jar dsl-analyzer.jar [options] <file-or-directory>
```

### 参数

| 参数 | 说明 | 默认值 |
|---|---|---|
| `<file-or-directory>` | 目标DSL文件或目录路径（必填，仅允许一个） | 无 |
| `--rule-dir <path>` | 自定义规则库目录路径 | 内置规则库 |
| `--no-type-check` | 禁用类型推断检查 | 类型推断默认启用 |
| `--verbose` | 启用详细输出模式 | 禁用 |
| `--config <path>` | 检查配置文件路径（JSON：规则启用/禁用、severity覆盖、根元素覆盖） | 无 |
| `--help, -h` | 显示帮助信息 | — |

### 退出码

| 退出码 | 含义 |
|---|---|
| 0 | 正常执行完成，无error级诊断 |
| 1 | 有error级诊断（后续版本启用） |
| 2 | 执行异常：参数错误、targetPath不存在或不是文件/目录、规则库加载失败、--config文件不存在/格式错误/数据非法、运行时异常等 |

## 当前状态（骨架阶段）

当前版本为CLI骨架阶段，实现参数解析、targetPath验证与配置输出，尚未调用分析管线（文件识别、AST构建、语义分析等模块已就绪但尚未接入CLI成功路径）。

**与上一版本的区别**：targetPath 现在必须真实存在于文件系统，否则报错退出（见下方错误示例）。--help 模式不受此限制。

运行后输出解析后的配置摘要：

```bash
$ java -jar dsl-analyzer.jar --rule-dir /path/to/rules --no-type-check --verbose /path/to/theme.xml

Configuration:
  Target: /path/to/theme.xml
  Rule directory: /path/to/rules
  Type check: disabled
  Verbose: enabled
  Config: (none)
```

```bash
$ java -jar dsl-analyzer.jar /path/to/theme.xml

Configuration:
  Target: /path/to/theme.xml
  Rule directory: (built-in)
  Type check: enabled
  Verbose: disabled
  Config: (none)
```

### --config 检查配置文件

`--config <path>` 指定 JSON 格式的检查配置文件，支持三个维度：

1. **规则子集启用/禁用**：通过 `enabledRuleIds` 或 `disabledRuleIds` 控制哪些规则生效
2. **severity 覆盖**：通过 `severityOverrides` 将特定规则的严重级别从原值覆盖为新值
3. **根元素列表覆盖**：通过 `rootElementNames` 覆盖内置的根元素集合

#### 配置文件格式（JSON）

所有字段均为可选，缺失字段使用默认行为（不覆盖）。

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

#### 约束规则

- `enabledRuleIds` 与 `disabledRuleIds` 不可同时指定（同时非空非null时报错）
- `severityOverrides` 的值必须是 `error`、`warning`、`info` 三者之一，非法值报错
- `rootElementNames` 若为空数组 `[]`，视为"不覆盖"（使用默认根元素集合）
- 配置文件不存在、不是文件、JSON格式错误、数据非法 → 退出码 2

### 帮助信息

```bash
$ java -jar dsl-analyzer.jar --help

Usage: java -jar dsl-analyzer.jar [options] <file-or-directory>
Options:
  --rule-dir <path>   Custom rule library directory (default: built-in)
  --no-type-check     Disable type inference checking (default: enabled)
  --verbose           Enable verbose output
  --config <path>     Inspection config file (JSON: ruleId enable/disable, severity override, root element override)
  --help, -h          Show this help message
```

### 错误输出示例

缺少必填参数：

```bash
$ java -jar dsl-analyzer.jar

Error: No target path provided
Usage: java -jar dsl-analyzer.jar [options] <file-or-directory>
Options:
  --rule-dir <path>   Custom rule library directory (default: built-in)
  --no-type-check     Disable type inference checking (default: enabled)
  --verbose           Enable verbose output
  --config <path>     Inspection config file (JSON: ruleId enable/disable, severity override, root element override)
  --help, -h          Show this help message
```

targetPath 不存在：

```bash
$ java -jar dsl-analyzer.jar /nonexistent/path/theme.xml

Error: Path not found: /nonexistent/path/theme.xml
Usage: java -jar dsl-analyzer.jar [options] <file-or-directory>
Options:
  --rule-dir <path>   Custom rule library directory (default: built-in)
  --no-type-check     Disable type inference checking (default: enabled)
  --verbose           Enable verbose output
  --config <path>     Inspection config file (JSON: ruleId enable/disable, severity override, root element override)
  --help, -h          Show this help message
```

`--rule-dir` 缺少路径值：

```bash
$ java -jar dsl-analyzer.jar --rule-dir theme.xml

Error: --rule-dir requires a path value
Usage: java -jar dsl-analyzer.jar [options] <file-or-directory>
...
```

多个目标路径（仅允许一个）：

```bash
$ java -jar dsl-analyzer.jar theme.xml layout.xml

Error: Multiple target paths provided. Only one <file-or-directory> argument is allowed.
Usage: java -jar dsl-analyzer.jar [options] <file-or-directory>
...
```

使用已废弃的 `--type-check` flag：

```bash
$ java -jar dsl-analyzer.jar --type-check theme.xml

Error: Unknown option: --type-check
Usage: java -jar dsl-analyzer.jar [options] <file-or-directory>
...
```

使用 `--config` 指定检查配置文件：

```bash
$ java -jar dsl-analyzer.jar --config /path/to/inspection-config.json /path/to/theme.xml

Configuration:
  Target: /path/to/theme.xml
  Rule directory: (built-in)
  Type check: enabled
  Verbose: disabled
  Config: /path/to/inspection-config.json
  Root element override: [Lockscreen, Wallpaper]
  Severity overrides: [SEM-CMD-001->info, SYN-003->warning]
```

`--config` 文件不存在：

```bash
$ java -jar dsl-analyzer.jar --config /nonexistent/config.json /path/to/theme.xml

Error: Config file not found: /nonexistent/config.json
```

`--config` 缺少路径值：

```bash
$ java -jar dsl-analyzer.jar --config theme.xml

Error: --config requires a path value
```

## 打包内容

| 内容 | 说明 |
|---|---|
| core 层全部类 | com.huawei.theme.analysis.core 下所有模块（cli, expression, ruledsl, function, fileidentification, rulelibrary, syntaxanalysis, semanticanalysis, shared） |
| ANTLR4 生成代码 | DslExpression / DslRuleCondition 的 Parser + Lexer + Visitor |
| 规则库资源 | rules/ 目录下所有元素规则JSON + global_vars.json + rule_sources.json |
| 函数签名库资源 | functions/ 目录下 dsl_functions.json |
| GSON 2.9.0 | JSON反序列化运行时依赖 |
| ANTLR4 runtime 4.13.1 | 表达式与规则DSL解析运行时依赖 |

| 排除内容 | 说明 |
|---|---|
| plugin 层全部类 | PSI Adapter, UI, quickfixui, navigation, language |
| IntelliJ Platform SDK | IDEA插件框架依赖 |
| META-INF/plugin.xml | 仅IDEA插件需要的配置文件 |
| dom4j | 已迁移至JDK SAX，不再使用 |
| Lombok | 编译期注解处理器，运行时不需要 |

## 后续版本计划

以下功能将在后续版本（M7 Extension层）逐步启用：

| 功能 | CLI参数 | 说明 |
|---|---|---|
| 语法检查模式 | `--syntax-only` | 只执行M3语法分析，跳过M4/M5 |
| 语义检查模式 | `--semantic-only` | 只执行M4语义分析 |
| 输出格式 | `--format json/terminal/markdown` | 选择报告输出格式 |
| 报告输出路径 | `--output <path>` | 报告导出到文件（仅md/json） |
| 禁止终端彩色 | `--no-color` | Terminal输出不使用ANSI颜色 |
| 只输出error | `--quiet` | 过滤WARNING/INFO级别诊断 |
