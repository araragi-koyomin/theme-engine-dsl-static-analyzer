# 延时解锁

功能概述

延时解锁基于ExternCommand功能实现，允许用户在执行解锁操作一定时间后（延迟时间）执行系统解锁命令。关键在于执行ExternCommand的解锁（"unlock"）命令时增加延时参数delay，用于控制延迟解锁的时间。

支持范围

**起始规范版本：**

HarmonyOS 5.0

**是否平台特性：**

否

|  | 锁屏（Lockscreen） | 桌面（Wallpaper） | 一镜到底（LongTake） | 百变卡片（Widget） | 充电动效（ChargingSkin） |
| --- | --- | --- | --- | --- | --- |
| 是否支持 | √ | x | x | x | x |

|  | 直板机 | 折叠屏 | 平板 |
| --- | --- | --- | --- |
| 是否支持 | √ | √ | √ |

XML规范

不涉及

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| command | 字符串 | 必填 | 通用命令标签，用于向外部程序发生解锁命令，字段为"unlock"。 |
| delay | 数值 | 必填 | 通用命令标签，delay表明延迟解锁时间，单位为ms，最大值3s。 |

应用示例

延时解锁过程中播放视频

```
<?xml version="1.0" encoding="UTF-8"?>
<Lockscreen displayDesktop="true" frameRate="60" screenWidth="1080" version="1">
  <Var name="w" const="true" expression="#screen_width" persist="true" />
  <Var name="h" const="true" expression="#screen_height" persist="true" />
  <Var name="ratio" expression="max(#w/1080,#h/2400)" />
  <Var name="bgIndex" expression="0" />
  <ExternalCommands>
    <Trigger action="resume">
      <Command target="lockVideo.visibility" value="false" />
    </Trigger>
    <Trigger action="pause">
      <Command target="lockVideo.visibility" value="false" />
    </Trigger>
  </ExternalCommands>
  <Image src="page.jpg" srcid="#bgIndex" width="1080*#ratio" height="2400*#ratio" x="(#w-1080*#ratio)/2" y="(#h-2400*#ratio)/2" scaleType="center_crop" isBackground="true" />
  <Video name="lockVideo" looping="false" scaleType="fill" isBackground="false" play="false" src="" width="1080*#ratio" height="2400*#ratio" x="(#w-1080*#ratio)/2" y="(#h-2400*#ratio)/2" />
  <Button x="0" y="0" w="1080" h="#screen_height">
    <Trigger action="up">
      <Command target="lockVideo.visibility" value="true" />
      <VideoCommand name="lockVideo" play="true" src="lock.mp4" />
      <ExternCommand command="unlock" delay="20000" />
    </Trigger>
  </Button>
</Lockscreen>
```
