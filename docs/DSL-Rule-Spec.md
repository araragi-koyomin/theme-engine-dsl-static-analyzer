# HarmonyOS Theme Engine DSL - 规则规范文档

## 1. DSL语言概述

### 1.1 文件格式

- XML格式，必须包含声明头：`<?xml version="1.0" encoding="utf-8"?>`
- DSL文件识别：基于扩展名(.xml) + 根元素标签双重判定

### 1.2 根元素与应用位置

DSL有5种应用位置，每种对应一个根元素标签：

| 根元素 | 应用位置 | 关键属性 | 说明 |
|---|---|---|---|
| `<Lockscreen>` | 锁屏 | frameRate(可选,number), screenWidth(可选,number) | frameRate默认60fps；screenWidth定义虚拟屏幕宽度 |
| `<Wallpaper>` | 桌面 | screenWidth(可选,number) | 继承锁屏除解锁交互外的所有功能 |
| `<Widget>` | 百变卡片 | screenWidth(必填,number), screenHeight(必填,number), frameRate(可选,number) | 卡片尺寸固定 |
| `<ChargingSkin>` | 充电动效 | screenWidth(可选,number) | 替换系统充电效果 |
| `<LongTake>` | 一镜到底 | 无特有根属性 | 一镜到底动画场景，作为锁屏的扩展场景使用 |

> 根元素的判定规则：`allowedParents` 为空（null 或空列表）的元素即为根元素。LongTake 虽 category 为 `"longtake"` 而非 `"root"`，但因 allowedParents 为空列表，同样被识别为根元素。

---

## 2. 元素目录

### 2.1 视图元素

#### 2.1.1 通用属性

所有视图元素共享一组通用属性（部分元素有例外，见下表）：

| 属性 | 类型 | 必填 | supportsExpression | expressionKind | 说明 | 约束 |
|---|---|---|---|---|---|---|
| name | string | 选填 | false | — | 元素变量名，@name取字符串值 | |
| x | number | 选填 | true | number | 相对屏幕左上角x坐标(px)，默认0 | |
| y | number | 选填 | true | number | 相对屏幕左上角y坐标(px)，默认0 | |
| width(w) | number | 选填 | true | number | 显示宽度(px) | |
| height(h) | number | 选填 | true | number | 显示高度(px) | |
| pivotX(centerX) | number | 选填 | true | number | 旋转点X坐标(px) | |
| pivotY(centerY) | number | 选填 | true | number | 旋转点Y坐标(px) | |
| rotation(angle) | number | 选填 | true | number | 旋转角度(360度制) | |
| rotationX(angleX) | number | 选填 | true | number | X轴旋转角度 | |
| rotationY(angleY) | number | 选填 | true | number | Y轴旋转角度 | |
| alpha | number | 选填 | true | number | 透明度0-255，默认255 | <0取0，>255取255 |
| visibility | number | 选填 | true | number | <=0不可见，>0可见，默认1 | |
| category | string | 选填 | false | — | 充电状态显示枚举 | "Normal","Charging","BatteryLow","BatteryFull" |
| align | string | 选填 | false | — | 水平对齐枚举 | left,center,right |
| alignV | string | 选填 | false | — | 垂直对齐枚举 | top,center,bottom |
| enableMove | string | 选填 | false | — | 是否可移动 | true/false或0/非0 |
| moveRect | string | 选填 | false | — | 移动区域 | "minH,minV,maxH,maxV"，不支持表达式 |
| active | number | 选填 | false | — | 激活状态 | 0=不激活，默认1 |

**通用属性支持矩阵**：

| 标签 | 支持 | 不支持 |
|---|---|---|
| Text | 全部通用属性 | / |
| Image | 全部通用属性 | / |
| SourceImage | 除category外全部 | category |
| Time | 除width,height外 | width,height |
| DateTime | 除alpha,width,height,enableMove,moveRect外 | alpha,width,height,enableMove,moveRect |
| ImageNumber | 除width,height,rotationX,rotationY外 | width,height,rotationX,rotationY |
| ImageSeries | 除width,height,rotationX,rotationY外 | width,height,rotationX,rotationY |
| Arc/Circle/Rectangle | 除name,alpha外 | name,alpha |
| Ellipse | 除name,alpha外 | name,alpha |
| Line | 除name,alpha,width,height外 | name,alpha,width,height |

#### 2.1.2 视图元素详表

| 元素 | 标签 | 特有属性 | 说明 |
|---|---|---|---|
| 文本 | `<Text>` | color,size,format,paras,text/textExp,autoLineFeed,textalign,bold,isSupportClipping,spaceTimes,spaceExtraAdd,shadowDx,shadowDy,Radius,shadowColor,scrollDisplay,marqueeRepeatLimit,clickable,delayTime | color支持#FFFFFF/#FFFFFFFF和argb()；设置color后alpha无效 |
| 图片 | `<Image>` | src,antiAlias,hybridMode | 静态图片，src为图片文件路径 |
| 动态图片 | `<Image>` | 同Image+srcExp等动态表达式属性 | 支持表达式驱动的图片切换 |
| 视频 | `<Video>` | name(必填),src(必填),play,looping,sound,scalingType,defaultBitmap,isTransparent,isFullScreenNode | sound值0-1；视频<25M；isTransparent仅mp4；分辨率≤4096x4096 |
| 时间 | `<Time>` | format,paras等时间显示属性 | 显示当前时间 |
| 日期 | `<DateTime>` | format,paras等日期显示属性 | 显示当前日期 |
| 倒计时 | `<CountDownTime>` | 倒计时相关属性 | 倒计时显示 |
| 数字图片 | `<ImageNumber>` | src等 | 资源必须从0开始命名，序列不能缺失 |
| 串联图片 | `<ImageSeries>` | 串联图片属性 | 多图串联展示 |
| 帧解锁视图 | `<SourceImage>` | 解锁交互属性 | 解锁相关视图 |
| 遮罩 | `<Mask>` | 遮罩属性 | 蒙版效果 |
| 图片混合 | `<GroupImage>` | 混合属性 | 多图混合处理 |
| 几何图形 | `<Arc>/<Circle>/<Ellipse>/<Line>/<Rectangle>` | 几何图形特有属性 | 各几何形状 |
| 路径解析 | `<PathUtil>` | 路径属性 | SVG路径解析 |
| 视图切换 | `<Swiper>` | Swiper特有属性 | 子视图切换容器 |

