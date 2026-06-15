# 注意事项

| **分类** | **注意事项说明** |
| --- | --- |
| 通用 | 针对标签的x,y,w,h属性，如果表达式第一个变量值或者数字前是负号，需在前面补0，如x="-#w" 替换为x="0-#w" |
| 底部相机坐标不能被锁屏通知遮挡 |  |
| 锁屏上时间和日历功能都需要支持跳转，底部如果有天气和相机图标，需双击跳转 |  |
| NEXT主题引擎计算精度不准，在进行数值判断时，最好加1 ，如：<Var name="slide" expression="min(#PageMove+1,#w)/#w" />NEXT主题引擎计算精度不准，在进行数值判断时，最好加1 ，如：<Var name="slide" expression="min(#PageMove+1,#w)/#w" /> |  |
| 亮屏时不需要启动的animation需要在ExternalCommands的resume中暂停掉，如：<Command target="rotationAnimation.animation" value="stop" /> |  |
| 视图 | 主题包设计时，首屏里划动容易误触位置或者大视图里时间、日期的跳转命令使用click代替up防误触导致的解锁后跳转应用。 |
| 脚本中Image标签的bmp_width 、bmp_height 如果存在需要替换为actual_w、actual_h。 |  |
| 长文本中使用英文逗号时，需使用单引号 ',' 进行转义。 |  |
| Video 标签使用了defaultBitmap图片，但是 resource文件目录中找不到对应的图片会导致黑屏，可使用Image标签作为兜底图。 |  |
| 尽量减少Video标签使用，可使用VideoCommand切换视频源 |  |
| 桌面上使用Video标签进行视频播放，不再使用VideoWallpaper |  |
| 桌面为视频，使用视频的首帧作为背景图，在Image标签中使用 |  |
| ImageNumber标签的资源必须从0开始命名，且图片序列不能缺失 |  |
| 折叠屏和平板中全屏的 Image 或 Video 标签需要添加 IsFullScreenNode="true"参数，填充满屏幕宽高，此时 x,y 必须要等于0 |  |
| 脚本中的动画暂停，需要在ExternalCommands的pause中调用stop方法 |  |
| 变量 | 脚本中如果变量为负数，需在前面补0，如x="0-#w" |
| 脚本中变量计算，尤其是坐标计算不准确，有精度误差，需要添加偏移量计算来保证结果准确性，如：<Var name="slide" expression="min(#PageMove+1,#w)/#w" /> |  |
| 字符串表达式里嵌套数字表达式需要加一个花括号，例如：srcExp="number/hour/{int(#system.time.hour1)}_{int(#aniTime)}.png" |  |
| 脚本中未使用Var标签定义的变量，直接使用后，不会重置，需要在ExternalCommand的resume中进行手动重置 |  |
| 脚本中变量如果不写persist，默认是不保存 |  |
| 控件 | NEXT主题上Button的响应区域是脚本中设置的区域，如果手指抬起时不在该区域内，Button不会响应 |
| 命令 | IntentCommand 中的包名和类名需要替换 |
| NEXT主题中IntentCommand不能跳转二级页面，如支付宝微信的付款码 |  |
| 文件夹包名com.huawei.hmos.filemanager需要替换成com.huawei.hmos.files |  |
| 数据开放 | 系统开放数据刷新标签要放在所有数据标签的最后才能生效， <Var name="pollingTime" time="1" /> |
| 锁屏步数steps_value使用前需要添加Healthy标签，从运动健康获取，一般加在脚本最开始定义变量的地方，补充： <Healthy polling="true" time="5"> <Var name="steps_value" /> </Healthy> |  |
| 音乐控制功能标签MediaControl改为MediaController |  |
