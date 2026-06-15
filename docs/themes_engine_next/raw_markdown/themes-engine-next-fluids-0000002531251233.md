# 2D流体动效<FluidsComponent>

功能概述

模拟流体流动效果，可以设置流体的颜色，数量以及区域等，可以应用在锁屏和桌面上。

**创意场景**

1. 晃动手机牛奶翻滚。

支持范围

**起始规范版本：**

HarmonyOS 6.0

**是否平台特性：**

否

|  | 锁屏（Lockscreen） | 桌面（Wallpaper） | 一镜到底（LongTake） | 百变卡片（Widget） | 充电动效（ChargingSkin） |
| --- | --- | --- | --- | --- | --- |
| 是否支持 | √ | √ | x | x | √ |

|  | 直板机 | 折叠屏 | 平板 |
| --- | --- | --- | --- |
| 是否支持 | √ | √ | √ |

XML规范

```
<FluidsComponent gravityRatio="" viscosity = "" color = "" waterAlpha="" bgSrc="" scaleTyp="" srcid="" persist="" waterAlphaThreshold="">
   <CircleShape type="" radius="" xPosition="" yPosition=""/>
   <PolygonShape type="" hx="" hy="" angle="" xPosition="" yPosition=""/>
</FluidsComponent>
```

参数说明

**FluidsComponent参数说明**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| bgSrc | 字符串 | 选填 | 设置流体的背景 |
| color | 表达式 | 选填 | 设置流体的颜色 |
| gravityRatio | 表达式 | 选填 | 设置流体的重力系数，取值范围为（0-1），默认为1 |
| viscosity | 表达式 | 选填 | 设置流体的粘滞系数，取值范围为（0-1）；有值且正确时，粒子为粘性粒子，否则为水粒子；默认为水粒子。 |
| waterAlpha | 表达式 | 选填 | 设置流体的透明度，取值范围为（0-1），默认为1 |
| waterAlphaThreshold | 表达式 | 选填 | 设置流体混和的透明度阈值，取值范围为（0-1），默认值为0.7 |
| CircleShape | 子元素 | 选填 | 流体的形状（圆形） |
| PolygonShape | 子元素 | 选填 | 流体的形状（方形） |
| scaleType | 字符串 | 选填 | 背景图片的缩放模式，目前支持两种模式，默认为center_crop。 fill表示填充满屏幕，若图片比例与屏幕不匹配会导致图片拉伸；center_crop表示按照宽或高的比例进行适配，使图片充满整个屏幕，多余部分裁剪 |

**CircleShape**

**参数说明**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| type | 字符串 | 选填 | 设置该形状是流体（water）或固体（solid），默认为流体（water） |
| xPosition | float | 必填 | 圆心的x轴坐标，取值范围为（0-3）。固定将实际屏幕的宽映射为3，故xPosition取值范围为（0-3） |
| yPosition | float | 必填 | 圆心的Y轴坐标，与xPosition取值的映射关系保持一致，同时与实际屏幕的宽高比保持一致 |
| radius | float | 必填 | 圆形流体的半径，与xPosition取值的映射关系保持一致，决定圆形流体的范围大小 |

**PolygonShape**

**参数说明**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| type | 字符串 | 选填 | 设置该形状是流体（water）或固体（solid），默认为流体（water） |
| xPosition | float | 必填 | 中心点的x轴坐标，取值范围为（0-3），固定将实际屏幕的宽映射为3，故xPosition取值范围为（0-3）。中心点为对角线的交点 |
| yPosition | float | 必填 | 中心点的Y轴坐标，与xPosition取值的映射关系保持一致，同时与实际屏幕的宽高比保持一致 |
| hx | float | 必填 | 方形流体的半宽，与xPosition取值的映射关系保持一致，决定方形流体的范围大小 |
| hy | float | 必填 | 方形流体的半高，与xPosition取值的映射关系保持一致，决定方形流体的范围大小 |
| angle | float | 必填 | 方形流体旋转的角度（0-360），围绕中心点进行旋转，顺时针计算角度 |

应用示例

**示例一：流体以圆形的形式出现下落，遇到方形固体停止。**

流体的背景为bg.jpg，重力系数为1，粘滞系数为0.1，颜色为argb(255, 255,255,255)，透明度为1。

圆形流体的坐标为（0.7,1.5），半径为1.5。方形固体的中心点坐标为（1.5,6），半宽为1.5，半高为0.6。

```
<FluidsComponent gravityRatio="1" viscosity = "1" color = "argb(255, 255,255,255)" waterAlpha="1" bgSrc="bg.jpg">
    <CircleShape radius="1.5" xPosition="0.7" yPosition="1.5"/>
    <PolygonShape type="solid" hx="1.5" hy="0.6" xPosition="1.5" yPosition="6"/>
</FluidsComponent>
```