### 2.2 组/容器元素

| 元素 | 标签 | 属性 | 说明 |
|---|---|---|---|
| 视图组 | `<Group>` | name,x,y,w,h,alpha,rotation/angle,visibility,clip,layered,align,alignV | clip=true裁剪超出w/h范围；clip=false测量内容大小；layered=true时最后一个Image需有hybridMode |

子元素：任意视图元素 + 动画元素

### 2.3 控件元素

| 元素 | 标签 | 属性 | 必填子元素 | 应用位置支持 |
|---|---|---|---|---|
| 按钮 | `<Button>` | name,w,h,x,y,visibility | `<Trigger>` | Lockscreen√, Wallpaper×, Widget√, ChargingSkin× |

### 2.4 变量元素

#### `<Var>` - 自定义变量

```
<Var name="" expression="" type="" threshold="" persist="" index="" values="" size="" const="" />
```

| 属性 | 类型 | 必填 | supportsExpression | expressionKind | 说明 |
|---|---|---|---|---|---|
| name | string | **必填** | false | — | 变量名，调用时用#name(数值)或@name(字符串) |
| expression | string | 选填 | true | 由type决定 | 表达式或常量；字符串常量需多套单引号：expression="'my string'" |
| type | string | 选填 | false | — | number/string/number[]/string[]，默认number |
| threshold | number | 选填 | true | number | 阈值，变化超阈值触发Trigger |
| persist | string | 选填 | false | — | 持久化，默认false |
| index | string | 选填 | false* | — | 数组索引(从0开始)；\*VarArray内index支持表达式(supportsExpression=true) |
| values | string | 选填 | false | — | "val,val,...批量赋值" |
| size | number | 选填 | false | — | 数组长度 |
| const | string | 选填 | false | — | true/false，赋值后不再改变，默认false |

**⚠ 禁止对时间/日期/星期变量使用persist/globalPersist/styleGlobalPersist**

**⚠ Var type属性缺失时默认为number**（类型推断需处理type属性缺失场景）

Var可包含子元素：`<Trigger>`（threshold触发时）、`<VariableAnimation>`

#### `<GlobalVariable>` - 全局变量

引擎预置变量，直接使用，用#取数值，@取字符串。详见第2.5节全局变量目录。

#### `<VarArray>` - 变量数组

独立XML元素，通过`<Vars>`+`<Items>`子结构声明变量数组：

```
<VarArray type="">
    <Vars>
        <Var name="" index=""/>
    </Vars>
    <Items>
        <Item value=""/>
        <Item value=""/>
    </Items>
</VarArray>
```

**VarArray自身属性**：

| 属性 | 类型 | 必填 | supportsExpression | expressionKind | 说明 |
|---|---|---|---|---|---|
| type | string | 选填 | false | — | number/string，默认number |

**Vars内Var属性**（与standalone Var不同）：

| 属性 | 类型 | 必填 | supportsExpression | expressionKind | 说明 |
|---|---|---|---|---|---|
| name | string | 必填 | false | — | 变量名，@name返回字符串数组中index位置的值 |
| index | string | 选填 | **true** | auto | 数组索引（支持表达式），Item顺序决定index取值 |

**Items内Item属性**：

| 属性 | 类型 | 必填 | supportsExpression | expressionKind | 说明 |
|---|---|---|---|---|---|
| value | string | 选填 | false | — | 数组值 |

#### `<Array>` - 控件数组

独立XML元素，批量创建相似控件：

```
<Array indexFlag="" frequency="">
    <Image src="" srcid="" w="" h="" x="" y=""/>
</Array>
```

| 属性 | 类型 | 必填 | supportsExpression | expressionKind | 说明 |
|---|---|---|---|---|---|
| indexFlag | string | **必填** | false | — | 索引变量名，可在子元素中用#indexFlag引用循环序号 |
| frequency | expression | **必填** | true | number | 重复生成元素的次数 |

**Array内变量引用**：子元素中`#__i`（indexFlag值）可用作循环变量，如`#arr[#__i]`数组访问模式。

### 2.5 全局变量目录

| 分类 | 变量名 | 类型 | 说明 |
|---|---|---|---|
| **触摸** | touch_x, touch_y | number | 当前触摸点坐标 |
| | touch_begin_x, touch_begin_y | number | 按下时初始坐标 |
| | touch_begin_time | number | 触摸开始时间(ms) |
| **解锁** | name.move_x, name.move_y | number | 解锁部件偏移 |
| | name.move_dist | number | 解锁部件移动距离 |
| | name.state | number | 解锁状态：NORMAL(0),PRESSED(1),REACHED(2),INVISIBLE(3) |
| **时间/日期** | year, month, date, day_of_week | number | month取值0-11，day_of_week从1=周日 |
| | hour, hour12, hour24, minute | number | hour24小时制 |
| | ishour12 | string | "true"/"false" |
| | lunarYear, lunarMonth, lunarDay | number | 农历 |
| | system.time.hour1, hour2, min1, min2 | number | 时间各位数字 |
| | system.time.ampm | string | AM/PM标识 |
| **电量** | battery_level | number | 1-100 |
| | battery_state | number | Normal(0),Charging(1),BatteryLow(2),BatteryFull(3) |
| **屏幕** | screen_width, screen_height | number | 虚拟屏幕宽高 |
| **深色模式** | darkMode | number | 1浅色,2深色,0不支持 |
| **情绪** | emotionValue | number | 0愉悦,2平静,4不愉悦,-1未感知 |
| **组件状态** | name.visibility | number | 组件可见性(1=可见) |
| | name.actual_x/y/w/h | number | 元素实际位置/尺寸 |
| | name.text_width/text_height | number | 文本实际宽高 |
| | name.actual_w/actual_h | number | 图片实际宽高 |
| **视频** | src.state | number | IDLE(0)~RELEASED(7) |
| | src.currentTime | number | 视频播放进度(ms) |
| **灭屏时间** | screenOnLeftTime | number | 距离灭屏时间(秒) |
| **AI语音** | matchSkill_value | string | 小艺语音匹配能力值 |
| **设备使用间隔** | public_deviceUsageIntervalTime | number | 用户使用设备间隔时长(秒) |
| **碰一碰** | enableCollaboration | number | 碰一碰能力开关(0关/1开)，仅百变卡片 |
| **手势** | dynamicSwingValue | number | 动态手势 |
| | staticSwingValue | number | 静态手势 |
| **场景感知** | Scenarios.ID.text | string | 场景文案 |
| | Scenarios.ID.jumpable | number | 1可跳转,0不可 |
| | Scenarios.ID.appName | string | 关联应用名 |
| | Scenarios.topId | string | 最高优先级服务ID |

