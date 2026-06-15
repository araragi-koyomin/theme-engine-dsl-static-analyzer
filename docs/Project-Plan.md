主题引擎DSL静态分析工具-项目计划与交付清单

1. 项目信息

1.1 项目名称
主题引擎DSL静态分析工具

1.2 项目类型
IDEA插件开发

1.3 项目周期
（待补充）

1.4 规范数据源
规则定义来源于 `docs/themes_engine_next/` 目录，包含元素定义、属性约束、枚举值域及互斥关系等全部DSL规范数据，作为静态分析引擎的规则输入。

2. 交付物清单

2.1 功能交付
| 交付项 | 说明 | 参考文档 |
|---|---|---|
| 表达式语法验证 | 检测无效表达式语法，如非法运算符组合（`-#var`）、精度超限（>7位小数）等 | PRD.md 2.1 / themes_engine_next 表达式规范 |
| 作用域约束验证 | 检测属性与元素的作用域支持矩阵违规，如非element-position支持的属性误用 | PRD.md 2.1 / themes_engine_next 元素-属性支持矩阵 |
| 全局变量引用验证 | 检测 `#var` / `@var` 引用是否存在，未声明全局变量引用报错 | PRD.md 2.1 / themes_engine_next 变量定义 |
| 互斥属性检测 | 检测同一元素内互斥属性组合，如VideoCommand的play+sound同时出现、Var的persist在时间变量上误用 | PRD.md 2.1 / themes_engine_next 互斥规则 |
| VariableCommand persist禁止检测 | 检测VariableCommand中persist属性的使用，对禁止persist的变量类型报错 | PRD.md 2.1 / themes_engine_next VariableCommand约束 |
| Quick Fix能力 | 按策略表提供自动修复动作 | PRD.md 2.1 |
| 诊断附带规则来源与文档URL | 每条诊断标注规则ID和文档链接 | DSL-Rule-Spec.md 3 |
| 未知元素/属性报错 | 并提供语义相似度建议 | DSL-Rule-Spec.md 2.1/2.3 |
| DSL文件识别 | 基于扩展名及根元素声明识别 | DSL-Rule-Spec.md 1.1 |
| 悬浮说明与依据追踪 | 悬浮显示元素规则说明与来源链接 | PRD.md 2.2 |
| 批量检查 | 全项目DSL文件批量扫描 | PRD.md 2.3 |

2.2 工程产出物
| 产出物 | 格式/说明 |
|---|---|
| 插件源码 | Gradle项目，Kotlin/Java |
| 编译产物 | JAR包，可安装至IDEA |
| 拓展指南文档 | 说明如何添加新元素规则，参见 DSL-Rule-Spec.md 4.2 |
| 用户使用手册 | 插件安装、配置、使用说明 |

2.3 非功能交付标准
| 项目 | 标准 |
|---|---|
| 平台兼容 | IntelliJ IDEA 2024.1+ Ultimate/Community |
| 性能 | 单文件实时检测 ≤ 50ms；全项目批量 ≤ 5s/100文件 |
| 可扩展性 | 规则库声明式定义，新增规则无需改引擎代码 |

2.4 验收标准（含示例）
| 功能项 | 验收条件 | 示例 |
|---|---|---|
| 表达式语法验证 | 非法运算符组合及精度超限均触发诊断 | `<Var value="-#bgColor"/>` 报错：表达式不允许 `-#var` 语法；`<Var value="3.14159268"/>` 报错：精度超过7位小数限制 |
| 作用域约束验证 | 属性在不支持的元素上使用时报错 | `<VideoCommand position="center"/>` 如position不在VideoCommand支持矩阵中则报错 |
| 全局变量引用验证 | `#var`/`@var`引用未声明变量时报错 | `<Var value="#undefinedVar"/>` 报错：全局变量 #undefinedVar 未声明 |
| 互斥属性检测 | 同一元素内互斥属性同时出现时报错 | `<VideoCommand play="true" sound="true"/>` 报错：play与sound互斥，不可同时使用；`<Var name="timeVar" persist="true"/>` 报错：时间类型变量禁止使用persist属性 |
| VariableCommand persist禁止检测 | VariableCommand中persist属性违规时报错 | `<VariableCommand persist="true"/>` 报错：VariableCommand禁止使用persist属性 |

3. 里程碑与排期

（待补充）

4. 风险与依赖

（待补充）