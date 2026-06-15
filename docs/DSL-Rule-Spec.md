# HarmonyOS Theme Engine DSL - 规则规范文档

## 1. DSL语言概述

### 1.1 文件格式

- XML格式，必须包含声明头：`<?xml version="1.0" encoding="utf-8"?>`
- DSL文件识别：基于根元素标签判定

### 1.2 根元素与应用位置

DSL有4种应用位置，每种对应一个根元素标签：

| 根元素 | 应用位置 | 关键属性 | 说明 |
|---|---|---|---|
| `<Lockscreen>` | 锁屏 | frameRate(可选,number), screenWidth(可选,number) | frameRate默认60fps；screenWidth定义虚拟屏幕宽度，所有坐标基于此虚拟坐标系 |
| `<Wallpaper>` | 桌面 | screenWidth(可选,number) | 继承锁屏除解锁交互外的所有功能 |
| `<Widget>` | 百变卡片 | screenWidth(必填,number), screenHeight(必填,number), frameRate(可选,number) | 卡片尺寸固定：1x2=(1372,530), 2x2=(1384,1384), 2x4=(1372,640), 4x4=(1384,1440) |
| `<ChargingSkin>` | 充电动效 | screenWidth(可选,number) | 替换系统充电效果 |

示例：
```xml
<?xml version="1.0" encoding="utf-8"?>
<Lockscreen frameRate="60" screenWidth="1080">
  ...
</Lockscreen>
```

> 注：原规范中还包含一镜到底(LongTake)应用位置，但无独立根元素标签，通常作为锁屏的扩展场景使用。

---

## 2. 元素目录

### 2.1 视图元素

#### 2.1.1 通用属性

所有视图元素共享一组通用属性（部分元素有例外，见下表）：

| 属性 | 类型 | 必填 | 说明 | 约束 |
|---|---|---|---|---|
| name | string | 选填 | 元素变量名，@name取字符串值 | |
| x | number | 选填 | 相对屏幕左上角x坐标(px)，默认0，支持表达式 | |
| y | number | 选填 | 相对屏幕左上角y坐标(px)，默认0，支持表达式 | |
| width(w) | number | 选填 | 显示宽度(px)，支持表达式 | |
| height(h) | number | 选填 | 显示高度(px)，支持表达式 | |
| pivotX(centerX) | number | 选填 | 旋转点X坐标(px)，支持表达式 | |
| pivotY(centerY) | number | 选填 | 旋转点Y坐标(px)，支持表达式 | |
| rotation(angle) | number | 选填 | 旋转角度(360度制)，支持表达式 | |
| rotationX(angleX) | number | 选填 | X轴旋转角度，支持表达式 | |
| rotationY(angleY) | number | 选填 | Y轴旋转角度，支持表达式 | |
| alpha | number | 选填 | 透明度0-255，默认255；<0取0，>255取255，支持表达式 | |
| visibility | number | 选填 | <=0不可见，>0可见，默认1，支持表达式 | |
| category | string | 选填 | 充电状态显示："Normal","Charging","BatteryLow","BatteryFull" | |
| align | string | 选填 | 水平对齐：left,center,right | |
| alignV | string | 选填 | 垂直对齐：top,center,bottom | |
| enableMove | string | 选填 | 是否可移动：true/false或0/非0，默认0 | |
| moveRect | string | 选填 | 移动区域："minH,minV,maxH,maxV"，不支持表达式 | |
| active | number | 选填 | 激活状态，0=不激活(视图不显示)，默认1 | |

**通用属性支持矩阵：**

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
| 视图组 | `<Group>` | name,x,y,w,h,alpha,rotation/angle,visibility,clip,layered,align,alignV | clip=true裁剪超出w/h范围的内容；clip=false测量内容大小；layered=true时最后一个Image需有hybridMode |

子元素：任意视图元素 + 动画元素（PositionAnimation, RotationAnimation, AlphaAnimation, SizeAnimation等）

### 2.3 控件元素

| 元素 | 标签 | 属性 | 必填子元素 | 应用位置支持 |
|---|---|---|---|---|
| 按钮 | `<Button>` | name,w,h,x,y,visibility | `<Trigger>` | Lockscreen√, Wallpaper×, Widget√, ChargingSkin× |

### 2.4 变量元素

