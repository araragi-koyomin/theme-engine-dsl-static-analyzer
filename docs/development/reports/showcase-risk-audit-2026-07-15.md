---
module_ids: [M0, M2, M4, M7, CLI, E2E]
doc_kind: report
status: active
created: 2026-07-15
---

# Showcase 风险与新缺陷审计

## 1. 执行摘要

本报告记录 2026-07-15 Showcase 准备期间发现的两项静态分析缺陷，并评估仓库已记录的未解决问题是否会影响现场演示。

- **FIX005（高）**：内置规则库共有 75 条 `RuleConstraint`；按当前 grammar、预处理器和上下文能力逐条比对，
  其中 30 条使用了当前 Rule DSL 不能完整表达的谓词或数据路径。**这不等于 30 个都应该成为核心功能**：
  复核后，25 条属于 DSL 文件内可静态确定的语义但需要不同实现层，2 条透明视频后缀检查属于可选领域检查，
  1 条需要主题包资源上下文，1 条与现有 ScopeAnalyzer 重复，1 条规则建模本身需要重新澄清。
  收窄后的最小 E2E 探针只保留 4 个明确的属性、范围和树语义，最新 Fat JAR 仍返回
  `0 diagnostics / exit 0`。
- **FIX006（高）**：逐条对照 `rule_sources.json` 的来源描述与 75 条 constraint 的诊断消息，发现至少 20 处明显语义错位，
  包括同一 Rule ID 被不同业务规则复用、编号描述整体错位，以及文档来源描述与 condition 检查对象不一致。
  即使 FIX005 让 condition 可执行，诊断仍可能链接到错误文档或用错误 Rule ID 表示另一条规则。
- **已登记的 FIX003（高）**：IntelliJ 插件规则服务没有加载函数签名库，而 `TypeAnalyzer` 在函数库为 null 时
  跳过全部类型诊断；CLI 与 LSP 的生产装配路径会加载函数库，因此三端可能不一致。
- **已登记的 FIX004（高）**：测试剧场审计原始发现 15 项 CRITICAL 与 34 项 HIGH 弱断言；FIX002 已关闭
  C1/C15 与相关 P6，但其余条目尚未重新建立剩余基线。因此仍不能仅用“949 tests green”推导所有规则均可执行、
  三端完全一致或不存在漏检。

## 2. 调查覆盖

### 已读与实测

- Rule DSL grammar：
  `feature/analysis/src/main/java/com/huawei/theme/analysis/core/ruledsl/grammar/DslRuleCondition.g4`
- Rule DSL 执行器：
  `feature/analysis/src/main/java/com/huawei/theme/analysis/core/ruledsl/DefaultRuleDslEvaluator.java`
- RuleConstraint 消费入口：
  `feature/analysis/src/main/java/com/huawei/theme/analysis/core/semanticanalysis/analyzers/ConstraintAnalyzer.java`
- 规则库：`feature/analysis/src/main/resources/rules/elements/**/*.json`
- CLI、IntelliJ、LSP 规则仓库装配路径。
- `docs/BACKLOG.md`、P0 known issues、测试剧场审计和变量引用实现报告。
- 使用 `main@3857eb7` 重新执行 `:feature:analysis:buildFatJar`，并运行三个 Showcase 脚本。

### 未覆盖

- 未进行主题资源打包、引擎运行、真机视觉、性能、功耗或设备兼容性验证。
- 未逐项确认 30 条能力错配规则的产品规格是否仍有效；本报告确认的是“内置 JSON 声明与执行器能力不一致”，
  不等价于确认每条规则都应该阻断发布。
- 未实现缺陷修复；本报告仅登记事实、复现方式、影响和候选验收标准。

## 3. 新发现缺陷

### FIX005：内置规则条件超出执行器能力

#### 3.1 现象

规则 JSON 已声明诊断，但满足违反条件的脚本没有产出对应 Rule ID，且 CLI 正常返回退出码 0。

复现文件：`showcase/script_rule_dsl_gap_probe.xml`。

探针只包含可由当前 XML 文本和 AST 确定、并且有官方规则依据的违反项：

