# 命令：周期命令<CycleCommand>

功能概述

CycleCommand主要和控件数组Array配合使用，旨在通过一行代码周期性地执行多条VariableCommand命令，实现对数组变量的赋值，可以降低代码量。

支持范围

**起始规范版本：**

HarmonyOS 5.0

**是否平台特性：**

否

|  | 锁屏（Lockscreen） | 桌面（Wallpaper） | 一镜到底（LongTake） | 百变卡片（Widget） | 充电动效（ChargingSkin） |
| --- | --- | --- | --- | --- | --- |
| 是否支持 | √ | √ | x | √ | √ |

|  | 直板机 | 折叠屏 | 平板 |
| --- | --- | --- | --- |
| 是否支持 | √ | √ | √ |

XML规范

```
<CycleCommand frequency="" indexFlag="" cycleCondition="" begin="" end="">
    <VariableCommand name="" type="" index="" expression=""/>
</CycleCommand>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| indexFlag | 字符串 | 必填 | 索引变量标识，用来表示循环次数的变量名，在VariableCommand的expression中会被引用。 |
| frequency | 数值 | 选填 | 控制周期命令执行次数，indexFlag索引变量取值范围是[0,frequency-1]；若设置frequency，则begin，end参数无效。 |
| begin | 数值 | 选填 | indexFlag索引变量从begin开始循环；通过begin,end参数确定循环范围为[begin,end]，实现对索引变量的赋值。 |
| end | 数值 | 选填 | indexFlag指定的变量到达end时终止计算 |
| cycleCondition | 数值 | 选填 | 周期命令执行条件，当cycleCondition不为0时执行VariableCommand命令，支持数值表达式。 |

应用示例

通过begin，end参数实现对数组find[0]=0到find[100]=100赋值，是示例一中使用frequency参数实现的周期命令的另一种写法。

```
<CycleCommand indexFlag="col" cycleCondition="ne(#col,20)" begin="0" end="100" />
```
