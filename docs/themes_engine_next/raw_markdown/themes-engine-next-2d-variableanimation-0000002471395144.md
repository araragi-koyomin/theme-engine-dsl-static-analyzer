# 变量动画<VariableAnimation>

功能概述

基础命令，让一个变量在规定时间内变化的动画。

支持范围

**起始规范版本：**

HarmonyOS 5.0

**是否平台特性：**

否

|  | 锁屏（Lockscreen） | 桌面（Wallpaper） | 一镜到底（LongTake） | 百变卡片（Widget） | 充电动效（ChargingSkin） |
| --- | --- | --- | --- | --- | --- |
| 是否支持 | √ | √ | √ | √ | x |

|  | 直板机 | 折叠屏 | 平板 |
| --- | --- | --- | --- |
| 是否支持 | √ | √ | √ |

XML规范

```
<VariableAnimation repeat="" delay="">
    <AniFrame time="" value=""/>
</VariableAnimation>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| delay | 数值 | 选填 | 延迟，以ms毫秒记。延迟delay毫秒后执行动画播放。 |
| repeat | 数值 | 选填 | 动画重复播放次数，N表示重复N次，0表示无限次循环。 |
| value | 数值 | 必填 | 这一刻时间变量的数值。 |
| time | 数值 | 必填 | [0-~]，相对于起始帧的间隔时间（毫秒），不小于0。 |

应用示例

```
<Var name="abcd">
    <VariableAnimation repeat="3" delay="0">
        <AniFrame time="0" value="0"/>
        <AniFrame time="1000" value="200"/>
        <AniFrame time="2000" value="300"/>
        <AniFrame time="3000" value="400"/>
    </VariableAnimation>
</Var>
```
