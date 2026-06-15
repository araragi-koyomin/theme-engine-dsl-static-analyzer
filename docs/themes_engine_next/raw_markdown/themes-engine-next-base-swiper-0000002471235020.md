# 视图：视图切换<Swiper>

功能概述

Swiper为Lockscreen的子节点且唯一，Swiper的子节点为Group，每个Group为一个屏幕，Group的顺序即为屏幕顺序，该组件仅用于全屏翻页。

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
<Swiper currentIndex="" animationType="" animationTime="">
    <Group />
    <Group />
    <Group />
  </Swiper>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| currentIndex | number | 必填 | 当前显示屏幕的索引 |
| animationTime | number | 必填 | 动画播放时间单位ms |
| animationType | number | 选填 | 动画类型 当前留空 仅支持滑动动画 |

应用示例

```
<Swiper currentIndex="1" animationType="0" animationTime="500">
    <Group>
      <Image src="pic1.jpg" />
    </Group>
    <Group>
      <Image src="pic2.jpg" />
    </Group>
    <Group>
      <Image src="pic3.png" />
    </Group>
</Swiper>
```
