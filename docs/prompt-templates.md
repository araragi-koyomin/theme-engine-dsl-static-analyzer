# 任务执行 Prompt 模板

本文件提供不同类型任务的 prompt 模板，用于在新会话中向 agent 提供足够的上下文。

---

## 模板 1：开发某模块功能

适用于：实现 M4 语义分析新 Analyzer、M3 语法检查扩展等模块级开发任务。

```
请实现 [模块编号] [模块名] 的 [具体功能]。

## 上游依赖
- 架构文档：docs/architecture/[M?-ModuleName].md（请先阅读此文档了解接口签名和数据模型）
- 上游模块源码（需要理解接口）：[上游模块包路径]
- 上游模块测试（参考测试风格）：[上游模块测试路径]

## 当前模块源码
- [当前模块包路径]

## 规则规范（如涉及检测规则）
- docs/DSL-Rule-Spec.md 中第 [章节号] 节的相关规则

## 要求
- 先阅读架构文档，再阅读上游模块源码，确保接口消费方式正确
- Core 层禁止 import com.intellij.*
- 遵循 AGENTS.md 代码风格指南
- 写完实现后运行 `./gradlew :feature:analysis:test` 确认测试通过
```

**示例：开发 M4 ConstraintAnalyzer**

```
请实现 M4 语义分析模块的 ConstraintAnalyzer，基于 RuleDslEvaluator 执行声明式约束条件检查。

## 上游依赖
- 架构文档：docs/architecture/M4-SemanticAnalysis.md（请先阅读此文档了解 Analyzer 注册机制和 Diagnostic 产出规范）
- 上游模块源码（需要理解接口）：
  - M0 规则 DSL：feature/analysis/src/main/java/com/huawei/theme/analysis/core/ruledsl/（EvaluationContext、RuleDslEvaluator）
  - M2 规则库：feature/analysis/src/main/java/com/huawei/theme/analysis/core/rulelibrary/（RuleRepository、RuleConstraint）
  - M3 语法分析：feature/analysis/src/main/java/com/huawei/theme/analysis/core/syntaxanalysis/（DslAstProvider）
  - Shared AST：feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/ast/（DslFileNode、DslElementNode、DslAttributeNode）

## 规则规范
- docs/DSL-Rule-Spec.md 第 5.4 节"规则驱动类（ConstraintAnalyzer + RuleDslEvaluator）"中的声明式约束条件列表

## 要求
- 先阅读 M4 架构文档，再阅读 RuleDslEvaluator 和 RuleConstraint 源码，确保约束条件求值调用方式正确
- Core 层禁止 import com.intellij.*
- 遵循 AGENTS.md 代码风格指南
- 写完实现后运行 `./gradlew :feature:analysis:test` 确认测试通过
```

---

## 模板 2：跨模块重构

适用于：修改共享数据模型、接口签名变更等涉及多个模块的任务。

```
请重构 [具体重构内容]。此重构影响 [模块列表]。

## 涉及模块及源码路径
- [模块1]：[包路径1]（[角色：接口定义方/接口消费方]）
- [模块2]：[包路径2]（[角色]）
- ...

## 相关架构文档
- docs/architecture/[相关模块文档].md
- docs/Architecture.md 第 [章节号] 节

## 要求
- 先派 explore subagent 搜索所有引用旧接口/旧模型的位置，列出完整影响清单
- 再逐模块修改，确保所有消费方同步更新
- 运行 `./gradlew :feature:analysis:test` 确认全部测试通过
```

**示例：重构 DslAstNode 增加 parent 引用**

```
请重构 DslAstNode，为每个节点增加 parent 引用字段（DslElementNode 到父 DslElementNode 的反向引用）。此重构影响 M3（AST 构建方）和 M4（AST 消费方）。

## 涉及模块及源码路径
- M3 语法分析：feature/analysis/src/main/java/com/huawei/theme/analysis/core/syntaxanalysis/（DslAstProvider：AST 构建方，需要设置 parent）
- M4 语义分析：feature/analysis/src/main/java/com/huawei/theme/analysis/core/semanticanalysis/（DiagnosticProvider：AST 消费方，可能利用 parent 向上遍历）
- Shared AST：feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/ast/（DslAstNode、DslElementNode：接口定义方）

## 相关架构文档
- docs/architecture/M3-SyntaxAnalysis.md
- docs/architecture/M4-SemanticAnalysis.md

## 要求
- 先派 explore subagent 搜索所有引用 DslAstNode/DslElementNode 的位置，列出完整影响清单
- 在 shared/ast 中修改模型定义，在 M3 DslAstProvider 中设置 parent，在 M4 中确认消费方兼容
- 运行 `./gradlew :feature:analysis:test` 确认全部测试通过
```

