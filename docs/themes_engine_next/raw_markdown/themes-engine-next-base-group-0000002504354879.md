# 组：视图组<Group>

功能概述

Group作为一个若干子控件元素的视图组，可以包住他元素，子控件元素比如图片Image、按钮Button、文字Text、时间DateTime等元素。 视图组内可以调整坐标以及宽与高，亦可以调整多个元素的位置和大小；此外Group可以添加各种基础动效动画，比如位移PositionAnimation、旋转RotationAnimation、透明度AlphaAnimation、缩放SizeAnimation等。

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
<Group name="" x="" y="" w="" h="" alpha="" angle="" visibility="" clip="" layered="">
    <Image/>
    <Time/>
    <Text/>
    <SizeAnimation/>
</Group>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| name | 字符串 | 选填 | 组控件的名称 |
| x | 数值 | 选填 | 相对于屏幕左上角的x坐标 |
| y | 数值 | 选填 | 相对于屏幕左上角的y坐标 |
| w | 数值 | 选填 | 容器宽 |
| h | 数值 | 选填 | 容器高 |
| alpha | 数值 | 选填 | 透明度，0-255,小于等于0不显示 |
| rotation 或(angle) | 数值 | 选填 | 旋转角度，一周360度 |
| visibility | 数值 | 选填 | 元素可见性，支持表达式<=0 不可见， >0可见，true为可见，false为不可见。 |
| clip | 字符串 | 选填 | 缺省为false；裁剪设置true则是测量整个Group长宽而不是测量元素的长宽，会裁剪掉超出w h标注范围的内容，不给予显示；设置为false测量元素的大小。 |
| layered | 字符串 | 选填 | 分层，设为true时，如可以对多图片进行混合处理，这个时候group中的最后一个Image需要具有hybridMode属性，前面的view都不能有hybridMode属性。 |
| align | 字符串 | 选填 | 水平方向对齐方式，默认为left，可选参数为left,center,right；使用时如果子view有自身的x坐标，则不会受Group设置的align参数影响，即子view的x坐标优先级大于Group的align参数。 |
| alignV | 字符串 | 选填 | 垂直方向对齐方式，默认为top，可选参数为top,center,bottom；使用时如果子view有自身的Y坐标，则不会受Group设置的alignV参数影响，即子view的y坐标优先级大于Group的alignV参数。 |

应用示例

示例一：将多张图片组合在一起同时控制展示。

```
<Group x="0" y="200" w="160" h="1000" alpha="155" angle="90" visibility="1" clip="true">
  <Image x="300" y="500" align="center" alignV="center" src="ty.png" />
  <PositionAnimation />
  <Image x="550" y="500" align="center" alignV="center" src="aixin7.png" />
  <RotationAnimation />
</Group>
```

示例二：将多张图片组合在一起同时控制展示。

```
<Group x="0" y="200">
  <Image x="300" y="500" src="ty.png" />
  <Image x="550" y="500" src="aixin7.png" />
  <Image x="700" y="500" src="aixin7.png" />
</Group>
```
