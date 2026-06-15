# 命令：天气数据刷新命令<RefreshWeatherCommand>

功能概述

RefreshWeatherCommand主要在需要手动刷新天气变量的值时使用。

![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/54/v3/JtnIDtk8Qdml7gYdqHGIKw/caution_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010800Z&HW-CC-Expire=86400&HW-CC-Sign=71DF6AD0B238D28F4EE32EFEC6492AA98EA493BC7DCDD50754A8746CA36C54D1)

天气在应用、亮屏、进入前台这几种情况下会自动刷新，不再需要在resume中添加刷新命令。

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

不涉及

参数说明

不涉及

应用示例

通过点击事件刷新当天的数据。

```
<Weather>
    <Var name="Weather.today.weatherid" expression="999"/>
    <Var name="Weather.today.aqivaluetext" expression="'bbb'"/>
    <Var name="Weather.today.currentTem" expression="222"/>
    <Var name="Weather.today.maxtemp" expression="333"/>
    <Var name="Weather.today.mintemp" expression="444"/>
</Weather>
<Image x="600" y="500" h="250" w="260" src="bj.jpg"/>
<Button x="600" y="500" h="250" w="260">
    <Trigger action="down">
        <RefreshWeatherCommand/>
    </Trigger>
</Button>
```