---

## 模板 3：添加新检测规则（零代码）

适用于：新增 DSL 元素、属性、枚举值、声明式约束条件等纯 JSON 配置任务。

```
请在规则库中添加 [具体规则内容]。

## 规则规范参考
- docs/DSL-Rule-Spec.md 中第 [章节号] 节的规则定义
- 规则库 JSON 目录：feature/analysis/src/main/resources/rules/

## 现有同类规则示例
- [参考文件路径，如 feature/analysis/src/main/resources/rules/elements/commands/VideoCommand.json]

## 要求
- 遵循 DSL-Rule-Spec.md 第 6 节定义的 JSON Schema
- 确保新规则的 ruleId 格式符合 `[类别]-[子类]-[编号]` 规范
- 添加后运行相关规则加载测试确认 JSON 可正确加载
```

**示例：添加 Button 元素声明式约束**

```
请在规则库中为 Button 元素添加声明式约束：Button 必须包含 Trigger 子元素（SEM-TRIG-002）。

## 规则规范参考
- docs/DSL-Rule-Spec.md 第 5.4 节 SEM-TRIG-002 定义
- 规则库 JSON 目录：feature/analysis/src/main/resources/rules/elements/control/Button.json

## 要求
- 在 Button.json 的 constraints 数组中追加新 RuleConstraint 条目
- ruleId: SEM-TRIG-002, condition 表达式需检测 Button 无 Trigger 子元素
- severity: error
- 添加后运行 `./gradlew :feature:analysis:test --tests "*LoadTest"` 确认 JSON 加载正常
```

---

## 模板 4：Bug 修复 / 测试失败修复

适用于：修复特定 bug 或修复失败的测试。

```
请修复 [问题描述 / 测试失败信息]。

## 涉及源码
- [失败测试类路径]
- [相关实现类路径]

## 诊断步骤
1. 先阅读失败测试的代码和断言，理解预期行为
2. 阅读相关实现类代码，定位 bug 根因
3. 修复实现代码（不要修改测试断言，除非测试本身有错误）
4. 运行 `./gradlew :feature:analysis:test --tests "[TestClass]"` 确认修复

## 要求
- Core 层禁止 import com.intellij.*
- 遵循 AGENTS.md 代码风格指南
```

---

## 模板 5：不确定范围的探索性任务

适用于：不确定涉及哪些文件和模块的任务，需要先广域搜索再定向执行。

```
请完成 [任务描述]。

## 第一步：探索
先派 explore subagent（thoroughness: very thorough）搜索以下内容并返回汇总：
1. 项目中与 [关键词] 相关的所有文件和代码位置
2. 涉及的模块和接口
3. 上游依赖和下游影响

## 第二步：执行
根据探索结果，制定实施计划并逐步执行。

## 要求
- Core 层禁止 import com.intellij.*
- 遵循 AGENTS.md 代码风格指南
- 完成后运行 `./gradlew :feature:analysis:test` 确认无回归
```

---

## 模板 6：编写测试

适用于：为已有实现补充测试、编写 TDD 风格的测试先行代码。

```
请为 [类名/模块名] 编写测试。

## 待测源码
- [实现类路径]

## 上游依赖（测试中需要 mock 或构造）
- [上游接口/类路径]

## 现有测试参考风格
- [同类测试路径，如 feature/analysis/src/test/java/com/huawei/theme/analysis/core/rulelibrary/Batch1LoadTest.java]

## 测试要求
- 使用 JUnit 5（Jupiter API + Params）
- 测试类放在 feature/analysis/src/test/java/ 下对应包路径
- 遵循 AGENTS.md 代码风格指南
- 运行 `./gradlew :feature:analysis:test --tests "[新TestClass]"` 确认测试通过
```

---

## 模板 7：开发进度分析与下一步规划

适用于：确定当前开发阶段，分析哪些模块的上游已完成可以立即开发。

**推荐方式**：派 explore subagent 做全部信息收集，主窗口只接收结构化分析结论。这样主窗口消耗极少上下文。

