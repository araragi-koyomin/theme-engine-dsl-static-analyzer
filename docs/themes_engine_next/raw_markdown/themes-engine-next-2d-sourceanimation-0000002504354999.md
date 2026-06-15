# 帧动画<SourceAnimation>

功能概述

基础命令，只有图片支持帧动画，图片帧动画稍有不同，无插值。可以设置图片播放之间的间隔时间。

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
<SourcesAnimation repeat="0" delay="0" />
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| delay | 数值 | 选填 | 延迟，以ms毫秒记。延迟delay毫秒后执行动画播放。 |
| repeat | 数值 | 选填 | 动画重复播放次数，N表示重复N次，0表示无限次循环。 |
| src | 字符串 | 必填 | 每一帧图片的文件名，该src值不可为空。 |
| time | 数值 | 必填 | [0-~]，相对于起始帧的间隔时间（毫秒），也是动画持续时间，帧图片人肉眼能识别时间延时为300ms左右，不小于0。 |

应用示例

```
<SourcesAnimation repeat="0" delay="0">
    <Source src="ImageAnimator/image_00017.png" time="0"/>
    <Source src="ImageAnimator/image_00018.png" time="50"/>
    <Source src="ImageAnimator/image_00019.png" time="100"/>
    <Source src="ImageAnimator/image_00020.png" time="150"/>
    <Source src="ImageAnimator/image_00021.png" time="200"/>
</SourcesAnimation>
```
