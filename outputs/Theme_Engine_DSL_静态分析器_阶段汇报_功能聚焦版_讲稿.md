# Theme Engine DSL 静态分析器阶段汇报（功能聚焦版讲稿）

- 汇报对象：Huawei 技术骨干
- 主讲时长：10:00
- 主汇报：第 1–10 页；第 11–12 页供问答
- 代码快照：`main@e9e9bcd`
- 测试状态：71 suites，777/777 通过
- 汇报主线：已完成能力 → 端到端数据流 → CLI / Editor / LSP → 测试覆盖 → fixture 演示 → 后续功能与性能方向

## 演示准备

CLI 演示直接使用现有 Fat JAR，不需要重新构建 `antlr4-intellij-adaptor` 或 plugin：

```powershell
$jar = "feature/analysis/build/cli/dsl-analyzer.jar"

java -jar $jar --no-color `
  feature/analysis/src/test/resources/fixtures/e2e-pipeline/clean/lockscreen_valid.xml

java -jar $jar --format terminal --no-color `
  feature/analysis/src/test/resources/fixtures/e2e-pipeline/wallpaper_constraint_enum.xml

java -jar $jar --format json --no-color `
  feature/analysis/src/test/resources/fixtures/e2e-pipeline/wallpaper_constraint_enum.xml

java -jar $jar --format markdown --no-color `
  feature/analysis/src/test/resources/fixtures/e2e-pipeline/wallpaper_constraint_enum.xml
```

Editor 只自动接管 `script.xml` 和 `script_*.xml`。演示前把 fixture 复制到独立演示目录并重命名，不修改原测试资源。例如：

```powershell
$demo = "$env:TEMP\theme-dsl-editor-demo"
New-Item -ItemType Directory -Path $demo -Force | Out-Null

Copy-Item `
  "feature/analysis/src/test/resources/fixtures/e2e-pipeline/lockscreen_type_and_ref.xml" `
  "$demo/script_type_and_ref.xml"

Copy-Item `
  "feature/analysis/src/test/resources/fixtures/complex/expression_syntax_errors.xml" `
  "$demo/script_expression_errors.xml"
```

建议提前在 IDEA 中打开这两个副本，并准备一个 Terminal 窗口。

## 第 1 页｜开场（0:00–0:25）

今天不从门禁或发布流程讲起，而是直接回答三个技术问题：当前已经实现了什么，数据怎样在 Core、CLI、Editor 和 LSP 中走动，以及怎样用真实 fixture 现场看到这些能力。

本轮先完成了一项基础修正：此前 10 个失败测试来自生产策略变化后的旧断言。现在四个测试文件已经同步，完整测试恢复为 777/777。

转场：先看当前已经形成的整体能力。

## 第 2 页｜整体能力（0:25–1:15）

当前项目可以理解为“一个分析内核，多种使用方式”。

Core 已经具备文件识别、规则与函数加载、StAX AST、表达式 AST、符号表、九类 Analyzer 和 FixAction 模型。

CLI 已经在 main 上形成可直接运行的交付：支持单文件、目录、配置、自定义规则目录、Terminal、JSON、Markdown 和退出码。

Editor/PSI 也已进入 main：标签、属性和表达式补全，高亮与文档，实时诊断，变量跳转、Find Usages 和 Rename 都有生产实现。

LSP 是独立并行线，已经有 standalone server、诊断、补全、Hover、semantic tokens、配置热更新和 IntelliJ client；它不替代现有 Editor，也不影响 main 当前进度。

本轮实测是 71 个 suite、777 个测试全部通过；Pipeline E2E 19/19，CLI E2E 34/34；直接运行 JAR 扫描 e2e-pipeline 得到 33E/1W。

转场：下面沿一条数据流解释这些模块如何协同。

## 第 3 页｜端到端数据流（1:15–2:10）

数据从 DSL XML 开始。

第一步是文件识别：Core 同时检查 `.xml` 和规则库中的根标签，并加载 JSON 规则和函数签名。

第二步由 `AstBuilder` 使用 StAX 构建独立 `DslFileNode`。表达式属性会在这里继续进入 ANTLR，形成嵌入表达式 AST。

第三步构建 `SymbolTable`，把变量声明、作用域、类型和引用组织起来。

第四步由九类 Analyzer 和表达式检查器生成统一 `Diagnostic`；FixAction 继续附加修复建议。

最后才进入端侧：CLI 负责报告，Editor 把诊断映射回 PSI，LSP 把诊断转成协议对象。

以 `alpha="999"` 为例：规则判断值必须在 0–255，产生 `SEM-ATTR-001`。Editor 显示红线，CLI 输出报告，两端看到的是同一条诊断；修复建议也是同一来源。

转场：Core 里已经实际落地了哪些模块？

## 第 4 页｜Core 实现（2:10–3:00）

M0 有两套 grammar：一套解析主题表达式，一套解析声明式规则条件；函数库当前有 40 个签名。

M1 和 M2 完成文件识别与数据驱动规则库：116 个元素规则、58 个全局变量和 75 条声明式 constraint。

M3 使用安全 StAX 构建独立 AST，并记录行列与表达式节点。