### 2.6 命令元素

| 元素 | 标签 | 必填属性 | 选填属性 | 说明 |
|---|---|---|---|---|
| 基础命令 | `<Command>` | target, value | condition, delay, delayCondition | target格式"name.property"，value: visibility→true/false/toggle; animation→play/stop |
| 变量命令 | `<VariableCommand>` | name, expression | type, condition, delay, delayCondition | **不支持persist属性** |
| 视频命令 | `<VideoCommand>` | name, src | play, sound, seekTime | **sound和play互斥** |
| 声音命令 | `<SoundCommand>` | sound, volume | loop, keepCur, play | 音频>1MB会被截断 |
| 可见性命令 | `<VisibilityCommand>` | visibility(表达式) | / | visibility为表达式，>0可见 |
| Intent命令 | `<IntentCommand>` | action | package, class | 包名类名需适配NEXT |
| 通用命令 | `<ExternCommand>` | / | / | 通用命令分发 |
| 命令组 | `<GroupCommand>` | / | / | 组合多条命令 |
| 命令组 | `<GroupCommands>` | / | / | 组合多条命令 |
| 周期命令 | `<CycleCommand>` | indexFlag | frequency, begin, end, cycleCondition | 配合Array使用 |
| 全景换肤 | `<StyleCommand>` | index | name, contentTypes, condition | index不支持表达式；耗时1.5-2秒；styleGlobalPersist初始值默认0 |
| 天气刷新 | `<RefreshWeatherCommand>` | / | / | 刷新天气数据 |
| 健康刷新 | `<RefreshHealthyCommand>` | / | / | 刷新运动健康数据 |
| 亮屏时间 | `<KeepScreenOnCommand>` | / | / | 自定义亮屏保持时间 |
| 碰一碰 | `<CollaborationCommand>` | / | / | 碰一碰协同 |
| 碰一碰发送 | `<CollaborationSendCommand>` | / | / | 长连接发送 |
| 碰一碰断开 | `<CollaborationDisconnectCommand>` | / | / | 长连接断开 |
| 情绪感知 | `<EmotionCommand>` | / | / | 情绪感知命令 |
| 线性振动 | `<VibrateCommand>` | / | / | 线性振动反馈 |
| AI语音 | `<VoiceCommand>` | / | / | 语音互动 |
| 场景跳转 | `<ScenarioIntentCommand>` | / | / | 场景感知跳转 |
| 隔空手势 | `<SwingCommand>` | / | / | 隔空手势互动 |
| 卡片互动 | `<CardInteractionCommand>` | / | / | 百变卡片互动 |

命令通用属性：
- **condition**：条件表达式，非0/true时执行
- **delay**：延迟毫秒数
- **delayCondition**：延迟条件，默认true/1生效

### 2.7 Trigger元素

| 元素 | 标签 | 必填属性 | 出现位置 |
|---|---|---|---|
| 触发器 | `<Trigger>` | action | Button, Unlocker, Slider, Var(threshold触发), ExternalCommands |

**action合法值**：`down`, `up`, `double`, `click`, `long`, `resume`, `pause`

Trigger子元素：各种Command元素

### 2.8 动画元素

#### 2D基础动画

| 元素 | 标签 | 说明 |
|---|---|---|
| 透明度动画 | `<AlphaAnimation>` | 控制alpha变化 |
| 位移动画 | `<PositionAnimation>` | 控制x/y位移 |
| 旋转动画 | `<RotationAnimation>` | 控制rotation旋转 |
| 缩放动画 | `<SizeAnimation>` | 控制w/h缩放 |
| 帧动画 | `<SourceAnimation>` | 图片帧序列 |
| 变量动画 | `<VariableAnimation>` | 控制变量值变化，子元素`<AniFrame value="" time="" />` |

动画元素出现在Group或Var的子元素中。

#### 2D高级特效

| 类别 | 标签 | 说明 |
|---|---|---|
| Mesh变换 | `<MeshImageTrans>` | 网格图片变换 |
| Mesh运动 | `<MeshImagesInMotion>` | 网格图片运动效果 |
| 粒子 | `<ParticleView>` | 粒子效果 |
| 掉落物理 | `<DropPhysicalView>` | 物理掉落效果 |
| 碰撞世界 | `<CollisionWorld>` | 碰撞物理世界 |
| 流体 | `<Fluids>` | 流体效果 |

#### 3D

| 类别 | 标签 | 说明 |
|---|---|---|
| 3D视图 | `<StereoView>` | 3D立体视图 |
| 多层 | `<MultiLayer>` | 3D多层渲染 |
| Scene3D | Scene3D | 3D场景 |

---

## 3. 表达式系统

### 3.1 数值表达式（NumericExpression）

- 返回值类型：number（float）
- 变量引用：`#varName`（数值型），数组：`#arr[expression]`
- 运算符：`+ - * / %`
- **⚠ `-#varName`语法无效，必须写为`-1*#varName`或`0-#varName`**
- **⚠ 数值超过7位有精度问题**
- 解析工具：ANTLR4 DslExpression.g4

**函数列表（定义在函数签名库JSON中）**：

