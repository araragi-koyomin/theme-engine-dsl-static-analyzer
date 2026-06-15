# 下落动效<DropPhysicalView>

功能概述

下落动效提供了一系列物体移动、旋转、透明度设置、移动区域设置的组合动效。对单张图片进行复用，减小相关内存的消耗。其中下落的动效符合物理规律，提供重力以及空气密度的设置项，能够随着物体大小和重力传感器的数值改变物体的运动轨迹。可以通过参数设置实现2D的下雨下雪、流星等动效。可以与传感器结合使用实现动效随手机移动的效果。

![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/d0/v3/cQ_j4xmsSZ-0vbR7O-1amQ/note_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010819Z&HW-CC-Expire=86400&HW-CC-Sign=C8C8231A8FA683D76D3D535C413B59EBE65202BD820B23DA9B9A221E7D617766)

下落动效为2D的物体移动下落动效，如需实现3D的效果，请使用俯视下落动效。

**ItemGroup**

ItemGroup提供了整个粒子组的运行范围和透明度限定等，限定了粒子的运动轨迹以及表现形式。

**Alpha**

Alpha是ItemGroup的子标签，定义了粒子运行的透明度区域，默认透明度为255，即不透明，可以增加多个区域的透明度。

**Size**

Size标签定义了粒子运行的尺寸变化区域，在区域内的粒子尺寸由系数控制，可以增加多个区域的尺寸变化，如果不设置该区域则粒子保持其设置的尺寸。

**Item**

Item定义了实际显示和运行的物体的参数和效果，能够通过不同的标签对物体进行修饰，影响物体的运行轨迹。

**Velocity**

Velocity影响粒子的初始速度，能够设置随机，随机默认低点为0，如果不随机需要设置isRandom="false"。

**Angle**

Angle为物体的初始旋转角度，不设置则随机角度，如果禁止随机角度必须设置isRandom="false"。

**Position**

Position表示物体的初始位置，如果不设置则在ItemGroup的运行范围内随机生成，如果指定则初始位置为指定位置。

**AngleVelocity**

AngleVelocity表示物体的旋转角速度，控制物体旋转速度的快慢，能够显示的设置物体随机旋转速度的大小。如果不写此标签则旋转速度的范围为0-30度。

**weight**

weight为物体的大小设置，根据设置的值能够对生成的图大小进行缩放。如果不设置为不随机，则大小在0~1之间随机。

**Trigger**

Trigger能够在标签Item中添加Trigger标签，同一个Item组的粒子在被点击时能够产生响应，并且执行Item中设置的所有命令。

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
<DropPhysicalView gravityX="" gravityY="" airDensity="">
    <ItemGroup x="" y="" width="" height="">
        <Alpha x="" y="" width="" height="" alphaStart="" alphaEnd="" direction=""/>
        <Size x="" y="" width="" height="" sizeStart="" sizeEnd="" direction=""/>
        <Item count="" src="">
            <Velocity isRandom="" velocityX="" velocityY="" lowestVelocityX="" lowestVelocityY=""/>
            <Position x="" y="" isRandom=""/>
            <Angle isRandom="" angle=""/>
            <AngleVelocity isRandom="" angleVelocity="" lowestAngleVelocity=""/>
            <weight isRandom="" value="" lowestWeight=""/>
            <Trigger action="">
                <Command name="" expression=""/>
            </Trigger>
        </Item>
    </ItemGroup>
