# 命令：碰一碰<CollaborationCommands>

功能概述

通过碰一碰协同框架能力，支持主题在碰一碰场景定义一些动画交互能力。

在原有碰一碰能力基础上，新增支持碰一碰长连接能力，新增设备连接成功和断开的触发动作，支持主动断开连接和发送消息命令，实现设备间通过碰一碰建立连接后的持续数据交互。

支持范围

**起始规范版本：**

HarmonyOS 6.0

**是否平台特性：**

否

|  | 锁屏（Lockscreen） | 桌面（Wallpaper） | 一镜到底（LongTake） | 百变卡片（Widget） | 充电动效（ChargingSkin） |
| --- | --- | --- | --- | --- | --- |
| 是否支持 | √ | x | x | √ | x |

|  | 直板机 | 折叠屏 | 平板 |
| --- | --- | --- | --- |
| 是否支持 | √ | √ | x |

XML规范

**碰一碰**

```
<CollaborationCommands collaborationId="AA00">
       <Trigger action="tapLink">
              <VideoCommand name="sp1" play="true"/>
       </Trigger>
       <DataShare>
              <Var name="serviceVar" expression="#local"/>
       </DataShare>
</CollaborationCommands>
```

**长连接**

```
<CollaborationCommands collaborationId="AA00" abilityId="1">
  <Trigger action="tapConnected">
    <CollaborationSendCommand name="connected_colSendMessage" dataName="dancingData"/>
  </Trigger>
  <Trigger action="tapDisconnected">
    <VariableCommand name="isDisconnected" expression="1"/>
  </Trigger>
  <DataShare name="dancingData">
    <Var name="dancingTime" expression="ifelse(#dancingPlayEnd,0,#tiaowu.mp4.currentTime)"/>
  </DataShare>
</CollaborationCommands>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| collaborationId | 字符串 | 必填 | 两个主题可以支持碰一碰协同效果的协同ID，不同协同ID的两个设备主题碰一碰不会触发协同效果，协同ID由ThemeStudio Pro工具自动生成，长度为4的字符串，内容为A-Z或a-z或0-9 |
| abilityId | 数值 | 选填 | 不配置时，默认为碰一碰 0：碰一碰 1：长连接 **起始规范版本**：HarmonyOS 7.0 |
| DataShare | 对象 | 选填 | 数据分享能力。在设计碰一碰互动主题的时候，双端设备可能需要感知对方设备主题的一些状态 ，以便于在处理设备碰一碰的时候定义一些设备状态感知的互动效果。通过数据分享能力节点，我们可以将当前设备的数据在手机碰一碰的时刻，同步到对端主题设备中。在设计主题脚本的时候，就可以基于DataShare里面定义的数据变量拿到对端设备的数据状态。 **碰一碰：**DataShare节点仅支持4个自定义数字变量（详情见[自定义变量](https://wiki.huawei.com/domains/51520/wiki/119285/WIKI202406113744698)[Var](https://wiki.huawei.com/domains/51520/wiki/119285/WIKI202406113744698)），支持数字表达式，变量值建议控制在0-127，abilityId不配置或为0时生效。 **长连接：** DataShare节点支持最大1M 的数据传输，abilityId为1时生效。 |
| enable | 数值 | 选填 | 支持数值表达式控制协同能力是否打开，0：关闭，1：打开。默认为1(例外：针对百变卡片，跨设备互动能力默认为关闭，需要通过设置全局变量enableCollaboration开启能力) |

**子节点：Trigger**

| **参数** | **类型** | **值** | **注释** |
| --- | --- | --- | --- |
| action | 字符串 | tapLink | 碰一碰触发动作 |
| 字符串 | tapConnected | 设备连接成功，abilityId为1时生效 |  |
| 字符串 | tapDisconnected | 设备断开，abilityId为1时生效 |  |

**子节点：DataShare**

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| name | 字符串 | 选填 | 共享数据名称,用于标识不同的数据分享节点,支持多个 DataShare 节点区分数据 |

![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/08/v3/xSuwpRFWQGuVvNAzlh5KSQ/caution_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010802Z&HW-CC-Expire=86400&HW-CC-Sign=9CE1EDA988966A407BD776E49B2F7A8A93F3141218AF0911FB8592A1F006CD41)

1、协同ID由主题制作工具ThemeStudio Pro自动生成，随意设置无效。

2、同一个作品必须使用同一个互动ID，上传主题联盟时，将校验同一个作品下的资源使用的互动ID是否一致。

3、同一时刻只有一个实例，一个脚本中只能有一个CollaborationCommands节点。

4、碰一碰卡片命令在百变卡片场景默认能力是关闭的，需要设计师通过动态设置变量enableCollaboration控制当前卡片碰一碰能力开启，同时结合系统enable变量控制协同卡片是否生效。

5、长连接场景

（1）已连接的2个设备A和B，第3个设备C碰设备A或设备B，无反应。

（2）由于长连接若不断开会持续产生功耗，以下场景系统会自动断开长连接：锁屏——灭屏或者解锁屏幕；百变卡片——离开当前屏幕。

应用示例

**示例一**

：展示碰一碰命令在锁屏场景下的使用,通过触发动作控制视频播放,并使用DataShare节点分享视频播放时间数据。

```
<?xml version="1.0" encoding="utf-8"?>
<Lockscreen version="1" frameRate="30" screenWidth="1440">
       <Var name="playEnd" expression="eq(#tiaowu.mp4.state,5)"/>
       <CollaborationCommands collaborationId="AA00">
              <Trigger action="tapLink">
                     <VideoCommand name="video" play="true" src="tiaowu.mp4" seekTime="ifelse(#playEnd,#playTime,max(#tiaowu.mp4.currentTime,#playTime))"/>
              </Trigger>
              <DataShare>
                     <Var name="playTime" expression="ifelse(#playEnd,0,#tiaowu.mp4.currentTime)"/>
              </DataShare>
       </CollaborationCommands>