M4 有九类 Analyzer，覆盖类型、引用、作用域、父子嵌套、枚举、必填属性和声明式约束。

M5 有六类 FixAction generator，诊断可以携带 `suggestedFixes`。

实际例子包括：`alpha=999` 的范围错误、`sin('1')` 的参数类型错误、重复变量的多位置定位、`-#base_val` 的表达式错误，以及 Layer 放在 Widget 下时同时出现 nesting 和 scope 诊断。

扩展方式已经明确：数据类规则可新增 JSON constraint，复杂逻辑可新增 Analyzer，修复策略可新增 FixActionGenerator。

转场：CLI 已经把这些结果交付成三种可直接消费的格式。

## 第 5 页｜CLI 实现与输出（3:00–4:00）

这一页展示的是 `widget_multi_violation.xml` 的真实输出，不是示意数字。

Terminal 面向本地开发：直接显示文件、行列、严重度、message、ruleId 和建议修复。例如 Button 缺少 Trigger 会给出 `SEM-TRIG-002`，并建议添加 Trigger 子元素。

JSON 保留结构化字段：`severity`、`line`、`col`、`ruleId`、`message`、`suggestedFixes` 和 summary。它适合脚本、平台或后续服务集成。

Markdown 按 Error、Warning、Info 分区，并生成文件汇总表，适合评审和归档。

三种格式来自同一个 `Diagnostic` 列表，因此 ruleId、位置和严重度保持一致。这个 fixture 实际得到 7 个 error，进程退出码为 1。

转场：在 Editor 中，同一结果转化为即时输入与代码理解体验。

## 第 6 页｜Editor/PSI 实现（4:00–5:05）

main 上的 Editor 先用 FileType 和 XML PSI 接管 `script.xml` 与 `script_*.xml`。

对表达式属性，插件会注入专用表达式语言，因此函数、全局变量、用户变量和局部变量可以独立补全和着色。

标签、属性、函数和变量都有悬浮文档；标签分类、表达式语义和 Core Diagnostic 分别由 Annotator 呈现。

变量引用支持 Ctrl+Click、Find Usages 和 Rename。诊断分析按 PSI modificationStamp 缓存，文件未变化时复用结果。

现场打开 `script_type_and_ref.xml`：先看两个 `dup_var` 声明的 `SEM-REF-003`，再看 `#undefined_var` 和 `alpha=300`，然后用一个合法变量演示跳转与 Rename。

这里有一个明确边界：Editor 的诊断 RuleRepository 当前没有加载函数库，因此函数类型诊断现场以 CLI 为准；Editor 演示使用 REF、ATTR 和字面量 TYPE-003 等确定能力。

转场：LSP 使用标准协议做同一 Core 的另一种适配。

## 第 7 页｜LSP 并行实现（5:05–5:55）

LSP 分支的链路是：Editor client 通过 stdio JSON-RPC 连接 server；文档使用 Full Sync，变更后以 300ms debounce 重新分析；server 调用同一 Core，再发布 diagnostics。

分支内已经实现 diagnostics、元素与属性补全、Tag Hover、semantic tokens、配置热更新和 IntelliJ client。

下一步是 `textDocument/codeAction`、标准 definition/references/rename、增量同步和 AST 复用，以及多编辑器协议级集成测试。

这条线路保持独立推进，不作为 main 上 Editor/PSI 的替代或阻塞项。

转场：接下来说明 777 个测试实际覆盖了什么。

## 第 8 页｜测试覆盖层次（5:55–7:05）

修复旧断言后，71 个 suite、777 个测试全部通过，没有 failure、error 或 skip。

按功能域拆分：批量、报告和 CLI 有 304 个；文件、AST 和表达式有 154 个；语义、类型和引用有 140 个；规则、函数和 Rule DSL 有 111 个；其余 68 个覆盖 FixAction、共享模型与 Editor lexer。

测试不只是一层：

第一层是组件测试，覆盖 grammar visitor、loader、Analyzer、Fix generator 和 formatter。

第二层是集成测试，用真实 rules/functions 贯通 AST、SymbolTable、Diagnostic 和 Fix。

第三层是 E2E：Pipeline 19/19、CliMain 34/34，验证三种格式、退出码、配置和非 DSL 跳过。

第四层是真实 fixture，把多个规则和表达式组合到同一文件中。

当前自动化的主要短板是 Editor：main 只有 1 个 lexer 回归测试，Annotator、Completion、Docs、Navigation 和 Rename 缺少 IntelliJ 平台级测试。LSP 分支有 22 个测试，但 client 生命周期、semantic tokens 和多编辑器协议还需要补齐。

转场：五组 fixture 可以把这些层次现场串起来。

## 第 9 页｜Fixture 演示（7:05–8:25）

建议演示顺序如下。

第一，`clean/lockscreen_valid.xml`：结果是 0E/0W。先证明合法文件不会被误报；Editor 里可以看 `sin(0)` 和 `#hour24` 着色，CLI 返回 exit 0。

第二，`wallpaper_constraint_enum.xml`：5E/1W。展示声明式 constraint、`src/srcExp` 冲突、alpha 范围、枚举和严重度，再连续切换 Terminal、JSON、Markdown。

