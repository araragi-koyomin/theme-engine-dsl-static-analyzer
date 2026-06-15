# 命令：命令组<GroupCommands>

功能概述

支持常用自定义一组命令组复用，方便与简化重复定义。

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
<GroupCommands method="perform" paramTypes="String" />
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| method | 字符串 | 必填 | 执行动作，缺省值"perform"，目前暂无其他选项。 |
| paramTypes | 表达式 | 选填 | 传入执行动作参数的类型，缺省值"String"，目前暂无其他选项。 |
| params | 字符串 | 必填 | 传入命令组中具体Trigger action名称。 |

应用示例

示例一：通过变量命令控制命令组内选项，来切换不同旋转动画。

```
<Unlock>
  <Button name="zk1" x="300" y="#screen_height-300" w="470" h="300">
    <Trigger -action="down">
      <GroupCommands method="perform" paramTypes="String" params="down" />
    </Trigger>
  </Button>
  <Group name="triggersContainer">
    <Trigger action="down" />
    <Trigger action="up" />
  </Group>
</Unlock>
```