</Lockscreen>
```

![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/b3/v3/kFGftwJgStux66_-YITCOw/note_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010802Z&HW-CC-Expire=86400&HW-CC-Sign=8F642938F7B5B491E61AB73BD4AD22D2A7834140BABD1804A8C22E0C73A954FD)

src.state，src.currentTime参考

[全局变量](https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-base-globalvar-0000002471235030)

的定义

**示例二**

：展示碰一碰长连接能力在锁屏场景下的使用,支持设备连接成功和断开的触发动作,使用多个 DataShare 节点分享不同数据,并使用 CollaborationSendCommand 和 CollaborationDisconnectCommand 实现主动消息发送和断开连接。

```
<?xml version="1.0" encoding="UTF-8"?>
<Lockscreen displayDesktop="true" frameRate="60" screenWidth="1080" version="1">
    <Var name="w" const="true" expression="#screen_width"/>
    <Var name="h" const="true" expression="#screen_height"/>
    <Var expression="eq(#tiaowu.mp4.state,5)" name="dancingPlayEnd" />
    <Var expression="eq(#changge.mp4.state,5)" name="singingPlayEnd" />
    <CollaborationCommands collaborationId="AA00" abilityId="1">
        <Trigger action="tapConnected">
            <CollaborationSendCommand name="connected_colSendMessage" dataName="dancingData"/>
        </Trigger>
        <Trigger action="tapDisconnected">
            <VariableCommand name="isDisconnected" expression="1"/>
        </Trigger>
        <DataShare name="dancingData">
              <Var name="dancingTime" expression="ifelse(#dancingPlayEnd,0,#tiaowu.mp4.currentTime)"/>
        </DataShare>
        <DataShare name="singingData">
              <Var name="singingTime" expression="ifelse(#singingPlayEnd,0,#changge.mp4.currentTime)"/>
        </DataShare>
    </CollaborationCommands>
    <Button h="#h" w="#w" x="0" y="0" name="断开长链接">
        <Trigger action="down">
            <CollaborationDisconnectCommand/>
        </Trigger>
    </Button>
    <Button h="#h" w="#w" x="0" y="0" name="发送消息">
        <Trigger action="down">
            <CollaborationSendCommand name="btn_colSendMessage" dataName="singingData"/>
        </Trigger>
    </Button>
    <Image src="goWork/find_money.png" x="#w*0.2013*0.2" y="#h*0.5289" w="#w*0.2013" h="#w*0.2013*0.7931" delayTime="0" visibility="eq(#isDisconnected,1)"/>
</Lockscreen>
```
