# Theme Engine DSL 静态分析器 Showcase 备战手册

> 基线：`main@3857eb7`，2026-07-15 实测。面向 Theme Engine DSL 深度用户和技术骨干。

## 1. 你要证明什么

不要教 DSL，也不要证明静态分析能够保证主题运行。

你的核心命题是：

> 把主题开发者已经掌握的文档约束、组合语义和经验规则，转化为一套可重复执行、可在编辑器反馈、
> 可被流水线消费、并且能随着 Theme Engine DSL 演进而扩展的工程契约。

开场原话：

> 我今天不证明静态分析能保证主题可运行。它只把运行前可以确定的结构、规则、引用和类型问题稳定地拦在更早阶段。
> 资源完整性、主题打包、引擎运行、动画效果、功耗和真机兼容性仍由现有门禁负责。
> 我主要展示真实业务脚本中的组合语义、CLI 为什么不可替代，以及 DSL 继续复杂化后规则怎么扩展。

## 2. 10 分钟现场流程

| 时间 | 内容 | 你要得出的结论 |
|---|---|---|
| 0:00-0:45 | 上述边界与命题 | 不与运行时、真机门禁争职责 |
| 0:45-1:45 | 运行 clean 脚本 | 复杂合法脚本 0 诊断，先证明不乱报 |
| 1:45-4:30 | 运行 faulty 脚本，只展开 3 条 | 能识别组合语义和生命周期问题，不是拼写检查 |
| 4:30-5:40 | 切换 JSON 输出与退出码 | CLI 是机器执行面，可接流水线和质量平台 |
| 5:40-8:20 | 按 Theme Engine Next 分类讲扩展 | JSON 适合数据规则，Analyzer 处理上下文，语言演进改内核 |
| 8:20-9:20 | 开箱即用与规则包策略 | 默认规则内置，外部 JSON 是高级扩展，不是员工负担 |
| 9:20-10:00 | 主动交代边界 | 展示工程判断力，避免被追问击穿 |

演示脚本：

- `script_immersive_lockscreen_clean.xml`
- `script_immersive_lockscreen_faulty.xml`

稳定结果：

```text
clean  -> 0 errors, 0 warnings, exit 0
faulty -> 5 errors, 3 warnings, exit 1
```

只展开：

1. `SEM-3D-001`：MultiLayer 模式与参数组合冲突。
2. `SEM-SRCIMG-001`：帧解锁方向、循环和解锁帧组合不完整。
3. `SEM-PERSIST-001`：时间派生变量被持久化，生命周期变化后可能保留旧值。

总结句：

> 这些问题不是 XML 解析失败，也不是属性名拼错，而是单个属性都合法、组合起来不成立，或者当前能跑、生命周期变化后不可靠。

## 3. CLI 为什么不是插件的低配版

一句话：

> 插件解决单个开发者此刻看到什么；CLI 定义团队在任意环境中能否重复得到同一个结论。

对开发者：

- 批量扫描主题库，不必逐个在 IDE 打开。
- DSL/规则版本升级前做迁移影响评估。
- 固定命令复现缺陷，排除 IDE 与插件状态差异。
- 规则作者批量验证 must-trigger、must-not-trigger 和回归样例。
- 在远程机、容器或无 UI 环境排查脚本。

对工程系统：

- JSON 报告供 PR 评论、质量平台、趋势统计和自动修复消费。
- Rule ID 的设计目标是支持豁免、分级门禁和历史比较；当前来源映射存在 FIX006，只有完成一一对齐后才能称为稳定契约。
- 退出码 `0/1/2` 区分通过、发现 error、执行失败。
- 适合按“观察 → 提醒 → 只拦高置信 error”逐步接入现有流水线。

不要说：“静态分析通过就可以打包发布。”

应该画成：

```text
DSL 静态分析
  -> 资源完整性/主题打包
  -> 引擎运行与集成
  -> 真机视觉/性能/功耗/兼容性
```

## 4. 按 Theme Engine Next 分类讲“具体怎么扩展”

### 4.1 先用三个例子讲清扩展层级

不要先抛 JSON、Analyzer、AST 三个名词。先把输入并排给观众看。

#### 第一层：只看当前元素——改 JSON，复杂度低

```xml
<!-- 不合法：同一个 VideoCommand 同时控制播放和声音 -->
<VideoCommand name="ambient" play="1" sound="1"/>

<!-- 合法：拆成单一意图 -->
<VideoCommand name="ambient" play="1"/>
```

实现只需要当前元素的两个属性：

```text
play != null AND sound != null
```