| 函数 | 参数签名 | 返回类型 | expressionKind | 说明 |
|---|---|---|---|---|
| sin(x) | (number) | number | number | 三角函数，x为弧度 |
| cos(x), tan(x) | (number) | number | number | 三角函数 |
| asin(x), acos(x), atan(x) | (number) | number | number | 反三角函数 |
| sqrt(x) | (number) | number | number | 开平方，x为负返回0 |
| abs(x) | (number) | number | number | 绝对值 |
| min(x,y), max(x,y) | (number,number) | number | number | 最小/最大值 |
| digit(x,pos) | (number,number) | number | number | 取数字第pos位 |
| round(x) | (number) | number | number | 四舍五入取整 |
| int(x) | (number) | number | number | 舍弃小数部分 |
| rand() | () | number | number | 0-1随机浮点数 |
| eq(x,y), ne(x,y) | (number,number) | number | number | 相等/不相等判断，返回0或1 |
| ge(x,y), gt(x,y), le(x,y), lt(x,y) | (number,number) | number | number | 比较，返回0或1 |
| isnull(x) | (number\|string\|reference) | number | number | 变量是否无值；x可为#varName或@varName引用 |
| not(x) | (number) | number | number | 逻辑非 |
| ifelse(x,y,z) | (number,T,T...) | T | number/string | 多条件判断；支持variadic形式ifelse(x1,y1,x2,y2,...,z) |
| pow(x,y) | (number,number) | number | number | x的y次方 |
| len(x) | (number) | number | number | 数字位数 |

### 3.2 字符串表达式（StringExpression）

- 返回值类型：string
- 变量引用：`@varName`（字符串型），`#varName`（数值型嵌入需`{expr}`花括号）
- 字符串必须使用**单引号**：`'hello'`
- `+`表示**拼接**而非加法
- 数组：`@arr[expression]`
- 解析工具：ANTLR4 DslExpression.g4

**⚠ 数值表达式嵌入字符串需加花括号**：`srcExp="number/hour/{int(#system.time.hour1)}_{int(#aniTime)}.png"`

**⚠ 字符串表达式中数值计算不能以#开头**：`#num*10`会被认为取名为"num*10"的变量，正确写法`10*#num`

**函数列表（定义在函数签名库JSON中）**：

| 函数 | 参数签名 | 返回类型 | expressionKind | 说明 |
|---|---|---|---|---|
| substr(str,pos,len) | (string,number,number) | string | string | 子串 |
| strIsEmpty(str) | (string) | string | string | 空串判断 |
| strIndexOf(str1,str2) | (string,string) | string | string | 首次位置 |
| strLastIndexOf(str1,str2) | (string,string) | string | string | 最后位置 |
| strContains(str1,str2) | (string,string) | string | string | 包含判断 |
| strReplaceAll(str1,str2,str3) | (string,string,string) | string | string | 全替换 |
| preciseeval(str,precision) | (string,number) | string | string | 计算字符串公式；其后不能再用运算符或+ |
| formatDate(format,timeVar) | (string,string) | string | string | 格式化时间 |
| plus(a,b) | (number\|string,number\|string) | string | string | 返回a+b整数和的字符串；a/b可为数值、字符串、变量或函数的混合类型 |
| ifelse(x1,y1,...,z) | (number,string,string...) | string | string | 多条件 |
| strEqual(str1,str2) | (string,string) | string | string | 字符串相等判断 |
| argb(a,r,g,b) | (number,number,number,number) | string | string | 返回8位16进制颜色字符串 |

---

## 4. 作用域约束

### 4.1 应用位置支持矩阵

每个元素在5种应用位置中有不同的支持状态（数据存储在M2 DslElementRule.scope字段中）：

| 元素 | 锁屏 | 桌面 | 一镜到底 | 百变卡片 | 充电动效 |
|---|---|---|---|---|---|
| Var | √ | √ | √ | √ | √ |
| Group | √ | √ | √ | √ | √ |
| Text | √ | √ | √ | √ | √ |
| Image | √ | √ | √ | √ | √ |
| Video | √ | √ | √ | √ | √ |
| Time | √ | √ | √ | √ | √ |
| DateTime | √ | √ | √ | √ | √ |
| Button | √ | × | × | √ | × |
| Command | √ | √ | × | √ | √ |
| VariableCommand | √ | √ | × | √ | √ |
| VideoCommand | √ | √ | × | √ | √ |
| SoundCommand | √ | √ | × | √ | √ |
| StyleCommand | √ | √ | × | √ | × |
| CycleCommand | √ | √ | × | √ | √ |

### 4.2 设备类型支持矩阵

| 元素 | 直板机 | 折叠屏 | 平板 |
|---|---|---|---|
| Var | √ | √ | √ |
| Group | √ | √ | √ |
| Button | √ | √ | √ |
| Command | √ | √ | √ |

> 折叠屏和平板中全屏Image或Video需添加`IsFullScreenNode="true"`，此时x,y必须为0

### 4.3 作用域验证规则

验证元素是否可用于当前应用位置：
1. 从DSL文件根元素标签确定应用位置（M1 + M3 AST根节点）
2. 查询M2规则库中该元素的scope字段
3. 不支持 → 报告错误SEM-SCOPE-001

---

## 5. 错误检测规则

### 5.1 XML结构语法错误（SAX解析阶段）

SAX解析XML时遇格式错误直接抛出SAXParseException，不做额外包装映射为自定义Diagnostic。XML well-formedness错误（标签未闭合、属性引号缺失、缺少XML声明头等）由SAX原生报错处理。

| 规则ID | 检测内容 | 检测机制 | 严重级别 |
|---|---|---|---|
| SYN-001 | 根元素标签错误 | M1文件识别+M3 AST根节点检测 | error |

> 注：原SYN-001(标签未闭合)、SYN-003(属性引号缺失)、SYN-009(缺少XML声明头)不再作为自定义规则ID使用，XML格式错误由SAX直接报出。编号已重新排列为连续序号。

### 5.2 DSL结构语法错误（M3语法分析+M2规则库比对）

| 规则ID | 检测内容 | 检测机制 | 严重级别 |
|---|---|---|---|
| SYN-002 | 标签嵌套违反父子约束 | M3 AST遍历 + M2 allowedParents比对（allowedChildren由反向索引推导） | error |
| SYN-003 | 未知元素标签 | M3 AST tagName + M2 DslElementRule名称集合比对 | error |
| SYN-004 | 未知属性名 | M3属性名 + M2 optionalAttrs+requiredAttrs比对 | warning |
| SYN-005 | 缺失必填属性 | M3属性存在性 + M2 requiredAttrs比对 | error |
| SYN-006 | 属性值类型错误（纯字面量） | 直接类型比对 | error |
| SYN-007 | 枚举值错误 | M2 enumValues比对 | error |

