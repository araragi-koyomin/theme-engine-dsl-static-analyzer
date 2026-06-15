# 适配功能：充电状态<BatteryCharging>

功能概述

充电状态功能可以获取当前手机电量状态，并根据文本Text中的category属性来决定是否展示该文本。category中可包括"Normal", "Charging", "BatteryLow", "BatteryFull"四种状态，分别表示电量正常，正在充电，电量不足和电量充满。如果Text中category属性中的电量状态与当前手机状态匹配，则显示该条文本信息；匹配不上，则不显示。

支持范围

**起始规范版本：**

HarmonyOS 5.0

**是否平台特性：**

否

|  | 锁屏（Lockscreen） | 桌面（Wallpaper） | 一镜到底（LongTake） | 百变卡片（Widget） | 充电动效（ChargingSkin） |
| --- | --- | --- | --- | --- | --- |
| 是否支持 | √ | √ | √ | √ | √ |

|  | 直板机 | 折叠屏 | 平板 |
| --- | --- | --- | --- |
| 是否支持 | √ | √ | √ |

XML规范

```
<Text category="" text="">
    <!-- category支持四种状态：BatteryFull（电量充满）、Charging（正在充电）、BatteryLow（电量不足）、Normal（电量正常） -->
</Text>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| category | 字符串 | 必填 | 设置电量状态，当手机电量状态与category中设置的值匹配时，则显示该条文本信息。支持四种状态：BatteryFull（电量充满）、Charging（正在充电）、BatteryLow（电量不足）、Normal（电量正常）。 |
| text | 字符串 | 选填 | 当手机电量状态与category中设置的值匹配时，则显示该text内容。 注意： 除了text属性，还支持Text标签中其他属性，例如：format、paras等。此处，省略其他属性描述，请参考Text标签。 |

应用示例

通常情况下，在xml脚本中需要写多条Text语句，分别对应手机电量在不同状态下的情况。某一时刻，只有category属性与当前手机电量状态匹配的Text文本信息才被显示出来，其他文本信息则不显示。

```
// 当手机电量不足时，显示该条文本信息
<Text x="#screen_width/2" y="508" category="BatteryLow" alignV="bottom" color="#ff0000" size="50" text="电量不足，请充电！" alpha="100" align="center" />
// 当手机充电时，显示该条文本信息
<Text x="#screen_width/2" y="708" category="Charging" alignV="bottom" color="#00ff00" align="center" size="50" format="充电中，当前电量为%d%%" alpha="180" paras="#battery_level" />
// 当手机电量充满时，显示该条文本信息
<Text x="#screen_width/2" y="908" category="BatteryFull" alignV="bottom" color="#00ff00" size="50" text="充电完成，可以继续玩手机啦！" alpha="25" align="center" />
```
