# 空间翻转<StereoView>

功能概述

可以展示多个页面，通过滑动页面可以实现3D的页面翻转效果。支持水平方向和垂直方向旋转，每个页面可以容纳多种标签元素，支持图片、文字、按钮。

**创意场景**

1. 切换锁屏壁纸。

2. 锁屏界面设置立体小组件。

![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/a/v3/AemIeBwaQEmq32JFrUiR-Q/caution_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010607Z&HW-CC-Expire=86400&HW-CC-Sign=9029B8B783A8DDFC97C023C942556D0B2278258A8DA151AA83CBA60CC0F5A7BC)

1. StereoView只支持StereoGroup标签，其余无效。

2. StereoGroup下子元素仅支持图片、文字、按钮。

3. StereoGroup数量限制为 3 - 10个。

4. 通用属性enableMove和moveRect会与滑动冲突，不建议在该场景使用。

**与**

**HarmonyOS4.0**

**区别：**

新增参数vertical，可以控制旋转方向，1代表垂直方向；0代表水平方向；2代表垂直+水平方向（即上下可以选择，左右也可以选择），默认为1。

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

```
<?xml version="1.0" encoding="UTF-8"?>
<StereoView sAngle="" can3D="" resistance="" fixed="" vertical="" w="" h="" x="" y="">
  <StereoGroup>
    <Image x="" y="" src="" />
    <Text x="" y="" size="" text="" />
  </StereoGroup>
</StereoView>
```

参数说明

**StereoView**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| sAngle | 数值 | 选填 | 旋转时两个页面的夹角，范围为0至180度，默认值为90 |
| can3D | 数值 | 选填 | 是否支持3D旋转模式，1：3D旋转模式，0：非3D旋转模式，其余值代表非3D旋转模式，默认值为1 |
| resistance | 数值 | 选填 | 旋转时的阻力效果，默认1.8，范围为0.1至10 |
| fixed | 数值 | 选填 | 是否固定图片大小，默认固定，0表示固定，1表示不固定。不固定时，可在一个旋转的扇面上自由设置图片大小及位置 |
| w\h | 数值 | 必填 | 3D旋转视图的宽、高 |
| x\y | 数值 | 必填 | 3D旋转视图的坐标位置 |
| vertical | 数值 | 选填 | 旋转方向，1：垂直方向（即上下滑动旋转）0：水平方向（即左右滑动旋转），2垂直+水平方向（即上下可以选择，左右也可以选择）,不填写默认为1 |
| StereoGroup | 子元素 | 必填 | 可以包含3-10个StereoGroup元素 StereoGroup无参数 StereoGroup可以将多个标签组合起来，所支持的标签有Image、Text、Button。 |

**StereoGroup**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| name | 字符串 | 选填 | 元素变量名，在支持表达式的情况中使用@name的格式来取当前变量的值。 |
| x | 数值 | 选填 | 相对于屏幕左上角的x坐标，单位为像素，显示范围为屏幕的宽（1920*1080的屏幕，显示的宽的范围为0~1080），默认为0，支持表达式。 |
| y | 数值 | 选填 | 相对于屏幕左上角的y坐标，单位为像素，显示范围为屏幕的高（1920*1080的屏幕，显示的高的范围为0~1920），默认为0，支持表达式。 |
| width | 数值 | 选填 | 显示在屏幕上的宽，单位为像素，显示范围为屏幕的宽（1920*1080的屏幕，显示的宽的范围为0~1080），支持表达式，w和width写法都可以。 |
| height | 数值 | 选填 | 显示在屏幕上的高，单位为像素，显示范围为屏幕的高（1920*1080的屏幕，显示的高的范围为0~1920），支持表达式，h和height写法都可以。 |
| pivotX | 数值 | 选填 | 旋转的点的X坐标，相对于View的左上角位置，单位为像素，支持表达式，pivotX和centerX写法都可以，默认值是0。 |
| pivotY | 数值 | 选填 | 旋转的点的Y坐标，相对于View的左上角位置，单位为像素，支持表达式，pivotY和centerY写法都可以，默认值是0。 |
| rotation | 数值 | 选填 | 旋转角度，一周360度，围绕(pivotX,pivotY)点旋转（若无pivotX和pivotY，则默认(0,0)点旋转），支持表达式。正数表示顺时针，负数表示逆时针，rotation和angle写法都可以。 |
| rotationX | 数值 | 选填 | 以X轴为旋转中心旋转，一周360度，支持表达式。正数表示顺时针，负数表示逆时针，rotationX和angleX写法都可以。 |
| rotationY | 数值 | 选填 | 以Y轴为旋转中心旋转，一周360度，支持表达式。正数表示顺时针，负数表示逆时针，rotationY和angleY写法都可以。 |
| alpha | 数值 | 选填 | 透明度，0-255，默认为255，当值小于0时则该值取0，当值大于255时，该值取255，支持表达式。 |
| visibility | 数值 | 选填 | 元素可见性，<=0 不可见 >0可见，true为可见，false为不可见，默认为1，支持表达式。 |
| align | 字符串 | 选填 | 坐标点水平对齐方式left, center, right，对齐的效果为view的左上角x坐标分别表示该view的左、中、右位置。 |
| alignV | 字符串 | 选填 | 坐标点垂直对齐方式top, center, bottom，对齐的效果为view的左上角y坐标分别表示该view的上、中、下位置。 |
| active | 数值 | 选填 | 激活状态，0代表不激活，视图不显示，所有的动画和可见性均失效，默认值1。 |

![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/b9/v3/Kz6oJCAdT1uPv_LdsKQHjw/note_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010607Z&HW-CC-Expire=86400&HW-CC-Sign=8E63986D0CDB60D34728DCD9EE2E7DC64F0364D4CAF7204DFCC3AA6E0C094365)

通用属性enableMove和moveRect会与滑动冲突，不建议在该场景使用

应用示例

```
<?xml version="1.0" encoding="UTF-8"?>
<StereoView sAngle="90" can3D="1" resistance="1.8" fixed="0" vertical="1" w="100" h="100" x="0" y="0">
  <StereoGroup>
    <Image x="0" y="0" src="img.jpg" />
    <Text x="0" y="0" size="100" text="test" />
  </StereoGroup>
  <StereoGroup>
    <Image x="0" y="0" src="img.jpg" />
    <Text x="0" y="0" size="100" text="test" />
  </StereoGroup>
  <StereoGroup>
    <Image x="0" y="0" src="img.jpg" />
    <Text x="0" y="0" size="100" text="test" />
  </StereoGroup>
</StereoView>
```
