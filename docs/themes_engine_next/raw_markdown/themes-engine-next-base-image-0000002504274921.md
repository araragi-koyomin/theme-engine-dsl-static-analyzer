# 视图：图片<Image>

功能概述

用于在界面上展示一张图片，可以指定图片路径，模糊程度等属性，支持通用属性来设置图片的位置、大小以及旋转等等。

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
<Image src="" srcid="" srcExp="" blur="" />
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| src | 字符串 | 选填 | **图片名称的相对路径，图片文件名**image.png，可在前加文件夹，如**wenjianjia/image.png**。**src**与**srcExp**两个参数必须填写一个，以指定显示的图片内容。图片必须为**png/jpg**格式。图片大小须小于**10M**。 |
| blur | 字符串 | 选填 | 模糊半径值的设置，其值为0到24，设置值越大则图片模糊程度越高。 |
| srcid | 数值 | 选填 | 图片序列后缀数字，一般用变量表示，可以根据变量显示不同的图片，如果src="pic.png" srcid="1" 则最后会显示图片 "pic_1.png"，大于0， 正整数，支持数字表达式。 |
| srcExp | 字符串 | 选填 | 图片源表达式，解析字符串表达式指定图片的源地址。无法与src共存，当存在src值时，图片使用src作为地址。 |
| scaleType | 字符串 | 选填 | **图片的缩放模式，目前支持三种模式：**fill**、**center_crop**和**hold_center_crop**，默认为**center_crop**模式。**fill **表示填充满控件的宽高，若图片比例与控件不匹配会导致图片拉伸。**center_crop **表示图片等比缩放并居中充满整个控件的宽高，多余部分裁剪。**hold_center_crop **表示图片不进行缩放处理，居中裁剪至控件的宽高。** |
| isBackground | 字符串 | 选填 | **允许设置为****“**true”**或****“**false”**，默认为**false**。设置为**true**表示该图片作为背景图使用，必须与**scaleType="center_crop"****配合使用。说明****isBackground**与**align**同时使用时，**isBackground**不生效。** |
| useVirtualScreen | 字符串 | 选填 | 是否支持虚拟屏幕，true/false，默认false，若为true支持虚拟屏幕则src参数必须为虚拟屏幕的名称。 |

应用示例

左上角(120,500)显示一张图片，距离左上角点x方向110，y方向80像素的位置为旋转中心，根据旋转中心x轴有一个50度，根据旋转中心y轴有一个50度的旋转，加载图片源名为yun_#id.png。图片包含位移动画和旋转动画，并且有一个模糊半径为15的模糊效果。

```
<Image x="120" y="500" pivotX="110" pivotY="80" rotationX="50" rotationY="50" src="yun.png" blur="15" srcid="#id">
  <RotationAnimation>
    <Rotation angle="0" time="0" />
    <Rotation angle="360" time="3000" />
  </RotationAnimation>
  <PositionAnimation />
</Image>
```