</DropPhysicalView>
```

参数说明

**DropPhysicalView**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| gravityX | 数值 | 选填 | x方向的重力数值，可以用数值表达式，默认值为0。该值大于0物体向右移动，小于0物体向左移动，为0时无x轴位移效果。 |
| gravityY | 数值 | 选填 | y方向的重力数值，可以用数值表达式，默认值为0。该值大于0物体向下移动，小于0物体向上移动，为0时无y轴位移效果。 |
| airDensity | 数值 | 选填 | 空气密度，影响空气阻力的大小，值越大导致空气阻力越大，范围为正实数，默认值为12.93。面积较大的物体下落时所受的空气阻力较大，所以实际动效中w，h较小的物体比w，h大的物体移动速度略快一些。 |

**ItemGroup**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| x | 数值 | 选填 | 运行范围的x起点，默认为0，支持数值表达式。 |
| y | 数值 | 选填 | 运行范围的y起点，默认为0，支持数值表达式。 |
| width | 数值 | 选填 | 运行范围的宽度，默认为屏幕宽度，支持数值表达式。 |
| height | 数值 | 选填 | 运行范围的高度，默认为屏幕高度，支持数值表达式。 |

**Alpha**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| x | 数值 | 选填 | 透明度限制范围的x起点，默认为0，支持表达式。 |
| y | 数值 | 选填 | 透明度限制范围的y起点，默认为0，支持表达式。 |
| width | 数值 | 选填 | 透明度限制范围的宽度，默认为屏幕宽度，支持表达式。 |
| height | 数值 | 选填 | 透明度限制范围的高度，默认为屏幕高度，支持表达式。 |
| alphaStart | 数值 | 选填 | 透明度值，0~255，默认值为255，支持表达式。 |
| alphaEnd | 数值 | 选填 | 透明度值，0~255，默认值为255，支持表达式。 |
| direction | 字符串 | 选填 | 透明度变化方向，在区域内根据粒子的位置对物体透明度进行线性的变化，值为(left, right, up, down)分别是向左. 右. 上. 下，物体透明度从alphaStart根据位置和方向线性变化到alphaEnd。默认方向向上。 |

**Size**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| x | 数值 | 选填 | 尺寸变化区域的x起点，默认为0，支持表达式。 |
| y | 数值 | 选填 | 尺寸变化区域的y起点，默认为0，支持表达式。 |
| width | 数值 | 选填 | 尺寸变化区域的宽度，默认为屏幕宽度，支持表达式。 |
| height | 数值 | 选填 | 尺寸变化区域的高度，默认为屏幕高度，支持表达式。 |
| sizeStart | 数值 | 选填 | 尺寸变化系数，0~1，默认值为0，支持表达式。 |
| sizeEnd | 数值 | 选填 | 尺寸变化系数，0~1，默认值为1，支持表达式。 |
| direction | 字符串 | 选填 | 尺寸变化方向，在区域内根据粒子的位置对物体尺寸进行线性的变化，值为(left, right, up, down)分别是向左. 右. 上. 下，物体尺寸系数从sizeStart根据位置和方向线性变化到sizeEnd。默认方向向上。 |

**Item**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| src | 字符串 | 必填 | 图片的源路径。 |
| count | 数值 | 选填 | 物体的数量，正整数，默认为0。 |

**Velocity**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| isRandom | 字符串 | 必填 | 初始速度是否随机，true/false。 |
| velocityX | 数值 | 选填 | x方向初始速度，默认为0，支持表达式。不随机则x轴初始速度为该值，随机则x轴初始速度为lowestVelocityX+[(0,velocityX)间随机值]。 |
| velocityY | 数值 | 选填 | y方向初始速度，默认为0，支持表达式.不随机则y轴初始速度为该值，随机则x轴初始速度为lowestVelocityY+[(0,velocityY)间随机值]。 |
| lowestVelocityX | 数值 | 选填 | isRandom等于true时生效，为x轴随机初始速度最低值，默认为0，支持表达式。 |
| lowestVelocityY | 数值 | 选填 | isRandom等于true时生效，为y轴随机初始速度最低值，默认为0，支持表达式。 |

**Angle**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| isRandom | 字符串 | 必填 | 初始旋转角度是否随机，true/false。 |
| angle | 数值 | 选填 | -360-360整数，不随机时生效，给每个粒子统一赋予同一个角度的旋转，默认为0。 |

**Position**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| isRandom | 字符串 | 选填 | 位置是否随机，true/false，默认false。 |
| x | 数值 | 选填 | 初始位置的x坐标，不随机时有效，默认为0，支持表达式。 |
| y | 数值 | 选填 | 初始位置的y坐标，不随机时有效，默认为0，支持表达式。 |

**AngleVelocity**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| isRandom | 字符串 | 选填 | 角速度是否随机，true/false，默认false。 |
| angleVelocity | 数值 | 选填 | 物体旋转角速度，范围为-360-360，默认为0，支持表达式。不随机则物体的旋转角度为该值，如果随机则物体旋转角度为lowestAngleVelocity+[(0,angleVelocity)间随机值]。 |
| lowestAngleVelocity | 数值 | 选填 | 物体最低旋转角速度，范围为-360-360，默认为0，支持表达式。仅当isRandom等于true时生效，为随机速度的最小值。 |

**weight**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| isRandom | 字符串 | 选填 | 是否随机缩放粒子，true/false，默认false。 |
| value | 数值 | 选填 | 粒子图片的缩放比例，默认为1，支持表达式。如果不随机则统一设置物体缩放比例为该值，随机则缩放比例是lowestWeight+[(0,value)间随机值]。 |
| lowestWeight | 数值 | 选填 | 仅当isRandom等于true的时候才生效，为随机尺寸的最小值，默认为0.1。 |

应用示例

**示例一**

雪花飘落效果，并能根据手机晃动实现粒子随手机摇摆运动。首先设置加速度传感器，得到x,y,z轴的加速度全局变量；在DropPhysicalview标签中使用x,y轴加速度作为x,y轴重力；在ItemGroup中设置粒子的作用范围为整个屏幕(0,0,screen_width,screen_height)；设置了Alpha和Size两个渐变区域，在屏幕五分之一处开始到屏幕最下方结束，物体的透明度从255渐变到50，尺寸系数从1渐变到0.2；设置多个Item，xuehua.png图片的粒子个数为40，以5的速度下落，粒子的初始位置进行随机，雪花在下落过程中以10°的角速度进行旋转，图片在（0.1~0.6）范围内进行随机缩放；lingdang.png图片粒子个数为20，以5的速度下落，初始位置随机，下落时角度不变换，因为没有设置Angle标签，所以粒子的初始旋转角度是随机的，图片在(0.1~0.5)范围内进行缩放。对于xuehua.png这张图片来说，其中40个图片能够响应点击操作，点击将名称为test变量的值加一。

```
<DropPhysicalView gravityX="-#xGra" gravityY="#yGra" airDensity="20">
  <ItemGroup x="0" y="0" width="#screen_width" height="#screen_height">
    <Alpha x="0" y="#screen_height/5" width="#screen_width" height="#screen_height*0.8" alphaStart="255" alphaEnd="50" direction="down" />
    <Size x="0" y="#screen_height/5" width="#screen_width" height="#screen_height*0.8" sizeStart="1" sizeEnd="0.2" direction="down" />
    <Item count="10" src="snow_images.png">
      <Velocity isRandom="true" velocityX="0" velocityY="5" lowestVelocityX="" lowestVelocityY="" />
      <Position x="" y="" isRandom="true" />
      <Angle isRandom="true" angle="10" />
      <AngleVelocity isRandom="true" angleVelocity="10" lowestAngleVelocity="" />
      <weight isRandom="true" value="0.5" lowestWeight="" />
      <Trigger action="down">
        <VariableCommand name="dropTest" expression="#dropTest+1" />
      </Trigger>
    </Item>
    <Item count="10" src="bell_images.png">
      <Velocity isRandom="true" velocityX="0" velocityY="5" lowestVelocityX="" lowestVelocityY="" />
      <Position x="" y="" isRandom="true" />
      <Angle isRandom="true" angle="10" />
      <AngleVelocity isRandom="false" angleVelocity="0" lowestAngleVelocity="" />
      <weight isRandom="true" value="0.4" lowestWeight="" />
      <Trigger action="down">
        <VariableCommand name="dropTest" expression="#dropTest+1" />
      </Trigger>
    </Item>
  </ItemGroup>
