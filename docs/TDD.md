主题引擎DSL静态分析工具-技术设计文档

1. 技术架构

1.1 整体架构
基于编译器前端技术构建，采用经典的词法分析→语法分析→语义分析三层架构：
- Lexer：DSL文本 → Token流
- Parser：Token流 → AST
- SemanticAnalyzer：AST + 规则库 → 诊断结果

整体架构划分为以下子系统：
- 语法分析子系统：负责DSL XML结构的PSI树构建与基础语法检查
- 表达式解析子系统：负责数字表达式（NumericExpression）和字符串表达式（StringExpression）的独立解析、语法验证与语义检查
- 语义分析子系统：负责规则驱动约束检查、上下文分析、变量引用验证、作用域检测等
- Trigger/Command链分析子系统：负责触发器-命令链的结构与语义约束检查

1.2 技术选型
- 开发框架：IntelliJ Platform Plugin SDK
- 构建工具：Gradle
- 语言：Java
- PSI体系：基于IntelliJ PSI自定义DSL文件解析树
- 检测引擎：Annotator + LocalInspectionTool双通道
- 表达式解析器：独立的双模式表达式解析器（NumericExprParser + StringExprParser），与PSI体系并行但共享诊断输出通道
- 规则库数据源：`docs/themes_engine_next/`目录下的爬取规范页面（82页、437节），包含元素定义、属性表、支持范围矩阵、约束说明与示例代码

1.3 检测策略
- 实时检测：通过Annotator实现，编辑即触发，仅检测当前文件可见范围
- 扩量检测：通过LocalInspectionTool实现，可全项目扫描，支持批量Quick Fix
- 异步与增量：所有检测任务在后台线程执行，采用增量解析策略，仅重分析变更部分
- 规则来源：每个诊断明确引用DSL规范中的具体章节/条款，提供可追溯的规则依据
- 规则库基于预定义的DSL规范规则进行检查，以数据驱动方式定义
- 规则库数据来源：`docs/themes_engine_next/`目录中的规范页面，包含元素定义（如Button、Var、Command等）、属性参数说明表、支持范围矩阵（5个应用位置）、约束注意事项、函数签名表及代码示例。规则库应从这些页面自动提取或手动整理生成。

2. 核心模块设计

2.1 语法分析模块
- 自定义PSI Tree结构，定义各DSL元素的NodeType
- Parser定义完整的DSL语法规则，生成PSI Tree
- 语法错误通过PSI构建过程中的ErrorElement标记
- 基础XML语法分析（标签未闭合、嵌套错误、属性引号缺失）利用IDEA内置XML PSI API完成

2.2 语义分析模块
- 规则库：以数据驱动方式定义所有元素的约束规则（必填属性、类型、枚举值等）
- 上下文分析：根据元素层级关系（父子、继承）进行约束检查
- 语义相似度匹配：对未知元素/属性提供最接近的合法建议

2.2.1 表达式验证
DSL包含两套独立的表达式系统，需分别进行语法与语义验证：

**数字表达式（NumericExpression）验证：**
- 返回值类型：float
- 变量引用语法：`#varName`（引用数值型变量）
- 数组引用语法：`#arrName[expression]`（索引支持表达式解析）
- 支持运算符：`+`、`-`、`*`、`/`、`%`（算术运算，`+`为加法）
- 支持函数（含参数数量约束）：
  - 单参数：sin, cos, tan, asin, acos, atan, sqrt, abs, int, round, digit, not, len, isnull
  - 双参数：eq, ne, ge, gt, le, lt, min, max, pow
  - 三参数及以上：ifelse(x1,y1,x2,y2,...,z)（至少3个参数）
  - 无参数：rand()
- 关键语法规则验证：
  - `-#varName`模式检测：禁止负号直接前缀变量引用（如`-#w`），应写成`-1*#varName`或`0-#varName`
  - 数值精度警告：当数值超过7位时发出精度问题警告，建议范围在7位及以内
  - 变量引用验证：`#varName`必须引用已定义的数值型变量或已知全局数值变量
  - 函数参数数量验证：检测函数调用的参数数量是否符合签名要求（如eq必须2参数，ifelse至少3参数）

