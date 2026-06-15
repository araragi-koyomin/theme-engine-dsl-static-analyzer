# 视图：图片混合<GroupImage>

功能概述

基于Group标签，可以对组内的多张图片实现混合模式的效果。其中，Group作为容器，组内最后一张图片设置混合模式参数，用于对之前所有的图片进行一个混合操作（除最后一张图片外所有图片作为一个整体）。不支持Mask标签中的绝对位置方式。

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
<Group h="" w="" x="" y="" layered="">
    <Image src="" x="" y="" alignV="" align=""/>
    <Image src="" x="" y="" alignV="" align="" hybridMode=""/>
</Group>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| layered | string | 必填 | 取值true/false，是否开启图片混合功能；开启后会对Group中的图片进行混合操作。 |
| hybridMode | string | 必填 | 图片混合模式，支持12种混合模式，具体参考Mask标签。 |

应用示例

该示例在Group标签种设置layered参数为true，开启了图片混合操作，并在最后一个Image标签中设置了xor混合模式。

```
<Group h="800" w="800" x="100" y="100" layered="true">
  <Image src="tgt1.png" x="100" y="100" alignV="center" align="center" />
  <Image src="tgt2.png" x="200" y="200" alignV="center" align="center" />
  <Image src="ori.png" x="261" y="261" alignV="center" align="center" hybridMode="11" />
</Group>
```
