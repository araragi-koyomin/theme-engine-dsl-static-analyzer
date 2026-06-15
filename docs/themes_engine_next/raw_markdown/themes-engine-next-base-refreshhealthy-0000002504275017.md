# 命令：运动健康数据刷新命令<RefreshHealthyCommand>

功能概述

RefreshHealthyCommand主要在需要手动刷新运动健康变量的值时使用。

![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/be/v3/hRerskloQZyYJbIoJXLSgw/caution_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010800Z&HW-CC-Expire=86400&HW-CC-Sign=D16952CF7EBB0A5DA3DBCEE96098CB3FEA7D115A45E8E1697525441A5B909A1D)

1. 运动健康在应用、亮屏、进入前台这几种情况下会自动刷新，不再需要在resume中添加刷新命令。

2. 不再需要在pause中添加刷新命令，会导致在熄屏后打开监听，增加功耗。

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
