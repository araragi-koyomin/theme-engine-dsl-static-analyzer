# 数据开放：蓝牙耳机数据开放< BluetoothBattery>

功能概述

开放蓝牙耳机数据，包括：蓝牙耳机连接状态、名称和电量。

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

使用BluetoothBattery需要在manifest中添加权限申请；使用蓝牙耳机数据相关变量，需要首先声明BluetoothBattery节点。

```
<BluetoothBattery />
```

参数说明

| **参数** | **类型** | **注释** |
| --- | --- | --- |
| connectedStatus | 数值 | 蓝牙耳机连接状态，0：当前无蓝牙权限；1：当前未连接蓝牙耳机或未识别到蓝牙耳机； 2：当前连接的蓝牙耳机不支持获取数据；3：当前连接的蓝牙耳机可以正常获取数据。 说明： 当connectedStatus返回值为3时，才会获取到headsetName和headsetBatteryLevel的值。当connectedStatus返回值为0、1、2时，headsetName和headsetBatteryLevel的值为空。 |
| headsetName | 字符串 | 蓝牙耳机名称，支持展示用户自己设置的设备名称。 |
| headsetBatteryLevel | 数值 | 前连接蓝牙耳机的电量，1~100。说明 用户使用单电量耳机和三电量耳机时，headsetBatteryLevel取值逻辑如下： 单电量耳机：1~100，按耳机实际电量取值。三电量耳机（左/右耳机+耳机盒子）：左/右两个耳机电量中，取较低的那个电量值，再向上取十位整数。例如，左耳机电量55%，右耳机电量66%，则返回值为60。 |

应用示例

不涉及