| 元素 | 内置 Rule ID | 违反内容 | JSON 使用的当前不支持能力 |
|---|---|---|---|
| `Layer` | `SEM-3D-LAYER-001` | `z=8` 超出 `[-10,7]` | `parseFloat(...)`、负数字面量 |
| `Group` | `SEM-ATTR-005` | layered Group 最后一张图片缺少 `hybridMode` | `lastChildImage(...)`、前序兄弟查询 |
| `CollaborationCommands` | `SEM-COLLAB-001` | `collaborationId="BAD!"` 不符合四位字母数字契约 | `matchesPattern(...)` |
| `Translation` | `SEM-EFFECT-003` | `duration=500` 小于文档规定的 1000ms | `number(...)` |

实测命令：

```powershell
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 `
    -jar feature/analysis/build/cli/dsl-analyzer.jar `
    --format json --no-color showcase/script_rule_dsl_gap_probe.xml
```

实测结果：

```text
diagnostics: []
errors: 0
warnings: 0
exit: 0
```

#### 3.2 直接原因

当前 grammar 原生只支持：

- `element.attrs['name']`
- `element.tagName`
- `element.parent.tagName`
- `== != > < >= <= MATCHES IN NOT IN`
- 字符串、非负数字和 null 字面量
- `AND / OR / NOT`

`DefaultRuleDslEvaluator` 另有两个正则预处理特例：

- 只按直接子元素 `tagName` 计数的简单 `children.filter(...).size()`；
- `containsExpression(element.attrs['...'])`。

执行器没有通用函数注册表、文档级索引、兄弟节点查询、资源元数据、子元素属性过滤或 data-open 变量字典上下文。
同时，解析器移除了错误监听器，入口规则没有显式 `EOF`，异常路径返回 `false`。因此无效条件可能静默不命中，
也可能经 ANTLR 错误恢复后只按可解析前缀求值；两者都会使规则资产状态与实际执行状态不一致。

#### 3.3 “30 条未支持”的产品范围复核

判断标准以 PRD/TDD 为准：当前核心覆盖 XML/DSL 结构、表达式、类型、引用、作用域以及可声明式约束；
TDD 明确将复杂 Trigger/Command、上下文依赖和资源约束交给 Analyzer。是否属于“静态分析”不由通用 IDE 是否内置同类检查决定，
而由结论能否从静态输入确定；但**属于静态分析不代表必须进入本项目核心门禁**。

下面的“依据”链接指向仓库内置的 theme-engine-next 文档快照。原始文档大多没有细粒度 Markdown 标题，
因此链接文字同时标出本次快照中的关键行号，便于打开后定位和复核；行号不是规则契约的一部分。