### 5.3 DSL表达式语法错误（ANTLR4 DslExpressionParser捕获）

| 规则ID | 检测内容 | 检测机制 | 严重级别 |
|---|---|---|---|
| SEM-EXPR-001 | 数值表达式使用`-#var`语法 | ANTLR4解析：负号直接前缀变量引用检测 | error |
| SEM-EXPR-002 | 数值表达式值超过7位精度限制 | ANTLR4解析：数值常量位数检查 | warning |
| SEM-EXPR-003 | 字符串表达式中数值计算以#开头 | ANTLR4解析：变量名边界检测 | error |
| SEM-EXPR-004 | 字符串表达式未使用单引号 | ANTLR4解析：字符串常量引号类型检查 | error |
| SEM-EXPR-005 | 字符串表达式嵌入数值表达式缺少花括号 | ANTLR4解析：嵌套语法检查 | error |
| SEM-EXPR-006 | preciseeval后使用运算符或+连接符 | ANTLR4解析：函数后缀约束检查 | error |
| SEM-EXPR-ANTLR | ANTLR4词法/语法错误 | ANTLR4自动报错：不可识别token、表达式结构不合法 | error |

### 5.4 语义/规则错误（M4语义分析阶段）

**类型推断类（TypeAnalyzer）**：

| 规则ID | 检测内容 | 检测机制 | 严重级别 |
|---|---|---|---|
| SEM-TYPE-001 | 表达式类型与属性期望类型不匹配 | M4 TypeInferenceEngine.inferType()结果与AttrTypeSpec期望类型比对 | error |
| SEM-TYPE-002 | 函数调用参数类型不匹配 | M4 TypeInferenceEngine + FunctionSignatureLibrary参数类型比对 | error |

**规则驱动类（ConstraintAnalyzer + RuleDslEvaluator）**：

| 规则ID | 检测内容 | 声明式条件 | 严重级别 |
|---|---|---|---|
| SEM-CMD-001 | VideoCommand中sound和play共存 | `element.attrs['play'] != null AND element.attrs['sound'] != null` | error |
| SEM-PERSIST-001 | 时间/日期/星期变量使用persist/globalPersist/styleGlobalPersist | `element.attrs['persist'] != null OR element.attrs['globalPersist'] != null OR element.attrs['styleGlobalPersist'] != null AND type in ['time','date','week']` | error |
| SEM-PERSIST-002 | VariableCommand使用persist属性 | `element.attrs['persist'] != null AND element.tagName == 'VariableCommand'` | error |

**模式匹配类（各Analyzer）**：

| 规则ID | 检测内容 | 检测机制 | 严重级别 |
|---|---|---|---|
| SEM-SCOPE-001 | 元素不支持当前应用位置 | M2 scope字段 + 根元素应用位置比对 | error |
| SEM-SCOPE-002 | 元素不支持当前设备类型 | M2 deviceSupport字段比对 | warning |
| SEM-REF-001 | 引用未定义的变量名 | SymbolTable声明集合比对 | error |
| SEM-REF-002 | 引用未定义的元素name | SymbolTable name集合比对 | error |
| SEM-REF-003 | 重复name定义 | SymbolTable声明去重检测 | error |
| SEM-ATTR-001 | alpha值超出0-255范围 | 字面量范围检查 | warning |
| SEM-ATTR-003 | category枚举值不合法 | M2 enumValues比对 | error |
| SEM-ATTR-004 | Group clip=true但无w/h | 属性组合检查 | warning |
| SEM-ATTR-005 | Group layered=true但最后一个Image无hybridMode | 属性组合检查 | error |
| SEM-ATTR-006 | Text autoLineFeed=true但无width | 属性组合检查 | warning |
| SEM-ATTR-007 | Text marqueeRepeatLimit但无scrollDisplay | 属性依赖检查 | warning |
| SEM-ATTR-008 | Text clickable但无scrollDisplay | 属性依赖检查 | warning |
| SEM-ATTR-009 | Text delayTime但无scrollDisplay | 属性依赖检查 | warning |
| SEM-VAR-001 | 变量未用Var标签定义直接使用 | SymbolTable比对 | warning |
| SEM-VAR-002 | Var声明字符串常量缺少双套单引号 | 表达式解析检查 | error |
| SEM-VAR-003 | Var values与size同时存在 | 属性组合检查 | warning |
| SEM-VAR-004 | 数组变量使用前未声明size | 属性存在性检查 | error |
| SEM-VAR-005 | Var type属性缺失时expression值不是数值表达式 | type默认number，expression必须为数值表达式 | warning |
| SEM-ARR-001 | VarArray内Var的index超出Items数量范围 | Items数量与index值范围比对 | warning |
| SEM-ARR-002 | Array frequency值不是正整数 | 数值范围检查(frequency>0) | error |
| SEM-ARR-003 | Array indexFlag变量在Array外引用 | SymbolTable作用域检查 | warning |
| SEM-TRIG-001 | Trigger action值不在合法集合中 | 枚举值比对 | error |
| SEM-TRIG-002 | Button缺少Trigger子元素 | 子元素存在性检查 | error |
| SEM-IMG-001 | ImageNumber资源不从0开始命名或序列缺失 | 资源命名检查 | error |
| SEM-IMG-002 | Image src与srcExp同时存在 | 属性互斥检查 | error |
| SEM-IMG-003 | Image isBackground与align同时使用 | 属性组合检查 | warning |
| SEM-SRCIMG-001 | SourceImage direction=0但loop≠true或unlockTo未设 | 属性组合检查 | error |
| SEM-SRCIMG-002 | SourceImage unlockTo已设但同级无Button触控区域 | 属性组合+同级元素存在性检查 | warning |
| SEM-VID-001 | 视频文件>25MB | 文件大小检查 | error |
| SEM-VID-002 | 透明视频非mp4格式 | 属性组合+资源检查 | error |
| SEM-VID-003 | 视频分辨率>4096x4096 | 资源分辨率检查 | error |
| SEM-VID-004 | 全屏Video缺少IsFullScreenNode(折叠屏/平板) | 属性存在性+设备类型检查 | warning |
| SEM-VID-005 | Video defaultBitmap图片不存在 | 资源存在性检查 | error |
| SEM-CMD-002 | SoundCommand音频文件>1MB | 文件大小检查 | warning |
| SEM-CMD-003 | StyleCommand频繁切换 | 切换频率检查 | warning |
| SEM-CMD-004 | StyleCommand index使用表达式 | supportsExpression=false属性含表达式语法检测 | error |
| SEM-GEN-001 | 变量值为负数时前面未补0 | 表达式模式检测 | error |
| SEM-GEN-002 | 变量计算精度误差 | 数值范围检查 | warning |
| SEM-PERSIST-003 | styleGlobalPersist初始值默认为0 | 属性语义检查 | warning |

