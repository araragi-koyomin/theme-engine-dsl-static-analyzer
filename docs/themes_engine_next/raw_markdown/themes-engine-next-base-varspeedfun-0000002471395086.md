# 数学曲线：变速函数<VarSpeedFun>

功能概述

变速函数提供了数值的非线性变化能力，主要用于基础动画中，控制缩放大小、透明度值等按照非线性进行变化。使用方法：在动画中设置varSpeedFlag属性，它作用于设置varSpeedFlag的帧以及下一帧，支持30种函数类型。例如，在位移动画中可以利用变速函数来控制图片从起始位置移动到结束位置过程中的移动速率。

目前变速函数已支持动画有AlphaAnimation、PositionAnimation、RotationAnimation、SizeAnimation、VariableAnimation。

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
<PositionAnimation>
    <Position x="" y="" time="" varSpeedFlag=""/>
    <Position x="" y="" time=""/>
</PositionAnimation>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| SineFun_In | 字符串 | 选填 | 按照正弦曲线图呈现的效果缓入 |
| SineFun_Out | 字符串 | 选填 | 按照正弦曲线图呈现的效果缓出 |
| SineFun_InOut | 字符串 | 选填 | 按照正弦曲线图呈现的效果缓入缓出 |
| QuadFun_In | 字符串 | 选填 | 按照二次方曲线图呈现的效果缓入 |
| QuadFun_Out | 字符串 | 选填 | 按照二次方曲线图呈现的效果缓出 |
| QuadFun_InOut | 字符串 | 选填 | 按照二次方曲线图呈现的效果缓入缓出 |
| CubicFun_In | 字符串 | 选填 | 按照三次方曲线图呈现的效果缓入 |
| CubicFun_Out | 字符串 | 选填 | 按照三次方曲线图呈现的效果缓出 |
| CubicFun_InOut | 字符串 | 选填 | 按照三次方曲线图呈现的效果缓入缓出 |
| QuartFun_In | 字符串 | 选填 | 按照四次方曲线图呈现的效果缓入 |
| QuartFun_Out | 字符串 | 选填 | 按照四次方曲线图呈现的效果缓出 |
| QuartFun_InOut | 字符串 | 选填 | 按照四次方曲线图呈现的效果缓入缓出 |
| QuintFun_In | 字符串 | 选填 | 按照五次方曲线图呈现的效果缓入 |
| QuintFun_Out | 字符串 | 选填 | 按照五次方曲线图呈现的效果缓出 |
| QuintFun_InOut | 字符串 | 选填 | 按照五次方曲线图呈现的效果缓入缓出 |
| ExpoFun_In | 字符串 | 选填 | 按照指数曲线图呈现的效果缓入 |
| ExpoFun_Out | 字符串 | 选填 | 按照指数曲线图呈现的效果缓出 |
| ExpoFun_InOut | 字符串 | 选填 | 按照指数曲线图呈现的效果缓入缓出 |
| CircFun_In | 字符串 | 选填 | 按照圆形曲线图呈现的效果缓入 |
| CircFun_Out | 字符串 | 选填 | 按照圆形曲线图呈现的效果缓出 |
| CircFun_InOut | 字符串 | 选填 | 按照圆形曲线图呈现的效果缓入缓出 |
| BackFun_In | 字符串 | 选填 | 按照超过范围的三次方曲线图呈现的效果缓入 |
| BackFun_Out | 字符串 | 选填 | 按照超过范围的三次方曲线图呈现的效果缓出 |
| BackFun_InOut | 字符串 | 选填 | 按照超过范围的三次方曲线图呈现的效果缓入缓出 |
| ElasticFun_In | 字符串 | 选填 | 按照指数衰减的正弦曲线图呈现的效果缓入 |
| ElasticFun_Out | 字符串 | 选填 | 按照指数衰减的正弦曲线图呈现的效果缓出 |
| ElasticFun_InOut | 字符串 | 选填 | 按照指数衰减的正弦曲线图呈现的效果缓入缓出 |
| BounceFun_In | 字符串 | 选填 | 按照指数衰减的反弹曲线图呈现的效果缓入 |
| BounceFun_Out | 字符串 | 选填 | 按照指数衰减的反弹曲线图呈现的效果缓出 |
| BounceFun_InOut | 字符串 | 选填 | 按照指数衰减的反弹曲线图呈现的效果缓入缓出 |

应用示例

```
<Image src="ty.png" x="100" y="690" centerX="136" centerY="351">
  <PositionAnimation>
    <Position x="0" y="0" time="0" varSpeedFlag="SineFun_Out" />
    <Position x="500" y="0" time="4000" />
    <Position x="800" y="0" time="6000" />
  </PositionAnimation>
</Image>
```
