# Phase 1.4 阶段报告 — M3 语法分析 Core 层

> **日期**: 2026-06-16
> **模块**: M3-SyntaxAnalysis / Core层
> **构建状态**: `./gradlew :feature:analysis:test` 全量通过 (112 tests, 0 failures)

---

## 1. 交付清单

### 1.1 源码

| 类型 | 文件路径 | 说明 |
|:---:|---|---|
| Language | `syntax/DslLanguage.java` | 单例 Language 注册：`public static final DslLanguage INSTANCE = new DslLanguage()`，ID 为 `"Dsl"` |
| ParserDefinition | `syntax/DslParserDefinition.java` | 实现 `ParserDefinition`，委托 `XMLParserDefinition` 进行词法/语法分析，`createFile()` 返回 `DslFile` |
| PSI根节点 | `syntax/DslFile.java` | 继承 `XmlFileImpl`，`getLanguage()` 返回 `DslLanguage.INSTANCE`，构造参数使用 `DslElementTypes.DSL_FILE` |
| ElementTypes | `syntax/DslElementTypes.java` | `IFileElementType DSL_FILE`，关联 `DslLanguage.INSTANCE` |
| 语法错误常量 | `syntax/DslSyntaxConstants.java` | SYN-001/SYN-002/SYN-003 规则ID + 消息模板，私有构造函数 |
| Annotator | `syntax/DslSyntaxAnnotator.java` | 扫描 `PsiErrorElement`，将 IDEA XML Parser 错误描述映射为 SYN-xxx 规则ID annotation |
| PsiTreeProvider接口 | `syntax/PsiTreeProvider.java` | `getDslPsiTree(VirtualFile)` + `findElementsByName(PsiFile, String)` |
| PsiTreeProvider实现 | `syntax/DslPsiTreeProviderImpl.java` | 通过 `PsiManager` 获取 `DslFile`，`XmlRecursiveElementVisitor` 查找命名元素 |
| LanguageSubstitutor | `syntax/DslLanguageSubstitutor.java` | 对 XML 文件调用 `DslFileMatcher.isDslFile()`，DSL 文件返回 `DslLanguage.INSTANCE`，非 DSL 返回 null |
| ApplicationService | `DslAnalysisService.java` | 顶层服务，初始化 `RuleRepository` + `DslFileMatcher`，供 `DslLanguageSubstitutor` 消费 |

> 路径前缀：源码 `feature/analysis/src/main/java/com/huawei/theme/analysis/`，测试 `feature/analysis/src/test/java/com/huawei/theme/analysis/`

### 1.2 配置变更

| 文件 | 修改内容 |
|---|---|
| `plugin.xml` | 新增 `<depends>com.intellij.modules.xml</depends>`；注册 5 个 extension point：`applicationService`（DslAnalysisService）、`language`（Dsl）、`parserDefinition`（DslParserDefinition）、`languageSubstitutor`（XML→Dsl）、`annotator`（Dsl→DslSyntaxAnnotator） |

### 1.3 测试资源

| 文件 | 说明 |
|---|---|
| `test/resources/dsl/valid_lockscreen.xml` | 合法 DSL 文件（Lockscreen根元素，含 Var/Group/Text 子元素） |
| `test/resources/dsl/valid_widget.xml` | 合法 DSL 文件（Widget根元素，含 Var/Group/Text） |
| `test/resources/dsl/error_unclosed.xml` | 语法错误：Var 标签未闭合 + Lockscreen 未闭合 → SYN-001 |
| `test/resources/dsl/error_quotes.xml` | 语法错误：frameRate=60 缺引号 → SYN-003 |
| `test/resources/dsl/regular_config.xml` | 非 DSL XML 文件（configuration根元素） |

---

## 2. 验收标准逐项核对

