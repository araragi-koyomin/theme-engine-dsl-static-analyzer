# 视图：文本<Text>

功能概述

按照特定格式来显示文本，支持对文字大小、颜色、行间距、是否自动换行（当文字实际宽度超过设置的宽度时）等进行设置；此外，还提供文字阴影等高级的文字效果，支持通用属性。

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
<Text width="" color="" size="" format="" paras="" text="" autoLineFeed="" textalign="" bold="" isSupportClipping="" spaceTimes="" spaceExtraAdd="" shadowDx="" shadowDy="" Radius="" shadowColor="" scrollDisplay="" marqueeRepeatLimit="" clickable="" delayTime="" />
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| color | string | 选填 | 文字颜色，支持6位或8位16进制颜色表示，如：#FFFFFF . #FFFFFFFF，8位中前两位表示透明度，后六位依次表示红. 绿. 蓝；支持函数argb(255,255,255,255) (argb(透明度，红，绿，蓝)，每个数值在[0-255]范围内）；支持字符串变量，如：@abc，字符串变量需为无#的6或8位16进制表示："ffffff". “ffffffff"，或argb函数。 注意： 设置color后通用属性alpha设置无效。 |
| size | 数值 | 选填 | 文字大小，取值正数，默认40，单位为像素。 |
| format | 字符串 | 选填 | 支持在文字中引用数值或字符串变量，用%d，%s 指定变量位置，并在paras参数中指定对应变量表达式；支持引用多个变量；%d表示数值表达式，%s表示字符串表达式。 |
| paras | 字符串 | 选填 | 如果使用format参数, 则需要在paras参数中指定%d，%s对应的变量表达式；多个变量表达式用"," 隔开；%d表示数值表达式，%s表示字符串表达式。 |
| textExp（text） | 字符串 | 选填 | 文字表达式，可直接引用单变量，不支持由多个变量构建的表达式。示例：想要输出“现在时间是9点”，则可以写成textExp="'现在时间是'+#hour+'点'"；text与textExp效果相同。使用text方式，则可以写成text="现在时间是9点"。 |
| width | 数值 | 选填 | 文字宽度，当文字超过该宽度时会被切掉。如果设置了自动换行，则会折行显示文字。 |
| autoLineFeed | 字符串 | 选填 | true/false，控制是否支持自动换行。true为支持自动换行，默认false。为true时须设置width，否则不会自动换行。 |
| textalign | 字符串 | 选填 | 文字的对齐方式，可选输入为left, right, center，分别为左对齐，右对齐和居中对齐。 说明： 对齐功能生效需满足：不换行，不滚动，不裁剪，且设置宽度大于文本实际宽度。 |
| bold | 字符串 | 选填 | true/false，字体是否加粗，true表示加粗，默认false。 |
| isSupportClipping | 字符串 | 选填 | true/false，设置属性超出width后是否裁剪，true表示裁剪，默认false。 |
| spaceTimes | 数值 | 选填 | 行间距倍数，类似于word行间距，默认1。 |
| spaceExtraAdd | 数值 | 选填 | 行间距的额外增加值，即在基础行距上增加多少行间距。单位像素，默认0。 |
| shadowDx | 数值 | 选填 | 文字阴影横向偏移距离，单位为像素。 |
| shadowDy | 数值 | 选填 | 文字阴影纵向偏移距离，单位为像素。 |
| Radius | 数值 | 选填 | 模糊半径，建议范围[1-24],值越小文字阴影越清晰。（模糊半径相同时，size大的文本阴影相对size小的文本模糊程度略小一些） 注意： 小于等于0无阴影效果。 |
| shadowColor | 字符串 | 选填 | 阴影颜色，颜色取值见color参数解释；不设置该参数则无文字阴影效果。 |
| scrollDisplay | 布尔值 | 选填 | true：文本支持滚动显示；false: 文本不支持滚动显示。默认为false，不支持滚动显示。 |
| marqueeRepeatLimit | 数值 | 选填 | 滚动显示时循环滚动次数，和scrollDisplay配合使用。 |
| clickable | 布尔值 | 选填 | true：滚动显示文本可以响应单击和双击，单击和双击文本回到初始状态；false：滚动显示文本可以不响应单击和双击。默认为false，不响应单击和双击，和scrollDisplay配合使用。 |
| delayTime | 数值 | 选填 | 延迟滚动的时间，和scrollDisplay配合使用。 |

应用示例

演示文本行间距、自动换行、文字阴影等效果。

```
<Text x="100" y="400" size="38" text="华为引擎文本1.4倍行间距，支持自动换行“ width="300" autoLineFeed="true" spaceTimes="1" spaceExtraAdd="0.4" Radius="5" shadowDx="3" shadowDy="3" shadowColor="#FFFFFFFF" format="您有%d个未读信息和大约%d个未接来电” paras="#sms_unread_count,#call_missed_count" visibility="#sms_unread_count" />
```
