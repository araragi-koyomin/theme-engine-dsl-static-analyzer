# 位移动画<PositionAnimation>

功能概述

基础命令，有针对一个元素，例如一张图位移，也有多张图组成的帧动画，通过这些动画可以实现动画位移效果。

支持范围

**起始规范版本：**

HarmonyOS 5.0

**是否平台特性：**

否

|  | 锁屏（Lockscreen） | 桌面（Wallpaper） | 一镜到底（LongTake） | 百变卡片（Widget） | 充电动效（ChargingSkin） |
| --- | --- | --- | --- | --- | --- |
| 是否支持 | √ | √ | √ | √ | x |

|  | 直板机 | 折叠屏 | 平板 |
| --- | --- | --- | --- |
| 是否支持 | √ | √ | √ |

XML规范

```
<Image x="" y="" centerX="" centerY="" src="">
    <PositionAnimation delay="" repeat="">
        <Position x="" y="" time=""/>
    </PositionAnimation>
</Image>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| delay | 数值 | 选填 | 延迟，以ms毫秒记。延迟delay毫秒后执行动画播放。 |
| repeat | 数值 | 选填 | 动画重复播放次数，N表示重复N次，0表示无限次循环。 |
| x | 数值 | 必填 | 相对于图片初始位置的x坐标，整数。 |
| y | 数值 | 必填 | 相对于图片初始位置的y坐标，整数。 |
| time | 数值 | 必填 | [0-~]，相对于起始帧的间隔时间（毫秒），不小于0。 |

应用示例

```
<Lockscreen version="1" screenWidth="1080" frameRate="30" vibrate="false" displayDesktop="true">
    <Image src="bg.png"/>
    <Image src="1.png">
        <PositionAnimation repeat="0">
            <Position x="-1080" y="0" time="0"/>
            <Position x="-900" y="0" time="1000"/>
            <Position x="-600" y="0" time="2000"/>
            <Position x="-300" y="0" time="3000"/>
            <Position x="0" y="0" time="4000"/>
            <Position x="300" y="0" time="5000"/>
            <Position x="600" y="0" time="6000"/>
            <Position x="900" y="0" time="7000"/>
            <Position x="1200" y="0" time="8000"/>
        </PositionAnimation>
    </Image>
    <Image src="2.png">
        <PositionAnimation repeat="0">
            <Position x="-1080" y="0" time="0"/>
            <Position x="-800" y="0" time="1000"/>
            <Position x="-600" y="0" time="2000"/>
            <Position x="-400" y="0" time="3000"/>
            <Position x="-200" y="0" time="4000"/>
            <Position x="0" y="0" time="5000"/>
            <Position x="200" y="0" time="6000"/>
            <Position x="400" y="0" time="7000"/>
            <Position x="600" y="0" time="8000"/>
            <Position x="800" y="0" time="9000"/>
            <Position x="1000" y="0" time="10000"/>
            <Position x="1200" y="0" time="12000"/>
        </PositionAnimation>
    </Image>
</Lockscreen>
```