### 5.5 语义相似度匹配

当检测到SYN-003(未知元素)或SYN-004(未知属性)时：
- 基于编辑距离推荐最接近的合法标签/属性名
- 优先级：完全匹配 > 编辑距离匹配 > 语义匹配
- Quick Fix候选列表使用CandidateItem（含similarityScore）

---

## 6. 规则库数据结构

### 6.1 元素规则条目Schema

```json
{
  "element": "Var",
  "category": "variable",
  "requiredAttrs": ["name"],
  "optionalAttrs": ["expression", "type", "threshold", "persist", "index", "values", "size", "const"],
  "attrTypes": {
    "name": {"type": "string", "supportsExpression": false, "defaultValue": null},
    "expression": {"type": "string", "supportsExpression": true, "expressionKind": "auto", "defaultValue": null},
    "type": {"type": "string", "enumValues": ["number", "string", "number[]", "string[]"], "supportsExpression": false, "defaultValue": "number"},
    "threshold": {"type": "number", "supportsExpression": true, "expressionKind": "number", "defaultValue": null},
    "persist": {"type": "string", "enumValues": ["true", "false"], "supportsExpression": false, "defaultValue": "false"},
    "index": {"type": "string", "supportsExpression": false, "defaultValue": null},
    "values": {"type": "string", "supportsExpression": false, "defaultValue": null},
    "size": {"type": "number", "supportsExpression": false, "defaultValue": null},
    "const": {"type": "string", "enumValues": ["true", "false"], "supportsExpression": false, "defaultValue": "false"}
  },
  "allowedParents": ["Lockscreen", "Wallpaper", "Widget", "ChargingSkin", "Group"],
  "inherits": null,
  "scope": {
    "Lockscreen": true,
    "Wallpaper": true,
    "LongTake": true,
    "Widget": true,
    "ChargingSkin": true
  },
  "deviceSupport": {
    "barPhone": true,
    "foldable": true,
    "tablet": true
  },
  "constraints": [
    {
      "ruleId": "SEM-PERSIST-001",
      "condition": "element.attrs['persist'] != null OR element.attrs['globalPersist'] != null OR element.attrs['styleGlobalPersist'] != null AND element.attrs['type'] IN ['time','date','week']",
      "message": "禁止对时间/日期/星期变量使用persist/globalPersist/styleGlobalPersist",
      "severity": "error",
      "suggestedFixes": ["移除persist属性"]
    },
    {
      "ruleId": "SEM-VAR-003",
      "condition": "element.attrs['values'] != null AND element.attrs['size'] != null",
      "message": "Var的values与size属性同时存在，优先取size",
      "severity": "warning",
      "suggestedFixes": ["移除values属性", "移除size属性"]
    }
  ]
}
```

**AttrTypeSpec字段语义说明**：

| 字段 | 语义 | 说明 |
|---|---|---|
| `type` | 属性期望值类型 | 描述字面量或表达式返回值的类型，而非值的形式（number/string/boolean/enum/expression/action/object/reference） |
| `supportsExpression` | 值是否可以是表达式形式 | 与type独立：`type=number, supportsExpression=true`表示属性可以是数值表达式如`#var*2` |
| `expressionKind` | 表达式语义类别 | "number"→期望数值表达式，"string"→期望字符串表达式，"auto"→根据上下文(如Var的type属性)动态推断 |
| `aliases` | 属性别名列表 | 规范名在optionalAttrs/attrTypes中，别名仅在aliases字段。M3通过resolveAttrAlias()映射到规范名；M5 QuickFix别名替换建议 |
| `defaultValue` | 属性默认值 | 省略该属性时引擎使用的隐式值。null表示无默认值（省略=属性不存在）。M4消费：如Var.type默认"number"用于SEM-VAR-005推断 |


**`type`与`supportsExpression`组合示例**：

| 组合 | 语义 | 实例 |
|---|---|---|
| `type=number, supportsExpression=false` | 纯字面量数值 | Lockscreen.frameRate="60" |
| `type=number, supportsExpression=true, expressionKind=number` | 可以是数值表达式 | x="#screen_width/2", threshold="#x*2" |
| `type=string, supportsExpression=false` | 纯字面量字符串 | Image.src="icon.png" |
| `type=string, supportsExpression=true, expressionKind=string` | 可以是字符串表达式 | textExp="@var+'hello'" |
| `type=string, supportsExpression=true, expressionKind=auto` | 根据上下文动态推断 | Var.expression（type=number→数值表达式，type=string→字符串表达式） |

**别名处理机制**：

optionalAttrs和attrTypes中只包含属性规范名（如`width`、`height`、`pivotX`等）。属性别名（如`w`、`h`、`centerX`等）仅出现在attrTypes规范名条目的`aliases`字段中，不作为独立条目。

消费方通过`RuleRepository.resolveAttrAlias(elementName, attrName)`方法将别名映射到规范名；`getAttrTypeSpec(elementName, attrName)`自动处理别名——传入别名时先resolve到规范名再查询。

示例：Text元素中，`width`是规范名，`w`是别名：
```json
"width": {"type": "number", "aliases": ["w"], "supportsExpression": true, "expressionKind": "number", "defaultValue": null}
```
查询`getAttrTypeSpec("Text", "w")`时，内部resolve `"w"` → `"width"`，返回width的AttrTypeSpec。

**规范名/别名对照表**：