这就是现有 `SEM-CMD-001`。新增同类规则时，在元素 JSON 的 `constraints` 中增加 condition、Rule ID、消息和修复建议，
再补一坏一好两个 golden。

#### 第二层：要看子节点或整份文件——加 Analyzer，复杂度中等

```xml
<!-- 不合法：MediaCommand 存在，但整份文件没有 MediaController -->
<Trigger action="click">
    <MediaCommand command="mediaLike"/>
</Trigger>

<!-- 合法：文件级依赖完整 -->
<MediaController packageName="com.huawei.hmsapp.music"/>
<Trigger action="click">
    <MediaCommand command="mediaLike"/>
</Trigger>
```

这里不能只看 `MediaCommand` 自己，必须先建立文档元素索引，再由 `MediaDependencyAnalyzer` 判断依赖。
JSON 可以保存字段和错误消息，但执行逻辑不应伪装成局部 condition。

#### 第三层：语言本身变了——改 grammar/AST/类型系统，复杂度高

```xml
<!-- 当前模型：扁平变量 -->
<Var name="user_age" type="number" expression="18"/>
<Text paras="#user_age"/>

<!-- 假设未来出现对象和成员访问 -->
<Var name="user" type="User" expression="new User()"/>
<Text paras="#user.profile.age"/>
```

第二种不是新增一个属性规则：需要解析 `.`、建立 `User` 类型、成员表、构造表达式、可见性、补全、跳转和重命名。
这是语言内核升级。

### 4.2 每次增加规则的固定步骤——以“透明视频只支持 MP4”为例

这个例子技术上能静态检查，但是否进入默认规则包是产品选择；它适合说明“先判断价值，再实现”。

1. **提取规格**

   文档写的是：普通 Video 支持 mp4/avi/mov，但 `isTransparent=true` 时只支持 mp4。

   ```xml
   <!-- 违反文档，但不一定要作为默认阻断项 -->
   <Video name="v" src="scene.mov" isTransparent="true"/>

   <!-- 满足文档 -->
   <Video name="v" src="scene.mp4" isTransparent="true"/>
   ```

2. **判断静态输入是否足够**

   `src="scene.mov"` 是字面量时可以判断；`srcExp="@video_path"` 无法在“不做常量折叠”的前提下保证后缀。

3. **确定产品级别**

   它是领域检查，不是 XML/类型错误。可以放在 opt-in profile 或 warning，而不是默认 error 门禁。

4. **选择实现层**

   增加受控的字符串 suffix predicate；不要为了一个规则开放任意 Java 风格方法调用。

5. **补正反测试**

   must-trigger：透明 + `.mov`；must-not-trigger：透明 + `.mp4`、非透明 + `.mov`、动态 `srcExp`。

6. **验证三端一致**

   同一 fixture 在 CLI、IntelliJ、LSP 应产生相同 Rule ID；否则不能发布进默认规则包。

这个流程的重点是：**可静态检查、值得默认检查、应该用哪层实现，是三个不同问题。**

### 4.3 基础视图与布局：`base-*` / `view` / `layout`

#### 例 1：新增属性或枚举——只改规则数据

假设下一版 Image 新增 `blendMode`，合法值为 `normal/multiply/screen`：

```xml
<!-- 不合法 -->
<Image src="bg.png" blendMode="magic"/>

<!-- 合法 -->
<Image src="bg.png" blendMode="multiply"/>
```

扩展位置：`Image.json` 的 `optionalAttrs` 和 `attrTypes.blendMode.enumValues`。现有 EnumValueAnalyzer 自动消费，
不需要分别改 CLI、插件和 LSP。复杂度低。

#### 例 2：当前元素的组合约束——RuleConstraint

```xml
<!-- 不合法：两个图片来源同时存在 -->
<Image src="bg.png" srcExp="@dynamic_src"/>

<!-- 合法：只保留一种来源 -->
<Image srcExp="@dynamic_src"/>
```

只读取当前 Image 的属性，适合 JSON condition。现有 `SEM-IMG-002` 就属于这一层。

#### 例 3：兄弟顺序——专用 Tree/Sibling Analyzer

```xml
<!-- 不合法：layered Group 最后一张图没有 hybridMode -->
<Group layered="true">
    <Image src="bottom.png"/>
    <Image src="top.png"/>
</Group>

<!-- 合法：最后一层声明混合模式 -->
<Group layered="true">
    <Image src="bottom.png"/>
    <Image src="top.png" hybridMode="oriOver"/>
</Group>
```