</DropPhysicalView>
```

**示例二**

流星动效。设置DropPhysicalView的x轴重力为10，y轴重力为6；ItemGroup中设置流星动效范围为整个屏幕；设置两个Alpha区域，第一个Alpha区域使得流星在屏幕左上角为(700,0)，右下角为(1080,#screen_height-1500)的矩形区域内透明度为100，第二个Alpha区域使得流星在在屏幕下方高为#screen_height-1500，宽为屏幕宽度的矩形区域内透明度为0；流星的x轴速度为10，y轴速度为6，初始位置随机，旋转角度AngleVelocity 和初始角度Angle 需要显式设置为0；流星图片在(0.1~0.3)范围内缩放。

```
<DropPhysicalView gravityX="10" gravityY="6">
  <ItemGroup x="0" y="0" width="#screen_width" height="#screen_height">
    <Alpha x="700" y="0" width="380" height="#screen_height-1500" alphaStart="100" alphaEnd="100" />
    <Alpha x="0" y="1500" width="#screen_width" height="#screen_height -1500" alphaStart="0" alphaEnd="0" />
    <Item count="5" src="meteor.png">
      <Velocity isRandom="false" velocityX="10" velocityY="6" />
      <Position isRandom="true" />
      <Angle isRandom="false" angle="0" />
      <AngleVelocity isRandom="false" angleVelocity="0" />
      <weight isRandom="false" value="0.2" lowestWeight="" />
    </Item>
  </ItemGroup>
</DropPhysicalView>
```
