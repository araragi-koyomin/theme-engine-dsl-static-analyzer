# 变量：自定义变量<Var>

功能概述

用户自定义变量，其标签为Var，用户指定变量名称、变量类型和变量值等，并被放入到全局变量中，供其他功能或者表达式调用。数组变量是放入到全局变量中的，其索引名字为 数组名+[索引值]，例如当name = sss时index=1，取变量sss[1]的值， 数组索引从0开始。对于不是数组的普通变量，type 的属性值不为number[] 或者string[] 即可，如果type为空默认为数字变量。

![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/ce/v3/nAPyZNZ5QxK8zcBmXir16A/caution_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010750Z&HW-CC-Expire=86400&HW-CC-Sign=E922933E27E430E42D5E39C73217EBBE39C97368C8E1B76E2AC07DE5BF124650)

1. 使用变量前建议不要省略Var标签。

2. 针对时间日期星期等变量禁止使用persist/globalPersist/styleGlobalPersist

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
<Var name="" expression="" type="" threshold="" persist="" index="" values="" size="" const="" />
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| name | string | 必填 | 变量名，也是全局变量的调用索引名，在支持表达式的情况中使用#+变量名来调用该变量目前的值。 |
| expression | string | 选填 | 变量对应的表达式或常量 注意：如果定义字符串常量需要多一套单引号: expression="'my string'"。 |
| type | string | 选填 | 定义数值变量或字符串变量，数字为number，字符串为string，如果是数字数组为number[]，字符串数组为string[]， 默认为"number"。 |
| threshold | number | 选填 | 阈值，当变量值的变化超过阈值时，可以触发一些命令。 |
| persist | string | 选填 | 变量持久化，默认为false不进行持久化，如果需要保存则在声明变量的时候显式设置persist="true"。开启变量持久化之后，会优先读取本地同名变量是否有值，如果没有则赋予expression或者默认值，如果有值则会忽略设置的expression值。 |
| index | string | 选填 | 如果使用数组型的变量则需要添加该项，标识数组的索引，索引值从0开始，index等于0代表值name[0]的取值。 |
| values | string | 选填 | 格式为values="val, val, val, ..."。该参数会写入所有需要赋的值，然后自动计算数组变量的大小，并且给每一个变量赋值。type类型不同，输出的值的类型也不同，例如：values="10,20"，type为number时，输出的值为10.0,20.0；type为string时，输出的值为10,20。与size冲突，同时存在时优先取size |
| size | number | 选填 | 声明数组的长度。与values冲突，同时存在时优先取size |
| const | string | 选填 | true或者false，变量值是否为常量，表示该变量只要被赋值一次之后其值就不会再改变，默认：false。 |

应用示例

**示例一**

变量的变量动画，设置name等于sss的全局变量在0~300ms内值从#ani_begin_x变化到0，然后一直保持不变。

```
<Var name="sss">
    <VariableAnimation>
      <AniFrame value="#ani_begin_x" time="0" />
      <AniFrame value="0" time="300" />
      <AniFrame value="0" time="100000000000000" />
    </VariableAnimation>
</Var>
```

**示例二**

演示设置阈值，当expression的值大于或等于阈值threshold时，则执行Trigger中的命令，设置图片的visibility属性为toggle。

```
<Image name="img" x="540" y="300" w="200" h="200" src="123.jpg" />
<Var name="timeTest" expression="#second % 2" threshold="1">
    <Trigger>
      <Command target="img.visibility" value="toggle" />
    </Trigger>
</Var>
```

数组变量

数组变量通过声明能够在全局变量中生成数组变量，方便循环操作，能够节省大量的开发者的时间。由于与变量的功能大致相似，所以将其集成在了<variable>的标签之中。

数组变量声明方法：

1. 声明同时给一个变量赋值.

```
<Var name="sss" type="number[]" index="0" expression="1" size="4" const="true" />
<Var name="sss" type="number[]" index="1" expression="2" const="true" />
<Var name="sss" type="number[]" index="2" expression="3" const="true" />
<Var name="sss" type="number[]" index="3" expression="4" const="true" />
<Text x="100" y="400" textExp="#sss[0]+' '+#sss[1]+' '+#sss[2]+' '+#sss[3]" size="30" color="#ffffff" />
```

通过name来定义数组变量的名字，通过size去声明数组变量的容量。在使用数组变量之前，一定要先声明数组变量的容量大小。

其中number[]表示的是数字数组，将其换成string[]能够容纳字符串数组变量。其中index表示数组变量的索引，expression的结果为该索引数组变量对应的值。

1. 只声明不赋值。

```
<Var name="sss" type="number[]" size="4" const="true" />
```

其中声明了一个name为sss的数组变量，其大小为4，数组的类型为数字，声明了const为true，表明在给变量赋初值之后便不能再改变。

1. 声明同时全部赋值。

```
<Var name="_Num" type="number[]" values="100,200,300,666,888" />
<Text x="200" y="500" textExp="#_Num[0]+' '+#_Num[1]+' '+#_Num[2]+' '+#_Num[3]+' '+#_Num[4]" size="30" color="#FFFFFF" />
```

利用values写入所有需要赋的值，该类会自动计算变量数组的大小，并且给每一个变量赋值。
