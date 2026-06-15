# 旋转动画<RotationAnimation>

功能概述

基础命令，有针对一个元素，例如一张图旋转，也有多张图组成的帧动画，通过这些动画可以实现动画旋转效果。

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
<RotationAnimation repeat="" delay="">
    <Rotation angle="" time=""/>
</RotationAnimation>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| delay | 数值 | 选填 | 延迟，以ms毫秒记。延迟delay毫秒后执行动画播放。 |
| repeat | 数值 | 选填 | 动画重复播放次数，N表示重复N次，0表示无限次循环。 |
| angle | 数值 | 必填 | 旋转角度。 |
| time | 数值 | 必填 | [0-~]，相对于起始帧的间隔时间（毫秒），不小于0。 |

应用示例

```
<Image name="damuzhi" src="anim/dj_100.jpg" h="300" w="150" centerX="0" centerY="0" x="0" y="0">
    <RotationAnimation repeat="0" delay="0">
        <Rotation angle="0" time="0"/>
       <Rotation angle="72" time="200"/>
        <Rotation angle="144" time="400"/>
        <Rotation angle="216" time="600"/>
        <Rotation angle="288" time="800"/>
        <Rotation angle="360" time="1000"/>
    </RotationAnimation>
</Image>
```