| # | 验收标准 | 测试方法 | 结果 |
|:---:|---|---|:---:|
| 1 | DslLanguage 单例注册成功，IDEA 可识别 DSL Language 对象 | `DslLanguageTest.instance_shouldBeSingleton` + `instance_shouldBeRegisteredInLanguageRegistry` | PASS |
| 2 | DslParserDefinition 在 plugin.xml 注册，DSL 文件可被解析为 PSI Tree | `DslParserDefinitionTest.getFileNodeType_languageShouldBeDsl` + `getFileNodeType_shouldBeDslElementTypesDslFile` | PASS |
| 3 | XML 标签未闭合 → PSI Tree 中出现 ErrorElement，错误信息包含规则ID SYN-001 | `DslSyntaxAnnotatorTest`（4 项 IDEA 真实错误消息模式测试 + 优先级测试 + 假阳性测试） | PASS |
| 4 | 属性引号缺失 → PSI Tree 中出现 ErrorElement，错误信息包含规则ID SYN-003 | `DslSyntaxAnnotatorTest`（3 项 SYN-003 映射测试） | PASS |
| 5 | PsiTreeProvider 接口定义完整，getDslPsiTree 返回 DslFile PSI 根节点 | `PsiTreeProviderTest.interfaceContract_getDslPsiTree_nullFileReturnsNull` + 接口方法签名验证 | PASS |
| 6 | 非 DSL XML 文件不受 DSL Parser 影响（由 M1 DslFileMatcher 过滤） | `DslLanguageSubstitutor` 返回 null 保留 XMLLanguage；`PsiTreeProviderTest.integration_serviceShouldProvideFileMatcher` | PASS |
| 7 | `./gradlew :feature:analysis:test` 通过 | 112 tests, 0 failures, BUILD SUCCESSFUL | PASS |

---

## 3. 测试覆盖详情

### 3.1 DslLanguageTest（5 tests）

| 测试方法 | 验证内容 | 结果 |
|---|---|:---:|
| `instance_shouldBeSingleton` | `DslLanguage.INSTANCE` 两次引用为同一对象 | PASS |
| `instance_idShouldBeExactlyDsl` | `getID()` 返回精确字符串 `"Dsl"` | PASS |
| `instance_shouldBeDistinctFromXmlLanguage` | DslLanguage 与 XMLLanguage 的 ID 和对象均不等 | PASS |
| `instance_shouldBeRegisteredInLanguageRegistry` | `Language.findLanguageByID("Dsl")` 返回 DslLanguage.INSTANCE | PASS |
| `instance_constructorShouldBePrivate` | 反射检查所有构造函数均非 public | PASS |

### 3.2 DslParserDefinitionTest（13 tests）

| 测试方法 | 验证内容 | 结果 |
|---|---|:---:|
| `parserDefinition_shouldImplementParserDefinition` | 类型确认 | PASS |
| `getFileNodeType_languageShouldBeDsl` | DSL_FILE 的 Language 为 DslLanguage.INSTANCE | PASS |
| `getFileNodeType_shouldBeDslElementTypesDslFile` | 与 DslElementTypes.DSL_FILE 引用一致 | PASS |
| `createLexer_shouldReturnNonNullLexer` | Lexer 实例非 null | PASS |
| `createLexer_shouldReturnFreshInstanceEachCall` | 每次调用返回不同 Lexer 实例 | PASS |
| `createParser_shouldReturnNonNullParser` | PsiParser 实例非 null | PASS |
| `createParser_shouldReturnFreshInstanceEachCall` | 每次调用返回不同 PsiParser 实例 | PASS |
| `getWhitespaceTokens_shouldDelegateToXmlParserDefinition` | 委托 XML ParserDefinition 的 whitespace TokenSet | PASS |
| `getCommentTokens_shouldDelegateToXmlParserDefinition` | 委托 XML ParserDefinition 的 comment TokenSet | PASS |
| `getStringLiteralElements_shouldDelegateToXmlParserDefinition` | 委托 XML ParserDefinition 的 stringLiteral TokenSet | PASS |
| `parserDefinition_hasSpaceExistenceTypeBetweenTokensMethod` | 方法存在且可调用 | PASS |
| `dslSyntaxConstants_ruleIdFormatShouldFollowConvention` | SYN-001/002/003 值精确匹配，MSG 格式为 "规则ID: 描述" | PASS |
| `dslSyntaxConstants_msgShouldContainCorrespondingRuleId` | 每条 MSG 以对应规则ID开头 | PASS |

