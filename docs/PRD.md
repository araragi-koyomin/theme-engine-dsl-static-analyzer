---
module_ids: [CORE]
doc_kind: guide
status: stale
created: 2026-06-15
---
# 主题引擎DSL静态分析工具 - 产品需求文档

## 1. 产品概述

### 1.1 产品名称
Theme Engine DSL静态分析工具

### 1.2 产品类型
双形态交付：CLI分析工具（dsl-analyzer.jar） + IntelliJ IDEA插件（plugin.zip）

### 1.3 产品目标
为主题开发者提供Theme Engine XML DSL文件的静态分析能力，覆盖Lockscreen、Wallpaper、Widget、ChargingSkin四大应用场景，支持两种使用形态：
- **CLI形态**：命令行运行语法检查、语义检查、类型推断检查、声明式规则约束检查、报告导出，适用于CI/CD流水线和批量质量检查
- **IDEA插件形态**：在IDEA中提供实时编辑器标注、悬浮提示（含变量信息）、Quick Fix一键修复、诊断面板、Ctrl+Click跳转定义、查找引用、重命名重构等交互能力

### 1.4 目标用户
- 主题开发者（IDEA插件形态）
- CI/CD工程师（CLI形态）
- 需要实时语法语义检查的UI开发人员（IDEA插件形态）
- 团队技术负责人（CLI形态，批量质量检查+报告导出）

### 1.5 用户痛点
- DSL文件缺乏IDE级别的实时语法检查，错误只能运行时发现
- 语法错误难以定位，无上下文提示
- 表达式类型错误无检测（如visibility赋值字符串表达式，期望数值）
- 修正错误需手动查阅文档，效率低
- 缺乏项目级别的批量质量检查手段
- 缺乏CI/CD集成的自动化检查能力
- 表达式语法错误（如`-#varName`）无提示，运行时静默失败
- 变量引用`#varName`/`@varName`不存在时无警告
- 无法跳转到变量定义处，无法查找引用、无法重命名
- 元素在不适用的应用位置中使用无检测
- 新增检测规则必须写代码，无法通过配置快速扩展

## 2. 功能需求

### 2.1 CLI分析工具功能

#### 2.1.1 基础使用方式
```
java -jar dsl-analyzer.jar [options] <file-or-directory>
```

#### 2.1.2 检查范围控制
- `--syntax-only`：只做语法检查（M3层：SyntaxChecker SYN-001/003/004 + ExpressionSyntaxChecker SYN-EXPR-*）
- `--semantic-only`：只做语义检查（M4 analyzer，不含类型推断）
- 默认全量检查（语法+语义+类型推断+规则约束）
- `--no-type-check`：禁用类型推断检查（TypeAnalyzer）

#### 2.1.3 语法错误检测

**XML结构语法错误（StAX解析阶段）**：
- StAX (XMLStreamReader)解析XML时遇格式错误直接抛出XMLStreamException，不做额外包装映射
- 包含：标签未闭合、属性引号缺失、缺少XML声明头等XML well-formedness错误
- 根元素标签错误（SYN-010）：M1文件识别阶段检测

**DSL结构语法错误（M3语法分析阶段捕获，基于StAX解析后的AST+规则库）**：
- 标签嵌套违反父子约束（SYN-002）
- 未知元素标签（SYN-004）
- 必填属性缺失（SYN-006）
- 未知属性名（SYN-005）

**DSL表达式语法错误（ANTLR4表达式解析器捕获）**：
- 无效`-#varName`语法模式（SEM-EXPR-001）
- 字符串表达式未使用单引号（SEM-EXPR-004）
- 字符串表达式嵌入数值表达式缺少花括号（SEM-EXPR-005）
- 字符串表达式中数值计算以#开头（SEM-EXPR-003）
- preciseeval后使用运算符或+连接符（SEM-EXPR-006）
- ANTLR4词法/语法错误：不可识别的token、表达式结构不合法等

#### 2.1.4 语义与规则错误检测
- 构成性语义检查（模式匹配类Analyzer）：
  - 未知组件（未在规范中定义）
  - 必填属性缺失
  - 未知属性
  - 枚举值不合法
  - 父子结构不合法
  - 元素不支持当前应用位置（作用域矩阵）
