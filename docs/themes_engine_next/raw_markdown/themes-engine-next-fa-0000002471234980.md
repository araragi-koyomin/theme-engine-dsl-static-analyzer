# 百变卡片<Widget>

功能概述

百变卡片，是创作者基于主题引擎能力编写出各式各样动态效果的卡片，最终用户应用时可根据自己的想法将百变卡片自由组合，打造个性化桌面。

XML规范

```
<?xml version="1.0" encoding="utf-8"?>
<Widget screenWidth="" screenHeight="" frameRate="">
</Widget>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| screenWidth | 数值 | 必填 | 描述文件的虚拟的屏幕宽度，其中： 1*2卡片为1372 2*2卡片为1384 2*4卡片为1372 4*4卡片为1384 |
| screenHeight | 数值 | 必填 | 描述文件的虚拟的屏幕宽度，其中： 1*2卡片为530 2*2卡片为1384 2*4卡片为640 4*4卡片为1440 |
| frameRate | 数值 | 选填 | 屏幕刷新的帧率，数值越大，效果越好，功耗相对较高。默认60，推荐使用30 |

应用示例

以2*2时钟卡片为例，脚本示例如下：

```
<?xml version="1.0" encoding="utf-8"?>
<Widget screenWidth="1384" screenHeight="1384" frameRate="60">
    <Var name="w" const="true" expression="#screen_width" persist="true" />
    <Var name="h" const="true" expression="#screen_height" persist="true" />
    <Image x="0" y="0" w="1384" h="1384" src="bg.png"/>
    <Image x="429" y="161" pivotX="51" pivotY="319" src="minute.png" rotation="360*(#minute/60)"/>
    <Image x="429" y="258" pivotX="51" pivotY="222" src="hour.png" rotation="360*(#hour12/12)+30*(#minute/60)"/>
    <Button x="0" y="0" w="#w" h="#h">
        <Trigger action="click">
            <IntentCommand action="action.system.home" package="com.huawei.hmos.clock" class=" com.huawei.hmos.clock.phone"/>
        </Trigger>
    </Button>
</Widget>
```
