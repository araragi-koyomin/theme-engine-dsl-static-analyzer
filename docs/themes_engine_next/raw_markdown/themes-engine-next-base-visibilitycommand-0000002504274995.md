# 命令：可见性命令<VisibilityCommand>

功能概述

除了在基础命令command中通过target的visibility属性来控制对象的可见性外，还支持在图片、文本等对象中配置visibility参数来控制对象可见性。

支持范围

**起始规范版本：**

HarmonyOS 5.0

**是否平台特性：**

否

|  | 锁屏（Lockscreen） | 桌面（Wallpaper） | 一镜到底（LongTake） | 百变卡片（Widget） | 充电动效（ChargingSkin） |
| --- | --- | --- | --- | --- | --- |
| 是否支持 | √ | √ | x | √ | √ |

|  | 直板机 | 折叠屏 | 平板 |
| --- | --- | --- | --- |
| 是否支持 | √ | √ | √ |

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| visibility | 表达式 | 必填 | 用于设置对象可见性，为true、>0则可见，为false、<=0不可见，支持表达式 |

应用示例

通过自定义变量控制文本或图片可见、不可见。

```
<Image x="" y="" w="" h="" align="" antiAlias="" visibility=""/>
<Text x="" y="" align="" alignV="" color="" size="" textExp="" visibility=""/>
```