| # | 元素 / Rule ID | theme-engine-next 依据 | 静态输入是否足够 | 复核结论 | 合理承载方式 |
|---:|---|---|---|---|---|
| 1 | CollaborationCommands / `SEM-COLLAB-001` | [碰一碰：collaborationId 格式（L62）](../../themes_engine_next/raw_markdown/themes-engine-next-base-collaboration-0000002489842474.md) | 当前属性足够 | 核心 DSL 契约，应保留 | 将 `matchesPattern` 改写为已有 `MATCHES` |
| 2 | CollaborationCommands / `SEM-COLLAB-002` | [碰一碰：每脚本单实例（L87）](../../themes_engine_next/raw_markdown/themes-engine-next-base-collaboration-0000002489842474.md) | 当前文档足够 | 项目内一致性，可保留但不应强行塞进局部 Rule DSL | DocumentAnalyzer 统计节点 |
| 3 | SoundCommand / `SEM-CMD-002` | [声音命令：音频小于 1 MB（L7）](../../themes_engine_next/raw_markdown/themes-engine-next-base-soundcommand-0000002471395052.md) | 仅 XML 不够，需要读取音频文件 | 扩展的主题包静态检查，不属于当前单文件核心 | ResourceIndex + 可选 ResourceAnalyzer |
| 4 | StyleCommand / `SEM-CMD-003` | [全景换肤：关联 Var 与切换频率（L51-52）](../../themes_engine_next/raw_markdown/themes-engine-next-base-stylecommand-0000002471235086.md) | 规则意图不清；官方文档约束的是关联 Var 的 `styleGlobalPersist` | 规则建模有误，先撤出门禁并重新抽取规格 | PHASE 1 重新澄清，不直接扩 grammar |
| 5 | VibrateCommand / `SEM-VIBRATE-002` | [线性振动：define=true 必须有 Vibrate（L79、L86）](../../themes_engine_next/raw_markdown/themes-engine-next-vibratecommand-0000002499411342.md) | 当前子树足够 | 核心结构语义，应保留 | TreeAnalyzer 或 typed `hasChild` |
| 6 | Wave / `SEM-VIBRATE-003` | [线性振动：Slice 最大 64 个（L96-100）](../../themes_engine_next/raw_markdown/themes-engine-next-vibratecommand-0000002499411342.md) | 当前子树足够 | 核心结构/数量语义，应保留 | TreeAnalyzer 或 child-count predicate |
| 7 | Button / `SEM-SCOPE-001` | [按钮：支持范围矩阵（L17-19）](../../themes_engine_next/raw_markdown/themes-engine-next-base-button-0000002471395018.md) | 根场景 + scope 矩阵足够 | 与现有 ScopeAnalyzer 重复，应删除该 constraint | 只保留 scope 数据和 ScopeAnalyzer |
| 8 | ExternalCommands / `SEM-EXTCMD-001` | [注意事项：pause/stop 生命周期建议（L9、L19）](../../themes_engine_next/raw_markdown/themes-engine-next-precautions-0000002504275099.md) | 当前子树足够 | 来自注意事项的生命周期建议，适合 warning/可配置 | TreeAnalyzer；不作为默认阻断 error |
| 9 | BluetoothBattery / `SEM-BT-001` | [蓝牙耳机数据：使用前声明节点（L27-30）](../../themes_engine_next/raw_markdown/themes-engine-next-base-bluetoothbattery-0000002471235108.md) | 当前文档的引用和元素集合足够 | DSL 声明依赖，可保留 | DocumentAnalyzer + 引用索引 |
| 10 | Calendar / `SEM-CAL-001` | [日历数据：Constellations 先声明 ID（L34-39、L158-161）](../../themes_engine_next/raw_markdown/themes-engine-next-calendar-0000002531331269.md) | 当前文档足够 | DSL 声明依赖，可保留 | DataOpenAnalyzer |
| 11 | Calendar / `SEM-CAL-002` | [日历数据：Almanac 字段表（L50-86）](../../themes_engine_next/raw_markdown/themes-engine-next-calendar-0000002531331269.md) | Calendar 子 Var 名称足够 | 数据字段合法性，可保留 | 版本化字段字典 + DataOpenAnalyzer |
| 12 | Calendar / `SEM-CAL-003` | [日历数据：Constellations 字段表（L92-105）](../../themes_engine_next/raw_markdown/themes-engine-next-calendar-0000002531331269.md) | Calendar 子 Var 名称足够 | 数据字段合法性，可保留 | 版本化字段字典 + DataOpenAnalyzer |
| 13 | MediaCommand / `SEM-MEDIA-001` | [音乐数据：MediaCommand 依赖 MediaController（L13-15）](../../themes_engine_next/raw_markdown/themes-engine-next-base-mediacontroller-0000002471235098.md) | 当前文档足够 | 元素存在性，可保留 | DocumentAnalyzer |
| 14 | MediaCommand / `SEM-MEDIA-002` | [音乐数据：like/dislike 仅华为音乐（L67、L82-86）](../../themes_engine_next/raw_markdown/themes-engine-next-base-mediacontroller-0000002471235098.md) | 当前文档足够 | 跨节点属性关联，可保留 | DocumentAnalyzer |
| 15 | MediaController / `SEM-MEDIA-001` | [音乐数据：控制器前置声明总则（L7-23）](../../themes_engine_next/raw_markdown/themes-engine-next-base-mediacontroller-0000002471235098.md) | 当前文档足够 | 与 MediaCommand/MediaIcon 同一依赖规则，应合并实现 | 一个 MediaDependencyAnalyzer |
| 16 | MediaIcon / `SEM-MEDIA-001` | [音乐数据：MediaIcon 依赖 MediaController（L17-23）](../../themes_engine_next/raw_markdown/themes-engine-next-base-mediacontroller-0000002471235098.md) | 当前文档足够 | 同上 | 一个 MediaDependencyAnalyzer |
| 17 | SensorBinder / `SEM-SENSOR-001` | [传感器：Variable 的 name/index（L28-32、L46）](../../themes_engine_next/raw_markdown/themes-engine-next-base-sensorbinder-0000002504354969.md) | 当前子树足够 | 子变量结构语义，可保留 | DataOpenAnalyzer 或 typed child predicate |
| 18 | SensorBinder / `SEM-SENSOR-002` | [传感器：index 方向示例（L55-60、L79-84）](../../themes_engine_next/raw_markdown/themes-engine-next-base-sensorbinder-0000002504354969.md) | 当前子树足够 | 子变量枚举语义，可保留 | DataOpenAnalyzer 或 typed child predicate |
| 19 | VariableFramerate / `SEM-VFR-001` | [可变帧率：目前只改变一次（L5）](../../themes_engine_next/raw_markdown/themes-engine-next-base-variableframerate-0000002504354979.md) | 当前子树足够 | 官方“只支持改变一次帧率”，可保留 | child-count predicate |
| 20 | Weather / `SEM-WEATHER-001` | [天气数据：变量命名与 today/yesterday/tomorrow（L5、L28-47）](../../themes_engine_next/raw_markdown/themes-engine-next-base-weather-0000002504275029.md) | Weather 子 Var 名称足够 | 数据字段合法性，可保留 | 版本化字段字典 + DataOpenAnalyzer |
| 21 | Weather / `SEM-WEATHER-002` | [天气数据：无昨日/明日数据字段（L58、L61、L68-71）](../../themes_engine_next/raw_markdown/themes-engine-next-base-weather-0000002504275029.md) | Weather 子 Var 名称足够 | 日期维度合法性，可保留 | 版本化字段字典 + DataOpenAnalyzer |
| 22 | MeshImage / `SEM-EFFECT-001` | [Translation 结构（L28-31）](../../themes_engine_next/raw_markdown/themes-engine-next-2da-meshimagetrans-0000002471235150.md)、[SinMotion 结构（L28-30）](../../themes_engine_next/raw_markdown/themes-engine-next-2da-meshimagesinmotion-0000002504355009.md) | 当前子树足够 | 子元素存在语义可保留；官方分别给出两种结构，是否“至少一个”仍需规格化 | TreeAnalyzer |
| 23 | MeshImage / `SEM-EFFECT-002` | [Translation 结构（L28-31）](../../themes_engine_next/raw_markdown/themes-engine-next-2da-meshimagetrans-0000002471235150.md)、[SinMotion 结构（L28-30）](../../themes_engine_next/raw_markdown/themes-engine-next-2da-meshimagesinmotion-0000002504355009.md) | 当前子树足够 | 互斥语义可静态检查，但原文未直接写“不可同时存在”，需先补规则规格 | TreeAnalyzer |
| 24 | Translation / `SEM-EFFECT-003` | [网格位移：duration 最小 1000 ms（L57）](../../themes_engine_next/raw_markdown/themes-engine-next-2da-meshimagetrans-0000002471235150.md) | 当前属性字面量足够；表达式不做常量折叠 | 字面量范围属于核心；表达式仅做类型检查 | 直接数值比较 + signed literal，或 LiteralRangeAnalyzer |
| 25 | Translation / `SEM-EFFECT-004` | [网格位移：repeat 最小 -1（L58）](../../themes_engine_next/raw_markdown/themes-engine-next-2da-meshimagetrans-0000002471235150.md) | 当前属性字面量足够；表达式不做常量折叠 | 字面量范围属于核心；表达式仅做类型检查 | 直接数值比较 + signed literal，或 LiteralRangeAnalyzer |
| 26 | Group / `SEM-ATTR-005` | [视图组：layered/hybridMode 顺序（L49）](../../themes_engine_next/raw_markdown/themes-engine-next-base-group-0000002504354879.md) | 当前兄弟序列足够 | DSL 树语义，可保留 | SiblingAnalyzer |
| 27 | Layer / `SEM-3D-LAYER-001` | [多层空间：z 范围与顺序（L72）](../../themes_engine_next/raw_markdown/themes-engine-next-3d-multilayer-0000002490002442.md) | 字面量足够；表达式值不可静态求值 | 字面量范围属于核心 | 去掉 `parseFloat`；LiteralRangeAnalyzer |
| 28 | SourceImage / `SEM-SRCIMG-002` | [帧解锁：unlockTo 需要 Button 区域（L42、L48）](../../themes_engine_next/raw_markdown/themes-engine-next-base-sourceimage-0000002504274941.md) | 当前祖先/兄弟树足够 | DSL 结构语义，可保留 | TreeAnalyzer |
| 29 | Video / `SEM-VID-002` | [视频：透明视频只支持 mp4（L43、L47）](../../themes_engine_next/raw_markdown/themes-engine-next-base-video-0000002504354849.md) | `src` 是字面量时足够；`srcExp` 不可保证 | 有官方依据，但优先级和默认 severity 属于产品决策 | 字符串 suffix predicate；表达式值跳过；建议 opt-in warning |
| 30 | Video / `SEM-VID-003` | [视频：透明与全屏不可联用（L47）](../../themes_engine_next/raw_markdown/themes-engine-next-base-video-0000002504354849.md) | 当前属性足够 | 有官方依据，但当前 condition 实际仍在检查后缀，与 message 不一致 | 修正为属性组合判断；建议 opt-in warning |

