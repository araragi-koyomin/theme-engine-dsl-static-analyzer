# 适配功能：可变帧率<VariableFramerate>

功能概述

根据时间线指定不同帧率，可以指定在规定的时间内使用某一种帧率，实现不同帧率切换。目前只支持改变一次帧率。

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
<VariableFramerate>
    <VariablePoint frameRate="" time=""/>
    <VariablePoint frameRate="" time=""/>
</VariableFramerate>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| frameRate | 数值 | 必填 | 屏幕刷新的帧率，数值越大，效果越好，功耗相对较高。默认60，推荐使用30 |
| time | 数值 | 必填 | 循环时间，单位为ms毫秒 |

应用示例

演示屏幕刷新帧率在5和30之间的动态切换的示例。设置了切换时间，先显示帧率为5的效果，然后10秒后切换到帧率30展示动效。

```
<VariableFramerate>
        <VariablePoint frameRate="5" time="0"/>
        <VariablePoint frameRate="30" time="10000"/>
</VariableFramerate>
<Wallpaper/>
<Text align="center" text="位置移动" size="48" color="#000000" y="700" x="240"/>
<Image y="390" x="610" src="ty.png" centerY="151" centerX="136">
    <PositionAnimation>
        <Position time="0" y="0" x="0"/>
        <Position time="1000" y="0" x="-150"/>
        <Position time="2000" y="150" x="-150"/>
        <Position time="3000" y="150" x="0"/>
        <Position time="4000" y="0" x="0"/>
    </PositionAnimation>
</Image>
<Unlocker bounceAcceleration="3000" bounceInitSpeed="2000" name="unlocker">
    <StartPoint y="#screen_height-300" x="300" h="300" w="470"/>
    <EndPoint y="#screen_height-650" x="300" h="300" w="50">
        <Path y="#screen_height-300" x="300" h="300" w="470">
            <Position y="0" x="0"/>
            <Position y="-200" x="0"/>
       </Path>
    </EndPoint>
</Unlocker>
```