它需要“最后一个 Image”和“前序兄弟”，不是当前 Rule DSL 的属性比较。应由 SiblingAnalyzer 实现。
当前 JSON 虽有 `SEM-ATTR-005`，但条件不可执行，属于 FIX005。

#### 例 4：引擎自动 clamp——不要轻易设为 error

```xml
<!-- 引擎文档说明会被钳制为 255 -->
<Image src="bg.png" alpha="300"/>

<!-- 明确合法 -->
<Image src="bg.png" alpha="255"/>
```

这可以静态提示，但更合理的是 warning；当前 `SEM-ATTR-001` 使用 error，可能在流水线中过度阻断。

### 4.4 变量、函数与未来 OOP：`base-var/base-exp` / `variable` / `functions`

#### 例 1：现有类型体系内新增函数——函数 JSON

假设新增 `clamp(number, number, number) -> number`：

```xml
<!-- 不合法：第一个参数是字符串 -->
<Var name="alpha" type="number" expression="clamp('300',0,255)"/>

<!-- 合法 -->
<Var name="alpha" type="number" expression="clamp(300,0,255)"/>
```

在 `resources/functions/` 增加签名后，TypeAnalyzer 可复用现有函数参数检查。复杂度低。

#### 例 2：引用前缀与变量类型——SymbolTable + TypeAnalyzer

```xml
<Var name="title" type="string" expression="'Music'"/>

<!-- 不合法：string 变量使用数值前缀 # -->
<Text textExp="substr(#title,0,2)"/>

<!-- 合法：使用字符串前缀 @ -->
<Text textExp="substr(@title,0,2)"/>
```

这需要知道声明类型和引用位置，不能只在 Text JSON 里枚举。当前符号表和 TypeAnalyzer 已覆盖这一类。

#### 例 3：新增运算符——改语言内核

```xml
<!-- 假设未来支持空值合并运算符 -->
<Text textExp="@media_title ?? 'No title'"/>
```

需要同步修改表达式 grammar、AST、类型推断、语法诊断、语义高亮和补全。复杂度高。

#### 例 4：OOP——不是 JSON 扩展

```xml
<!-- 假设未来出现类与继承 -->
<Class name="WeatherCard" extends="Card">
    <Field name="temperature" type="number"/>
</Class>
```

要检查重复成员、继承环、override、成员可见性和 `card.temperature`，必须建设正式类型/符号/resolver 模型。
现有 JAR、报告和前端适配可以保留，语言前端需要演进。

### 4.5 命令、触发与控制：`base-command` / `commands` / `trigger` / `control`

#### 例 1：同元素互斥——局部 RuleConstraint

```xml
<!-- 不合法 -->
<VideoCommand name="v" play="1" sound="1"/>

<!-- 合法 -->
<VideoCommand name="v" play="1"/>
```

当前 `SEM-CMD-001` 已实现并进入 Showcase，复杂度低。

#### 例 2：子节点属性——TreeAnalyzer

```xml
<!-- 缺少 pause 生命周期处理 -->
<ExternalCommands>
    <Trigger action="resume"/>
</ExternalCommands>

<!-- resume/pause 都有 -->
<ExternalCommands>
    <Trigger action="resume"/>
    <Trigger action="pause"/>
</ExternalCommands>
```

需要同时检查子节点标签和 `action` 属性。它是可静态确定的领域建议，但更适合作为 warning；当前 condition 不可执行。

#### 例 3：命令目标解析——DocumentAnalyzer

```xml
<!-- 不合法：目标元素不存在 -->
<Command target="ghost.animation" value="play"/>

<!-- 合法：目标声明存在 -->
<Video name="ambient" src="ambient.mp4"/>
<Command target="ambient.animation" value="play"/>
```

需要文件级 name 索引和目标属性模型，复杂度中等。

#### 例 4：时间/状态流——谨慎限定范围

```xml
<!-- 可以静态看到命令顺序，但无法保证真机播放效果 -->
<Trigger action="pause">
    <Command target="ambient.animation" value="stop"/>
</Trigger>
```

“是否存在 pause/stop”可检查；“动画是否及时停止、是否省电”必须运行验证。不要把后者算进静态分析承诺。

### 4.6 数据开放：`base-weather/base-media/...` / `data_open` / `calendar`

#### 例 1：Weather 的日期维度——字段字典 + DataOpenAnalyzer

文档说明 `currentTem` 只有 today，没有 yesterday/tomorrow：

```xml
<!-- 不合法 -->
<Weather>
    <Var name="Weather.tomorrow.currentTem" type="number"/>
</Weather>

<!-- 合法 -->
<Weather>
    <Var name="Weather.today.currentTem" type="number"/>
    <Var name="Weather.tomorrow.maxtemp" type="number"/>
</Weather>
```