第三，`lockscreen_type_and_ref.xml`：8E。展示重复声明、未定义引用、类型不匹配，并在 Editor 中演示红线、跳转和 Rename。

第四，`charging_skin_cmd_nest.xml`：8E。重点看父子嵌套、scope、命令互斥和两个候选修复建议。

第五，`complex/expression_syntax_errors.xml`：9E/2W。展示 `-#var`、精度、引号、花括号、ANTLR 和函数检查。

如果现场时间只有两分钟，保留 clean、wallpaper 三格式和 type/ref Editor 三步即可。

转场：最后看下一阶段如何把已有骨架做深。

## 第 10 页｜未来方向与收束（8:25–10:00）

功能闭环方面，优先把结构 `SyntaxChecker` 接入生产，初始化 FixActionRegistry，补 Editor IntentionAction、ToolWindow 和右键批量；同时完成 quiet、verbose、type-check 的真实语义，并让 Editor 加载与 CLI 相同的函数库。

LSP 与扩展方面，继续补 codeAction、标准导航与重命名、规则与函数热更新、Analyzer/Fix generator 自动发现和规则编辑器 UI。

性能方面，先建立单文件、100 文件和真实编辑延迟基线，再推进 Editor 后台/增量分析、表达式与 AST 缓存、LSP 增量同步和 AST 复用。

测试方面，补 IntelliJ 平台级行为、LSP 协议和 client 生命周期、main/LSP 共用契约、更多 clean/negative/真实主题仓以及性能回归。

最后收束为三句话：

第一，Core 已经把规则、AST、语义、诊断和修复模型串起来。

第二，CLI 和 Editor 已能把这些能力转成可使用、可演示的端侧体验；LSP 也已经跑通独立协议链路。

第三，下一阶段的重点是把用户可见功能做完整、把性能边界测清楚、把 Editor/LSP 自动化覆盖做深。

## 附录 A｜真实实现状态矩阵

用于回答“哪些设计已经进入代码”。

- M0–M2：主体已完成；热更新和规则编辑 UI 未完成。
- M3：StAX AST 和表达式嵌入已完成；结构 SyntaxChecker 未接生产。
- M4：九类 Analyzer 和符号表已完成；继承链、通用重复 ID 与完整引用仍是扩展项。
- M5：Core generator 与 Provider 已有；生产 registry 初始化和 Editor UI 未完成。
- M6：补全、高亮、文档、Annotator 已有；Quick Fix UI、ToolWindow、右键批量未完成。
- M7：批量与三格式报告已完成；模式、参数语义与性能基线继续推进。
- M8：跳转、Find Usages、Rename 已有；作用域精化和批量重命名预览继续推进。
- LSP：独立并行进行中。

## 附录 B｜测试修复与文档偏差

本轮测试修复只修改四个测试文件，没有修改生产代码：

- `TypeInferenceEngineTest.java`
- `SemanticAnalysisIntegrationTest.java`
- `TypeAnalyzerTest.java`
- `VarRefAnalyzerTest.java`

更新内容是函数签名跨上下文回退、`#elem.prop` 的 `SEM-REF-001` 分类，以及 `duplicateVarNames` 对所有重复声明的断言。定向 84 个测试和完整 777 个测试均通过。

文档需要同步的主要口径包括：SAX 改为真实 StAX、统一 PSI Adapter 与当前 `plugin/editor` 实现差异、CLI 模式和参数语义、Quick Fix/ToolWindow 完成度，以及 LSP semantic tokens、测试数量和并行路线说明。

## 可能问答

### 这 777 个测试能证明什么？

可以证明组件、Core 集成、批处理和进程内 CLI E2E 的既定契约全部通过。它不能替代 Editor 平台级行为测试，也不能替代 LSP 多客户端协议测试和真实性能基线。

### Fixture E2E 和普通单测有什么区别？

Fixture 使用真实规则、函数和 XML 文件，一次触发文件识别、AST、表达式、符号表、多 Analyzer、FixAction、报告和退出码。它验证的是组合链路，而不是单个方法。

### Editor 与 CLI 的结果现在完全一致吗？

规则、引用、范围、枚举、嵌套等主体诊断共享 Core。函数类型分析目前存在差异，因为 Editor 的诊断 RuleRepository 尚未加载函数库；这是下一阶段的明确功能项。

### LSP 是否会影响当前 Editor 进度？

不会。Editor/PSI 已在 main，LSP 在独立分支通过标准协议探索另一种集成方式，两者并行推进、分别验证。

### 现场为什么不直接展示 Quick Fix 一键执行？

Core 已经生成 suggestedFixes，CLI 会实际输出；但 Editor IntentionAction 和 Quick Fix UI 尚未接入，所以当前现场只展示建议内容，不把未完成 UI 描述为已交付。

### 后续测试为什么优先补 Editor 和 LSP？

Core/CLI 已有较完整的组件、集成和 E2E 层次，而 Editor 只有 1 个 lexer 测试，LSP 也缺少 client 生命周期与多客户端协议测试；这两端是当前覆盖最不对称的部分。
