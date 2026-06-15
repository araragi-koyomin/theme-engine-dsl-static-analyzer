# 命令：线性振动<VibrateCommand>

功能概述

与震动设置Vibrate相比，线性振动命令VibrateCommand支持触发振动的组件更多，支持实现的振动效果更丰富，搭配酷炫动效或音乐，给用户带来无限“振”撼。

- 线性振动命令，有标签和多个子标签，集成在Trigger标签下。
- 支持触发线性振动命令的组件无限制，常用场景为点击按钮/滑动/亮屏触发线性马达振动。
- 支持系统预置和自定义振动两种模式，系统预置模式下可切换振动的类型；自定义振动模式下可自定义设置震动的时长、强弱和间隔时间。
- 线性马达振动命令有机型限制。

**创意场景**

1. 持续按压汽车油门，伴随振动和酷炫音效，带来汽车启动的无限振感。
2. 点击杯子破碎、按压气球爆炸触发不同振感。
3. 在屏幕上滑动不倒翁，根据不同的方位滑动触发不同的线性马达振动类型，带来不同的振感。

支持范围

**起始规范版本：**

HarmonyOS 6.0

**是否平台特性：**

否

|  | 锁屏（Lockscreen） | 桌面（Wallpaper） | 一镜到底（LongTake） | 百变卡片（Widget） | 充电动效（ChargingSkin） |
| --- | --- | --- | --- | --- | --- |
| 是否支持 | √ | √ | √ | √ | √ |

|  | 直板机 | 折叠屏 | 平板 |
| --- | --- | --- | --- |
| 是否支持 | √ | √ | √ |

XML规范

- **系统预置模式**

```
<Button x="" y="" w="" h="">
	<Triggers>
		<Trigger action="">
			<VibrateCommand id ="" type="" define = "" />
		</Trigger>
	</Triggers>
</Button>
```

- **自定义振动模式**

```
<Button x="" y="" w="" h="">
	<Triggers>
		<Trigger action="">
			<VibrateCommand id ="" define = "true" >
				<Vibrate loop="">
					<Wave>
						<Slice time="" duration="" type="2" intensity="" />
						<Slice time="" duration="" type="2" intensity="" />
						<Slice time="" duration="" type="2" intensity="" />
						…………
					</Wave>
				</Vibrate>
			</VibrateCommand>
		</Trigger>
	</Triggers>
</Button>
```

参数说明

**VibrateCommand**

**参数说明**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| define | 字符串 | 必填 | define为是否启用自定义模式，false为执行系统预置，true为自定义模式，默认为false。当define="true"时，自定义振动标签<Vibrate>及其子标签<Wave>、<Slice> 为必填。 |
| vibrateType | 字符串 | 选填 | 当define="true"时，可不填。当define="false"时，为必填，表示启用系统预置模式下的振动类型，其为固定的枚举值： haptic.grade.strength1 haptic.grade.strength2 haptic.grade.strength3 haptic.grade.strength4 haptic.grade.strength5 haptic.camera.focus haptic.camera.gear_slip haptic.camera.portrait_switch haptic.camera.mode_switch haptic.camera.long_press haptic.common.button haptic.common.charging haptic.common.delete_long_press haptic.common.fail_pattern1 haptic.common.fail_pattern2 haptic.common.long_press haptic.common.long_press1 haptic.common.long_press2 haptic.common.notice haptic.common.pinch haptic.common.success haptic.common.switch haptic.common.threshold haptic.common.upglide haptic.mode.change haptic.poweroff haptic.slide.type1 haptic.slide.type2 haptic.slide.type3 haptic.slide.type4 haptic.slide.type5 haptic.slide.type6 haptic.common.long_press3 haptic.common.click_up haptic.common.tick haptic.common.double_click haptic.common.click haptic.volume.max haptic.volume.min haptic.notice.Arrow haptic.clock.stopwatch |

- **自定义振动标签<Vibrate>**

自定义振动标签<Vibrate>为<VibrateCommand>的子标签，表示使用自定义振动模式。

当<VibrateCommand>中的define参数赋值为true时，必须使用<Vibrate>标签。

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| loop | 字符串 | 必填 | loop属性表示是否循环播放，true循环，false表示非循环，默认值为false，支持表达式。 |

- **自定义振动波形标签 <Wave>**

自定义振动波形标签<Wave>为<Vibrate>的子标签，表示自定义振动模式下的振动波形。

- **自定义振动波形标签<Slice>**

自定义振动波形标签<Slice>为<Wave>的子标签，表示每个振动波形。

一个<Wave>中可以有多个<Slice>，<Slice>的最大数目为64。

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| time | 数值 | 必填 | 表示振动波形的时间间隔，取值范围[0，1800000]，单位为ms，不支持表达式。 考虑到用户友好性，建议取值在1000以内。当前slice的time要大于等于上个slice的time+duration的值且time的值必须唯一。 |
| duration | 数值 | 必填 | 表示振动波形的时长，单位为ms，取值范围[0，1800000]，不支持表达式。 考虑到马达振动的持续时间以及自身器件特性，建议取值为5秒。 |
| intensity | 数值 | 必填 | 表示振动波形强度，属性值为浮点类型，取值范围[0，1]，不支持表达式。0代表振动强度为零，即不振动，数值越大则振动强度越大。 |
| type | 数值 | 必填 | 表示该波段振动波类型，当前仅支持2类型，不支持表达式。 |

应用示例

**示例一：点击按钮，得到系统预置的振动反馈**

```
<Image x="600" y="500" h="250" w = "260" src="bj.jpg"  />
<Button x="600" y="500" h="250" w="260">
    <Trigger action="down">         
        <RefreshWeatherCommand />
    </Trigger>
</Button>
<Image x="20" y="640" w="200" h="300" src="v1.png" />
<Button x="20" y="640" w="200" h="300">
        <Triggers>
            <Trigger action="up">
                <VibrateCommand type="haptic.common.success" define="false" />
            </Trigger>
        </Triggers>
</Button>
```

**示例二：点击按钮，得到自定义模式的振动反馈**

该自定义振动为非循环振动，分为三段振动波：第一段振动开始时间为0，振动时长800ms，振动强度为0.2；第二段振动开始时间为1000，振动时长500ms，振动强度为0.5；第三段振动开始时间为1800，振动时长800ms，振动强度为0.8。

```
<Image x="20" y="640" w="200" h="300" src="v1.png" />
<Button x="20" y="640" w="200" h="300">
<Triggers>
  <Trigger action="up">
  <VibrateCommand  define = "true" >
  <Vibrate loop="false">
      <Wave>
          <Slice time="0"    duration="800" type="2" intensity="0.2" />
          <Slice time="1000" duration="500" type="2" intensity="0.5" />
          <Slice time="1800" duration="800" type="2" intensity="0.8" />
      </Wave>
  </Vibrate>
  </VibrateCommand>
  </Trigger>
</Triggers>
</Button>
```
