# 缩放动画<SizeAnimation>

功能概述

基础命令，有针对一个元素，例如一张图缩放，也有多张图组成的帧动画，通过这些动画可以实现动画缩放效果。

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
<Image name="" src="" h="" w="" centerX="" centerY="" x="0" y="0">
    <SizeAnimation repeat="">
        <Size h="" w="" time=""/>
    </SizeAnimation>
</Image>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| delay | 数值 | 选填 | 延迟，以ms毫秒记。延迟delay毫秒后执行动画播放。 |
| repeat | 数值 | 选填 | 动画重复播放次数，N表示重复N次，0表示无限次循环。 |
| w | 数值 | 必填 | 图片的大小宽，正整数。 |
| h | 数值 | 必填 | 图片的大小高，正整数。 |
| time | 数值 | 必填 | [0-~]，相对于起始帧的间隔时间（毫秒），不小于0。 |

应用示例

```
<Image src="you.png" x="564" y="937">
  <SizeAnimation repeat="0">
    <Size h="74" w="100" time="0" />
    <Size h="103" w="139" time="600" />
    <Size h="74" w="100" time="1200" />
  </SizeAnimation>
</Image>
```
