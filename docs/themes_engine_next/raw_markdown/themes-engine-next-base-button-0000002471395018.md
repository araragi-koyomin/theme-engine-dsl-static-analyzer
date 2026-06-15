# 控件：按钮<Button>

功能概述

按钮标签，在锁屏界面有一块显示区域，能够触发Trigger等一系列的操作和命令，可以通过不同的状态标签来展现对应状态下设置的图片或者文字等。

支持范围

**起始规范版本：**

HarmonyOS 5.0

**是否平台特性：**

否

|  | 锁屏（Lockscreen） | 桌面（Wallpaper） | 一镜到底（LongTake） | 百变卡片（Widget） | 充电动效（ChargingSkin） |
| --- | --- | --- | --- | --- | --- |
| 是否支持 | √ | x | x | √ | x |

|  | 直板机 | 折叠屏 | 平板 |
| --- | --- | --- | --- |
| 是否支持 | √ | √ | √ |

XML规范

```
<Button name="" w="" h="" x="" y="" visibility="" />
```

参数说明

**Button**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| name | 字符串 | 选填 | 按钮的名称 |
| visibility | 字符串 | 选填 | 元素可见性支持表达式<=0 不可见 >0可见 |
| Trigger | 字符串 | 必填 | 用来定义按钮、Unlocker、Slider等动作引发相关的操作，可以控制某元素的属性。其规定了触发的动作(action)，并且触发了一系列命令(Command)的执行。 |

**Trigger**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| action | 字符串 | 必填 | 按钮动作： down (按下), up (抬起), double (双击)，resume（亮屏触发）,pause（熄屏触发），long（长按），click（点击） |

应用示例

未点击按钮时，按钮可见、text文本框显示"可见性：visibility为false，按钮不变"；单击之后，按钮不可见、text文本框显示"可见性：visibility为true,按钮消失"。

```
<Group name="Button">
    <Text x="0" y="0" align="left" alignV="top" color="#ffffff" size="45" text="可见性：visibility为true,按钮消失" visibility="eq(#kj,1)"/>
    <Text x="0" y="0" align="left" alignV="top" color="#ffffff" size="45" text="visibility为false，按钮不变" visibility="eq(#kj,0)"/>
    <Button w="#w" h="1200" x="0" y="750" visibility="1">
        <Trigger action="down">
            <VariableCommand name="kj" expression="#kj+1" condition="le(#kj,2)" persist="true"/>
            <VariableCommand name="kj" expression="0" condition="eq(#kj,2)" persist="true"/>
        </Trigger>
    </Button>
</Group>
```
