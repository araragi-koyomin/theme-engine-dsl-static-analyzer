# 视图：遮罩<Mask>

功能概述

该功能需要与Image共同使用，对Image标签中的图片（tgt.png）和mask标签中的图片（ori.png）进行混合，支持12种混合模式。例如，通过混合可起到遮罩效果，即按照ori图片对tgt图片进行裁剪，使tgt图片显示与ori图片重叠区域的内容。通过设置align参数，可以达到tgt图片移动但ori图片不移动的效果。

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
<Image src="">
    <Mask src="" align="" hybridMode=""/>
</Image>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| src | 字符串 | 必填 | src为遮罩图片的源地址 |
| align | 字符串 | 选填 | 取值为absolute或relative。其中，absolute表示绝对位置，即ori图片不随着tgt图片移动而移动，遮罩**(Mask)x,y坐标为相对于屏幕左上角的坐标；如果为相对位置relative**，则ori会随着tgt而移动，遮罩**(Mask)x,y坐标为相对于图片左上角的坐标。 默认情况下为relative。 |
| hybridMode | 字符串 | 选填 | 设置两张图片之间的混合模式，共有12种混合模式供选择： clear(0)ori(1)tgt(2)oriOver(3)tgtOver(4)oriIn(5)tgtIn(6)oriOut(7)tgtOut(8)oriATop(9)tgtATop(10)xor(11) 可以通过输入字符串或者对应的数字编号来选择混合模式，默认为6 |

应用示例

```
<Image src="tgt.png" x="0" y="#screen_height-92">
  <Mask x="0" y="#screen_height-92" src="ori.png" align="absolute" hybridMode="tgtIn" />
</Image>
```