#### `<Var>` - 自定义变量

```
<Var name="" expression="" type="" threshold="" persist="" index="" values="" size="" const="" />
```

| 属性 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | string | **必填** | 变量名，调用时用#name(数值)或@name(字符串) |
| expression | string | 选填 | 表达式或常量；字符串常量需多套单引号：expression="'my string'" |
| type | string | 选填 | number/string/number[]/string[]，默认number |
| threshold | number | 选填 | 阈值，变化超阈值触发Trigger |
| persist | string | 选填 | 持久化，默认false；设true后优先读取本地值 |
| index | string | 选填 | 数组索引(从0开始) |
| values | string | 选填 | "val,val,..."批量赋值；与size冲突时优先取size |
| size | number | 选填 | 数组长度；与values冲突时优先取size |
| const | string | 选填 | true/false，赋值后不再改变，默认false |

**⚠ 禁止对时间/日期/星期变量使用persist/globalPersist/styleGlobalPersist**

Var可包含子元素：`<Trigger>`（threshold触发时）、`<VariableAnimation>`

#### `<GlobalVariable>` - 全局变量

引擎预置变量，直接使用，用#取数值，@取字符串。详见第2.5节全局变量目录。

#### `<VarArray>` - 变量数组

数组变量声明机制，集成在Var标签中通过type="number[]"或type="string[]"实现。

#### `<Array>` - 控件数组

控件数组，用于批量创建相似控件。

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
| | lunarYear, lunarMonth, lunarDay | number | 农历；lunarMonth取值1-12 |
| | system.time.hour1, hour2, min1, min2 | number | 时间各位数字 |
| | system.time.ampm | string | AM/PM标识，ishour12为true时有值 |
| **电量** | battery_level | number | 1-100 |
| | battery_state | number | Normal(0),Charging(1),BatteryLow(2),BatteryFull(3) |
| **屏幕** | screen_width, screen_height | number | 虚拟屏幕宽高 |
| **深色模式** | darkMode | number | 1浅色,2深色,0不支持 |
| **情绪** | emotionValue | number | 0愉悦,2平静,4不愉悦,-1未感知 |
| **组件状态** | name.visibility | number | 组件可见性(1=可见) |
| | name.actual_x/y/w/h | number | 元素实际位置/尺寸 |
| | name.text_width/text_height | number | 文本实际宽高 |
| | name.actual_w/actual_h | number | 图片实际宽高 |
| **视频** | src.state | number | IDLE(0),INITIALIZED(1),PREPARED(2),PLAYING(3),PAUSED(4),COMPLETED(5),STOPPED(6),RELEASED(7) |
| | src.currentTime | number | 视频播放进度(ms) |
| **灭屏时间** | screenOnLeftTime | number | 距离灭屏时间(秒) |
| **手势** | dynamicSwingValue | number | 动态手势：-1未识别,0抓屏,1下翻,4上翻,8释放 |
| | staticSwingValue | number | 静态手势：-1未识别,1掌型,2剪刀,3拳型,4比心 |
| **场景感知** | Scenarios.ID.text | string | 场景文案 |
| | Scenarios.ID.jumpable | number | 1可跳转,0不可 |
| | Scenarios.ID.appName | string | 关联应用名 |
| | Scenarios.topId | string | 最高优先级服务ID |

### 2.6 命令元素

| 元素 | 标签 | 必填属性 | 选填属性 | 说明 |
|---|---|---|---|---|
| 基础命令 | `<Command>` | target, value | condition, delay, delayCondition | target格式"name.property"，value: visibility→true/false/toggle; animation→play/stop |
| 变量命令 | `<VariableCommand>` | name, expression | type, condition, delay, delayCondition | **不支持persist属性** |
| 视频命令 | `<VideoCommand>` | name, src | play, sound, seekTime | **sound和play互斥：有sound时不可用play** |
| 声音命令 | `<SoundCommand>` | sound, volume | loop, keepCur, play | 音频>1MB会被截断；loop默认false；volume值0-1 |
| 可见性命令 | `<VisibilityCommand>` | visibility(表达式) | / | visibility为表达式，>0可见，<=0不可见 |
| Intent命令 | `<IntentCommand>` | action | package, class | 包名类名需适配NEXT |
| 通用命令 | `<ExternCommand>` | / | / | 通用命令分发 |
| 命令组 | `<GroupCommand>` | / | / | 组合多条命令 |
| 命令组 | `<GroupCommands>` | / | / | 组合多条命令 |
| 周期命令 | `<CycleCommand>` | indexFlag | frequency, begin, end, cycleCondition | 配合Array使用，子元素VariableCommand |
| 全景换肤 | `<StyleCommand>` | index | name, contentTypes, condition | index不支持表达式；耗时1.5-2秒，避免频繁切换；styleGlobalPersist初始值默认0 |
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
- **condition**：条件表达式，非0/true时执行，0/false时不执行
- **delay**：延迟毫秒数
- **delayCondition**：延迟条件，默认true/1生效