| 规范名 | 别名 | 适用元素 |
|---|---|---|
| width | w | 视图元素(有width属性的) |
| height | h | 视图元素(有height属性的) |
| pivotX | centerX | 视图元素 |
| pivotY | centerY | 视图元素 |
| rotation | angle | 视图元素 |
| rotationX | angleX | 视图元素 |
| rotationY | angleY | 视图元素 |

### 6.2 命令规则条目Schema

```json
{
  "element": "VideoCommand",
  "category": "command",
  "requiredAttrs": ["name", "src"],
  "optionalAttrs": ["play", "sound", "seekTime"],
  "attrTypes": {
    "name": {"type": "string", "supportsExpression": false},
    "src": {"type": "string", "supportsExpression": false},
    "play": {"type": "expression", "supportsExpression": true, "expressionKind": "number"},
    "sound": {"type": "expression", "supportsExpression": true, "expressionKind": "number"},
    "seekTime": {"type": "number", "supportsExpression": false}
  },
  "allowedParents": ["Trigger"],
  "inherits": "CommandBase",
  "scope": {
    "Lockscreen": true,
    "Wallpaper": true,
    "LongTake": false,
    "Widget": true,
    "ChargingSkin": true
  },
  "deviceSupport": {
    "barPhone": true,
    "foldable": true,
    "tablet": true
  },
  "constraints": [
    {
      "ruleId": "SEM-CMD-001",
      "condition": "element.attrs['play'] != null AND element.attrs['sound'] != null",
      "message": "VideoCommand中play和sound互斥，不能同时存在",
      "severity": "error",
      "suggestedFixes": ["移除play属性", "移除sound属性"]
    }
  ]
}
```

### 6.3 VarArray规则条目Schema

```json
{
  "element": "VarArray",
  "category": "variable",
  "requiredAttrs": [],
  "optionalAttrs": ["type"],
  "attrTypes": {
    "type": {"type": "string", "enumValues": ["number", "string"], "supportsExpression": false}
  },
  "childElements": {
    "Vars": {
      "Var": {
        "requiredAttrs": ["name"],
        "optionalAttrs": ["index"],
        "attrTypes": {
          "name": {"type": "string", "supportsExpression": false},
          "index": {"type": "string", "supportsExpression": true, "expressionKind": "auto"}
        }
      }
    },
    "Items": {
      "Item": {
        "requiredAttrs": [],
        "optionalAttrs": ["value"],
        "attrTypes": {
          "value": {"type": "string", "supportsExpression": false}
        }
      }
    }
  },
  "allowedParents": ["Lockscreen", "Wallpaper", "Widget", "ChargingSkin", "Group"],
  "inherits": null,
  "scope": {
    "Lockscreen": true,
    "Wallpaper": true,
    "LongTake": true,
    "Widget": true,
    "ChargingSkin": true
  },
  "deviceSupport": {
    "barPhone": true,
    "foldable": true,
    "tablet": true
  },
  "constraints": []
}
```

> 注：JSON规则条目中不再包含 `allowedChildren` 字段。父→子方向的关系由 `DefaultRuleRepository.buildChildrenMap()` 从所有元素的 `allowedParents` 反向推导构建索引，通过 `RuleRepository.getAllowedChildren(elementName)` 查询。

### 6.4 Array规则条目Schema

```json
{
  "element": "Array",
  "category": "variable",
  "requiredAttrs": ["indexFlag", "frequency"],
  "optionalAttrs": ["x", "y"],
  "attrTypes": {
    "indexFlag": {"type": "string", "supportsExpression": false, "defaultValue": null},
    "frequency": {"type": "number", "supportsExpression": true, "expressionKind": "number", "defaultValue": null},
    "x": {"type": "number", "supportsExpression": true, "expressionKind": "number", "defaultValue": "0"},
    "y": {"type": "number", "supportsExpression": true, "expressionKind": "number", "defaultValue": "0"}
  },
  "allowedParents": ["Lockscreen", "Wallpaper", "Widget", "ChargingSkin", "Group"],
  "inherits": null,
  "scope": {
    "Lockscreen": true,
    "Wallpaper": true,
    "LongTake": true,
    "Widget": true,
    "ChargingSkin": true
  },
  "deviceSupport": {
    "barPhone": true,
    "foldable": true,
    "tablet": true
  },
  "constraints": []
}
```

> 注：Array作为控件数组容器，同时具有视图通用属性(x,y)。Array的x/y定义整个数组容器在屏幕上的起始偏移，默认为0。

### 6.5 全局变量条目Schema

```json
{
  "name": "battery_level",
  "type": "number",
  "scope": "global",
  "description": "当前电量1-100",
  "accessPattern": "#battery_level",
  "constraints": []
}
```

### 6.6 函数签名条目Schema（独立JSON文件：resources/functions/signatures.json）

```json
{
  "functions": [
    {
      "name": "ifelse",
      "params": [
        {"name": "cond", "type": "number", "isVariadic": false},
        {"name": "y", "type": "number", "isVariadic": false},
        {"name": "z", "type": "number", "isVariadic": true}
      ],
      "returnType": "number",
      "expressionKind": "number"
    },
    {
      "name": "sin",
      "params": [{"name": "x", "type": "number", "isVariadic": false}],
      "returnType": "number",
      "expressionKind": "number"
    },
    {
      "name": "substr",
      "params": [
        {"name": "str", "type": "string", "isVariadic": false},
        {"name": "pos", "type": "number", "isVariadic": false},
        {"name": "len", "type": "number", "isVariadic": false}
      ],
      "returnType": "string",
      "expressionKind": "string"
    }
  ]
}
```

### 6.7 规则条目扩展指南

| 扩展类型 | 方式 | 是否需要编码 |
|---|---|---|
| 新增元素 | 追加元素条目JSON | 否 |
| 新增属性 | 在optionalAttrs/attrTypes中追加 | 否 |
| 新增枚举值 | 在attrTypes.enumValues中追加 | 否 |
| 新增作用域 | 在scope矩阵中追加 | 否 |
| **新增检测逻辑** | 在constraints数组中追加RuleConstraint（含声明式condition） | **否** |
| 新增函数 | 在functions JSON中追加条目 | 否 |
| 复杂约束（如Trigger链结构） | 编写Analyzer并注册到M4引擎 | 是 |