- 类型推断检查（TypeAnalyzer）：
  - 表达式类型与属性期望类型不匹配（SEM-TYPE-001）
  - 函数调用参数类型不匹配（SEM-TYPE-002）
  - 变量引用类型推断与上下文不一致
- 声明式规则约束检查（ConstraintAnalyzer）：
  - 互斥属性共存（如VideoCommand的play+sound）
  - 禁止属性组合（如persist作用于time/date变量）
  - 数值范围越界
  - 其他可声明式描述的约束条件
- DSL表达式模式检查：
  - 无效`-#varName`语法模式
  - 字符串表达式未使用单引号
  - 字符串表达式嵌入数值表达式缺少花括号
  - 数值精度超过7位警告
- 变量引用完整性检查：
  - 引用未声明的变量名（SEM-REF-001）
  - 引用未定义的元素name（SEM-REF-002）
  - 重复name定义（SEM-REF-003）

#### 2.1.5 输出格式
- **JSON stdout**（`--format json`）：结构化JSON输出，每条诊断包含severity/file/line/col/ruleId/message/suggestedFixes/ruleDocUrl，多文件扫描时按文件聚合+汇总统计。生产路径中 `FixActionRegistry` 已初始化，`suggestedFixes` 字段在存在可修复诊断时非空
- **终端彩色输出**（`--format terminal`）：类似gcc/clang格式的可读输出，file:line:col: severity: message [code]
- **报告文件导出**（`--format markdown --output path`）：Markdown格式报告，按严重级别分组，含文件路径/行列号/诊断code/修复建议/规则来源
- **退出码语义**：0=无error级诊断，1=有error级诊断，2=执行异常（文件不存在/规则库加载失败）

#### 2.1.6 规则库配置
- `--rule-dir <path>`：指定自定义规则库目录（替代内置规则）
- 规则库包含：元素规则条目JSON + 命令规则条目JSON + 全局变量JSON + 函数签名库JSON
- 新增检测逻辑通过在规则JSON中追加constraints条目实现（声明式规则DSL），无需编写代码
- 规则DSL条件语法：属性访问(`element.attrs['play']`)、属性存在性(`!= null`)、比较运算、逻辑运算(AND/OR/NOT)、集合运算(IN)

#### 2.1.7 其他CLI参数
- `--output <path>`：报告文件输出路径（仅markdown/json格式有效）
- `--no-color`：禁止终端彩色输出
- `--quiet`：只输出error级别诊断
- `--config <path>`：检查配置文件路径
- `--verbose`：详细输出（含类型推断过程）

### 2.2 IDEA插件功能

#### 2.2.1 实时编辑器标注
- 自动识别DSL文件（双重识别：扩展名+根元素标签），项目树显示自定义图标
- 非DSL XML文件不受影响
- 编辑器中实时显示诊断标注（波浪线），沿用IDEA原生配色（Error红色/Warning黄色/Info蓝色）
- 标注精确到具体属性值范围

#### 2.2.2 悬浮提示（增强版）
- **错误场景**：悬浮在波浪线处显示精简版Tooltip（错误摘要+建议修复+规则ID+文档链接）
- **变量信息场景**（新增）：悬浮在`#varName`/`@varName`处显示变量类型、声明位置、常量标记
- **元素规则场景**（新增）：悬浮在元素标签处显示该元素的规则摘要（合法属性列表、允许子元素等）
- **Var声明场景**（新增）：悬浮在`<Var>`标签处显示变量类型+常量标记

#### 2.2.3 Quick Fix一键修复
- **无需确认类**：Alt+Enter直接执行
  - 补闭合标签、补属性引号、删除多余结束标签
  - 插入必填属性占位值/默认值
  - 数字/布尔/路径格式归一化
  - 表达式语法修正（`-#varName` → `-1*#varName`）
  - 移除互斥属性之一、移除禁止属性组合
- **需确认类**：Alt+Enter → 下拉候选列表 → diff预览 → 确认执行
  - 替换为最接近的合法组件名（基于编辑距离匹配）
  - 替换为别名属性/删除属性/转为通用属性
  - 替换为最接近合法枚举值
  - clamp到合法范围
- 修复后标注自动消失

