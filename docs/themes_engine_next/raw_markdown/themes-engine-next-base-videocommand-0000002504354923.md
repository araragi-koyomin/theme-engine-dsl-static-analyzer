# 命令：视频命令<VideoCommand>

功能概述

VideoCommand：控制视频的播放停止、替换视频源、控制视频播放声音等一系列功能。

![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/59/v3/Gjsfum21TeqvsSaPW-b2Vw/caution_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010757Z&HW-CC-Expire=86400&HW-CC-Sign=566A10A5BF9D57916FEB99BE9F1D58E05989114B2AECEF6A7ED3DE854BF1464B)

其中声音与播放只能同时实现一个，只有在没有控制声音的参数时，才能使用play参数。

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

第一种方式没有声音参数时，可以使用play

```
<VideoCommand name="" src="" play="" />
```

第二种方式有声音参数时，不可以使用play

```
<VideoCommand name="" src="" sound="" />
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| name | 字符串 | 必填 | 所控制的视频的标识 |
| src | 字符串 | 必填 | 需要替换的视频的资源地址 |
| play | 表达式 | 必填 | 控制视频的播放或者停止 |
| sound | 表达式 | 选填 | 控制视频播放的声音，支持0~1的一个浮点数；数值越大，音量越大。也支持有音量为true，无音量为false |
| seekTime | 数值 | 选填 | 支持数字表达式， 指定时间播放视频 |

应用示例

循环播放sp1视频，按钮按下视频播放暂停即播暂停，按下视频播放即播放。

```
<Image x="0" y="(#screen_height-2400)/2" w="1350" h="2400" src="bj.jpg"/>
<Text x="50" y="120" align="left" alignV="top" color="#000000" size="40" text="视频播放"/>
<Button x="50" y="80" w="300" h="300">
    <Trigger action="down">
        <VideoCommand name="sp1" play="true"/>
    </Trigger>
</Button>
<Video name="sp1" src="sp.mp4" x="200" y="200" w="810*0.8" h="450*0.8" sound="1" defaultBitmap="sp1.png" play="true" looping="true"/>
<Text x="50" y="620" align="left" alignV="top" color="#000000" size="40" text="视频播放暂停"/>
<Button x="50" y="580" w="300" h="300">
    <Trigger action="down">
        <VideoCommand name="sp1" play="false"/>
    </Trigger>
</Button>
```