**字符串表达式（StringExpression）验证：**
- 返回值类型：string
- 变量引用语法：`@varName`（引用字符串型变量）、`#varName`（引用数值型变量）
- 数组引用语法：`@arrName[expression]`（索引支持表达式解析）
- 运算符：`+`（字符串拼接，非数值加法）
- 嵌套数字表达式语法：`{numericExpr}`（花括号包裹）
- 字符串常量必须使用单引号：如`'hello'`，双引号不合法
- 支持函数（含参数数量约束）：
  - substr(str, start, length) — 3参数
  - strIsEmpty(str) — 1参数
  - strIndexOf(str1, str2) — 2参数
  - strLastIndexOf(str1, str2) — 2参数
  - strContains(str1, str2) — 2参数
  - strReplaceAll(str1, str2, str3) — 3参数
  - preciseeval(str, digits) — 2参数（注意：preciseeval后不能使用其他运算符和+连接符）
  - formatDate(format, timeVar) — 2参数
  - plus(a, b) — 2参数（返回整数的和）
  - ifelse(x1,y1,x2,y2,...,z) — 至少3参数（xi为数字表达式，yi/z为字符串结果）
  - strEqual(str1, str2) — 2参数
  - argb(a, r, g, b) — 4参数
- 关键语法规则验证：
  - 字符串常量单引号检测：字符串表达式中的字符串必须使用单引号
  - `+`语义歧义检测：在字符串表达式中`+`为拼接而非加法，检测可能混淆的数值加法用法
  - `#varName*10`模式检测：字符串表达式以`#varName`开头后接运算符时会被误认为变量名（如`#num*10`被解析为名为"num*10"的变量），正确写法为`10*#num`
  - `{numericExpr}`嵌套验证：花括号内必须是合法的数字表达式
  - 变量引用验证：`@varName`必须引用已定义的字符串型变量或已知全局字符串变量；`#varName`必须引用数值型变量

2.2.2 作用域（Scope）验证
每个DSL元素在5个应用位置上有明确的支持矩阵，根元素决定当前文件的应用位置：

**应用位置定义：**
- Lockscreen（锁屏） — 根元素 `<Lockscreen>`
- Wallpaper（桌面） — 根元素 `<Wallpaper>`
- Widget（百变卡片） — 根元素 `<HwWidget>`
- ChargingSkin（充电动效） — 根元素 `<ChargingSkin>`
- LongTake（一镜到底） — 根元素 `<LongTake>`

**检测逻辑：**
- 从DSL文件根元素标签识别应用位置
- 查询规则库中该元素的支持范围矩阵，判断当前应用位置是否支持
- 当元素在不支持的应用位置中使用时，生成错误诊断
- 示例：Button仅支持Lockscreen✓和Widget✓，在Wallpaper中使用Button应报错

**典型不支持组合（来自规范页面）：**
| 元素 | Lockscreen | Wallpaper | LongTake | Widget | ChargingSkin |
|------|-----------|-----------|----------|--------|-------------|
| Button | ✓ | ✗ | ✗ | ✓ | ✗ |
| Command | ✓ | ✓ | ✗ | ✓ | ✓ |
| ExternCommand | ✓ | ✗ | ✗ | ✗ | ✓ |
| StyleCommand | ✓ | ✓ | ✗ | ✓ | ✗ |
| VideoCommand | ✓ | ✓ | ✗ | ✓ | ✓ |
| NumericExpression | ✓ | ✓ | ✓ | ✓ | ✓ |
| StringExpression | ✓ | ✓ | ✓ | ✓ | ✓ |

2.2.3 全局变量验证
引擎预置了一系列固定名称的全局变量，需验证引用的正确性与约束：