#### 2.2.4 导航与重构（新增）
- **跳转定义**：Ctrl+Click `#varName`/`@varName` → 跳转到`<Var name="varName">`定义处
- **查找所有引用**：在Var声明处右键 → Find Usages → 列出所有`#varName`/`@varName`引用位置
- **重命名重构**：在Var声明处右键 → Rename → 所有`#varName`/`@varName`引用同步更新

#### 2.2.5 DSL诊断面板
- IDEA底部DSL Analysis面板，按Error/Warning/Info分组展示当前文件/项目级诊断
- 点击问题条目跳转编辑器对应位置
- 右键问题条目：Quick Fix/查看规则文档/复制
- 底部工具栏：Run Analysis按钮 + Export下拉按钮（Markdown/JSON）

#### 2.2.6 右键菜单批量检查
- 右键文件/目录/项目节点 → "Check DSL Rules" → 触发M7批量检查
- IDEA原生进度条展示
- 检查完成 → 面板刷新 + 通知气泡摘要

#### 2.2.7 规则来源说明
- 每个诊断问题和Quick Fix附带规则来源和文档链接
- 用户可追溯每条诊断的规则依据

### 2.3 零代码扩展能力（新增）

- 新增DSL元素：在规则库JSON中追加元素规则条目
- 新增属性：在元素规则的optionalAttrs/attrTypes中追加
- 新增枚举值：在attrTypes的enumValues中追加
- 新增作用域支持：在scope矩阵中追加
- **新增检测逻辑**：在元素规则的constraints数组中追加声明式条件条目
  - condition字段使用规则DSL语法：`element.attrs['play'] != null AND element.attrs['sound'] != null`
  - 自动由ConstraintAnalyzer执行，无需编写Analyzer代码
- 新增函数：在函数签名库JSON中追加条目
- 规则库可通过`--rule-dir`指定外部目录，实现完全零代码的自定义规则集

### 2.4 类型推断能力（新增）

- 对标记为支持表达式的属性值（visibility、expression等）进行类型推断验证
- 从变量符号表推断`#varName`/`@varName`的类型
- 从函数签名库推断函数调用（ifelse、sin、substr等）的返回类型
- 验证推断类型与属性期望类型是否匹配
- 验证函数参数类型是否匹配签名
- `+`运算符语义由上下文决定：number上下文为加法，string上下文为拼接
- 不做常量折叠（不对表达式求值），不做符号执行

## 3. 非功能需求

### 3.1 性能需求
- 单文件实时检测响应时间 ≤ 50ms（IDEA插件形态）
- CLI单文件检查响应时间 ≤ 100ms
- CLI批量检查 ≤ 5s/100文件
- 表达式类型推断对单个属性值 ≤ 5ms

### 3.2 兼容性需求
- IntelliJ IDEA 2024.1+（Ultimate / Community Edition）
- Java 17+（CLI jar运行环境）
- ANTLR4 runtime（作为CLI jar依赖打包）

### 3.3 可用性需求
- CLI：输出格式遵循gcc/clang诊断格式惯例，便于与现有工具链集成
- IDEA插件：检测结果以IDEA标准诊断格式呈现，交互与IDEA原生功能一致
- 规则库：声明式JSON+规则DSL，零代码扩展新检测逻辑

### 3.4 架构需求
- 分析核心与IDEA UI解耦：core包无IDEA SDK依赖，CLI jar只打包core包
- Plugin包依赖IDEA SDK + core包，在core基础上叠加交互能力
- ANTLR4用于表达式和规则DSL的词法/语法分析，XML结构解析使用JDK StAX (XMLStreamReader)
- 隔离保障：编译期扫描core包内无com.intellij import

## 4. 用户场景

### 4.1 IDEA插件场景

**场景1：开发者编辑DSL文件，输入错误标签名**
→ 实时标红波浪线 → 悬浮提示"未知元素" → Quick Fix建议替换为最接近的合法标签 → 一键修正

**场景2：开发者遗漏必填属性**
→ 实时标红波浪线 → 悬浮提示"缺失必填属性name" → Quick Fix自动补全 → 一键添加

**场景3：开发者写错表达式类型**
→ 实时标红波浪线 → 悬浮提示"属性类型不匹配，期望number，实际string" → 删除错误值/替换为正确类型表达式

