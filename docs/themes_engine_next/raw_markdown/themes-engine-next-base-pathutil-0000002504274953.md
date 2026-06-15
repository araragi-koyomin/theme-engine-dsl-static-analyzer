# 视图：路径解析<PathUtil>

功能概述

路径解析工具是对一段命令描述的路径字符串进行解析，实际是解析SVG路径。需要与跑马灯<marquee>配合使用，跑马灯动效可以通过pathname调用该路径，实现按照路径跑马灯的效果，注意跑马灯标签应该在该标签之后。路径指令中大写字母表示绝对位置，小写字母表示相对位置。

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
<PathUtil name="" path="" />
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| name | 字符串 | 必填 | 路径名称 |
| path | 字符串 | 必填 | 路径的SVG指令描述，其中Path中以命令标志开头后接命令规定数量的参数。命令与参数之间可以用空格、逗号也可以没有任何字符分割，参数与参数之间用空格、逗号或者换行来分割。 |

path svg指令说明：

| **指令** | **参数** | **注释** |
| --- | --- | --- |
| M,m | x,y | 该命令将绘制点移动至（x,y）点开始绘制 |
| L,l | x,y | 从路径当前绘制点绘制直线到（x,y）点，并将绘制点移动到(x,y)点 |
| H,h | x | 从路径当前绘制点绘制横线到x位置，并将绘制点移动到(x,y)点 |
| V,v | y | 从路径当前绘制点绘制竖线到x位置，并将绘制点移动到(x,y)点 |
| Z,z | 无 | 路径闭合参数，将当前绘制点与M指令的起始绘制点连接一条直线 |
| Q,q | x1,y1,x,y | 从路径当前绘制点绘制二次贝塞尔曲线至（x,y）点，控制点为（x1,y1），并移动绘制点至(x,y) |
| T,t | x,y | 从路径当前绘制点绘制二次贝塞尔曲线至（x,y）点，并移动绘制点至(x,y)。若前一个指令为Q或T，控制点为前一个控制点关于前一个指令终点的对称点，若前一个指令不为Q或T，则控制点与终点相同 |
| C,c | x1,y1,x2,y2,x,y | 从路径当前绘制点绘制三次贝塞尔曲线至（x,y）点，控制点1为（x1,y1）控制点2为（x2,y2），并移动绘制点至(x,y) |
| S,s | x2,y2,x,y | 从路径当前绘制点绘制二次贝塞尔曲线至（x,y）点，并移动绘制点至(x,y)。若前一个指令为C或S，控制点1为前一个控制点2关于前一个指令终点的对称点，若前一个指令不为C或S，则控制点1与控制点2相同 |
| A,a | rx,ry,x-axis-rotation,large-arc-flag,sweep-flag,x,y | 弧形命令A的前两个参数分别是x轴半径和y轴半径，弧形命令A的第三个参数表示弧形的旋转情况，large-arc-flag（角度大小） 和sweep-flag（弧线方向），large-arc-flag决定弧线是大于还是小于180度，0表示小角度弧，1表示大角度弧。sweep-flag表示弧线的方向，0表示从起点到终点沿逆时针画弧，1表示从起点到终点沿顺时针画弧 |

基础图形指令在SVG指令基础上新增圆角矩形、弧线（椭圆）、圆形、爱心基础图形的解析，与上述指令不可同时使用：

| **指令** | **参数** | **注释** |
| --- | --- | --- |
| Rect | left,top,right,bottom,rx,ry,direction | 绘制矩形，left,top,right,bottom表示矩形的左（x）、上(y)、右(x)、下(y)；rx,ry表示矩形圆角的x,y轴半径；direction为0表示路径为顺时针，1表示逆时针。 |
| Circle | x,y,r,direction | 绘制圆形，x,y表示圆心坐标；r表半径；direction与矩形一致。 |
| Arc | left,top,right,bottom,startAngle,sweepAngle | 绘制圆弧，left,top,right,bottom表示绘制的椭圆所在矩形的左（x）、上(y)、右(x)、下(y)；startAngle表示弧线绘制的起始角度；sweepAngle表示绘制弧线的角度。起点为矩形右边中点位置，顺时针方向。 |
| Love | 无 | 绘制爱心，爱心左上角坐标为（200,130）,w=200，h=170 |

应用示例

**示例一：**

生成左上角坐标为（200,130）,w=200，h=170的爱心路径，路径的name为love，其中跑马灯可以用pathname属性来引用。

```
<PathUtil name="love" path="love" />
```

**示例二：**

生成左边x坐标为0，顶部y坐标为0，右边x左边为100，底部y坐标为100，圆角的x和y的半径都为15的圆角矩形路径。

```
<PathUtil name="rect" path="Rect 0,0,100,100,15,15,0"/>
<Marquee state="1" showTime="20" deltaAngle="5" pathname="rect" scaleX="1" scaleY="1" translateX="0" translateY="0" rotate="0" weight="1" color="#00FFFF" marqueeColor="#FF0000" marqueePos="0.3"/>
```

**示例三：**

以x坐标100，y坐标100，生成一个半径为100的圆形路径。

```
<PathUtil name="circle" path="Circle 100,100,100,0"/>
<Marquee state="1" showTime="20" deltaAngle="5" pathname="circle" scaleX="1" scaleY="1" translateX="0" translateY="0" rotate="0" weight="1" color="#00FFFF" marqueeColor="#FF0000" marqueePos="0.3"/>
```

**示例四：**

以左边x坐标100，顶部y坐标100，右边x坐标300，底部y坐标400生成一个椭圆，其中椭圆的y方向轴为300，x方向轴为200。生成一个沿着椭圆，从最右边点开始，以矩形中点为旋转中心，顺时针360度的路径。

```
<PathUtil name="Arc" path="Arc 100,100,300,400,0,360"/>
<Marquee state="1" showTime="20" deltaAngle="5" pathname="Arc" scaleX="1" scaleY="1" translateX="0" translateY="0" rotate="0" weight="1" color="#00FFFF" marqueeColor="#FF0000" marqueePos="0.3"/>
```
