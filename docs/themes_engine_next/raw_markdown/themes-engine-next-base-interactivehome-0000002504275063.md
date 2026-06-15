# 适配功能：可交互桌面

功能概述

支持主题创作者通过主题引擎脚本的方式，实时获取桌面的左右滑动事件及桌面空白处的双击事件，设计师获取这些桌面事件后，可以通过主题引擎脚本触发动态桌面（通过主题引擎渲染）发生变化。

创意场景：

1. 划动桌面，领略不同季节的风景。

2. 设计一组动画效果，让用户通过划动屏幕触发，例如小猫卖萌的动作。

![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/40/v3/W7-KyBUlQmGq9ufo7nTorw/caution_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010813Z&HW-CC-Expire=86400&HW-CC-Sign=A87889B2847AE11CE48C0705FA6CD169C1E3F1BF4D65E6A05BC417110644ED4F)

若要实现动态效果，推荐使用序列帧；若使用视频，则需要制作多段视频，并截取每段视频的首帧图，作为defaultBitmap的参数值。

XML规范

```
<Button h="#h" w="#w" x="0" y="0">
  <Triggers>
    <Trigger action="move_right_start">
      <VariableCommand name="" expression="" />
    </Trigger>
  </Triggers>
</Button>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| move_left_start | Action | 选填 | 向左开始滑动 |
| move_left_end | Action | 选填 | 向左滑动结束 |
| move_right_start | Action | 选填 | 向右开始滑动 |
| move_right_end | Action | 选填 | 向右滑动结束 |
| double | Action | 选填 | 双击(只需要识别桌面空白双击，不包括Docker、状态栏、侧边热区) |

应用示例

不涉及
