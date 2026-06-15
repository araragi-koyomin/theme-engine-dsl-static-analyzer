# 变量：变量数组<VarArray>

功能概述

以数组的方式来保存变量；根据index值能够取相应索引的值并且显示。

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
<VarArray type="">
    <Vars>
        <Var name="" index=""/>
    </Vars>
    <Items>
        <Item value=""/>
        <Item value=""/>
    </Items>
</VarArray>
```

参数说明

| **参数** | **类型** | **选项** | **注释** |
| --- | --- | --- | --- |
| name | string | 必填 | 变量名，可以通过@name来返回字符串数组中index指定序号的变量值，返回为字符串无法用作计算 |
| type | string | 选填 | 定义数值变量number或字符串变量string，默认为number； |
| index | string | 选填 | 变量数组中的序号，从0开始，支持表达式。注意点：Item中的顺序决定了index的取值 |
| value | string | 选填 | 变量数组的值，可以是字符串，也可以是数字 |

应用示例

显示英文月份，建立一个名字为month_str的string数组，把十二个月份的英文加入到数组中，并把数组输出指定到全局变量month，这样使用@month_str得到 的就是当前月份的英文字符串。

```
<VarArray type="string">
    <Vars>
      <Var name="month_str" index="#month" />
    </Vars>
    <Items>
      <Item value="January" />
      <Item value="February" />
      <Item value="November" />
      <Item value="December" />
    </Items>
</VarArray>
<Text text="@month_str" color="#FFFFFFFF" size="20" x="100" y="100" />
```
