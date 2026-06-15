# 表达式：字符串表达式<StringExpression>

功能概述

支持字符型表达式的解析，相比于数值型的表达式，其返回值为字符串型，不能够作为数值进行计算，只能作为字符串相关的函数的参数，字符串表达式的主要功能提供了不同的操作字符串的功能。字符串表达式内能嵌套字符串表达式的函数、字符串、字符串变量、数值型变量。其他功能点中类型为字符串且支持表达式的参数可以调用字符串表达式解析能力。

字符串表达式里嵌套数字表达式需要加一个花括号。

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

支持数组：如@string[expression],索引支持表达式解析。

支持数值和字符串型的全局变量解析：如#num，@str。注意：如果需要在字符串表达式中进行数值计算不能以#num这样的变量开头，如#num*10会被认为是取名为"num*10"的数值型变量，正确写法为：10*#10

支持下表中字符串函数。

字符串表达式中的字符串必须使用单引号，例如"@a + 'hello' + @b"。注意："+"在字符串表达式中表示字符串的拼接，即在解析字符串表达式时会首先根据"+"对表达式进行拆分，然后分别解析拆分后的子字符串表达式，最后将子字符串表达式结果拼接得到最终结果，"+"不支持数值的加法计算。

参数说明

| **表达式** | **注释** |
| --- | --- |
| substr(原字符串，字串开始位置，字串长度) | 返回字符串的子串，substr('你好呀',1,2) = '好呀'（注意：字符位置是从左至右，索引从0开始） |
| strIsEmpty(str) | 字符串是否为空，即“”，为空返回“true”字符串，否则返回“false”字符串。 例如：strIsEmpty(@abc) ，str可以是字符串、字符串变量、字符串函数，下同 |
| strIndexOf(str1,str2) | 字符串str2在字符串str1中首次出现的位置，索引从0开始，例如：strIndexOf('hello','he')=0，返回“0”，如果目标字符串没有出现则返回“-1” |
| strLastIndexOf(str1, str2) | 字符串str2在字符串str1zhong 最后出现的位置，索引从0开始，例如：str_LastIndexOf('hello','l')=3，返回“3”，如果目标字符串没有出现则返回“-1” |
| strContains(str1,str2) | 字符串str1中是否包含字符串str2，包含则返回"true"，否则返回"false"。例如： strContains('hello','he')=true ，返回"true" |
| strReplaceAll(str1,str2,str3) | 将str1中所有的str2替换为str3 ,例如（strReplaceAll('hello','e','i')＝”hillo“ |
| preciseeval(str,数值/数值型变量) | 计算str的值，其中第一位是字符串/字符串变量型/数字表达式函数的计算公式，第二位为数值/数值型变量标识保留的小数位数，例如： preciseeval({1/3},3)=0.333，返回数值字符串“0.333”。注意使用preciseeval运算符则其后不能使用其他的运算符和+连接符 |
| formatDate('format',@time_sys) | 返回指定格式的时间字符串，时间为此刻的系统时间，如：formatDate('yyyy-MM-dd hh:mm:ss',@time_sys)=“2019-08-23 19:15:10”，具体能够转换的时间和日期的格式可以参考系统日期占位符 |
| plus(a,b) | 返回a和b的和的整数值，其中a和b可以为字符串/字符串变量或者数值/数值变量/返回数值的字符串函数，例如：plus(3,3)=6，返回“6” |
| ifelse(x1,y1,x2,y2,...,xn,yn,z) | if (x1!=0) return y1; elseif(x2 !=0) return y2;...; else return z。从x1开始判断，若存在xi不等于0，则返回yi，否则一直往后判断，都不满足条件则最终返回z。xi为数字表达式，yi、z为返回的字符串结果，可以是字符串/字符串变量/字符串函数 |
| + | 用以拼接字符串，如#year+'/'+#month+'/'+#date，其中字符串必须使用单引号标明 |
| strEqual(str1,str2) | 判断两个字符串是否相等，如'strEqual("1234567890","12345678")'，两个字符完全相等，会返回一个字符串'true',否则为'false' |
| argb(x,y,z,w) | argb函数使用，解析透明度和红绿蓝三原色的值，最终返回8位16进制颜色表示字符串。 |
| 字符串达式里嵌套数字表达式 | 字符串表达式里嵌套数字表达式需要加一个花括号，例如：srcExp="number/hour/{int(#system.time.hour1)}_{int(#aniTime)}.png" |

应用示例

示例一：字符串与变量混合拼接，取数值和字符串变量值并且拼接，如果20191001，该文本输出为2019/10/01。

```
<Text x="650" y="1650" size="30" textExp="#year+'/'+#month+'/'+#date"/>
```

示例二：substr字符串函数使用，截取字符串子串。

```
<Var name="a" expression="hello hello hello hello" type="string"/>
<Var name="K" expression="3"/>
<Text x="50" y="0" size="30" format="substr(@a,0,3):%s" paras="substr(@a,0,3)"/>
<Text x="50" y="0" size="30" format="substr(substr(@a,0,12),#k,5):%s" paras="substr(substr(@a,0,12),#k,5)"/>
```