**全局变量分类：**
- 触摸类：touch_x, touch_y, touch_begin_x, touch_begin_y, touch_begin_time
- 解锁类：name.move_x, name.move_y, name.move_dist, name.state
- 时间日期类：year, month, date, day_of_week, hour, hour12, hour24, minute, ishour12, lunarYear, lunarMonth, lunarDay, system.time.hour1/hour2/min1/min2/ampm
- 电量类：battery_level(数值), battery_state(数值)
- 深色模式：darkMode(数值)
- 屏幕宽高：screen_width(数值), screen_height(数值)
- 图片宽高：name.actual_w, name.actual_h
- 文本宽高：name.text_width, name.text_height
- 组件属性：name.visibility, name.actual_x/y, name.actual_w/h
- 视频状态：src.state, src.currentTime
- 灭屏时间：screenOnLeftTime
- AI语音：matchSkill_value(字符串)
- 设备间隔：public_deviceUsageIntervalTime
- 碰一碰：enableCollaboration（仅Widget）
- 情绪感知：emotionValue
- 隔空手势：dynamicSwingValue, staticSwingValue
- 场景感知：Scenarios.ID.text(字符串), Scenarios.ID.jumpable(数值), Scenarios.ID.appName(字符串), Scenarios.topId(字符串)

**验证规则：**
- 全局变量名匹配验证：`#varName`和`@varName`引用的变量名必须匹配已知全局变量名或自定义变量名
- 类型匹配验证：数值型全局变量必须用`#`引用，字符串型全局变量必须用`@`引用（如`@ishour12`为字符串，`#battery_level`为数值）
- 禁止persist约束：针对时间、日期、星期相关变量（year/month/date/day_of_week/hour/minute/system.time.*等），禁止使用persist/globalPersist/styleGlobalPersist属性
- 应用位置限制验证：如enableCollaboration仅限Widget，matchSkill_value仅限特定场景

2.3 表达式解析器设计

表达式解析器是独立于PSI体系的子模块，负责解析属性值中的表达式文本。因DSL表达式语法与XML属性值语法不同，需独立的Tokenizer和Parser。

**双模式架构：**
```
ExpressionAnalyzer
├── NumericExprParser（数字表达式解析器）
│   ├── NumericExprLexer → Token流（运算符、函数名、变量引用#varName、数组引用#arr[expr]、数值常量、括号）
│   ├── NumericExprParser → AST（算术表达式树、函数调用树、变量引用节点）
│   └── NumericExprValidator → 诊断（语法检查、函数参数数量、变量存在性、-#var模式、精度警告）
├── StringExprParser（字符串表达式解析器）
│   ├── StringExprLexer → Token流（运算符+、函数名、变量引用@varName/#varName、数组引用@arr[expr]、字符串常量'...'、花括号嵌套{expr}）
│   ├── StringExprParser → AST（拼接表达式树、函数调用树、花括号嵌套数字表达式节点）
│   └── StringExprValidator → 诊断（语法检查、单引号字符串、+语义、函数参数数量、变量存在性、嵌套表达式合法性）
```

**触发时机：**
- 当属性声明支持表达式时（如expression、paras、x、y、w、h、visibility、condition等），调用对应类型的表达式解析器
- NumericExpression适用于类型为数值且支持表达式的属性
- StringExpression适用于类型为字符串且支持表达式的属性（如textExp、srcExp）
- 从属性元数据（规则库）确定表达式类型，自动选择解析器

**解析与验证流程：**
1. 从规则库获取属性的"表达式类型"标记（numeric/string/none）
2. 将属性值文本传入对应ExpressionParser
3. Parser执行词法分析→语法分析→构建表达式AST
4. Validator遍历AST进行语义检查：
   - 变量引用存在性检查（#/@varName → 查询自定义变量表 + 全局变量目录）
   - 函数签名检查（函数名 + 参数数量）
   - 语法规则检查（-#var模式、单引号、+语义、花括号嵌套）
   - 精度警告（数值>7位）
5. 输出诊断信息，附加规则来源引用

2.4 Trigger/Command链分析模块

Trigger-Command链是DSL的核心交互机制，需进行结构和语义层面的约束检查。

**Trigger宿主元素：**
- `<Button>` — 按钮触发
- `<Unlocker>` — 解锁触发
- `<Slider>` — 滑动触发
- `<Var>`（threshold属性） — 变量阈值触发
- `<ExternalCommands>` — 外部命令触发（开屏resume/关屏pause）

