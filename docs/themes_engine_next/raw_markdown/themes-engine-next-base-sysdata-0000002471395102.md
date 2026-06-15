# 数据开放：系统数据开放

功能概述

开放以下系统数据和控制功能，以制作创意主题。

系统数据：

- 设备版本
- 设备可用空间
- 设备总空间
- 声音模式
- 手电筒开关状态
- 自动旋转开关状态
- 设备开机时长
- CPU使用率
- CPU空闲率
- 音量/亮度进度条

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

不涉及

参数说明

**系统数据基础变量**

| **变量** | **类型** | **注释** |
| --- | --- | --- |
| systemVersion | 字符串 | 设备版本。说明不支持获取旧荣耀手机的设备版本。 |
| storageFree | 字符串 | 设备可用空间。结果四舍五入取整，单位：显示实际单位 |
| storageFreeNum | 数值 | 设备可用空间。结果四舍五入取整，单位：显示实际单位 |
| storageTotal | 字符串 | 设备总空间。结果四舍五入取整，单位：显示实际单位 |
| storageTotalNum | 数值 | 设备总空间。结果四舍五入取整，单位：显示实际单位 |
| vibration | 数值 | 声音模式。2：响铃；1：震动；0：静音。超出[0,2]取值范围不生效。 |
| flashlight | 字符串 | 手电筒开关状态。0：关闭；1：打开。 |
| automaticRotary | 字符串 | 自动旋转开关状态。1：关闭；0：打开。 |

**设备开机时长获取**

**deviceTime**

通过建立Var标签，设置name="deviceTime"获取设备开机时长数据。

```
<var name="deviceTime" assigning="" />
```

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| assigning | 字符串 | 必填 | 设备开机时长的显示格式：dd天、dd天HH、dd天HH小时mm分钟。说明显示格式中的文字字符可替换，以dd天HH小时mm分钟这个格式为例，也可以写为dd/HH/mm、dd-HH-mm等。 |

**设备开机时长轮询 pollingTime**

设置轮询时间间隔后，将按照轮询时间间隔自动刷新设备开机时长数据。

```
<Text x="" y="" size="" color="" text="#time" />
```

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| time | number | 选填 | 设备开机时长的轮询时间，单位：分钟，最小值为1，不填即不开启轮询 |

**CPU使用率 processCpuRate**

通过建立Var标签，设置name="processCpuRate"获取CPU使用率数据。

```
<Var name="processCpuRate" bit="" />
```

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| bit | number | 选填 | 保留小数的位数。CPU空闲率的小数位数和CPU使用率的小数位数保持一致。如果不设置此参数，则默认显示整数。 |

**CPU空闲率 processCpuFree**

通过建立Var标签，设置name="processCpuFree"获取CPU空闲率数据。

```
<Var name="processCpuFree" />
```

**音量/亮度进度条 Progress**

包括通话音量、屏幕亮度、来电信息通知音量、音乐/视频/游戏音量/闹钟音量。

```
<Var name="processCpuFree" />
```

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| direction | number | 必填 | 进度条方向。0：水平；1：垂直。 |
| mode | number | 必填 | 音量或屏幕亮度模式。0：通话音量；1：屏幕亮度；2：来电信息通知音量；3：媒体音量；4：闹钟音量 |
| topColor | string | 必填 | 顶部进度条颜色 |
| bottomColor | string | 必填 | 底部进度条颜色 |
| bgSrc | string | 必填 | 进度条控件的背景图片。 |
| iconSrc | string | 必填 | 滑块图片。 |
| x | number | 必填 | 进度条位置横坐标。 |
| y | number | 必填 | 进度条位置纵坐标。 |
| w | number | 必填 | 进度条控件宽度。 |
| h | number | 必填 | 进度条控件高度。 |
| progressWidth | number | 必填 | 进度条宽度，进度条处于背景中间位置，最大值为 h或w 的一半。 |
| iconWidth iconHeight roundedCorners | number number number | 必填 必填 选填 | 滑块图片宽度。 滑块图片高度。 进度条圆角，取值范围：0-progressWidth/2，不填即为直角样式。 |