```
请分析当前项目开发进度，确定下一步可立即开发的模块。

执行方式：派 explore subagent（thoroughness: very thorough）完成以下信息收集，返回结构化摘要：

1. 逐模块扫描源码目录，判断哪些模块已有实质代码（不只是 package-info）：
   - 模块列表见 AGENTS.md "模块→包路径映射"表
   - 对每个模块，列出已有类名和关键 public 方法签名

2. 逐模块扫描测试目录，判断测试覆盖情况：
   - 测试目录：feature/analysis/src/test/java/com/huawei/theme/analysis/core/

3. 逐模块读取架构文档（docs/architecture/），只看"接口规范"和"依赖关系"段落，
   提取每个模块需要消费的上游接口列表和对外提供的接口签名

4. 基于以上信息，输出分析结论：

### 已完成模块
| 模块 | 已有类 | 核心方法 | 测试覆盖 |

### 部分完成模块
| 模块 | 已完成功能点 | 缺失功能点 |

### 未开始模块
| 模块编号 | 模块名 | 包路径 |

### 可立即开发模块（上游全部完成）
| 模块编号 | 模块名 | 可开发的功能点 | 架构文档路径 |

### 阻塞模块（上游未完成）
| 模块编号 | 缺失的上游模块 |
```

**示例：实际执行**

```
请分析当前项目开发进度，确定下一步可立即开发的模块。

执行方式：派 explore subagent（thoroughness: very thorough）完成以下信息收集，返回结构化摘要：

1. 逐模块扫描源码目录，判断哪些模块已有实质代码（排除 package-info.java）：
   - M0: feature/analysis/src/main/java/com/huawei/theme/analysis/core/expression/
        + feature/analysis/src/main/java/com/huawei/theme/analysis/core/ruledsl/
        + feature/analysis/src/main/java/com/huawei/theme/analysis/core/function/
   - M1: feature/analysis/src/main/java/com/huawei/theme/analysis/core/fileidentification/
   - M2: feature/analysis/src/main/java/com/huawei/theme/analysis/core/rulelibrary/
        + feature/analysis/src/main/java/com/huawei/theme/analysis/core/rulelibrary/model/
   - M3: feature/analysis/src/main/java/com/huawei/theme/analysis/core/syntaxanalysis/
        + feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/ast/
   - M4: feature/analysis/src/main/java/com/huawei/theme/analysis/core/semanticanalysis/
        + feature/analysis/src/main/java/com/huawei/theme/analysis/core/semanticanalysis/model/
   - M5: feature/analysis/src/main/java/com/huawei/theme/analysis/core/quickfix/
   - M7: feature/analysis/src/main/java/com/huawei/theme/analysis/core/batchinspection/
   - Shared: feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/type/
           + feature/analysis/src/main/java/com/huawei/theme/analysis/core/shared/diagnostic/
   对每个有实质代码的模块，列出类名和核心 public 方法签名（格式：ClassName.methodName(参数类型):返回类型）

2. 逐模块扫描测试目录，列出已有测试类名：
   feature/analysis/src/test/java/com/huawei/theme/analysis/core/

3. 逐模块读取架构文档，只看"接口规范"和"依赖关系"段落：
   - docs/architecture/M0-ParserInfrastructure.md
   - docs/architecture/M1-FileIdentification.md
   - docs/architecture/M2-RuleLibrary.md
   - docs/architecture/M3-SyntaxAnalysis.md
   - docs/architecture/M4-SemanticAnalysis.md
   - docs/architecture/M5-QuickFix.md
   - docs/architecture/M7-BatchInspection.md
   提取：每个模块需要消费的上游接口 + 对外提供的接口签名

4. 输出分析结论，按以下五个表格格式：

### 已完成模块
| 模块 | 已有类 | 核心方法 | 测试覆盖 |

### 部分完成模块
| 模块 | 已完成功能点 | 缺失功能点 |

### 未开始模块
| 模块编号 | 模块名 | 包路径 |

### 可立即开发模块（上游全部完成）
| 模块编号 | 模块名 | 可开发的功能点 | 架构文档路径 |

### 阻塞模块（上游未完成）
| 模块编号 | 缺失的上游模块 |
```

---

## 使用说明

1. **选择匹配的模板**：根据任务类型选择最合适的模板
2. **填充方括号占位符**：将 `[...]` 替换为实际信息
3. **精简不如详尽**：宁可多给文件路径，也不要让 agent 猜测
4. **始终指明验证命令**：每个模板末尾都有测试/构建验证命令
5. **Core-Plugin 隔离提醒**：涉及 Core 层的任务务必带上隔离约束提醒
6. **组合使用**：复杂任务可组合多个模板的段落（如"重构 + 写测试"）
7. **信息收集优先用 subagent**：模板 7 等"只读不写"的分析任务，务必通过 explore subagent 执行，主窗口只接收压缩结论