**Trigger action类型：**
- down（按下）
- up（抬起）
- double（双击）
- click（点击）
- long（长按）
- resume（亮屏触发）
- pause（熄屏触发）

**Command类型及其约束：**

| Command类型 | 关键约束 |
|-------------|---------|
| Command | target格式为`name.property`，目前仅支持visibility和animation属性 |
| VariableCommand | **不支持persist属性**；expression中不能用表达式（如#countNum+5）作为变量定义的expression（仅赋值时可用）；name必须引用已定义变量 |
| VideoCommand | **play与sound互斥**：当存在sound参数时不能使用play参数；反之无sound参数时才能使用play |
| SoundCommand | 音频文件大小限制1MB；声音与播放互斥场景（锁屏来电等冻结场景不执行） |
| ExternCommand | 仅支持unlock命令；仅在锁屏(Lockscreen)和充电动效(ChargingSkin)有效 |
| IntentCommand | 一次只能跳转一个应用；不能跳转二级页面 |
| StyleCommand | index不支持表达式；切换耗时1.5-2秒需避免频繁切换；搭配styleGlobalPersist |
| GroupCommands | method缺省值"perform"；params传入Trigger action名称 |
| CycleCommand | 与Array配合使用；indexFlag必填；frequency与begin/end互斥（frequency优先） |
| VisibilityCommand | visibility支持表达式，true/>0可见，false/≤0不可见 |

**检测逻辑：**
1. 结构检查：Trigger必须位于合法宿主元素内（Button/Unlocker/Slider/Var(threshold)/ExternalCommands）
2. Command类型合法性：Trigger内只能包含已知Command类型子元素
3. VideoCommand互斥检查：同一VideoCommand元素中play和sound属性不能同时存在
4. VariableCommand persist检查：VariableCommand不能使用persist属性
5. Var persist禁止检查：时间日期星期相关变量禁止使用persist/globalPersist/styleGlobalPersist
6. ExternCommand作用域检查：unlock命令仅在Lockscreen和ChargingSkin根元素下有效
7. Trigger action合法性：action值必须在宿主元素支持的action列表中

2.5 Quick Fix模块
- 每种诊断类型对应一个IntentionAction / QuickFixAction
- 修复策略包括：删除、替换为建议值、补全必填属性、修正嵌套关系
- 修复策略明细：
  - 补闭合标签、补属性引号、删除多余结束标签
  - 插入必填属性占位值或默认值
  - 数字/布尔/路径格式归一化
  - 根据编辑距离和知识库候选替换组件名（需确认）
  - 替换为别名属性、删除属性、转为通用属性（需确认）
  - 单位换算或删除错误单位（需确认）
  - 替换为最接近合法枚举值（需确认）
  - clamp到合法范围（需确认）
  - 表达式修复：`-#varName` → `-1*#varName` 或 `0-#varName`
  - 字符串表达式修复：双引号字符串 → 单引号字符串
  - VideoCommand互斥修复：删除冲突的play或sound属性（需确认）
  - VariableCommand persist修复：删除persist属性（需确认）
  - Var persist禁止修复：删除时间日期变量的persist/globalPersist/styleGlobalPersist属性（需确认）
- 需确认类修复通过IntentionAction弹窗让用户选择确认后执行

2.6 组件关系建模
- 定义DSL元素的父子关系约束树
- 定义组件继承关系及其属性传递规则
- 属性上下文约束：同一元素在不同父级下拥有不同的合法属性集
- Trigger宿主关系建模：定义哪些元素可以包含Trigger子元素

2.7 DSL文件识别模块
- 双重识别机制：文件扩展名（.xml）+ 根节点声明
- 非DSL XML文件不默认触发Theme Engine规则检查，需通过FileType或根元素过滤
- 根元素识别应用位置：Lockscreen/Wallpaper/HwWidget/ChargingSkin/LongTake → 确定Scope矩阵

