# 适配功能：恒定帧率<FrameRate>

功能概述

指定引擎的帧率为固定值，在效果与功耗之间寻求平衡。

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
<Lockscreen frameRate="" vibrate="" screenWidth="" pressure="" />
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| frameRate | 数值 | 选填 | 屏幕刷新的帧率，数值越大，效果越好，功耗相对较高。默认60，推荐使用30 |

应用示例

演示屏幕刷新的帧率为固定值30时的示例。

```
<Lockscreen frameRate="30" vibrate="" screenWidth="" pressure="" />
```