应把 `field + dateDimension + type` 做成版本化数据字典，由补全、文档和 Analyzer 共用。
当前 Weather condition 是自然语言式占位，不能执行。

#### 例 2：Media 的声明依赖——文档索引

```xml
<!-- 不合法：没有 MediaController -->
<MediaIcon src="default.png"/>

<!-- 合法 -->
<MediaController packageName="com.huawei.hmsapp.music"/>
<MediaIcon src="default.png"/>
```

这仍然是静态分析，但不属于局部属性比较。一个 MediaDependencyAnalyzer 应统一处理 MediaIcon/MediaCommand，
避免三个 JSON 分别维护同一个 `SEM-MEDIA-001`。

#### 例 3：SensorBinder 子变量结构——子树分析

```xml
<!-- 不合法：Variable 缺少 name/index -->
<SensorBinder>
    <Variable/>
</SensorBinder>

<!-- 合法 -->
<SensorBinder>
    <Variable name="gravity_x" index="0"/>
</SensorBinder>
```

读取当前子树即可确定，适合 DataOpenAnalyzer；不需要修改表达式 grammar。

### 4.7 2D 基础动画：`2d-*` / `animation`

#### 例 1：关键帧字段范围——JSON/LiteralRangeAnalyzer

```xml
<!-- 不合法 -->
<AlphaAnimation>
    <Alpha a="300" time="-1"/>
</AlphaAnimation>

<!-- 合法 -->
<AlphaAnimation>
    <Alpha a="255" time="0"/>
</AlphaAnimation>
```

字面量范围不需要理解整个动画，复杂度低。若属性支持表达式，则只检查类型，不做常量折叠。

#### 例 2：关键帧顺序——AnimationAnalyzer

```xml
<!-- 不合法：time 倒序 -->
<PositionAnimation>
    <Position x="0" y="0" time="1000"/>
    <Position x="100" y="0" time="500"/>
</PositionAnimation>

<!-- 合法：time 单调递增 -->
<PositionAnimation>
    <Position x="0" y="0" time="0"/>
    <Position x="100" y="0" time="1000"/>
</PositionAnimation>
```

需要按兄弟顺序比较多个节点，复杂度中等。

#### 例 3：父子关系——结构元数据

```xml
<!-- 不合法：Alpha 脱离 AlphaAnimation -->
<Group><Alpha a="100" time="0"/></Group>

<!-- 合法 -->
<AlphaAnimation><Alpha a="100" time="0"/></AlphaAnimation>
```

更新 `Alpha.allowedParents` 即可由 ParentChildAnalyzer 统一消费，不要再写一条重复 constraint。

### 4.8 2D 高级动效与物理：`2da-*` / `effect`

#### 例 1：性能建议——warning，不默认阻断

```xml
<!-- 引擎会限制到 120，适合 warning -->
<ParticleView src="star.png" w="160" h="160"/>

<!-- 推荐配置 -->
<ParticleView src="star.png" w="120" h="120"/>
```

当前 `SEM-EFFECT-PV-002` 已实现为 warning，这种 severity 更符合“能运行但配置无效/可能浪费”的语义。

#### 例 2：形状决定必填字段——局部条件

```xml
<!-- 不合法：圆形刚体缺少 radius -->
<CollBody id="ball" shape="1"/>

<!-- 合法 -->
<CollBody id="ball" shape="1" radius="60"/>
```

只看当前元素即可，适合 RuleConstraint；复杂度低。

#### 例 3：ID 唯一与命令引用——DocumentAnalyzer

```xml
<!-- 不合法：重复 id，命令目标不唯一 -->
<CollBody id="ball"/><CollBody id="ball"/>
<CollBodyCommand collbodyid="ball"/>

<!-- 合法 -->
<CollBody id="ball"/><CollBody id="box"/>
<CollBodyCommand collbodyid="ball"/>
```

需要文档级 symbol index，复杂度中高。

#### 例 4：资源大小与真实卡顿——拆开看

```xml
<SoundCommand sound="audio/long.mp3"/>
```

XML 只能看到路径。检查是否超过 1MB 需要主题包 ResourceIndex，属于可选的包级静态分析；
检查播放是否卡顿、功耗是否异常则必须运行/真机。两者不能混为一谈。

### 4.9 3D：`3d-*` / `three_d`

#### 例 1：模式与属性组合——局部 RuleConstraint

