# 命令：通用命令<ExternCommand>

功能概述

通用命令ExternCommand，用来向外部程序发送命令。目前仅能够支持解锁命令(仅在锁屏中有效)用这个命令可以实现解锁操作；ExternalCommand，与ExternCommand相对，是用来接收外部命令的命令，ExternalCommand是一种特殊的Command，不集成在Trigger标签之下而是直接写在Lockscreen标签之下，作为监测开关屏的命令。

![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/a6/v3/egRDa7RyQG-x-QxTMg0lSQ/caution_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010758Z&HW-CC-Expire=86400&HW-CC-Sign=52448958C3C2331002AEEE23F0022C648FF80A1EE4C56BE49828F3E0AD41B053)

视图里的动画一定要在action="pause"里暂停。

支持范围

**起始规范版本：**

HarmonyOS 5.0

**是否平台特性：**

否

|  | 锁屏（Lockscreen） | 桌面（Wallpaper） | 一镜到底（LongTake） | 百变卡片（Widget） | 充电动效（ChargingSkin） |
| --- | --- | --- | --- | --- | --- |
| 是否支持 | √ | x | x | x | √ |

|  | 直板机 | 折叠屏 | 平板 |
| --- | --- | --- | --- |
| 是否支持 | √ | √ | √ |

XML规范

```
<ExternCommand command="" condition="" delay="" delayCondition="" />
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| command | 字符串 | 必填 | 命令名，目前可以使用的一种命令是解锁命令unlock(仅在锁屏中有效) |
| condition | 表达式 | 选填 | 条件判断该Command是否执行，支持表达式。仅当值为非0或者为true时，该命令执行。为false或者0则不执行。 |
| delay | 数值 | 选填 | 延迟，单位ms。延迟delay毫秒后执行该命令。 |
| delayCondition | 表达式 | 选填 | 延迟判断，为真则delay命令生效，否则失效。默认为true或者1时，表示可以延迟启动命令，如果false或者非1则不延迟执行。 |

应用示例

示例一：解锁命令，执行该命令能够直接解锁锁屏。

```
  <Button x="0" y="0" h="100" w="100">
    <Trigger action="down">
      <ExternCommand command="unlock" />
    </Trigger>
  </Button>
```

示例二：在锁屏中，接收开屏/关屏命令，从而执行一些命令；在桌面插件中，用来检测切屏从而执行命令resume表示开屏时执行的命令，pause表示关屏时执行的命令。该示例在接受开屏信号之后，将name等于target的组件的动画启动，在关屏了之后，将目前的时间记录在pause_time变量里面。

```
  <ExternalCommands>
    <Trigger action="resume">
      <Command target="target.animation" value="play" />
    </Trigger>
    <Trigger action="pause">
      <VariableCommand name="pause_time" expression="#time_sys" />
    </Trigger>
  </ExternalCommands>
```