复核结果按 condition 数量统计：

- **25 条**：属于 XML/DSL 文件内可确定的静态语义，但应分别落到局部 Rule DSL、TreeAnalyzer、DocumentAnalyzer 或 DataOpenAnalyzer。
- **2 条**：透明视频后缀是有效的领域静态检查，但优先级和 severity 应由产品决定，不必模拟通用 IDE 的默认检查集合。
- **1 条**：SoundCommand 文件大小需要主题包资源上下文，放到后续可选 ResourceAnalyzer。
- **1 条**：Button scope 与现有 ScopeAnalyzer 重复，应删除。
- **1 条**：StyleCommand 规则建模与官方文档对象不一致，应先澄清，而不是扩展执行器去兼容错误模型。

#### 3.4 已实现 45 条 constraint 的越界/过严抽查

已能被当前 evaluator 执行，不代表 severity 和建模一定合理。逐条阅读后发现以下值得修正的类型：

| 问题 | 当前例子 | 为什么不合适 | 建议 |
|---|---|---|---|
| 引擎会自动修正，却按 error 阻断 | 5 个元素上的 `SEM-ATTR-001`：alpha 超出 0-255 | 官方通用属性文档写明小于 0 取 0、大于 255 取 255；这是可疑配置，不是必然运行失败 | 默认 warning；严格项目可覆盖为 error |
| 条件缺少适用前提，可能误报 | `SEM-EXTERN-001` 对所有 ExternCommand 的 delay>3000 报错 | 3 秒上限来自“延时解锁”页面，只应作用于 `command="unlock"` | condition 增加 command 判定，或拆成 DelayUnlockAnalyzer |
| constraint 绑定到不存在的属性 | StyleCommand 的 `SEM-PERSIST-003` 读取 `element.attrs['styleGlobalPersist']` | StyleCommand JSON 不允许该属性；官方文档说的是与它搭配的 Var | 删除该 constraint，合并到重新设计的 StyleCommand/Var 关联规则 |
| 与结构元数据重复 | ParticleView `SEM-EFFECT-PV-001`、Swiper `SEM-SWIPER-001` | `allowedParents` + ParentChildAnalyzer 已能判断父节点；再写 constraint 容易产生双诊断和双重维护 | 选择一个权威来源，优先保留结构元数据 |
| 建议性规则已正确使用 warning | ParticleView 尺寸、VariablePoint 帧率、MultiLayer 无效参数、Text 滚动附属属性 | 不一定导致脚本失败，但能减少无效配置、功耗或维护歧义 | 保持 warning，不进入默认阻断门禁 |