2.8 悬浮说明与依据追踪模块
- 在IDEA工具提示（DocumentationTooltip）中集成详细诊断信息
- 显示内容：错误摘要、建议修复、当前组件/属性说明、来源文档链接、规则置信度
- 表达式相关诊断附带函数签名说明与规范示例

2.9 批量检查模块
- 通过菜单选项和快捷键触发批量检查
- 检查范围：当前文件、当前目录、整个项目
- 报告导出格式：Markdown/JSON
- 报告内容按error/warning/info分组，包含文件路径、行列号、诊断code、修复建议、规则来源

3. 规则库数据源设计

3.1 规则来源
规则库数据来源于`docs/themes_engine_next/`目录，该目录包含从华为开发者官网爬取的规范页面：
- 82个规范页面，437个章节
- 每个页面包含：功能概述、支持范围矩阵、XML规范、参数说明表、约束注意事项、应用示例
- 页面覆盖：基础功能（视图/控件/变量/命令/表达式）、2D基础动效、2D高级动效、3D高级动效、一镜到底、注意事项

3.2 规则提取策略
- 自动提取：从规范页面的Markdown结构化数据（表格、代码块、注意事项标注）中自动解析规则
- 手动补充：对注意事项页面中的隐含规则（如`-#var`禁止、精度限制、persist约束等）手动录入规则库
- 规则分类存储：
  - 元素定义规则：标签名、属性列表、属性类型、必填/选填、枚举值
  - 支持范围矩阵：元素 × 5个应用位置的✓/✗映射
  - 函数签名规则：函数名、参数数量、参数类型、返回类型
  - 全局变量目录：变量名、类型（数值/字符串）、所属分类、约束（如persist禁止）
  - 约束规则：互斥约束（VideoCommand.play/sound）、禁止约束（VariableCommand不支持persist、时间变量禁止persist）
  - 注意事项规则：从precautions页面提取的通用/视图/变量/控件/命令/数据开放类规则

3.3 规则库格式
- 规则以声明式数据结构定义（JSON/Kotlin DSL），不硬编码
- 新增元素规则只需在规则库中追加条目，无需修改分析引擎代码
- 规则条目包含source_url字段，指向规范原始页面，实现规则可追溯

4. 扩展性设计

4.1 规则库扩展
- 规则以声明式数据结构定义（JSON/Kotlin DSL），不硬编码
- 新增元素规则只需在规则库中追加条目，无需修改分析引擎代码
- 规范页面更新时，可重新执行爬取与规则提取流程，增量更新规则库

4.2 检测类型扩展
- 诊断类型通过注册机制管理，新增检测类型只需实现对应Analyzer并注册
- Quick Fix通过IntentionAction注册机制扩展
- 表达式解析器可扩展新增函数签名（通过规则库更新）

4.3 表达式解析器扩展
- NumericExprParser和StringExprParser的函数签名表来自规则库，新增函数只需更新规则库
- 运算符扩展通过Tokenizer配置实现
- 新增表达式类型时，可注册新的ExpressionParser子类

5. 性能设计

5.1 响应时间目标
- 单文件实时检测响应时间 ≤ 50ms
- 全项目批量检查响应时间 ≤ 5s / 100文件
- 表达式解析单次耗时 ≤ 5ms（表达式通常短小）

5.2 性能保障措施
- 异步执行：所有分析任务提交至DumbService后台线程
- 增量分析：基于PSI增量解析，仅分析变更节点及其依赖范围
- 规则库缓存：规则库数据预加载并缓存，避免重复IO
- 表达式解析缓存：同一属性值未变更时跳过重复解析
- 全局变量目录常驻内存：预置全局变量目录作为不可变数据常驻

6. 兼容性

6.1 平台兼容
- 目标平台：IntelliJ IDEA 2024.1+
- 兼容范围：Ultimate / Community Edition

6.2 规范版本兼容
- 规范起始版本：HarmonyOS 5.0
- 规则库标注每个元素的起始规范版本，检测时可按目标版本过滤规则
- 部分属性标注更高起始版本（如IntentCommand.uri/type起始HarmonyOS 6.0），低版本目标时应报警告
