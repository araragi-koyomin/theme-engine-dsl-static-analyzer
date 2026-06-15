# 视图：视频<Video>

功能概述

视频播放控件，将一段视频加入到视图中，能够设置播放、循环播放、声音控制等功能；支持AVI，MP4，MOV等多种格式的本地视频的播放。

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
<Video name="" x="" y="" w="" h="" src="" defaultBitmap="" sound="" scaleType="" play="" looping="" visibility="" />
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| name | 字符串 | 必填 | 视频命名，可用于videoCommand的定位Video对象，以控制视频的播放、换源和音量等。 |
| x | 数值 | 选填 | 视频左上角x位置，支持数字表达式，默认值为0。 |
| y | 数值 | 选填 | 视频左上角y位置，支持数字表达式，默认值为0。 |
| w(width) | 数值 | 选填 | 视频显示的宽，单位像素，默认为屏幕大小。 |
| h(height) | 数值 | 选填 | 视频显示的高，单位像素，默认为屏幕大小。 |
| play | 字符串 | 选填 | 是否加载之后自动开始播放，true/false，默认值为true。 |
| looping | 字符串 | 选填 | 视频是否循环播放，true/false，默认值为false不循环播放。 |
| sound | 数值 | 选填 | 视频播放声音控制，值为0~1之间的值，值越大声音越大。 |
| src | 字符串 | 必填 | 视频的文件路径，可带文件夹路径，例如aa/exmple.mp4，js.mp4等。视频格式支持mp4/avi/mov。视频文件须小于25M。 |
| scaleType | 字符串 | 选填 | 支持fill、fit_width、center_crop三种模式，默认为fill模式。fill表示填充满width和height宽高，若视频比例与屏幕不匹配会导致视频拉伸。fit_width表示宽匹配功能，视频缩放到设置的width那么宽，按照视频比例调整height，若视频宽高比例大于屏幕，则填充不满屏幕，若视频宽高比小于屏幕，则会有部分视频不在显示区域中。center_crop表示将视频文件宽高显示效果均不小于控件宽高。如果视频宽或者高小于控件宽或者高，则等比放大直到铺满控件，有超出屏幕部分的则截取视频中间部分铺满控件播放；如果视频资源文件的宽高均大于控件宽高，则等比缩小视频，当视频的宽高任意一值等于控件的宽高时停止缩放，截取中间部分视频显示播放。使用center_crop模式时建议搭配image来设置视频第一帧（参照示例2），不建议搭配defaultbitmap设置第一帧（效果不太协调）。 |
| visibility | 数值 | 选填 | 视频组件的可见性，支持true/false和1/0来分别调整视频的可见或者不可见。 |
| defaultBitmap | 字符串 | 选填 | 选填，默认图片，即视频未加载时或者停止时默认显示的图片。普通手机/折叠屏手机折叠态/平板竖屏态时使用。图片需要和普通手机/折叠屏手机折叠态/平板竖屏态原本的视频保持一致的宽高，推荐使用视频首帧图片。因为视频加载需要时间，先显示一张首帧图，让视频显示有个平滑的过渡效果。 |
| isTransparent | 布尔 | 选填 | 1. 当设置该参数为true的时候，设计师必须保证资源本身是异形视频资源（原图 + 蒙版），否则会出现显示失常 2.当设置了该参数为true的时候无法动态控制sound声音为0，视频正常的声音会随着视频的播放而播放, 跟随手机音量调节。 3. 由于和AvPlayer的实现方式不同，无法获取关键帧，配置该参数为true的情况下不支持isFullScreenNode连用。 4. 只支持mp4格式的使用 5. 透明视频只有在播放完成的时候才能换源src.state == 'COMPLETED' 6. 透明视频由于无法获取到首帧，不支持只加载不播放的场景，playing=false的场景下屏幕上什么都不会显示，建议设计师通过Image和Video的组合完成功能替代 7. 设计师设计的资源要求裁剪不必要的空白区域来降低内存的峰值数据 8. 透明视频不配置宽高的情况下默认是父容器的宽高，即全屏 9. 视频分辨率不能超过4096 x 4096。 10.异形视屏切换不同的视频时要添加兜底图。 <Group visibility="ne(#turbo_status,2)"> <Image src="bg.png" scaleType="center_crop" h="#screen_height" w="#screen_width" x="0" y="0" visibility="le(#video.mp4.state,2)"/> <Video name="video1" src="video.mp4" scaleType="center_crop" play="true" sound="0" looping="true" h="#screen_height" w="#screen_width" x="0" y="0" isTransparent="true"/> </Group> <Group visibility="eq(#turbo_status,2)"> <Image src="bg.png" scaleType="center_crop" h="#screen_height" w="#screen_width" x="0" y="0" visibility="le(#turbo_video.mp4.state,2)"/> <Video name="video2" src="turbo_video.mp4" scaleType="center_crop" play="true" sound="0" looping="true" h="#screen_height" w="#screen_width" x="0" y="0" isTransparent="true"/> </Group> |

应用示例

插入一段视频，视频左上角坐标是x=0，y=0，宽高为屏幕的宽高，采用的模式是fit_width，那么最终显示的宽度为屏幕宽度，显示的高度为原视频高宽比例乘以屏幕宽度。视频的源是目录下的js.mp4文件，并且在视频未加载的时候会展示bg.jpg这张图片。视频加载之后会自动播放，并且没有声音，视频设置为循环播放。

```
<Video name="js" src="js.mp4" defaultBitmap="bg.jpg" play="true" sound="0" looping="true" scaleType="fit_width" />
```