```xml
<!-- 不合理：touchType=0 不使用滑动/调距参数 -->
<MultiLayer touchType="0" pitchAngle="8,-8" stepZ="0.1"/>

<!-- 合理：重力模式只保留重力参数 -->
<MultiLayer touchType="0" gravityX="0" gravityY="0"/>
```

当前 `SEM-3D-001` 已实现为 warning，适合现场展示。

#### 例 2：Layer.z 字面量范围——简单，但当前写法不可执行

```xml
<!-- 不合法：官方范围 [-10,7] -->
<Layer z="8" w="1080" h="2400" src="front.png"/>

<!-- 合法 -->
<Layer z="6.5" w="1080" h="2400" src="front.png"/>
```

这本来只需字面量数值比较；当前 JSON 使用未支持的 `parseFloat(...)`，所以 `SEM-3D-LAYER-001` 不触发。
修复不应先造通用函数系统，直接使用 LiteralRangeAnalyzer 或扩 signed number 即可。

#### 例 3：图层 z 顺序——SiblingAnalyzer

```xml
<!-- 不合理：远景写在近景后，z 逆序 -->
<MultiLayer>
    <Layer z="6" w="1080" h="2400" src="front.png"/>
    <Layer z="-8" w="1080" h="2400" src="sky.png"/>
</MultiLayer>

<!-- 合理：从远到近 -->
<MultiLayer>
    <Layer z="-8" w="1080" h="2400" src="sky.png"/>
    <Layer z="6" w="1080" h="2400" src="front.png"/>
</MultiLayer>
```

它需要兄弟顺序和可判定的 z 字面量；表达式 z 无法排序时应跳过或提示“不确定”，不能误报。

#### 例 4：最终透视效果——不属于静态保证

同样合法的 z 和尺寸，在不同素材、屏幕和传感器输入下仍可能遮挡或穿帮。静态分析最多检查契约和启发式，
最终效果仍交给引擎和真机。

### 4.10 根场景与一镜到底：Lockscreen/Wallpaper/Widget/ChargingSkin/LongTake

#### 例 1：新增根场景——根元素 JSON + scope

```xml
<!-- Widget 缺少必填虚拟尺寸 -->
<Widget><Image src="card.png"/></Widget>

<!-- 合法 -->
<Widget screenWidth="1384" screenHeight="1440">
    <Image src="card.png"/>
</Widget>
```

根元素的 requiredAttrs、scope 和 deviceSupport 都在规则 JSON。文件识别从 RuleRepository 的 root 集合读取，
因此新增同类根场景通常不需要给 CLI/插件/LSP 分别硬编码。

#### 例 2：场景作用域——ScopeAnalyzer

```xml
<!-- ParticleView 文档只支持 Lockscreen，放 Wallpaper 不合法 -->
<Wallpaper><ParticleView src="star.png"/></Wallpaper>

<!-- 合法 -->
<Lockscreen><ParticleView src="star.png"/></Lockscreen>
```

只更新 ParticleView 的 scope/allowedParents，由 ScopeAnalyzer/ParentChildAnalyzer 统一消费。
当前另写 `SEM-EFFECT-PV-001` constraint 属于重复建模。

#### 例 3：LongTake 转场图——SceneGraphAnalyzer

```xml
<!-- 不完整：只声明锁屏到桌面，没有桌面回锁屏 -->
<LongTake>
    <StoryBoard name="OneShotLockHome"/>
</LongTake>

<!-- 结构更完整的静态输入 -->
<LongTake>
    <StoryBoard name="OneShotLockHome"/>
    <StoryBoard name="OneShotHomeLock"/>
</LongTake>
```

检查固定名称、成对转场和重复节点属于场景图静态分析；读取引用的视频是否存在需要 ResourceIndex。

#### 例 4：视频编码、时长和真机转场——分层验证

```text
XML/场景图：静态分析器
视频是否存在、分辨率/编码/时长：主题包资源检查器
转场是否连贯、是否黑帧、性能是否达标：引擎与真机
```

这就是不越界的边界：每层只承诺自己能从输入证明的事情。

## 5. 扩展复杂度的最终回答

| 变化 | 成本判断 | 原因 |
|---|---:|---|
| 同一语言模型中新增元素/属性/枚举/scope | 低 | 数据驱动，三端复用 Core |
| 新增局部跨属性规则 | 低 | Rule DSL 能表达时只需规则+golden |
| 新增树、文档、资源级谓词 | 中 | 需要 typed context、index 或 Analyzer |
| 新增语法、类型和解析行为 | 高 | grammar、AST、类型、诊断、编辑器联动 |
| DSL 向 OOP 演进 | 很高 | 符号表、成员解析、继承、重载、模块和重构能力系统升级 |

记忆句：

