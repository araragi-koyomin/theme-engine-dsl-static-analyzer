# 数据开放：日历数据开放<Calendar>

功能概述

开放了日历App中订阅管理模块的黄历、星座相关数据。配置Calendar标签后，需要给该标签配置card属性，card属性值分别对应黄历（Almanac）、星座（Constellations）。然后在Calendar标签里面声明var变量并使用指定的变量名，即可声明对应的日历数据变量。变量支持设置默认值，如果获取到接口返回数据会更新该值。

支持范围

**起始规范版本：**

HarmonyOS 6.0

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
<!--黄历字段声明-->
<Calendar card="Almanac">  
<Var name="Almanac.caishen" expression="正南" type="string"/>
<Var name="Almanac.yi" expression="'打扫 签订合同 开业 安机械 安葬 开光 迁坟 上梁'" type="string"/>
<Var name="Almanac.ji" expression="'结婚 祈福'" type="string"/>
</Calendar>
<!--星座字段声明 声明星座字段前必须先声明星座id变量，变量名称固定为constellationsId并且设置默认值为十二星座Id的其中一个-->
<!--星座对应Id和星座映射关系参看附表4.5-->
<Var name="constellationsId" expression="0003001" type="string" persist="true"/>
<Calendar card="Constellations">  
<Var name="Constellations.signName" expression="魔羯座" type="string"/>
    <Var name="Constellations.today.loveDes" expression="'注意调养,适合独处,单身者别与过多的异性纠缠，那样只会让你身心疲惫。'" type="string"/>
    <Var name="Constellations.today.loveStar" expression="3" type="int"/>
</Calendar>
```

参数说明

**黄历数据**

| **变量** | **类型** | **注释** |
| --- | --- | --- |
| Almanac.caishen | 字符串 | 财神位 |
| Almanac.xiongshen | 字符串 | 凶神宜忌 |
| Almanac.taishen | 字符串 | 今日胎神 |
| Almanac.yi | 字符串 | 宜 |
| Almanac.ji | 字符串 | 忌 |
| Almanac.jishen | 字符串 | 吉神宜趋 |
| Almanac.ganzhi | 字符串 | 干支 |
| Almanac.xishen | 字符串 | 喜神位 |
| Almanac.shengxiao | 字符串 | 生肖 |
| Almanac.nongli | 字符串 | 例：腊月十四 |
| Almanac.shen12 | 字符串 | 建除十二神 |
| Almanac.fushen | 字符串 | 福神位 |
| Almanac.yangli | 字符串 | 例： 2025-01-26 |
| Almanac.ziA | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.ziB | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.chouA | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.chouB | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.yinA | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.yinB | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.maoA | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.maoB | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.chenA | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.chenB | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.siA | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.siB | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.wuA | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.wuB | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.weiA | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.weiB | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.shenA | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.shenB | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.youA | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.youB | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.xuA | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.xuB | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.haiA | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |
| Almanac.haiB | 字符串 | A表示拼音地支对应的天干，B表示凶吉 |

**星座数据**

| **变量** | **类型** | **注释** |
| --- | --- | --- |
| Constellations.signName | 字符串 | 星座名称 |
| Constellations.today.loveStar | 数字 | 爱情运势指数 |
| Constellations.today.loveDes | 字符串 | 爱情运势描述 |
| Constellations.today.moneyStar | 数字 | 财富运势指数 |
| Constellations.today.moneyDes | 字符串 | 财富运势描述 |
| Constellations.today.workStar | 数字 | 事业运势指数 |
| Constellations.today.workDes | 字符串 | 事业运势描述 |
| Constellations.today.summaryStar | 字符串 | 综合运势指数 |
| Constellations.today.summaryDes | 字符串 | 综合运势描述 |
| Constellations.today.luckyColor | 字符串 | 幸运颜色 |
| Constellations.today.luckyNumber | 字符串 | 幸运数字 |
| Constellations.today.healthStar | 数字 | 健康指数 |
| Constellations.today.datetime | 字符串 | 日期 格式：2025-01-26 |
| Constellations.today.matchSign | 字符串 | 速配星座 |

**星座ID（constellationsId变量）映射星座名称对应关系为**

：

"constellationsId": "0003001","name": "摩羯座"。

"constellationsId": "0003002", "name": "水瓶座"。

"constellationsId": "0003003","name": "双鱼座"。

"constellationsId": "0003004","name": "白羊座"。

"constellationsId": "0003005", "name": "金牛座"。

"constellationsId": "0003006", "name": "双子座"。

"constellationsId": "0003007","name": "巨蟹座"。

"constellationsId": "0003008","name": "狮子座"。

"constellationsId": "0003009","name": "处女座"。

"constellationsId": "0003010","name": "天秤座"。

"constellationsId": "0003011", "name": "天蝎座"。

"constellationsId": "0003012","name": "射手座"。

![](https://contentcenter-vali-drcn.dbankcdn.cn/pvt_2/DeveloperAlliance_scene_100_1/70/v3/cIZFd11lRJaX9EaM2yhsDA/note_3.0-zh-cn.png?HW-CC-KV=V1&HW-CC-Date=20260607T010806Z&HW-CC-Expire=86400&HW-CC-Sign=E3C05E63FAB3857615500A2A8C321A45972486FAA346E3DD47B535681CA432DC)

刷新频率：每天一次。

刷新时机：用户每天亮屏或进入桌面时触发刷新；星座数据可以通过脚本方式让用户手工触发刷新，即触发constellationId切换时，刷新星座数据。

当使用日历数据变量时，需要提供通过Intent命令的方式跳转至日历App，以便用户在想了解更多日历信息的时候，提升用户体验。

应用示例

示例一：展示黄历数据

```
<!--黄历字段声明-->
<Calendar card="Almanac">  
 <Var name="Almanac.caishen" expression="正南" type="string"/>
</Calendar>
<Text x="40" y="100" alignV="top" color="#ffffff" size="30" format="财神: %s" width ="1000" autoLineFeed="true" paras="#Almanac.caishen"/>
```

示例二：展示星座数据

```
<!--星座字段声明 声明星座字段前必须先声明星座id变量，变量名称固定为constellationsId并且设置默认值为十二星座Id的其中一个-->
<Var name="constellationsId" expression="0003001" type="string" persist="true"/>
<Calendar card="Constellations">  
<Var name="Constellations.signName" expression="魔羯座" type="string"/>
</Calendar>
<Text x="40" y="500" alignV="top" color="#ffffff" size="30" format="星座名称: %s" width ="1000" autoLineFeed="true" paras="#Constellations.signName"/>
<Button x="300" y="900" h="100" w="200"> 
    <Trigger action="down"> 
       <VariableCommand name="constellationsId" expression="0003012" type="string" /> 
    </Trigger> 
</Button>
```