因此本次审计结论不是“实现更多检查越好”，而是：

1. 先保证已有规则可执行、可验证；
2. 再删除重复/错误建模；
3. 根据引擎行为校准 error/warning；
4. 最后才决定哪些领域检查进入默认规则包。

#### 3.5 影响

- **CLI/流水线**：可能给出 exit 0，形成 false negative。
- **插件/LSP**：三端共用 Core ConstraintAnalyzer，因此同一缺口会传播到所有前端。
- **规则扩展**：新增 JSON 并不等于新增了可执行规则，当前缺少加载时能力校验。
- **测试可信度**：只验证 JSON 能加载、constraint 数量或结果列表非 null，无法发现此问题。

#### 3.6 候选修复方向与验收证据

本节为建议，不代表已进入实现阶段。

1. 为 Rule DSL 增加独立的 compile/validate API；内置规则启动时必须 100% 编译成功。
2. grammar 入口增加 `EOF`，禁止只消费表达式前缀。
3. 不再把解析/能力错误当普通 `false`；至少产生可观察的规则加载失败或 internal diagnostic。
4. 建立 typed predicate registry，谓词声明所需上下文（element/document/resource/device）。
5. 对无法声明式表达的复杂规则使用专用 Analyzer，不继续写自然语言 condition。
6. 每个进入默认规则包的 Rule ID 至少具备一个 must-trigger 和一个 must-not-trigger golden fixture，并用 Fat JAR 子进程执行。
7. 默认门禁只启用“核心契约 error”；建议性、资源型和产品策略型规则使用 warning、opt-in pack 或独立 profile。