### 2.7 Trigger元素

| 元素 | 标签 | 必填属性 | 出现位置 |
|---|---|---|---|
| 触发器 | `<Trigger>` | action | Button, Unlocker, Slider, Var(threshold触发) |

**action合法值：** `down`(按下), `up`(抬起), `double`(双击), `click`(点击), `long`(长按), `resume`(亮屏), `pause`(熄屏)

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

### 3.1 数值表达式

- 返回值：浮点数
- 变量引用：`#varName`（数值型），数组：`#arr[expression]`
- 运算符：`+ - * / %`
- **⚠ `-#varName`语法无效，必须写为`-1*#varName`或`0-#varName`**
- **⚠ 数值超过7位有精度问题**

**函数列表：**

| 函数 | 说明 |
|---|---|
| sin(x), cos(x), tan(x) | 三角函数，x为弧度 |
| asin(x), acos(x), atan(x) | 反三角函数 |
| sqrt(x) | 开平方，x为负返回0 |
| abs(x) | 绝对值 |
| min(x,y), max(x,y) | 最小/最大值 |
| digit(x,pos) | 取数字第pos位（从右往左，索引从1起），digit(12345,2)=4 |
| round(x) | 四舍五入取整，round(4.5)=5, round(-4.5)=-4 |
| int(x) | 舍弃小数部分，int(4.5)=4 |
| rand() | 0-1随机浮点数 |
| eq(x,y), ne(x,y) | 相等/不相等判断，返回0或1 |
| ge(x,y), gt(x,y), le(x,y), lt(x,y) | 比较，返回0或1 |
| isnull(x) | 变量是否无值，返回0或1；支持#var和@var |
| not(x) | x=0返回1，x≠0返回0 |
| ifelse(x,y,z) | x>0返回y，否则返回z |
| ifelse(x1,y1,x2,y2,...,z) | 多条件，x1>0→y1, x2>0→y2, ...→z |
| pow(x,y) | x的y次方 |
| len(x) | 数字位数，len(1234)=4, len(-123.123)=6 |

### 3.2 字符串表达式

- 返回值：字符串
- 变量引用：`@varName`（字符串型），`#varName`（数值型嵌入字符串需用`{expr}`花括号）
- 字符串必须使用**单引号**：`'hello'`
- `+`表示**拼接**而非加法
- 数组：`@arr[expression]`

**⚠ 数值表达式嵌入字符串表达式需加花括号：** `srcExp="number/hour/{int(#system.time.hour1)}_{int(#aniTime)}.png"`

**⚠ 字符串表达式中数值计算不能以#开头：** `#num*10`会被认为取名为"num*10"的变量，正确写法`10*#num`

**函数列表：**

| 函数 | 说明 |
|---|---|
| substr(str,pos,len) | 子串，索引从0起，substr('你好呀',1,2)='好呀' |
| strIsEmpty(str) | 空串→"true"，否则"false" |
| strIndexOf(str1,str2) | str2在str1中首次位置，未找到→"-1" |
| strLastIndexOf(str1,str2) | str2在str1中最后位置，未找到→"-1" |
| strContains(str1,str2) | 包含→"true"，否则"false" |
| strReplaceAll(str1,str2,str3) | 将str1中所有str2替换为str3 |
| preciseeval(str,precision) | 计算字符串公式，precision为小数位数；其后不能再用运算符或+ |
| formatDate('format',@time_sys) | 返回指定格式时间字符串 |
| plus(a,b) | 返回a+b整数的字符串，a/b可为字符串或数值 |
| ifelse(x1,y1,...,z) | xi≠0返回yi字符串，否则z |
| strEqual(str1,str2) | 相等→"true"，否则"false" |
| argb(a,r,g,b) | 返回8位16进制颜色字符串 |

