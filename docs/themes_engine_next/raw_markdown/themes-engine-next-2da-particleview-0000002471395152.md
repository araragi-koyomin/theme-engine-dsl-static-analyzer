# 跟手粒子<ParticleView>

功能概述

当手指在锁屏上滑动时，在滑动轨迹处会生成跟手粒子。粒子形状、颜色由输入的粒子图片控制，粒子数量和大小等可通过参数进行设置。此外，还可以通过Range子标签来设置跟手粒子响应区域，即当手指滑动处于Range区域内滑动时，才生成跟手粒子。

**创意场景**

1. 浩瀚的海洋跟手一群鱼。

2. 震撼的星空跟手闪烁的星星。

3. 一阵风吹过，跟手飘落的树叶与花瓣。

![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/15/v3/E69GszY9R66t7wBGS4lzxQ/caution_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010818Z&HW-CC-Expire=86400&HW-CC-Sign=924AD8A4D0F1DE6AE7DBFBFC9D667BEE00456F535DAD890EFACFFA0A8055219F)

1. 跟手粒子ParticleView标签只能在根标签下使用，若内嵌到其他标签内则无法生效。

2. 对于粒子的w和h，限制最大值为120 px，若超过最大值，则按120 px处理。

3. 对于粒子图片实际宽和高小于定义值时，则取粒子的原始尺寸大小；对于粒子图片原始尺寸大于定义值时，则等比缩小至定义尺寸。

4. 控制粒子数量，粒子数量越多对系统性能影响越大，会造成系统卡顿。

5. 多粒子图片数不超过10个。

6. 如果指定区域，只在特定的区域内才产生跟手粒子；从区域外向区域内滑动时，不产生跟手粒子。

7. 无法通过visibility控制功能开启或关闭。

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

**单粒子图片**

```
<?xml version="1.0" encoding="UTF-8"?>
<lockScreen displayDesktop="" frameRate="" id="" screenWidth="" version="">
  <ParticleView w="" h="" count="" move_radius="" speek_sec="" reduce_size="" set_path="" src="">
    <PathData>
      <Range rect="" />
      <Range rect="" />
      <Range rect="" />
      <Range rect="" />
    </PathData>
  </ParticleView>
</lockScreen>
```

**多粒子图片**

```
<?xml version="1.0" encoding="UTF-8"?>
<lockScreen>
  <ParticleView w="120" h="120" count="1" move_radius="3" speek_sec="1" reduce_size="2" set_path="false" src="lizi0.png,lizi1.png,lizi2.png,lizi3.png,lizi4.png,lizi5.png,lizi6.png,lizi7.png,lizi8.png" />
</lockScreen>
```

参数说明

**ParticleView**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| w | 数值 | 选填 | 粒子宽度，取值正实数，最大值为120 px，超过最大值则按120处理，默认值30，支持数值表达式。 |
| h | 数值 | 选填 | 粒子高度，宽高比例建议1:1，取值正实数，最大值为120 px，超过最大值则按120处理，默认值30，支持数值表达式。 |
| count | 数值 | 选填 | 每次滑动产生的粒子数量，取值为正整数，建议范围[1,30]，默认为6；数值太大会造成卡顿现象。 |
| move_radius | 数值 | 选填 | 粒子在手指运动轨迹的基础上随机运动半径的反比例系数；数值越大，粒子随机运动半径越小；取值范围为(1，屏幕宽度)，默认为32，支持数值表达式。 |
| speek_sec | 数值 | 选填 | 单位时间粒子运动的距离，该值越大粒子消失越快。取值正实数，默认为2.5，支持数值表达式。 |
| reduce_size | 数值 | 选填 | 每次运动时粒子宽高缩小的尺寸，取值正实数（设置过大会导致粒子消失太快），取值范围[0.1,5]，默认为1.5，支持数值表达式。 |
| set_path | 字符串 | 选填 | 是否设置跟手粒子响应区域，true/false；true表示设置，false表示不设置，默认false；如果为true，需要在PathData标签中设置矩形区域。 |
| src | 字符串 | 必填 | 粒子图片资源名称，生成跟手粒子时使用。支持以间隔符的形式引入多个粒子图片，多粒子图片数不超过10个。 |
| PathData | 数值 | 选填 | 设置跟手粒子响应的矩形区域，支持在PathData中设置多个区域。 |

**PathData**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| Range | 子元素 | 选填 | 矩形区域坐标信息用rect(left,top,right,bottom)表示，其中left、top相当于矩形左上角坐标<x1,y1>; right,bottom相当于矩形右下角坐标<x2,y2>；那么矩形的x轴范围为x1-x2，y轴范围为y1-y2。 |

应用示例

```
<ParticleView w="60" h="60" count="6" move_radius="16" speek_sec="2.5" reduce_size="3.0" set_path="true" src="snow_flower.png">
    <PathData>
      <Range rect="600,580,650,1230" />
      <Range rect="650,1150,950,1230" />
      <Range rect="650,580,950,630" />
      <Range rect="930,580,970,1230" />
    </PathData>
</ParticleView>
```
