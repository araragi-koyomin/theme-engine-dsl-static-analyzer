# 数据开放：天气数据开放<Weather>

功能概述

开放天气App中天气相关数据：配置Weather标签后，在Weather标签中声明Var变量并使用指定的变量名，即可声明气象、空气质量指数、温度、湿度、风向、日出日落时间、月出月落时间、穿衣指数、运动指数、感冒指数、洗车指数等天气数据对应的变量。变量支持默认值，如果获取到实际天气情况会更新该值。昨天和明天的数据只需要将today改成yesterday和tomorrow即可。

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
<Weather>
    <Var name="Weather.today.weatherIcon" expression="1" type="int"/>
    <Var name="Weather.today.weatherIconDes" expression="晴" type="string"/>
    <Var name="Weather.today.aqivalue" expression="1" type="int"/>
    <Var name="Weather.today.aqivaluetext" expression="优质" type="string"/>
    <Var name="Weather.today.currentTem" expression="25" type="int"/>
    <Var name="Weather.today.maxtemp" expression="28" type="int"/>
    <Var name="Weather.today.mintemp" expression="20" type="int"/>
    <Var name="Weather.today.humidity" expression="20" type="int"/>
    <Var name="Weather.today.winddir" expression="NE" type="string"/>
    <Var name="Weather.today.winddirDes" expression="东北风" type="string"/>
    <Var name="Weather.today.sunRise" expression="06：05" type="string"/>
    <Var name="Weather.today.sunSet" expression="18：05" type="string"/>
    <Var name="Weather.today.moonRise" expression="06：05" type="string"/>
    <Var name="Weather.today.moonSet" expression="06：05" type="string"/>
    <Var name="Weather.today.dressingLevel" expression="1" type="int"/>
    <Var name="Weather.today.sportsLevel" expression="1" type="int"/>
    <Var name="Weather.today.coldLevel" expression="1" type="int"/>
    <Var name="Weather.today.carWashLevel" expression="1" type="int"/>
</Weather>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| Weather.today.weatherIcon | 数值 |  | 天气现象类型编号，详情下方见：天气现象类型 |
| Weather.today.weatherIconDes | 字符串 |  | 天气现象类型描述，详情下方见：天气现象类型 |
| Weather.today.aqivalue | 数值 |  | 空气质量指数值：详情下方见：空气质量类别 |
| Weather.today.aqivaluetext | 字符串 |  | 空气质量等级划分枚举值：详情下方见：空气质量类别 |
| Weather.today.currentTem | 数值 |  | 当前温度。无昨天和明天数据。 |
| Weather.today.maxtemp | 数值 |  | 最高温度。 |
| Weather.today.mintemp | 数值 |  | 最低温度。 |
| Weather.today.humidity | 字符串 |  | 湿度。无昨天和明天数据。 |
| Weather.today.winddir | 字符串 |  | 风向枚举值：NE，E，SE， S，SW，W，N，NW。 |
| Weather.today.winddirDes | 字符串 |  | 风向枚举值：NE 东北风，E 东风，SE 东南风， S 南风，SW 西南风，W 西风，N 北风，NW 西北风。 |
| Weather.today.sunRise | 字符串 |  | 日出 HH:mm格式 06：05。 |
| Weather.today.sunSet | 字符串 |  | 日落 HH:mm格式 18：05。 |
| Weather.today.moonRise | 字符串 |  | 月出 HH:mm格式 18：05。 |
| Weather.today.moonSet | 字符串 |  | 月落 HH:mm格式 06：05。 |
| Weather.today.dressingLevel | 数值 |  | 穿衣指数：1 羽绒服-寒冷；2 厚外套-冷；3 毛衣-较冷；4 薄外套-较舒适；5 长袖-舒适；6 短袖-热；7 短袖-炎热。无昨天和明天数据。 |
| Weather.today.sportsLevel | 数值 |  | 运动指数：1 适宜运动；2 较适宜运动；3 较不宜运动。无昨天和明天数据。 |
| Weather.today.coldLevel | 数值 |  | 感冒指数：1 感冒少发；2 感冒较易发；3 感冒易发；4 感冒极易发热。无昨天和明天数据。 |
| Weather.today.carWashLevel | 数值 |  | 洗车指数：1 适宜；2 较适宜；3 较不宜；4 不宜。无昨天和明天数据。 |