规则ID格式：`[类别]-[子类]-[编号]`，如SYN-001~007(语法), SEM-EXPR-001~006(表达式), SEM-TYPE-001~002(类型), SEM-CMD-001~004(命令), SEM-PERSIST-001~003(持久化), SEM-ARR-001~003(数组), SEM-VAR-001~005(变量), SEM-REF-001~003(引用), SEM-ATTR-001~009(属性), SEM-SCOPE-001~002(作用域), SEM-TRIG-001~002(触发器), SEM-VID-001~005(视频), SEM-IMG-001~003(图片), SEM-GEN-001~002(通用)

CLI可通过`--rule-dir`指定外部规则库目录，实现完全零代码的自定义规则集。

---

## 7. 文档来源参考

### 7.1 本地缓存

所有官方规范文档缓存在 `docs/themes_engine_next/raw_markdown/` 目录下。

### 7.2 官方文档URL映射

| 主题 | 本地文件 | 官方URL |
|---|---|---|
| 锁屏 | themes-engine-next-lock-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-lock-0000002244659534 |
| 桌面 | themes-engine-next-home-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-home-0000002244819386 |
| 百变卡片 | themes-engine-next-fa-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-fa-0000002471234980 |
| 充电动效 | themes-engine-next-charging-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-charging-0000002490002440 |
| 应用位置 | themes-engine-next-scope-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-scope-0000002279698481 |
| 通用属性 | themes-engine-next-base-general-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-general-0000002504354839 |
| 文本 | themes-engine-next-base-text-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-text-0000002471394976 |
| 图片 | themes-engine-next-base-image-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-image-0000002504274921 |
| 动态图片 | themes-engine-next-base-image2-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-image2-0000002471234986 |
| 视频 | themes-engine-next-base-video-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-video-0000002504354849 |
| 时间 | themes-engine-next-base-time-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-time-0000002471394986 |
| 日期 | themes-engine-next-base-datetime-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-datetime-0000002504274931 |
| 倒计时 | themes-engine-next-base-countdowntime-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-countdowntime-0000002471234998 |
| 数字图片 | themes-engine-next-base-imagenumber-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-imagenumber-0000002504354859 |
| 串联图片 | themes-engine-next-base-imageseries-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-imageseries-0000002471394996 |
| 帧解锁视图 | themes-engine-next-base-sourceimage-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-sourceimage-0000002504274941 |
| 遮罩 | themes-engine-next-base-mask-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-mask-0000002471235010 |
| 图片混合 | themes-engine-next-base-groupimage-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-groupimage-0000002504354869 |
| 几何图形 | themes-engine-next-base-figure-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-figure-0000002471395006 |
| 路径解析 | themes-engine-next-base-pathutil-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-pathutil-0000002504274953 |
| 视图切换 | themes-engine-next-base-swiper-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-swiper-0000002471235020 |
| 视图组 | themes-engine-next-base-group-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-group-0000002504354879 |
| 按钮 | themes-engine-next-base-button-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-button-0000002471395018 |
| 自定义变量 | themes-engine-next-base-var-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-var-0000002504274963 |
| 全局变量 | themes-engine-next-base-globalvar-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-globalvar-0000002471235030 |
| 变量数组 | themes-engine-next-base-vararray-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-vararray-0000002504354889 |
| 控件数组 | themes-engine-next-base-array-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-array-0000002471395028 |
| 数值表达式 | themes-engine-next-base-exp-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-exp-0000002504274983 |
| 字符串表达式 | themes-engine-next-base-stringexp-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-stringexp-0000002471235050 |
| 基础命令 | themes-engine-next-base-command-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-command-0000002504354913 |
| 变量命令 | themes-engine-next-base-variablecommand-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-variablecommand-0000002471395064 |
| 视频命令 | themes-engine-next-base-videocommand-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-videocommand-0000002504354923 |
| 声音命令 | themes-engine-next-base-soundcommand-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-soundcommand-0000002471395052 |
| 可见性命令 | themes-engine-next-base-visibilitycommand-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-visibilitycommand-0000002504274995 |
| Intent命令 | themes-engine-next-base-intentcommand-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-intentcommand-0000002471235064 |
| 通用命令 | themes-engine-next-base-externcommand-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-externcommand-0000002504275007 |
| 命令组 | themes-engine-next-base-groupcommand-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-groupcommand-0000002471235074 |
| 周期命令 | themes-engine-next-base-cyclecommand-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-cyclecommand-0000002504354935 |
| 全景换肤 | themes-engine-next-base-stylecommand-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-stylecommand-0000002471235086 |
| 天气刷新 | themes-engine-next-base-refreshweather-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-refreshweather-0000002471395074 |
| 健康刷新 | themes-engine-next-base-refreshhealthy-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-refreshhealthy-0000002504275017 |
| 亮屏时间 | themes-engine-next-base-keepscreenon-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-keepscreenon-0000002522202229 |
| 碰一碰 | themes-engine-next-base-collaboration-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-collaboration-0000002489842474 |
| 线性振动 | themes-engine-next-vibratecommand-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-vibratecommand-0000002499411342 |
| 透明度动画 | themes-engine-next-2d-alphaanimation-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-2d-alphaanimation-0000002504354989 |
| 位移动画 | themes-engine-next-2d-positionanimation-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-2d-positionanimation-0000002471395132 |
| 旋转动画 | themes-engine-next-2d-rotationanimation-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-2d-rotationanimation-0000002504275071 |
| 缩放动画 | themes-engine-next-2d-sizeanimation-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-2d-sizeanimation-0000002471235140 |
| 帧动画 | themes-engine-next-2d-sourceanimation-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-2d-sourceanimation-0000002504354999 |
| 变量动画 | themes-engine-next-2d-variableanimation-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-2d-variableanimation-0000002471395144 |
| 注意事项 | themes-engine-next-precautions-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-precautions-0000002504275099 |
| 基础功能索引 | themes-engine-next-base-0000002279818413.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-0000002279818413 |
| 2D基础动效 | themes-engine-next-2d-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-2d-0000002471235130 |
| 2D高级特效 | themes-engine-next-2da-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-2da-0000002504275081 |
| 3D | themes-engine-next-3d-*.md | https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-3d-0000002504355019 |
