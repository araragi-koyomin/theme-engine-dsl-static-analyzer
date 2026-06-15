# 命令：变量命令<VariableCommand>

功能概述

变量命令，用来控制变量（Var）的值。包括name、expression和type三个特殊属性（用expression中的数据对name中的变量进行赋值），condition、delay、delayCondition的用法与Command一致。

![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/72/v3/zdWUaegiRG29uJ8qwFocJA/caution_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010757Z&HW-CC-Expire=86400&HW-CC-Sign=FC5A44D07C0D3C2779ACA20C5A3DD491F33B5418F42D11579F1A8E571710A867)

该命令不支持persist属性。

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
<VariableCommand name="" expression="" type="" condition="" delay="" delayCondition="" />
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| name | string | 必填 | 变量名 |
| expression | string | 必填 | 对变量进行赋值时使用的表达式，支持常量赋值；变量定义的时候不能用表达式（如#countNum+5） |
| type | string | 选填 | 标识是string类型变量还是number类型变量，默认为number类型 |
| condition | string | 选填 | 条件判断，支持表达式。当condition里的条件判断为非0或者为true时，该命令执行，为false或者0则不执行。支持输入表达式 |
| delay | number | 选填 | 延迟，以毫秒记。延迟delay毫秒后执行该命令 |
| delayCondition | string | 选填 | 延迟判断，为真则delay命令生效，否则失效。默认为true或者1时，表示可以延迟启动命令，如果false或者非1则不延迟执行。支持输入表达式 |

应用示例

执行变量赋值命令

```
  <Button x="0" y="0" h="100" w="100">
    <Trigger action="down">
      <VariableCommand name="delayCond0" expression="#delayCond0+5" condition="lt(#second,40)" />
      <VariableCommand name="delayCond1" expression="#delayCond1+5" delayCondition="lt(#second,40)" delay="6000" />
      <VariableCommand name="delayCond2" expression="#delayCond2+5" condition="lt(#second,40)" delayCondition="lt(#second,40)" delay="4000" />
    </Trigger>
  </Button>
```
