# 命令：全景换肤< StyleCommand>

功能概述

使用StyleCommand命令可以实现同一主题不同aod、skin样式的切换。触发方式包括但不限于：通过数据变化驱动（比如天气数据、步数数据、音量数据等）、通过单击/滑动/双击等驱动变化、通过时间范围驱动变化。

**创意场景**

1. 实时捕捉天气动态，让主题样式随晴雨、昼夜自然切换，每一次点亮界面，都是与当下天气的温柔邂逅，让用户随时随地感知四季与阴晴的细腻变化。

2. 以全局时间为线索，让主题样式随晨光、午后、暮色缓缓流转，从清新晨晖到静谧夜色，每一分变化都藏着时间的痕迹，让用户在界面切换间，悄然感知时光的温柔流。

3. 把步数化作能量解锁的密钥，当步数累积达到目标，主题样式便会随之焕新，从初始的清新到逐步点亮的活力，让每天的运动能量看得见、摸得着，在一步步间见证。

支持范围

**起始规范版本：**

HarmonyOS 5.0

**是否平台特性：**

否

|  | 锁屏（Lockscreen） | 桌面（Wallpaper） | 一镜到底（LongTake） | 百变卡片（Widget） | 充电动效（ChargingSkin） |
| --- | --- | --- | --- | --- | --- |
| 是否支持 | √ | √ | x | √ | x |

|  | 直板机 | 折叠屏 | 平板 |
| --- | --- | --- | --- |
| 是否支持 | √ | √ | √ |

XML规范

```
<?xml version="1.0" encoding="UTF-8"?>
<StyleCommand name="" index="" contentTypes="" condition=""/>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| name | 字符串 | 选填 | style的别名。 |
| index | 数值 | 必填 | 对应style文件路径的索 0:代表默认，1：style1， 2： style2， x: style*x* （不支持表达式） |
| contentTypes | 字符串 | 选填 | 需要刷新的模块。模块包括：aod、skin，写法为：模块名\|模块名\|...，默认值为”aod\|skin“ |
| condition | 表达式 | 选填 | 条件判断，支持表达式。当condition里的条件判断为非0或者为true时，该命令执行，为false或者0则不执行。 |

说明：

1. 可搭配变量节点Var的参数styleGlobalPersist，实现锁屏、桌面、卡片、换肤等模块的全场景联动效果。
2. 由于涉及到文件资源的拷贝，耗时1500ms-2000ms（依赖具体的资源大小），需要避免频繁切换。 ![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/91/v3/YSbby1flT4SmiIqiBesLFQ/caution_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010801Z&HW-CC-Expire=86400&HW-CC-Sign=542E87AA3B3B549164250F4EF3801588511CAE6AFDFD55790E1E3E998CD76113) 1、如果变量设置styleGlobalPersist为true，则初始值默认为0，expression中初始赋值无效，重新应用主题也会重置为0； 2、全景换肤在锁屏界面上对切换做个1-2秒的点击约束，不能频繁切换，因为AOD、耳机弹窗换肤切换要1~2秒； 3、全景换肤设计样式切换的变量需要设置styleGlobalPersist为true

应用示例

点击按钮切换aod、skin、fa的样式到style1

```
<?xml version="1.0" encoding="UTF-8"?>
<Button x="0" y="0" w="100" h="100">
  <Trigger action="up">
    <StyleCommand name="sty" index="1" contentTypes="aod|skin" condition="${condition1}" />
  </Trigger>
</Button>
```
