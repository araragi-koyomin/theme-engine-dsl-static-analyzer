# 透明度动画<AlphaAnimation>

功能概述

基础命令，有针对一个元素，例如一张图透明度，也有多张图组成的帧动画，通过这些动画可以实现动画透明度效果。

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
    <AlphaAnimation delay="" repeat="">
        <Alpha a="" time=""/>
    </AlphaAnimation>
</Image>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| delay | 数值 | 选填 | 延迟，以ms毫秒记。延迟delay毫秒后执行动画播放。 |
| repeat | 数值 | 选填 | 动画重复播放次数，N表示重复N次，0表示无限次循环。 |
| a | 数值 | 必填 | [0-255]，透明度0-255,小于等于0不显示，正数值。 |
| time | 数值 | 必填 | [0-~]，相对于起始帧的间隔时间（毫秒），不小于0。 |

应用示例

```
<Lockscreen version="1" screenWidth="1080" frameRate="30" vibrate="false" displayDesktop="true">
    <Image src="bg.jpg"/>
    <Image x="0" y="0" src="guang.png">
        <AlphaAnimation repeat="0">
            <Alpha a="0" time="0"/>
            <Alpha a="65" time="1000"/>
            <Alpha a="200" time="2000"/>
            <Alpha a="65" time="3000"/>
            <Alpha a="0" time="4000"/>
        </AlphaAnimation>
    </Image>
</Lockscreen>
```
