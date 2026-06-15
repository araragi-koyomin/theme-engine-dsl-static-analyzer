# 命令：Intent命令<IntentCommand>

功能概述

通过IntentCommand命令，可以跳转打开其他应用程序App，一次只能跳转一个应用，不能实现连续跳转多个应用。

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

```
<IntentCommand action="" category="" package="" class="" condition="" delay="" delayCondition="" />
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| action | 字符串 | 选填 | intent的action（使用package和class时忽略该参数。参考应用示例） |
| category | 字符串 | 选填 | intent需要设置的category（参考应用示例） |
| package | 字符串 | 必填 | intent需要设置的的包名（参考应用示例） |
| class | 字符串 | 必填 | intent需要设置的类名（参考应用示例） |
| condition | 表达式 | 选填 | 条件判断，支持表达式。当condition里的条件判断为非0或者为true时，该命令执行，为false或者0则不执行。 |
| delay | 数值 | 选填 | 延迟，以毫秒记。延迟delay毫秒后执行该命令。 |
| delayCondition | 表达式 | 选填 | 延迟判断，为真则delay命令生效，否则失效。默认为true或者1时，表示可以延迟启动命令，如果false或者非1则不延迟执行。 |
| uri | 字符串 | 选填 | 目标跳转的页面。 起始规范版本为HarmonyOS 6.0 |
| type | 字符串 | 选填 | 表示MIME type类型描述，打开文件的类型，主要用于文管打开文件。比如：'text/xml' 、 'image/*'等。 起始规范版本为HarmonyOS 6.0 |

应用示例

**示例一：打开相机**

```
<IntentCommand action="action.system.home" category="entity.system.home" package="com.huawei.hmos.camera" class="com.huawei.hmos.camera.MainAbility" />
```

**示例二：跳转游戏中心元神游戏下载界面**

```
<IntentCommand uri="https://game.cloud.huawei.com/gc/link/detail?appId=C5765880207854347801" type="" />
```

华为鸿蒙应用包名列表

| **应用名** | **包名** | **页面名** |
| --- | --- | --- |
| 桌面 | com.ohos.sceneboard | / |
| 设置 | com.huawei.hmos.settings | com.huawei.hmos.settings.MainAbility |
| 图库 | com.huawei.hmos.photos | com.huawei.hmos.photos.MainAbility |
| 相机 | com.huawei.hmos.camera | com.huawei.hmos.camera.MainAbility |
| 联系人 | com.ohos.contacts | / |
| 电话 | com.ohos.callui | / |
| 短信 | com.ohos.mms | / |
| 畅连 | com.huawei.hmos.meetime | / |
| 备忘录 | com.huawei.hmos.notepad | / |
| 华为笔记 | com.huawei.hmos.hinote | / |
| 日历 | com.huawei.hmos.calendar | MainAbility |
| 主题 | com.huawei.hmsapp.thememanager | MainAbility |
| 云空间 | com.huawei.hmos.clouddrive | / |
| 文件管理 | com.huawei.hmos.filemanager | / |
| 智慧搜索 | com.huawei.hmsapp.hisearch | / |
| 负一屏 | com.huawei.hmsapp.intelligent | / |
| 天气 | com.huawei.hmsapp.totemweather | com.huawei.hmsapp.totemweather.MainAbility |
| 华为钱包 | com.huawei.hmos.wallet | / |
| 小艺输入法 | com.huawei.hmos.inputmethod | / |
| 华为应用市场 | com.huawei.hmsapp.appgallery | / |
| 华为浏览器 | com.huawei.hmos.browser | / |
| 华为视频 | com.huawei.hmsapp.himovie | MainAbility |
| 华为音乐 | com.huawei.hmsapp.music | MainAbility |
| 华为阅读 | com.huawei.hmsapp.books | / |
| 游戏中心 | com.huawei.hmsapp.gamecenter | / |
| 智慧生活 | com.huawei.hmos.ailife | / |
| 运动健康 | com.huawei.hmos.health | MainAbility |
| Welink | com.huawei.it.works | / |
| xGate | com.huawei.xgate | / |
| 开机向导（OOBE） | com.huawei.hmos.hwstartupguide | / |
| 玩机技巧 | com.huawei.hmos.tips | / |
| 智能提醒 | com.huawei.hmos.advisor | / |
| 手机克隆 | com.huawei.hmos.dataclone | / |
| 信息中转站 | com.huawei.hmos.superhub | / |
| 计算器 | com.huawei.hmos.calculator | com.huawei.hmos.calculator.CalculatorAbility |
| 时钟 | com.huawei.hmos.clock | com.huawei.hmos.clock.phone |
| 健康使用设备 | com.huawei.hmos.parentcontrol | / |
| 屏幕朗读 | com.huawei.hmos.screenreader | / |
| 华为商城 | com.huawei.hmos.vmall | / |
| 查找设备 | com.huawei.hmos.findservice | / |
| 指南针 | com.huawei.hmsapp.compass | / |
| 播控中心 | com.huawei.hmos.mediacontroller | / |
| 紧急拨号 | com.ohos.callui | / |
| 移动网络设置 | com.ohos.callui | / |
| 华为分享 | com.huawei.hmos.instantshare | / |

其他主要鸿蒙应用包名列表

| **应用名** | **包名** | **页面名** |
| --- | --- | --- |
| 支付宝 | com.alipay.mobile.client | EntryAbility |
| 小红书 | com.xingin.xhs_hos | EntryAbility |
| 微博 | com.sina.weibo.stage | EntryAbility |
| 知乎 | com.zhihu.hmos | PhoneAbility |
| 抖音 | com.ss.hm.ugc.aweme | MainAbility |
| 美团 | com.sankuai.hmeituan | EntryAbility |
| 芒果TV | com.mgtv.phone | EntryAbility |
| 腾讯视频 | com.tencent.videohm | AppAbility |
| 优酷视频 | com.youku.next | EntryAbility |
| 哔哩哔哩 | yylx.danmaku.bili | EntryAbility |
| 爱奇艺 | com.qiyi.video.hmy | EntryAbility |