> 交付面复杂度不会线性乘三，因为插件、CLI、LSP 共用 Core；语言内核复杂度会随语义跃迁。

以及：

> JSON 是规则配置层，不是未来整个语言语义系统。

## 6. 开箱即用怎么回答

当前事实：默认规则会进入 Fat JAR、LSP JAR 和插件制品，普通员工不需要自己准备规则 JSON。

推荐产品形态：

1. 内置、版本化的官方基线规则，零配置启用。
2. 部门/产品线维护的可选 overlay 规则包。
3. 项目配置只负责 enable/disable/severity，不让普通员工维护规则实现。
4. 输出披露 analyzer、rule-pack、DSL/spec 和 effective-config 版本。
5. 外部规则包加载/编译失败必须显式失败，不能静默降级为 0 诊断。

当前最后一公里缺口：

- Windows 中文终端当前建议显式指定 UTF-8 JVM 参数。
- 客户端依赖 Java 运行时发现，交付包还需明确自带/复用哪一个 JRE。
- IntelliJ 插件函数签名库装配缺失（FIX003），类型诊断可能与 CLI/LSP 不一致。
- 应直接发布预构建 JAR、VSIX/插件制品，不要求员工拉源码构建。

## 7. 已知风险与现场红线

### 可以展示

- clean/faulty 两个生产形态脚本。
- `SEM-3D-001`、`SEM-SRCIMG-001`、`SEM-PERSIST-001`。
- CLI JSON、Rule ID、suggested fixes 和退出码。
- “具备接入现有流水线的执行契约”。

### 不要展示或夸大

- `script_rule_dsl_gap_probe.xml` 作为成功案例：它是 FIX005 复现文件。
- Weather/Calendar/Media 文档级规则已经完整可执行。
- layered Group、Layer.z、Weather/Calendar/Media 等当前未验证规则已稳定检出。
- 透明视频后缀、ExternalCommands.pause 等领域建议已经确定为默认阻断项；它们仍需要产品 profile/severity 决策。
- 当前所有 Rule ID、message 和文档链接已经一一对齐；FIX006 已发现至少 20 处明显错位。
- IntelliJ、CLI、LSP 当前完全一致。
- Quick Fix IDE 灯泡、ToolWindow 已交付。
- 949 tests 意味着没有漏检；FIX004 已证明部分测试是弱保护。
- 已经接入组织流水线、达到代码覆盖率或性能目标。

## 8. 技术骨干可能提出的尖锐问题

### Q1：你说 JSON 可扩展，那为什么我加一个 condition 就一定能执行？

答：不能保证。当前只支持有限的属性、父标签、比较、集合和两个预处理特例；本次审计发现 30/75 条内置 condition
超出能力。复核后其中 25 条属于 DSL 文件内静态语义，应该分流到局部 Rule DSL、Tree/Document/DataOpen Analyzer；
另外 5 条应分别做 opt-in、资源扩展、去重或重新澄清。正确方向不是盲目扩大 grammar，而是加载时编译校验并选择正确承载层。

### Q2：30 条 condition 都有能力错配，是不是规则库大半都不可信？

答：不能把“能力错配”直接换算成 30 个核心缺陷：其中有可选领域检查、资源检查、重复规则和建模错误。
但执行器不校验 condition 仍是基础缺陷，因为它无法告诉规则作者哪些规则实际生效。探针已经用 4 个明确的 DSL 属性/树语义实证漏检。
可信度应按 Rule ID 的 E2E 证据逐条建立，而不是按 JSON 文件数量建立。Showcase 中的 8 条规则已由最新 Fat JAR
对 clean/faulty 成对验证；未验证规则不应进入阻断门禁。FIX005 的价值正是把“看起来存在”转成“可验证执行”。

### Q3：为什么没有在规则加载时发现这些非法 condition？

答：当前加载器只反序列化字符串，真正解析发生在逐元素求值阶段；错误监听又被移除并降级为 false。需要把 compile/validate
前移到规则包加载或构建门禁，并要求完整消费到 EOF。

### Q4：为什么不把所有复杂逻辑都继续扩充进 Rule DSL？

答：不同规则需要 element、tree、document、resource、device 五种上下文。把文件 IO、符号解析和状态流都塞进字符串 DSL，
会失去类型安全、可测试性和性能可控性。Rule DSL 负责局部声明式逻辑，Analyzer 是复杂语义的 escape hatch。

### Q5：插件已经即时提示了，CLI 对开发者到底有什么价值？

答：批量扫描、迁移评估、固定命令复现、规则开发回归和无 UI 环境执行。插件是交互视图，CLI 是可重复的参考执行面。