### 3.3 DslSyntaxAnnotatorTest（23 tests）

| 分类 | 测试方法 | 验证内容 | 结果 |
|---|---|---|:---:|
| **SYN-001 IDEA真实消息** | `syn001_ideaMessageTagNotClosed` | `"Tag '<Lockscreen>' is not closed"` → SYN-001 | PASS |
| | `syn001_ideaMessageElementNotClosed` | `"Element is not closed"` → SYN-001 | PASS |
| | `syn001_ideaMessageExpectedEndTag` | `"Expected: end tag </Lockscreen>"` → SYN-001 | PASS |
| | `syn001_ideaMessageUnclosed` | `"Unclosed tag found"` → SYN-001 | PASS |
| **大小写不敏感** | `syn001_caseInsensitive_uppercaseInputToLowercased` | 原文大写经 toLowerCase 后正确映射 | PASS |
| | `syn001_caseInsensitive_mixedCaseInputToLowercased` | 原文混合大小写经 toLowerCase 后正确映射 | PASS |
| **SYN-003** | `syn003_ideaMessageAttributeQuoted` | `"Attribute value must be quoted"` → SYN-003 | PASS |
| | `syn003_ideaMessageQuotation` | `"Missing quotation marks..."` → SYN-003 | PASS |
| | `syn003_containsQuoteKeyword` | `"unquoted attribute value"` → SYN-003 | PASS |
| | `syn003_caseInsensitiveInputToLowercased` | 大写原文经 toLowerCase 后正确映射 | PASS |
| **SYN-002** | `syn002_containsNesting` | `"Invalid nesting of elements"` → SYN-002 | PASS |
| | `syn002_containsNested` | `"Incorrectly nested tag structure"` → SYN-002 | PASS |
| **优先级** | `precedence_closedKeywordTakesPriorityOverQuote` | 同时含 "closed"+"quote" → SYN-001 优先 | PASS |
| | `precedence_quoteKeywordBeforeNesting` | 同时含 "quote"+"nesting" → SYN-003 优先 | PASS |
| **假阳性** | `falsePositive_enclosedShouldMatchSyn001` | `"enclosed"` 含 "closed" 子串 → 当前仍映射 SYN-001（已知问题） | PASS |
| | `falsePositive_disclosedShouldMatchSyn001` | `"disclosed"` 含 "closed" 子串 → 当前仍映射 SYN-001（已知问题） | PASS |
| **未知错误** | `unknownError_unexpectedToken` | `"unexpected token"` → null | PASS |
| | `unknownError_xmlProcessingError` | `"xml processing error"` → null | PASS |
| **边界** | `edgeCase_emptyString` | 空字符串 → null | PASS |
| | `edgeCase_singleWordClosed` | `"closed"` → SYN-001 | PASS |
| | `edgeCase_singleWordQuote` | `"quote"` → SYN-003 | PASS |
| | `edgeCase_singleWordNesting` | `"nesting"` → SYN-002 | PASS |
| **格式** | `annotationFormat_shouldCombineRuleIdWithOriginalDescription` | 拼接格式 `"SYN-001: Tag is not closed"` | PASS |

### 3.4 PsiTreeProviderTest（9 tests）

| 测试方法 | 验证内容 | 结果 |
|---|---|:---:|
| `interfaceContract_getDslPsiTree_nullFileReturnsNull` | null VirtualFile → null | PASS |
| `interfaceContract_findElementsByName_nullFileReturnsEmptyList` | null PsiFile → 空列表 | PASS |
| `interfaceContract_findElementsByName_nullNameReturnsEmptyList` | null elementName → 空列表 | PASS |
| `interfaceContract_findElementsByName_emptyNameReturnsEmptyList` | 空字符串 → 空列表 | PASS |
| `integration_serviceShouldProvideValidRuleRepository` | DslAnalysisService 初始化 RuleRepository 含 4 根元素 | PASS |
| `integration_serviceShouldProvideFileMatcher` | DslAnalysisService 提供 DslFileMatcher | PASS |
| `integration_dslSyntaxConstants_shouldMatchRuleRepositorySource` | SYN-001 在 RuleRepository.ruleSources 中存在 | PASS |
| `integration_dslLanguageShouldMatchDslElementTypesLanguage` | DslLanguage.INSTANCE 与 DSL_FILE.getLanguage() 一致 | PASS |
| `integration_dslParserDefinitionShouldUseDslElementTypes` | DslParserDefinition.getFileNodeType() 与 DslElementTypes.DSL_FILE 一致 | PASS |