建议验收标准：

- 75/75 内置 constraint 在构建门禁中通过静态编译；
- `script_rule_dsl_gap_probe.xml` 的四类明确 DSL 语义 Rule ID 全部出现，或相应 JSON constraint 被明确迁移/撤销；
- 任意未知谓词、未知 value path、残留 token 都使规则包加载失败，不能返回“分析成功且 0 诊断”；
- CLI、IntelliJ、LSP 针对同一文件输出相同 Core Rule ID 集合。

### FIX006：Rule ID 与规则来源语义错位

#### 3.7 现象

`rule_sources.json` 负责 Rule ID 的分类、描述和文档链接，元素 JSON constraint 负责实际 condition、message 和 severity。
两者应表达同一条规则，但至少存在以下 20 处直接可见的错位：

| 类型 | Rule ID | 来源描述 | 实际 constraint/message |
|---|---|---|---|
| 属性缺失与格式校验混用 | `SEM-COLLAB-001` | 缺少 collaborationId | 校验四位字母数字格式 |
| scope 与文档计数混用 | `SEM-COLLAB-002` | 仅在百变卡片可用 | 一个脚本最多一个节点 |
| 顺序编号整体错位 | `SEM-VIBRATE-004/005/006` | required/intensity/type | 实际分别检查 time/duration/intensity 范围 |
| 不同业务含义 | `SEM-CMD-003` | StyleCommand 频繁切换 | 关联变量需要 styleGlobalPersist |
| 顺序编号整体错位 | `SEM-VIBRATE-001/002/003` | loop/scope/amplitudes | 实际检查 vibrateType/Vibrate 子标签/Slice 数量 |
| 结构与组合含义错位 | `SEM-EFFECT-001` | MeshImage 缺少 src/mesh | 必须有 Translation 或 SinMotion |
| 属性来源错位 | `SEM-EFFECT-003/004` | Translation/SinMotion 结构属性 | 实际检查 duration/repeat 范围 |
| 一组编号整体错位 | `SEM-3D-001~004` | w/h、Layer、staticCondition、stereoId | 实际检查四种 touchType 下的无效属性组合 |
| 同 Rule ID 表示两个规则 | `SEM-ATTR-005` | 来源指向 layered Group | 在 Image 上实际表示 isBackground + scaleType |
| 资源序列与数值位数混用 | `SEM-IMG-001` | 图片序列从 0 开始且无缺失 | 实际检查 number 小于 8 位 |
| 分辨率/全屏/后缀错位 | `SEM-VID-003/004` | 分辨率、缺少全屏标记 | 实际检查 mp4 后缀、透明与全屏互斥 |

这说明当前不能只问“condition 能不能执行”，还必须问：

```text
官方条款 == Rule ID == rule_sources 描述/URL == constraint condition/message/severity == golden 期望
```

任一环不一致，诊断就不可追溯。

#### 3.8 影响

- 用户点击规则文档可能看到与当前报错无关的条款。
- 流水线按 Rule ID 做豁免或趋势统计时会混合不同语义。
- 同一个 Rule ID 无法作为稳定契约，破坏 CLI/插件/LSP 的一致性承诺。
- 修执行器可能激活原本静默的错配规则，放大误报或错误文档链接。

#### 3.9 候选验收标准

- 每个 Rule ID 只表达一个稳定语义；不同元素可复用的前提是条款、message 模板和修复策略相同。
- 构建时校验 constraint Rule ID 必须存在唯一 source，并生成反向清单。
- 为每个 Rule ID 建立官方文档锚点、must-trigger 和 must-not-trigger。
- 对上述至少 20 项逐条决定：修正 source、重编号 constraint、合并重复规则或删除错误规则。
- 在 FIX006 完成前，不以当前 Rule ID 总数或来源链接完整度作为 Showcase 卖点。