### Q6：流水线为什么不能直接全量阻断？

答：当前规则覆盖与误报率没有全量生产基线，且存在 FIX005/FIX004。应先观察，再提醒，最后只阻断具有 must-trigger、
must-not-trigger、clean golden 和真实脚本回归证据的高置信 error。

### Q7：静态分析通过了，为什么主题仍可能黑屏？

答：Video 的 defaultBitmap 缺失、资源路径、编码格式、主题包结构和引擎行为不完全属于当前 XML 静态上下文。
静态分析是上游一层，不替代打包、引擎和真机。

### Q8：插件、CLI、LSP 结果现在完全一致吗？

答：共享 Core 使一致性具备架构基础，但目前不能宣称完全一致。FIX003 会让 IntelliJ 直接插件路径缺少函数签名库，
因此类型诊断可能少于 CLI/LSP；需要统一 Composition Root 并做三端 contract test。

### Q9：员工拿到插件为什么还要配置 Java 或规则目录？

答：规则目录不应配置，默认基线应内置。Java 运行时和制品分发仍是交付问题，应优先复用 IDE JBR或随 LSP 明确打包运行时。
`--rule-dir` 只给规则维护者和产品线 overlay 使用。

### Q10：外部规则覆盖内置规则后，怎么知道到底执行了哪一版？

答：当前还需要补 effective-config 可观察性。目标输出应包含 analyzer version、rule-pack version、DSL/spec version、
overlay 来源、启停与 severity 覆盖；否则流水线结果不可审计。

### Q11：如果后面 DSL 增加 class、对象和继承，现有项目是不是推倒重来？

答：CLI、诊断模型、报告、规则包和三端适配可以保留；需要升级的是 grammar、AST、符号表、类型系统和 resolver。
现在应避免把 OOP 语义编码成更多 attr JSON，并提前稳定语言前端接口。

### Q12：性能怎么样？十万个脚本能扫多久？编辑器延迟多少？

答：当前没有可复核 benchmark，不能给数字。应先定义真实语料规模、冷/热启动、P50/P95、内存和增量编辑场景，
再建立持续基线。现场不要临时报一个目标值当实测值。

### Q13：949 个测试都过了，为什么还会有这种漏检？

答：测试数量不等于行为覆盖。FIX004 已发现 guard 跳过、`exit 0||1`、合法文件容忍 error、只断言非 null 等问题。
更可靠的标准是按 Rule ID 检查具体消息/位置，并对 Fat JAR 做 must-trigger/must-not-trigger golden。

### Q14：你们规则库的来源怎么防止文档抄错？

答：当前还没有完全防住。FIX006 已发现至少 20 处 Rule ID/source 描述与实际 constraint 语义错位。
需要建立“官方文档锚点 → 唯一 Rule ID → condition/message/severity → 正反 fixture → 三端结果”的一一追踪，
并在构建时做引用完整性校验、由 DSL 领域负责人 review。自动抽取可以提效，但不能替代语义审查。

### Q15：哪些问题应该是 error，哪些只是 warning？

答：可证明会导致无效结构、引用不存在、契约互斥的规则适合 error；性能建议、引擎会自动 clamp、依赖设备/效果判断的规则
更适合 warning。门禁策略还应允许按规则包和项目覆盖 severity。

### Q16：为什么 clean 脚本 0 诊断就能证明误报低？

答：不能。它只能证明这一份同复杂度对照脚本没有误报。误报率需要生产语料抽样、人工复核和规则维度统计。
Showcase 应把它说成“对照证据”，不是统计结论。

### Q17：你作为实习生能对这套架构负责到什么程度？

答：不要回答身份。回答证据边界：哪些提交、规则、E2E 是你验证过的，哪些是当前缺口，下一步验收标准是什么。
技术问题回到可复现输入、Rule ID、执行路径和测试证据。

## 9. 被现场击穿时的处理模板

如果观众提供一个脚本但工具没有报：

> 这条先不猜。我会按三层定位：规则库是否声明、condition 是否在执行器能力范围、对应 Analyzer 是否装配；
> 然后补成 must-trigger fixture。静态分析器最危险的不是承认覆盖缺口，而是把未执行的规则当成已支持。

如果插件和 CLI 结果不一致：

> 两端共享 Core，但装配依赖可能不同。当前已知函数签名库在 IntelliJ 路径存在缺口；我会先比较有效规则包和函数库版本，
> 再判断是 Composition Root 还是前端映射问题。

如果被问到没做的能力：