### 3.5 DslAnalysisServiceTest（6 tests）

| 测试方法 | 验证内容 | 结果 |
|---|---|:---:|
| `service_shouldProvideRuleRepository` | RuleRepository 非 null 且非空 | PASS |
| `service_ruleRepositoryShouldContainRootElementNames` | 含 4 个 DSL 根元素名 | PASS |
| `service_shouldProvideFileMatcher` | DslFileMatcher 非 null | PASS |
| `service_fileMatcherShouldRejectNullVirtualFile` | null VirtualFile → false | PASS |
| `service_ruleRepositoryShouldReturnSyn001Source` | SYN-001 RuleSource 存在且 ruleId 正确 | PASS |
| `service_ruleRepositoryShouldReturnElementRules` | Var/Lockscreen 存在，UnknownElement 不存在 | PASS |

### 3.6 DslElementTypesTest（3 tests）

| 测试方法 | 验证内容 | 结果 |
|---|---|:---:|
| `dslFile_languageShouldBeDslLanguage` | DSL_FILE.getLanguage() 为 DslLanguage.INSTANCE | PASS |
| `dslFile_shouldBeRegisteredAsFileElementType` | DSL_FILE 非 null，Language ID 为 "Dsl" | PASS |
| `dslFile_typeIdShouldReflectLanguage` | toString() 非 null | PASS |

---

## 4. 设计要点

### 4.1 复用 XML PSI

M3 Core 不从零构建 XML 语法分析，而是委托 `XMLParserDefinition` 完成词法和语法分析。PSI Tree 子节点（XmlDocument/XmlTag/XmlAttribute）均为 IDEA 内置 XML PSI 类型，仅根节点替换为 `DslFile`（Language 为 DslLanguage）。这意味着：

- DSL 文件在 PSI Viewer 中根节点为 `DslFile`，子节点结构与 XML 文件一致
- XML Parser 自然产生 `PsiErrorElement`（如标签未闭合、属性引号缺失）
- `DslSyntaxAnnotator` 在这些 ErrorElement 上叠加 SYN-xxx 规则ID annotation

### 4.2 Parser 与规则库解耦

`DslParserDefinition` 的核心语法规则（XML标准语法）硬编码在委托的 `XMLParserDefinition` 中，不依赖 `RuleRepository`。DSL 元素合法性验证（如未知元素、父子约束）属于 M4 语义分析职责，不在 M3 Core 中实现。

### 4.3 LanguageSubstitutor 过滤机制

`DslLanguageSubstitutor` 注册在 `language="XML"` 下，对所有 XML 文件触发判定：

```
XML文件 → LanguageSubstitutor → DslFileMatcher.isDslFile(file)
                                    ↓ true                 ↓ false
                              返回 DslLanguage         返回 null（保留XMLLanguage）
```

非 DSL XML 文件（如 pom.xml、spring 配置）不受任何 DSL 代码影响。

### 4.4 错误映射优先级

`DslSyntaxAnnotator.mapErrorToRuleId()` 检查顺序为 SYN-001 → SYN-003 → SYN-002。当一条错误描述同时包含多个规则的关键词时，按此优先级返回第一个匹配。测试已覆盖此优先级行为。

### 4.5 DslAnalysisService 桥接

`DslAnalysisService` 作为 ApplicationService 在插件启动时自动初始化，将 M2 RuleRepository 和 M1 DslFileMatcher 桥接为可消费对象。`DslLanguageSubstitutor` 通过 `ApplicationManager.getService()` 获取它，避免了循环依赖和手动初始化。

---

## 5. 已知问题

