# 适配功能：传感器<SensorBinder>

功能概述

支持获取四种类型的传感器数据，即加速传感器（accelerometer）、重力传感器（gravity）、线性加速传感器（linearAccelerometer）和陀螺仪（gyroscope）。其中，加速传感器获取的值是线性加速度传感器与重力传感器相加得到的结果，即：加速度(accelerometer) = 线性加速度（linearAccelerometer）+ 重力加速度（gravity）。基于传感器功能，可实现重力感应和摇一摇等效果。

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
<VariableBinders>
    <SensorBinder type="" vibrate="" shakeTime="" delay="">
        <Variable name="" index=""/>
        <Variable name="" index=""/>
        <Variable name="" index=""/>
    </SensorBinder>
</VariableBinders>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| type | 字符串 | 必填 | 支持：accelerometer,gravity,linearAccelerometer,gyroscope四种类型 |
| vibrate | 数值 | 选填 | 摇一摇是否触发震动，取值0/1。0表示不触发震动，1表示触发震动。默认为0，支持表达式 |
| shakeTime | 数值 | 选填 | 摇一摇触发震动的震动时长，单位为ms，默认为0，支持表达式 |
| delay | 数值 | 选填 | 摇一摇触发震动的延时时间，单位为ms，默认为0，支持表达式 |
| index | 数值 | 必填 | 用于标识传感器某个特定方向，取值为0,1,2；具体含义参考下文表格中的解释说明 |
| name | 字符串 | 必填 | 变量名，用于记录传感器在对应index方向上的值，在使用时可通过#name获取该变量的值 |

应用示例

**示例一：**

用获取的重力传感器值来控制DropPhysicalView中雪花飘落的方向和速度。由于重力传感器x轴正方向为水平向左，与手机屏幕x轴正方向相反，所以x_gra的值需要设置负号，使其与手机偏移方向一致。当手机偏移的时候，雪花下落会朝向重力传感器的方向。

```
<VariableBinders>
    <SensorBinder type="gravity">
        <Variable name="x_gra" index="0"/>
        <Variable name="y_gra" index="1"/>
        <Variable name="z_gra" index="2"/>
    </SensorBinder>
</VariableBinders>
<DropPhysicalView gravityX="-#x_gra" gravityY="#y_gra" airDensity="50">
    <ItemGroup x="0" y="0" width="#screen_width" height="#screen_height">
        <Item count="40" src="xuehua.png">
            <Velocity isRandom="true" velocityX="0" velocityY="5"/>
            <Position isRandom="true"/>
            <AngleVelocity isRandom="true" angleVelocity="10"/>
            <weight isRandom="true" value="0.5"/>
        </Item>
    </ItemGroup>
</DropPhysicalView>
```

**示例二：**

基于加速传感器实现重力感应，type设置为accelerometer加速传感器类型。在SensorBinder中获取各个方向的加速度值后，在Image中使用x_acc和y_acc来控制图片的x和y坐标，进而达到重力感应效果。注意：可以调节x_acc和y_acc的符号以及相乘系数来控制图片移动方向和移动幅度大小。

```
<VariableBinders>
    <SensorBinder type="accelerometer">
        <Variable name="x_acc" index="0"/>
        <Variable name="y_acc" index="1"/>
        <Variable name="z_acc" index="2"/>
    </SensorBinder>
</VariableBinders>
<Image x="610+#x_acc*12" y="390-#y_acc*12" centerX="139" centerY="151" src="ty.png"/>
```