> 当前证据只能支持“接口/数据契约已经具备”或“还在规划”，我不把它说成已交付。验收时需要看到对应制品和 E2E 结果。

## 10. Showcase 前的修复与冻结策略

结论：**Showcase 前不做大范围生产修复，优先把已验证路径冻结成可重复演示。** 当前几个高风险项都不是“改一行就能安全关闭”的问题；
规则执行、Rule ID 重排或测试治理一旦半途而废，可能改变诊断数量、消息和退出码，直接破坏已经彩排过的脚本。

| 项目 | 现场相关性 | 改动/验证半径 | Showcase 前决定 |
|---|---:|---:|---|
| FIX003：插件函数签名库未装配 | 高，只有现场演示插件类型诊断时触发 | 中；装配代码不大，但缺插件级回归测试和打包实测 | 默认延期；插件只演示已验证能力。若必须演示类型诊断，单独走 SDD/TDD 和插件打包验收 |
| FIX004：测试剧场治理 | 高，影响置信度表述 | 很大；原始审计跨 89 个测试文件，且 FIX002 后需先重建剩余基线 | 不突击修；只引用具体 golden 和 Fat JAR E2E，不用测试总数证明无漏检 |
| FIX005：Rule DSL 能力错配 | 高，观众可能给真实规则反例 | 很大；涉及 grammar、错误契约、上下文分层和 30 条规则迁移 | 不突击扩 grammar；保留 gap probe 作为内部复现，不作为成功演示 |
| FIX006：Rule ID/source/condition 错位 | 高，点击文档或谈流水线契约时触发 | 很大；至少 20 项需领域专家逐条裁决、重编号和补 golden | 不在会前批量改数据；主动限定“Rule ID 稳定契约是下一步治理目标” |
| M-1：quiet 隐藏 analyzer 内部异常 | 中，流水线话题会触发 | 中；需要先决定软降级还是 exit 2 | 现场不用 `--quiet`；流水线方案强调 internal failure 必须可观察 |
| M-6：目录扫描静默跳过不可读文件 | 中，批量扫描话题会触发 | 中；涉及结果计数和退出码契约 | 不演示权限异常；不声称当前目录扫描具备完整性证明 |
| 文档证据链和生命周期 | 高，回答追问时直接使用 | 小，且不改变运行行为 | 已收口：30 条有本地依据，reports 有活跃索引，完成材料已归档，BACKLOG 只追活跃项 |

今晚剩余时间更值得投入到以下四件事：

1. 重新构建一次 Fat JAR，记录提交号、命令、clean/faulty 诊断数量和退出码。
2. 把 terminal 与 JSON 的成功输出保存为备用材料；现场命令失败时展示同一制品的已验证结果，而不是临时改代码。
3. 用真实计时彩排两遍：第一遍完整讲，第二遍只保留“边界—真实错误—CLI/流水线—扩展分层—已知缺口”。
4. 预先准备三句边界回答：不替代引擎/真机、可接流水线不等于已接入、共享 Core 不等于三端装配已经完全一致。

只有当“插件类型诊断”是明天不可删的验收项时，才值得在会前启动 FIX003；否则最稳妥的工程决策是冻结生产代码，
把它作为会后的第一个独立修复项。

## 11. 会前检查清单

- 使用最新构建的 `feature/analysis/build/cli/dsl-analyzer.jar`。
- 显式加 UTF-8 JVM 参数，关闭颜色输出，避免终端乱码和转义字符。
- clean、faulty 各运行一次，确认仍为 `0` 和 `5E/3W`。
- 准备 terminal 和 JSON 两个命令，不现场修改脚本。
- 不使用 `--quiet`。
- 不演示 FIX005 探针里的规则。
- 插件若演示导航，从引用处 Ctrl+Click；声明处查找用法使用 Alt+F7。
- 准备一句话说明“可接流水线”与“已经接入流水线”的区别。
- 把本手册的 Q1、Q2、Q5、Q8、Q11、Q13 至少口头练两遍。

## 12. 最后一分钟速记

1. 静态分析是上游增量，不替代运行和真机。
2. 插件服务人，CLI 服务可重复执行和机器系统。
3. JSON 解决字段和局部规则，Analyzer 解决上下文，语言内核解决 OOP 演进。
4. 默认规则应内置，外部 JSON 是维护者能力，不是员工负担。
5. 已验证能力用 E2E 说话；未验证规则不进入阻断门禁。
6. 当前最大扩展性缺口是规则 JSON 与 Rule DSL 能力没有加载时校验。
7. Rule ID 只有在来源、条件、消息和 golden 一一对齐后，才是可供流水线依赖的稳定契约。