## 4. 已登记缺陷对 Showcase 的影响

| 已知问题 | 现场击穿可能性 | 触发方式 | Showcase 应对 |
|---|---:|---|---|
| FIX005：constraint 能力错配且静默漏检 | 高（若观众使用当前未验证规则） | Layer.z、layered Group、Weather 等输入不产出预期 Rule ID | 只展示已 E2E 验证的 8 条诊断；不按 JSON 数量宣称覆盖率 |
| FIX006：Rule ID/source/condition 语义错位 | 高（若点击文档或追问 Rule ID 稳定性） | 例如 Image 的 SEM-ATTR-005 链接到 Group layered 条款 | 不展示规则来源完整性；说明正在建立条款到 golden 的一一追踪 |
| FIX003：TypeAnalyzer 在函数库为 null 时跳过全部类型诊断 | 高（若展示 IntelliJ 类型错误） | 插件 `RuleRepositoryService` 使用不带函数库的 repository 构造路径 | CLI 演示类型能力；插件只展示已验证的补全/基础诊断，避免宣称三端 Rule ID 完全一致 |
| FIX004：测试剧场剩余项尚未重建基线 | 高（若声称“949 测试证明无漏检”） | 观众提供未进入 golden 的真实脚本 | 说明原始审计规模与 FIX002 已关闭项；置信度证据使用具体 golden、Fat JAR E2E 和 clean/faulty 对照 |
| M-1：per-analyzer 异常在 quiet 下可能不可见 | 中低 | analyzer 抛异常且 CLI 使用 `--quiet` | 现场不用 quiet；流水线设计中要求 internal failure 可观察 |
| M-3：JaCoCo 0% | 中 | 被问代码覆盖率 | 不声称达到 80%；说明当前门禁缺少可信覆盖率证据 |
| M-4：mode fixture 命名误导 | 低 | 深挖测试命名 | 与现场功能无直接关系 |
| M-6：目录扫描不可读文件静默跳过 | 中 | 目录中存在无权限文件 | 不演示权限异常；流水线需核对 discovered/analyzed/skipped 文件数 |
| 变量导航已知限制 | 中（若观众现场点声明） | 在 Var 声明处 Ctrl+Click | 从引用位置演示跳转；声明处查看用法使用 Alt+F7 |
| Quick Fix UI、ToolWindow 尚未交付 | 高（若按 UI 成品宣传） | 观众寻找灯泡/面板 | Quick Fix 只展示 CLI `suggestedFixes` 数据，不宣称 IDE UI 已完成 |
| 无性能 benchmark | 高（若声称“大仓秒级”） | 追问 P95、文件规模、冷启动 | 明确当前只有功能证据，性能需建立基准后回答 |
| 无组织级 CI/CD 接入证据 | 高（若声称“已进入流水线”） | 追问具体 job、门禁和历史数据 | 使用“具备 JSON/exit code，可接入”表述，不说已经接入 |

## 5. Showcase 安全结论

### 可安全展示

- CLI clean 脚本：`0 diagnostics / exit 0`。
- CLI faulty 脚本：稳定产出 `5 errors / 3 warnings / exit 1`。
- 展开 `SEM-3D-001`、`SEM-SRCIMG-001`、`SEM-PERSIST-001` 三条已 E2E 验证规则。
- JSON 输出、退出码、suggested fixes，以及本次已验证的 8 个 Rule ID。
- 说明 CLI 可作为现有流水线的机器执行面，但不替代包、引擎和真机验证。

### 不应在现场作为成功能力展示

- `SEM-3D-LAYER-001`、`SEM-ATTR-005`（layered Group）、Weather/Calendar/Media 文档级约束。
- `SEM-VID-002/003` 和 `SEM-EXTCMD-001` 即使修复，也应先确认是否进入默认 profile 及其 severity。
- IntelliJ 插件中的函数参数/类型诊断与 CLI 完全一致。
- quiet 模式下的 analyzer 异常可见性。
- 大规模工程的性能数据、代码覆盖率达标、组织流水线已经落地。
- 主题包资源存在、视频格式/大小、真机效果或功耗已经由静态分析保证。