---

## 4. 作用域约束

### 4.1 应用位置支持矩阵

每个元素在5种应用位置中有不同的支持状态：

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
1. 确定DSL文件根元素→确定应用位置
2. 查询元素在对应应用位置的支持矩阵
3. 不支持→报告错误SEM-SCOPE-001

---

## 5. 错误检测规则

### 5.1 语法错误（SYN类）

| 规则ID | 检测内容 | 示例 | 严重级别 |
|---|---|---|---|
| SYN-001 | XML标签未闭合 | `<Tag>...</Tag>` 缺少闭合 | error |
| SYN-002 | 标签嵌套违反父子约束 | Trigger出现在Var外 | error |
| SYN-003 | 属性引号缺失 | `attr=value` | error |
| SYN-004 | 未知元素标签 | `<UnknownTag>` | error |
| SYN-005 | 未知属性名 | Button上的`unknownAttr="value"` | warning |
| SYN-006 | 缺失必填属性 | Var缺少`name`属性 | error |
| SYN-007 | 属性值类型错误 | `threshold="abc"`（期望number） | error |
| SYN-008 | 枚举值错误 | Trigger `action="invalidAction"` | error |
| SYN-009 | 缺少XML声明头 | 文件缺少`<?xml version="1.0" encoding="utf-8"?>` | warning |
| SYN-010 | 根元素标签错误 | 使用未定义的根元素标签 | error |

### 5.2 语义/规则错误（SEM类）

| 规则ID | 检测内容 | 示例 | 严重级别 |
|---|---|---|---|
| SEM-SCOPE-001 | 元素不支持当前应用位置 | Button出现在Wallpaper中 | error |
| SEM-SCOPE-002 | 元素不支持当前设备类型 | 检查设备类型矩阵 | warning |
| SEM-EXPR-001 | 数值表达式使用`-#var`语法 | `x="-#w"` → 应为`x="0-#w"`或`x="-1*#w"` | error |
| SEM-EXPR-002 | 数值表达式值超过7位精度限制 | `#var=12345678` → 精度问题 | warning |
| SEM-EXPR-003 | 字符串表达式中数值计算以#开头 | `#num*10` → 应为`10*#num` | error |
| SEM-EXPR-004 | 字符串表达式未使用单引号 | `"hello"` → 应为`'hello'` | error |
| SEM-EXPR-005 | 字符串表达式嵌入数值表达式缺少花括号 | `int(#system.time.hour1)` 在字符串中 → 应为`{int(#system.time.hour1)}` | error |
| SEM-EXPR-006 | preciseeval后使用运算符或+连接符 | `preciseeval({1/3},3)+'abc'` → 禁止 | error |
| SEM-PERSIST-001 | 时间/日期/星期变量使用persist | `<Var name="hour" persist="true"/>` | error |
| SEM-PERSIST-002 | VariableCommand使用persist属性 | `<VariableCommand persist="true"/>` | error |
| SEM-PERSIST-003 | styleGlobalPersist初始值默认为0 | 依赖expression初始赋值无效 | warning |
| SEM-CMD-001 | VideoCommand中sound和play共存 | `<VideoCommand name="v" src="x" play="true" sound="0.5"/>` | error |
| SEM-CMD-002 | SoundCommand音频文件>1MB | 引用>1MB音频文件 | warning |
| SEM-CMD-003 | StyleCommand频繁切换 | 理论耗时1.5-2秒，检测切换频率 | warning |
| SEM-CMD-004 | StyleCommand index使用表达式 | `index="#styleIndex"` → index不支持表达式 | error |
| SEM-ATTR-001 | alpha值超出0-255范围 | `alpha="300"` → 自动clamp为255 | warning |
| SEM-ATTR-002 | visibility值语义 | <=0不可见，>0可见 | info |
| SEM-ATTR-003 | category枚举值不合法 | `category="InvalidState"` | error |
| SEM-ATTR-004 | Group clip=true但无w/h | clip=true需要w/h裁剪区域 | warning |
| SEM-ATTR-005 | Group layered=true但最后一个Image无hybridMode | layered场景缺少hybridMode | error |
| SEM-VAR-001 | 变量未用Var标签定义直接使用 | 使用未定义的#varName | warning |
| SEM-VAR-002 | Var声明字符串常量缺少双套单引号 | `expression="my string"` → 应为`expression="'my string'"` | error |
| SEM-VAR-003 | Var values与size同时存在 | values优先于size，可能非预期 | warning |
| SEM-VAR-004 | 数组变量使用前未声明size | 直接使用`#arr[0]`但未声明size | error |
| SEM-REF-001 | 引用未定义的变量名 | `#nonexistentVar` | error |
| SEM-REF-002 | 引用未定义的元素name | Command `target="ghost.visibility"` | error |
| SEM-REF-003 | 重复name定义 | 多个元素使用同一name | error |
| SEM-TRIG-001 | Trigger action值不在合法集合中 | `action="tap"` → 合法值：down/up/double/click/long/resume/pause | error |
| SEM-TRIG-002 | Button缺少Trigger子元素 | `<Button>` 无 `<Trigger>` | error |
| SEM-IMG-001 | ImageNumber资源不从0开始命名或序列缺失 | 资源命名不连续 | error |
| SEM-VID-001 | 视频文件>25MB | 视频文件超过大小限制 | error |
| SEM-VID-002 | 透明视频非mp4格式 | isTransparent=true但src非mp4 | error |
| SEM-VID-003 | 视频分辨率>4096x4096 | 超出最大分辨率 | error |
| SEM-VID-004 | 全屏Video缺少IsFullScreenNode(折叠屏/平板) | 折叠屏/平板场景未设置 | warning |
| SEM-VID-005 | Video defaultBitmap图片不存在 | 资源目录找不到对应图片→黑屏 | error |
| SEM-GEN-001 | 变量值为负数时前面未补0 | `x="-#w"` → 应为`x="0-#w"` | error |
| SEM-GEN-002 | 变量计算精度误差 | 建议添加偏移量 | warning |

