# 变量：全局变量<GlobalVariable>

功能概述

全局变量是指在引擎中已经预置好的固定名字的变量，可供直接使用，全局变量为放置在项目GlobalManager中的一系列全局变量，在整个项目的任意位置都能够调用获取最新的全局变量的值，使用#取数值，使用@取字符串。

XML规范

```
<Var name="" expression="#screen_width" />
```

参数说明

**触摸**

| **参数** | **注释** |
| --- | --- |
| touch_x | 当前触摸点的x坐标 |
| touch_y | 当前触摸点的y坐标 |
| touch_begin_x | 按下屏幕时的初始x坐标 |
| touch_begin_y | 按下屏幕时的初始y坐标 |
| touch_begin_time | 触摸开始的时间，单位ms，是当前时间与协调世界时 1970 年 1 月 1 日午夜之间的时间差。 |

**解锁**

| **参数** | **注释** |
| --- | --- |
| name.move_x | 解锁部件在x方向的偏移 |
| name.move_y | 解锁部件在y方向的偏移 |
| name.move_dist | 解锁部件移动的距离 |
| name.state | 解锁部件的状态： NORMAL_STATE（0）PRESSED_STATE（1）REACHED_STATE（2）INVISIBLE_STATE（3） |

**时间/日期**

| **参数** | **注释** |
| --- | --- |
| year | 年 |
| month | 月（取值范围是0~11，0表示一月，1表示二月，以此类推） |
| date | 日 |
| day_of_week | 星期（1表示星期日，2表示星期一，等等） |
| hour | 当前小时，显示24小时制 |
| hour12 | 12小时制小时数，显示与系统时间制一致 |
| hour24 | 24小时制小时数，显示与系统时间制一致 |
| minute | 分钟 |
| ishour12 | 字符串类型，是否是12小时制（true，false） |
| lunarYear | 农历年 |
| lunarMonth | 农历月（取值范围是1~12，1表示一月，2表示二月，以此类推，和month有区别，需注意） |
| lunarDay | 农历日 |
| system.time.hour1 | 小时的第一位，例如09:00显示0，时间在12小时制12点显示1，24点显示0 |
| system.time.hour2 | 小时的第二位，例如09:00显示9，时间在12小时制12点显示2，24点显示0 |
| system.time.min1 | 分钟的第一位，例如09:30显示3 |
| system.time.min2 | 分钟的第二位，例如09:30显示0 |
| system.time.ampm | ampm显示，根据不同的区域和语言显示不同的结果。该值当ishour12为true时才有值显示，为false时显示为空 |

![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/9/v3/HcX5PHNiRr-R5-uEohtyDw/caution_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010751Z&HW-CC-Expire=86400&HW-CC-Sign=88A1AE562E21820352E0CA959B1F93B9252DCA3745C11D2910C3DED2E83BFA6A)

针对时间、日期、星期等变量禁止使用persist/globalPersist/styleGlobalPersist

**电量**

| **参数** | **类型** | **注释** |
| --- | --- | --- |
| battery_level | 数值 | 当前电量，1~100 |
| battery_state | 数值 | 电池的状态： Normal(0)Charging(1)BatteryLow(2)BatteryFull(3) |

**系统深色模式**

| **参数** | **类型** | **注释** |
| --- | --- | --- |
| darkMode | 数值 | 当前模式：1 浅色模式，2 深色模式，0 不支持任何模式 |

**屏幕宽高**

| **参数** | **类型** | **注释** |
| --- | --- | --- |
| screen_width | 数值 | 虚拟屏幕宽度 |
| screen_height | 数值 | 虚拟屏幕高度 |

**图片宽高**

| **参数** | **类型** | **注释** |
| --- | --- | --- |
| name.actual_w | 数值 | 图片实际宽度 |
| name.actual_h | 数值 | 图片实际高度 |

**文本宽高**

| **参数** | **类型** | **注释** |
| --- | --- | --- |
| name.text_width | 数值 | 文本的宽度，name为文本功能中指定的name，例如：可通过a.text_width取得a文本的文本宽度 |
| name.text_height | 数值 | 文本的高度 |

**组件（**

**其中name都为组件的名字)**

| **参数** | **类型** | **注释** |
| --- | --- | --- |
| name.visibility | 数值 | 组件的可见性，1为可见 |
| name.actual_x/name.actual_y | 数值 | 元素实际x/y坐标 |
| name.actual_w/name.actual_h | 数值 | 元素实际高度与宽度 |

**视频**

| **参数** | **类型** | **注释** |
| --- | --- | --- |
| src.state | 数值 | src对应的视频状态。 0——IDLE，闲置状态（透明视频不支持） 1——INITIALIZED，资源初始化 2——PREPARED，已准备状态 3——PLAYING，正在播放状态 4——PAUSED，暂停状态（透明视频不支持） 5——COMPLETED，播放至结尾状态 6——STOPPED，停止状态（透明视频不支持） 7——RELEASED，销毁状态 |
| src.currentTime | 数值 | src对应的视频的播放进度，单位ms（透明视频不支持） |

更多信息请参考：

