# 命令：自定义亮屏时间<KeepScreenOnCommand>

功能概述

锁屏灭屏命令，通过该命令可以自定义锁屏亮屏的时间。

**创意场景**

1、冥想类主题。

2、长时间的游戏互动场景。

支持范围

**起始规范版本：**

HarmonyOS 6.0

**是否平台特性：**

否

|  | 锁屏（Lockscreen） | 桌面（Wallpaper） | 一镜到底（LongTake） | 百变卡片（Widget） | 充电动效（ChargingSkin） |
| --- | --- | --- | --- | --- | --- |
| 是否支持 | √ | x | x | x | x |

|  | 直板机 | 折叠屏 | 平板 |
| --- | --- | --- | --- |
| 是否支持 | √ | √ | √ |

XML规范

```
<KeepScreenOnCommand action="start" duration="{10000}"/>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| action | 字符串 | 必填 | 取值：start（开始亮屏）/reset（重置为系统默认设置），默认：start。 |
| duration | 数值 | 选填 | 单位：ms毫秒。控制锁屏在一定时间内抑制亮屏，不自动灭屏，限制：最小值10秒，最大值 10分钟。默认：10s |

![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/79/v3/hm4ypQONRgG5gHdW2Ffkxw/caution_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010801Z&HW-CC-Expire=86400&HW-CC-Sign=9E5DF5FE7182A093B4B10679C394A1CCF89664187F878140BD93E498B1C5F02E)

1. 亮屏时间，超过10分钟，按10分钟处理；小于10s，按10s处理。

2. 多个action为start的保持亮屏命令同时执行，只执行第一个；action为reset的命令执行后，可再次执行start命令。

应用示例

示例：通过点击事件锁屏延时10秒灭屏。

```
<Button x="600" y="500" h="250" w="260">
       <Trigger action="down">
              <KeepScreenOnCommand action="start" duration="100000"/>
       </Trigger>
</Button>
```
