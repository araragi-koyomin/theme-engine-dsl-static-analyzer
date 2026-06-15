# 视图：帧解锁视图<SourceImage>

功能概述

根据设定的图片和时间间隔顺序播放图片，是另外一种形式的帧动画。它提供解锁的功能，当触摸时帧数大于等于设定值时能够调用解锁操作，若未大于等于设定值时抬起手指，则加速递减回首帧。但是，帧解锁视图会在一定程度上牺牲流畅度，降低系统的内存占用，适用于不需要高流畅度的帧动画效果。

支持范围

**起始规范版本：**

HarmonyOS 5.0

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
<SourceImage sourceName="" format="" from="" to="" space="" loop="" direction="" unlockTo="" />
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| sourceName | 字符串 | 必填 | 指定图片名的前缀，例如name="aa/pic__"，表示aa文件夹中pic_开头的图片 |
| format | 字符串 | 必填 | 图片后缀名，.jpg/.png，组合成的图片的文件名，例如 aa/pic_0.jpg |
| from | 数值 | 选填 | 动画首帧图片名字的序号，正整数，默认0，支持表达式 |
| to | 数值 | 必填 | 动画末帧图片名字的序号，正整数，默认0，支持表达式 |
| space | 数值 | 选填 | 每帧动画播放的间隔时间（毫秒），正整数，默认30 |
| loop | 字符串 | 选填 | 动画是否需要循环，true/false，默认为true。true时循环播放，可与direction和unlockTo配合设置触发解锁。false时只播放一次，direction无效，即不能触发解锁，unlockTo参数依旧有效，在小于unlockTo时点击屏幕加速递减回到首帧并停止，若长按至unlockTo则停止在unlockTo；若大于等于unlockTo时点击屏幕则停止在当前帧；若不点击则在to帧时停止。 |
| direction | 数值 | 选填 | 取值0/1，如果需要使用帧动画解锁，direction设置为0，loop必须为true，unlockTo必须有值。如果不需要解锁，设置direction为1，loop为true时，会正常从首帧到末帧循环播放。direction默认值1 |
| unlockTo | 数值 | 选填 | 触发解锁的帧序号，(from,to)间正整数，如果需要使用帧动画解锁，必须设置触发解锁的帧序号。触摸屏幕后，当当前图片序号大于等于这一帧序号-1时解锁。如果unlock直接设置为末帧或者超过末帧，序号会直接增大到末帧并解锁。注意解锁功能需要与一个button区域结合使用，即需要一个button区域才能够触发。 |

应用示例

**示例一：**

图片从aa/pic_0.png到aa/pic_30.png以时间间隔为500ms进行循环播放，在没有触摸操作时，图片顺序增大，当到末帧时图片从递增变为递减播放帧图片。当触摸屏幕的时候，如果当时播放帧数大于unlock帧-1则执行解锁操作，否则当前帧数以3帧每次的速度回退到首帧。使用一个全屏的button区域作为触发区域。

```
<SourceImage sourceName="aa/pic_" format=".png" from="0" to="30" space="500" loop="true" direction="0" unlockTo="10" />
```

**示例二：**

图片从aa/pic_0.png到aa/pic_30.png以时间间隔为500ms进行循环播放，在没有触摸操作时，图片顺序增大，当到末帧时图片从首帧开始循环播放帧图片。当触摸屏幕的时候，则当前帧数以3帧每次的速度回退到首帧。若长按屏幕，如果当前帧小于等于unlock帧则一直在首帧到unlock帧循环，若当前帧大于unlock帧则回到首帧开始循环。使用一个全屏的button区域作为触发区域。

```
<SourceImage sourceName="aa/pic_" format=".png" from="0" to="30" loop="true" space="500" direction="1" unlockTo="10" />
```
