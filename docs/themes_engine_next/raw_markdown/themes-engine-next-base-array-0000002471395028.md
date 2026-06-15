# 变量：控件数组<Array>

功能概述

能够同样重复多条目的生成图片Image、文字Text等界面控件。

注意：在该控件中使用Text控件，需使用texEmp的方式获取数组中文本数据，如下面XML规范及用例中所写。

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
<Array indexFlag="" frequency="">
    <Image src="" srcid="" w="" h="" x="" y=""/>
</Array>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| indexFlag | 字符串 | 必填 | 代表索引的名称，能够在下方的元素中引用 |
| frequency | 表达式 | 必填 | 表示重复生成元素的次数 |

应用示例

元素数组能够与变量和周期命令结合共同调控。

声明了一个大小为100的变量数组但是并没有赋予初值，并且设置了参数const为true，代表变量只能被赋值一次。元素数组循环生成了100个不同的Text，其中textExp、x、y都是由其中的表达式和目前循环的次数计算得到。由于名字为find的数组没有赋予初值，所以目前没有任何显示。按下button之后，周期命令循环给find[index]变量赋值，并且通知到响应的Text，刷新最新的值。

```
  <Var name="find" type="number[]" size="3" const="1" />
  <Array indexFlag="__i" frequency="3" x="0" y="0">
    <Image src="weeks.png" srcid="#find[#__i]" scaleType="fill" w="130" h="130" x="0" y="#__i*140" />
  </Array>
  <Button w="700" h="150" x="180" y="30" visibility="1" active="1">
    <Trigger action="up">
      <CycleCommand frequency="3" indexFlag="__i">
        <VariableCommand name="find" type="number" index="#__i" expression="#__i" />
      </CycleCommand>
    </Trigger>
  </Button>
```