**场景4：开发者在表达式中写入`-#screen_height`**
→ 实时标红波浪线 → 提示"`-#varName`无效" → Quick Fix建议`-1*#screen_height` → 一键修正

**场景5：开发者在Wallpaper中使用`<Button>`元素**
→ 实时标红波浪线 → 提示"Button不支持Wallpaper应用位置" → Quick Fix建议移除 → 一键修正

**场景6：开发者Ctrl+Click `#steps_value`**
→ 跳转到`<Var name="steps_value">`定义处 → 悬浮显示变量类型(number)+声明位置

**场景7：开发者重命名变量**
→ 右键Var → Rename → 所有`#steps_value`/`@steps_value`引用同步更新

**场景8：团队提交前质量检查**
→ 右键项目 → Check DSL Rules → 面板展示所有问题 → 批量Quick Fix修正

### 4.2 CLI场景

**场景9：CI/CD流水线集成**
→ `java -jar dsl-analyzer.jar --format json src/` → JSON输出 → 退出码1(有error) → 流水线阻断

**场景10：团队提交前批量检查**
→ `java -jar dsl-analyzer.jar --format markdown --output report.md src/` → 生成Markdown报告

**场景11：开发者快速检查单个文件**
→ `java -jar dsl-analyzer.jar --format terminal theme.xml` → 终端彩色输出 → 定位错误行

**场景12：团队自定义规则集**
→ 编写规则JSON（含constraints条件）→ `java -jar dsl-analyzer.jar --rule-dir custom-rules/ src/` → 自定义检测逻辑生效，无需写代码

**场景13：开发者只做语法检查**
→ `java -jar dsl-analyzer.jar --syntax-only theme.xml` → 仅输出语法错误

## 5. 交付形态

| 形态 | 包名 | 内容 | 使用方式 |
|---|---|---|---|
| CLI分析工具 | dsl-analyzer.jar | core包全部代码 + GSON + ANTLR4 runtime（fat jar，XML解析用JDK内置StAX） | `java -jar dsl-analyzer.jar` |
| IDEA插件 | plugin.zip | core包 + plugin包 + plugin.xml（intellij plugin build） | IDEA安装插件 |

## 6. 相关文档

| 文档 | 说明 |
|---|---|
| [DSL-Rule-Spec.md](DSL-Rule-Spec.md) | DSL规则规范、错误检测类型定义、规则库维护指南 |
| [Architecture.md](Architecture.md) | 软件架构总览、模块依赖关系、接口规范 |
| [architecture/M0-ParserInfrastructure.md](architecture/M0-ParserInfrastructure.md) | M0解析器基础设施：ANTLR4 grammar + 表达式解析 + 规则DSL解析 |
| [architecture/M1-FileIdentification.md](architecture/M1-FileIdentification.md) | M1文件识别模块 |
| [architecture/M2-RuleLibrary.md](architecture/M2-RuleLibrary.md) | M2规则库模块（含RuleConstraint声明式约束） |
| [architecture/M3-SyntaxAnalysis.md](architecture/M3-SyntaxAnalysis.md) | M3语法分析模块（独立AST） |
| [architecture/M4-SemanticAnalysis.md](architecture/M4-SemanticAnalysis.md) | M4语义分析与类型系统模块 |
| [architecture/M5-QuickFix.md](architecture/M5-QuickFix.md) | M5修复逻辑模块 |
| [architecture/M6-UIInteraction.md](architecture/M6-UIInteraction.md) | M6 UI交互模块 |
| [architecture/M7-BatchInspection.md](architecture/M7-BatchInspection.md) | M7批量检查与报告模块 |
| [architecture/M8-Navigation.md](architecture/M8-Navigation.md) | M8导航与重构模块（仅Plugin层） |
| [architecture/PSI-Adapter.md](architecture/PSI-Adapter.md) | PSI Adapter模块（AST↔PSI桥接） |
| [UX-Design.md](UX-Design.md) | UX交互设计文档 |
| [Development-Plan.md](Development-Plan.md) | 开发计划与Phase划分 |
| [superpowers/specs/2026-06-17-architecture-refactor-design.md](superpowers/specs/2026-06-17-architecture-refactor-design.md) | 架构重构设计文档（本次重构依据） |