**声音模式改变命令**

声音模式共3种（详见全局变量vibration），支持通过VariableCommand命令改变声音模式。

```
<VariableCommand name="vibration" expression="" />
```

**手电筒开关命令**

手电筒开关状态共2种（详见全局变量flashlight），支持通过VariableCommand命令控制手电筒开关。

```
<VariableCommand name="flashlight" expression="" />
```

**自动旋转开关命令**

自动旋转开关状态共2种（详见全局变量automaticRotary），支持通过VariableCommand命令控制手机是否自动旋转屏幕。

```
<VariableCommand name="automaticRotary" expression="" />
```

**音量/亮度进度条加减命令**

支持通过音量/亮度进度条加减命令ProgressCommand，进行音量/亮度的加减。

```
<ProgressCommand mode="" change="" amplitude="" />
```

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| mode | number | 必填 | 音量或屏幕亮度模式。 0：通话音量； 1：屏幕亮度； 2：来电信息通知音量； 3：媒体音量； 4：闹钟音量 |
| change | number | 必填 | 进度条加减。0：减；1：加。 |
| amplitude | number | 必填 | 进度条加减的步进值。参照以下范围进行取值。 通话音量：1-15。屏幕亮度：4-255。来电信息通知音量：0-15。媒体音量：0-15。 1.步进值是指单次调节进度条时增加或减少的值。 2.不同系统的亮度范围可能会有所差异，以上亮度范围仅为参考。 |

应用示例

**示例一：**

1分钟刷新一次设备开机时长。

```
<Var name="pollingTime" time=""/>
<Var name="deviceTime" assigning="dd-HH-mm"/>
<Text name="t2" color="#ffffff" format="获取开机时长%s" paras="#deviceTime" size="50" x="100" y="500"/>
```

**示例二：**

获取CPU使用率

```
<Var name="processCpuRate" bit=""/>
<Text name="t3" color="#ffffff" format="获取CPU使用率:%s" paras="#processCpuRate" size="50" x="100" y="500"/>
```

**示例三：**

CPU空闲率

```
<Var name="processCpuFree" bit=""/>
<Text name="t3" color="#ffffff" format="获取CPU空闲率:%s" paras="#processCpuFree" size="50" x="100" y="500"/>
```

**示例四：**

音量/亮度进度条

```
<Progress direction="1" mode="2" topColor="#00ffff" bottomColor="#808080" bgSrc="anim/dj_0.jpg" iconSrc="zujian/right/zan.png" x="400" y="400" w="200" h="600" progressWidth="550" iconWidth="120" iconHeight="120" roundedCorners="25"/>
```

**示例五：**

声音模式改变

```
<Button w="700" h="150" x="180" y="1200" visibility="1" active="1">
<Trigger action="down">
        <VariableCommand name="vibration" expression="(#vibration+1)%3"/>
  <VariableCommand name="vibration" expression="0" condition="gt(#vibration,2)"/>
    </Trigger>
</Button>
```

**示例六：**

手电筒开关

```
<Button w="700" h="150" x="180" y="1200" visibility="1" active="1">
    <Trigger action="down">
        <VariableCommand name="flashlight" expression="(#flashlight+1)%2"/>
    </Trigger>
</Button>
```

**示例七：**

自动旋转

```
<Button w="700" h="150" x="180" y="1200" visibility="1" active="1">
    <Trigger action="down">
        <VariableCommand name="automaticRotary" expression="(#automaticRotary+1)%2"/>
    </Trigger>
</Button>
```

**示例八：**

音量/亮度进度条加减

```
<Button w="700" h="150" x="180" y="1200" visibility="1" active="1">
    <Trigger action="down">
        <ProgressCommand mode="2" change="0" amplitude="3"/>
    </Trigger>
</Button>
```
