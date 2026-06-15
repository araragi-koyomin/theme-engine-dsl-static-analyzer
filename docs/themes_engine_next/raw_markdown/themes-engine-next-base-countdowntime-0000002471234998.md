# 视图：倒计时<CountDownTime>

功能概述

能够计算目前时间到另外一个日期时间所剩余的时间，提供了多个不同粒度的时间显示以及不同的目标日期的设定方法。

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
<CountDownTime name="" date="" countDownType="" unit="" />
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| name | 字符串 | 必填 | 设定全局变量的名称，能够使用该名称对全局变量进行值的设置和获取，注意使用全局变量一定要在声明之后。 |
| countDownType | 字符串 | 必填 | 输入日期的类型，分为concrete和loop，详情见后文 |
| date | 字符串 | 必填 | 目标日期，根据不同的日期或者时间的格式，结合类型判断目标时间 |
| unit | 字符串 | 选填 | 粒度单位，提供不同粒度的倒计时显示，默认为day |

其中countDownType指定了不同的计时时间 （日期值必须为正常存在的日期）

- concrete：固定日期的倒计时 2020-01-01 、2020-01-01 09:00 计算为绝对值
- loop: 循环日期的倒计时，提供 1. 每年日期 01-01 2. 每月日期 01 3. 每周 Sunday Wednesday 等星期全称 4. 相对日期： yesterday tomorrow theDayAfterTomorrow theDayBeforeYesterday 5. 每天时间: 09:00

- unit能够提供不同的粒度来表示衡量时间包括： 1. day 天 2. hour 小时 3. minute 分钟 4. second 秒 5. hourOnly 将剩余时间全部转化成小时 6. minuteOnly 将剩余时间全部转化成分钟 7. secondOnly 将剩余时间全部转化成秒

![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/bd/v3/zBkQQS59QKqXwwYmWqIRVg/caution_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010743Z&HW-CC-Expire=86400&HW-CC-Sign=0BC42BEA26F67DAA09160410DE08C1E6821785C13C4D495D5E9FF48FE5E1A110)

无论是过去的时间还是将来还剩下来的时间的输出都是正值，方便自定义文字的输出。

unit 中hour、minute、second等只包含不满一个周期的时间，即hour范围为为0-23、 minute为0-59、 second为0-59。如果需要全部计算请使用后面带Only的unit字段。

所有填写的日期必须是真实存在的日期。

应用示例

**示例一：**

显示离下一个星期天的天数

```
<CountDownTime name="shijian" date="Sunday" countDownType="loop"/>
<Text text="'离下一个星期天 ' + #shijian + '天'"/>
```

**示例二：**

显示离下一个1月1日的天数

```
<CountDownTime name="shijian" date="01-01" countDownType="loop"/>
<Text text="'离下一个1月1日 ' + #shijian + '天'"/>
```