### 5.3 语义相似度匹配

当检测到SYN-004(未知元素)或SYN-005(未知属性)时：
- 基于编辑距离推荐最接近的合法标签/属性名
- 优先级：完全匹配 > 编辑距离匹配 > 语义匹配

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
    "name": "string",
    "expression": "string",
    "type": { "enum": ["number", "string", "number[]", "string[]"] },
    "threshold": "number",
    "persist": { "enum": ["true", "false"] },
    "index": "string",
    "values": "string",
    "size": "number",
    "const": { "enum": ["true", "false"] }
  },
  "allowedParents": ["Lockscreen", "Wallpaper", "Widget", "ChargingSkin", "Group"],
  "allowedChildren": ["Trigger", "VariableAnimation"],
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
      "attr": "persist",
      "condition": "type in ['time','date','week']",
      "message": "禁止对时间/日期/星期变量使用persist"
    }
  ]
}
```

### 6.2 命令规则条目Schema

```json
{
  "element": "VideoCommand",
  "category": "command",
  "requiredAttrs": ["name", "src"],
  "optionalAttrs": ["play", "sound", "seekTime"],
  "attrTypes": {
    "name": "string",
    "src": "string",
    "play": "expression",
    "sound": "expression",
    "seekTime": "number"
  },
  "allowedParents": ["Trigger"],
  "allowedChildren": [],
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
      "condition": "play != null AND sound != null",
      "message": "VideoCommand中play和sound互斥，不能同时存在"
    }
  ]
}
```

### 6.3 全局变量条目Schema

```json
{
  "name": "battery_level",
  "type": "number",
  "scope": "global",
  "description": "当前电量1-100",
  "accessPattern": "#battery_level",
  "constraints": [
    {
      "ruleId": "SEM-PERSIST-001",
      "condition": "time/date/week category",
      "message": "禁止persist"
    }
  ]
}
```

### 6.4 规则条目扩展指南

- 新增元素：追加元素条目，填写完整约束字段
- 新增属性：在对应元素的optionalAttrs/attrTypes中追加
- 新增检测规则：实现Analyzer并注册到检测引擎，在constraints中追加条目
- 规则ID格式：`[类别]-[子类]-[编号]`，如SYN-001, SEM-EXPR-001

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