**天气现象类型**

| **天气类型** | **值** | **含义** | **注释** |
| --- | --- | --- | --- |
| UNDEFINED | 0 | 未定义 | 未知（0/9/10/27/28） |
| SUNNY | 1 | 晴 | 晴 |
| MOSTLY_SUNNY | 2 | 多云 | 大部晴天 |
| PARTLY_SUNNY | 3 | 多云 | 局部晴天 |
| INTERMITTENT_CLOUDS | 4 | 多云 | 局部多云 |
| HAZY_SUNSHINE | 5 | 霾 | 霾 |
| MOSTLY_CLOUDY | 6 | 多云 | 大部多云 |
| CLOUDY | 7 | 多云 | 多云 |
| OVERCAST | 8 | 阴 | 阴 |
| FOG | 11 | 雾 | 雾 |
| SHOWERS | 12 | 阵雨 | 阵雨 |
| MOSTLY_CLOUDY_WITH_SHOWERS | 13 | 阵雨 | 大部多云，伴有阵雨 |
| PARTLY_SUNNY_WITH_SHOWERS | 14 | 阵雨 | 局部晴天，伴有阵雨 |
| T_STORMS | 15 | 雷阵雨 | 雷阵雨 |
| MOSTLY_CLOUDY_WITH_T_STORMS | 16 | 雷阵雨 | 大部多云，伴有雷阵雨 |
| PARTLY_SUNNY_WITH_T_STORMS | 17 | 雷阵雨 | 局部晴天，伴有雷阵雨 |
| RAIN | 18 | 雨 | 雨 |
| FLURRIES | 19 | 阵雪 | 阵雪 |
| MOSTLY_CLOUDY_WITH_FLURRIES | 20 | 阵雪 | 大部多云，伴有阵雪 |
| PARTLY_SUNNY_WITH_FLURRIES | 21 | 阵雪 | 局部晴天，伴有阵雪 |
| SNOW | 22 | 雪 | 雪 |
| MOSTLY_CLOUDY_WITH_SNOW | 23 | 雪 | 大部多云，伴有雪 |
| ICE | 24 | 雪 | 冰 |
| SLEET | 25 | 雨夹雪 | 雨夹雪 |
| FREEZING_RAIN | 26 | 雨 | 冻雨 |
| RAIN_AND_SNOW | 29 | 雨夹雪 | 雨夹雪 |
| HOT | 30 | 炎热 | 炎热 |
| COLD | 31 | 寒冷 | 寒冷 |
| WINDY | 32 | 有风 | 有风 |
| CLEAR | 33 | 晴 | 晴 |
| MOSTLY_CLEAR | 34 | 多云 | 大部晴天 |
| PARTLY_CLOUDY | 35 | 多云 | 局部多云，有阵雨 |
| INTERMITTENT_CLOUDS_NIGHT | 36 | 多云 | 局部多云 |
| HAZY_MOONLIGHT | 37 | 霾 | 霾 |
| MOSTLY_CLOUDY_NIGHT | 38 | 多云 | 大部多云 |
| PARTLY_CLOUDY_WITH_SHOWERS | 39 | 阵雨 | 局部多云，伴有阵雨 |
| MOSTLY_CLOUDY_WITH_SHOWERS_NIGHT | 40 | 阵雨 | 大部多云，伴有阵雨 |
| PARTLY_CLOUDY_WITH_T_STORMS | 41 | 雷阵雨 | 局部多云，伴有雷阵雨 |
| MOSTLY_CLOUDY_WITH_T_STORMS_NIGHT | 42 | 雷阵雨 | 大部多云，伴有雷阵雨 |
| MOSTLY_CLOUDY_WITH_FLURRIES_NIGHT | 43 | 阵雪 | 大部多云，伴有阵雪 |
| MOSTLY_CLOUDY_WITH_SNOW_NIGHT | 44 | 雪 | 大部多云，伴有雪 |
| THUNDERSHOWER_WITH_HAIL | 45 | 冰雹 | 雷阵雨，伴有冰雹 |
| LIGHT_RAIN | 46 | 小雨 | 小雨 |
| MODERATE_RAIN | 47 | 中雨 | 中雨 |
| HEAVY_RAIN | 48 | 大雨 | 大雨 |
| STORM | 49 | 暴雨 | 暴雨 |
| HEAVY_STORM | 50 | 暴雨 | 大暴雨 |
| SEVERE_STORM | 51 | 特大暴雨 | 特大暴雨 |
| LIGHT_SNOW | 52 | 阵雪 | 小雪 |
| MODERATE_SNOW | 53 | 中雪 | 中雪 |
| HEAVY_SNOW | 54 | 大雪 | 大雪 |
| SNOWSTORM | 55 | 暴雪 | 暴雪 |
| DUST_STORM | 56 | 沙尘暴 | 沙尘暴 |
| LIGHT_TO_MODERATE_RAIN | 57 | 中雨 | 小到中雨 |
| MODERATE_TO_HEAVY_RAIN | 58 | 大雨 | 中到大雨 |
| HEAVY_RAIN_TO_STORM | 59 | 暴雨 | 大到暴雨 |
| STORM_TO_HEAVY_STORM | 60 | 暴雨 | 暴雨到大暴雨 |
| HEAVY_TO_SEVERE_STORM | 61 | 特大暴雨 | 大暴雨到特大暴雨 |
| LIGHT_TO_MODERATE_SNOW | 62 | 中雪 | 小雪到中雪 |
| MODERATE_TO_HEAVY_SNOW | 63 | 大雪 | 中雪到大雪 |
| HEAVY_SNOW_TO_SNOWSTORM | 64 | 暴雪 | 大到暴雪 |
| DUST | 65 | 浮尘 | 浮尘 |
| SAND | 66 | 扬沙 | 扬沙 |
| SANDSTORM | 67 | 强沙尘暴 | 强沙尘暴 |
| DENSE_FOGGY | 68 | 雾 | 浓雾 |
| MODERATE_FOGGY | 69 | 雾 | 强浓雾 |
| MODERATE_HAZE | 70 | 霾 | 中度霾 |
| HEAVY_HAZE | 71 | 霾 | 重度霾 |
| SEVERE_HAZE | 72 | 霾 | 严重霾 |
| HEAVY_FOGGY | 73 | 雾 | 大雾 |
| SEVERE_FOGGY | 74 | 雾 | 特强浓雾 |
| OVERCAST_NIGHT | 75 | 阴 | 阴 |
| BLOWING_SNOW | 76 | 阵雪 | 吹雪 |

**空气质量类别**

| **空气质量类别** | 值 | 含义 | **注释** |
| --- | --- | --- | --- |
| UNDEFINED | 0 | 未定义 | 未知 |
| EXCELLENT | 1 | 优质 | 优质 |
| GOOD | 2 | 良 | 良好 |
| SLIGHT | 3 | 轻度污染 | 轻度污染 |
| MODERATE | 4 | 中度污染 | 中度污染 |
| HEAVY | 5 | 重度污染 | 重度污染 |
| SEVERE | 6 | 严重污染 | 严重污染 |

应用示例

展示今天的天气状况

```
<Text x="40" y="200" alignV="top" color="#ffffff" size="30" format="今天天气状况码: %s" width="1000" autoLineFeed="true" paras="#Weather.today.weatherIcon"/>
<Text x="40" y="200" alignV="top" color="#ffffff" size="30" format="今天空气质量: %s" width="1000" autoLineFeed="true" paras="#Weather.today.aqivaluetext"/>
```