[https://developer.huawei.com/consumer/cn/doc/harmonyos-references/arkts-apis-media-t#avplayerstate9](https://developer.huawei.com/consumer/cn/doc/harmonyos-references/arkts-apis-media-t#avplayerstate9)

**锁屏灭屏时间**

| **参数** | **类型** | **注释** |
| --- | --- | --- |
| screenOnLeftTime | 数值 | 距离锁屏灭屏的时间，单位：秒 |

**AI语音互动**

| **参数** | **类型** | **注释** |
| --- | --- | --- |
| matchSkill_value | 字符串 | 接收小艺处理的语音输入匹配出的能力值。 |

**用户使用设备间隔时长**

| **参数** | **类型** | **注释** |
| --- | --- | --- |
| public_deviceUsageIntervalTime | 数值 | 用户使用设备间隔时长（上次灭屏到本次亮屏），单位：秒 |

**碰一碰**

| **参数** | **类型** | **注释** |
| --- | --- | --- |
| enableCollaboration | 数值 | 碰一碰能力是否开启，0关闭，1开启，可以动态修改参数值控制卡片是否启用碰一碰协同能力（仅限百变卡片） |

**情绪感知**

| **参数** | **类型** | **注释** |
| --- | --- | --- |
| emotionValue | 数值 | 情绪值：0 - 愉悦； 2 - 平静； 4 - 不愉悦；-1 - 未感知用户情绪 |

**隔空手势互动**

| **参数** | **类型** | **注释** |
| --- | --- | --- |
| dynamicSwingValue | 数值 | 实时的动态手势结果 |
| staticSwingValue | 数值 | 实时的静态手势结果 |

动态手势结果枚举说明

| **枚举值** | **枚举值含义** |
| --- | --- |
| -1 | 未识别到用户手势，默认手势 |
| 0 | 抓屏 |
| 1 | 下翻 |
| 4 | 上翻 |
| 8 | 释放 |

静态手势结果枚举说明

| **枚举值** | **枚举值含义** |
| --- | --- |
| -1 | 未识别到用户手势，默认手势 |
| 1 | 掌型 |
| 2 | 剪刀 |
| 3 | 拳型 |
| 4 | 比心 |

**场景感知**

| **参数** | **类型** | **注释** |
| --- | --- | --- |
| Scenarios.ID.text | string | 场景文案，示例： 1）你的心情，今日主打歌是青春不打烊。 2）直达追剧第一线：新剧热综实时更新，弹幕吐槽更有趣。 |
| Scenarios.ID.jumpable | number | 是否支持跳转应用或元服务，1表示支持，0表示不支持，支持跳转时，可配合ScenarioIntentCommand使用实现跳转功能，详细用法参考ScenarioIntentCommand场景感知跳转命令 |
| Scenarios.ID.appName | string | 关联跳转的应用或元服务名称，示例： 1）主题 2）游戏中心 |
| Scenarios.topId | string | 优先级最高的服务分类ID |

![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/fb/v3/dvAHZJBpRveqr2J4woe3cg/note_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010751Z&HW-CC-Expire=86400&HW-CC-Sign=4E988EA2D3922A4BFBBA5256A71DC452BE2EF64F77F9E16F732E85F5C2DE2D3C)

以上列表中参数名称中的ID为服务分类ID，如想要获取生活服务的场景文案和是否支持跳转，全局变量名称为Scenarios.10000020.text和Scenarios.10000020.jumpable。

服务分类列表

| **服务分类****ID** | **服务分类** | **是否必做** |
| --- | --- | --- |
| 10000013 | 影视与直播 | 必做 |
| 10000020 | 生活服务 | 必做 |
| 10000023 | 美食 | 必做 |
| 10000025 | 出行导航 | 必做 |
| 40000999 | 游戏 | 必做 |

应用示例

**示例一**

触摸屏幕时会显示它的坐标。

```
<Text x="40" y="200" alignV="top" color="#ffffff" size="44" text="当前触摸点的x坐标touch_x: +#touch_x " />
<Text x="40" y="240" alignV="top" color="#ffffff" size="44" text="当前触摸点的y坐标touch_y: +#touch_y " />
<Text x="40" y="280" alignV="top" color="#ffffff" size="44" text="按下屏幕时的初始x坐标touch_begin_x:+#touch_begin_x " />
<Text x="40" y="320" alignV="top" color="#ffffff" size="44" text="按下屏幕时的初始y坐标touch_begin_y:+#touch_begin_y " />
```

**示例二**

获取系统的日期、时间、一周中的第几天、12和24小时制、时分秒、是否为12小时制。

```
<Text x="40" y="500" alignV="top" color="#ffffff" size="44" format=" year：%d年" paras="#year" />
<Text x="40" y="540" alignV="top" color="#ffffff" size="44" format=" month：%d月" paras="#month+1" />
<Text x="40" y="580" alignV="top" color="#ffffff" size="44" format=" date：%d日" paras="#date" />
<Text x="40" y="660" alignV="top" color="#ffffff" size="44" format=" day_of_week：%d " paras="#day_of_week-1" />
<Text x="40" y="700" alignV="top" color="#ffffff" size="44" format="hour：%d" paras="#hour" />
<Text x="40" y="740" alignV="top" color="#ffffff" size="44" format="hour12：%d" paras="#hour12" />
<Text x="40" y="780" alignV="top" color="#ffffff" size="44" format="hour24：%d" paras="#hour24" />
<Text x="40" y="820" alignV="top" color="#ffffff" size="44" format="minute：%d" paras="#minute" />
<Text x="40" y="900" alignV="top" color="#ffffff" size="44" format="ishour12：%d" paras="#ishour12" />
```