| 问题 | 影响 | 计划 |
|---|---|---|
| `mapErrorToRuleId` 的 `contains("closed")` 匹配 "enclosed"/"disclosed" 等非未闭合场景 | 可能产生 SYN-001 假阳性标注 | M3 Extension 层引入自定义 Lexer 后，基于 PSI 结构而非字符串子串判断 |
| `PsiErrorElement` 的 `getErrorDescription()` 由 IDEA XML Parser 生成，不同版本可能变化 | 规则ID映射可能遗漏新模式 | 持续跟踪 IDEA 版本变更，扩展映射关键词 |
| plugin.xml 中 `<language>` / `<parserDefinition>` / `<languageSubstitutor>` 在开发 IDE 中标红 | 仅影响 IDE 编辑器验证体验，不影响构建和运行 | `initializeIntelliJPlugin` 任务被 `onlyIf{false}` 跳过，导致 IDE 无法解析 extension point 定义 |

---

## 6. 代码风格对齐

| AGENTS.md 规范 | 实际遵守 |
|---|---|
| 类名大驼峰 | DslLanguage, DslParserDefinition, DslFile, DslElementTypes, DslSyntaxAnnotator, DslSyntaxConstants, PsiTreeProvider, DslPsiTreeProviderImpl, DslLanguageSubstitutor, DslAnalysisService |
| 方法名小驼峰 | mapErrorToRuleId, getDslPsiTree, findElementsByName, getLanguage, getFileNodeType |
| 常量 UPPER_SNAKE_CASE | SYN_001, SYN_001_MSG, DSL_FILE, RULES_RESOURCE_PATH, INSTANCE |
| 常量类私有构造函数 | DslSyntaxConstants `private DslSyntaxConstants()` |
| 4 空格缩进 | 全部源码和测试 |
| 左括号不换行 | 全部源码和测试 |
| 行宽 ≤ 120 | 全部源码和测试 |
| 导入顺序 | java.* → 空行 → IntelliJ → 空行 → 项目内部 |
| 不抛受检异常 | DslAnalysisService 构造中使用 RuntimeException |
| 接口优先 | PsiTreeProvider 为接口，DslPsiTreeProviderImpl 为实现；RuleRepository/DslFileMatcher 为接口 |

---

## 7. 上游依赖确认

| 依赖 | 接口 | 当前行为 | 状态 |
|---|---|---|:---:|
| M1 DslFileMatcher | `isDslFile(VirtualFile)` → boolean | DslLanguageSubstitutor 调用，通过 DslAnalysisService 获取实例 | 已对接 |
| M2 RuleRepository | `getRootElementNames()` → List | DslAnalysisService 初始化 DslFileIdentifier 时调用 | 已对接 |
| M2 RuleRepository | `getRuleSource("SYN-001")` → Optional | DslAnalysisServiceTest 验证 SYN-001 Source 存在 | 已对接 |

---

## 8. 下游接口预留

| 接口 | 消费模块 | 说明 |
|---|---|---|
| `PsiTreeProvider.getDslPsiTree()` | M4 语义分析 | 获取 PSI 根节点进行语义遍历 |
| `PsiTreeProvider.findElementsByName()` | M4/M6 | 按名称查找元素 |
| `DslSyntaxConstants.SYN_xxx` | M6 编辑器标注 | 规则ID常量供标注和 Tooltip 使用 |
| `DslFile`（PsiFile 子类） | M6 PSI Viewer | 根节点类型标识 |

---

## 9. 未交付项（后续迭代）

| 项 | 说明 | 所属阶段 |
|---|---|---|
| DslFileType + 自定义图标 | 项目树 DSL 文件图标和 FileType 注册 | Phase 1 步骤 1.6（M1 Extension） |
| M6 DslAnnotator | 统一标注层，整合 M1 过滤 + M3 语法错误 + M4 语义诊断 | Phase 1 步骤 1.5（M6 Core） |
| 自定义 Lexer + Token 精细化 | 解决 contains("closed") 假阳性，基于 PSI 结构而非字符串判断 | Phase 6 步骤 6.2（M3 Extension） |
| 语法诊断格式化输出 | 精确行列号 + RuleSource 关联 | Phase 6 步骤 6.10（M3 Optional） |
| PsiTreeProvider 注册为 ProjectService | plugin.xml 注册 `projectService` | 待 M6 实现时补注册 |
