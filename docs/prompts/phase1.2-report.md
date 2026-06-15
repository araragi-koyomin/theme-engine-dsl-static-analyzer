# Phase 1.2 阶段报告 — M2 规则库 Core 层

## 验收结果

| 验收标准 | 状态 | 说明 |
|---|---|---|
| DslElementRule可正确加载JSON规则数据 | ✅ PASS | JsonRuleLoaderTest 6个测试全部通过 |
| getElementRule("Var") 返回 Optional.of(varRule) | ✅ PASS | RuleRepositoryTest.getElementRule_knownElement 通过 |
| getElementRule("UnknownElement") 返回 Optional.empty() | ✅ PASS | RuleRepositoryTest.getElementRule_unknownElement 通过 |
| getRootElementNames() 返回合法根元素列表 | ✅ PASS | 返回 ["Lockscreen", "Wallpaper", "Widget", "ChargingSkin"] |
| ./gradlew :feature:analysis:test 通过 | ✅ PASS | 41个测试, 0个失败, BUILD SUCCESSFUL |

## 变更清单

### 1. 数据模型（@Data @Builder）

| 文件 | 包路径 | 说明 |
|---|---|---|
| AttrTypeSpec.java | rule.model | 属性类型规范：type, enumValues, aliases |
| DslElementRule.java | rule.model | 元素规则：elementName, requiredAttrs, optionalAttrs, attrTypes, allowedParents, allowedChildren, inherits |
| RuleSource.java | rule.model | 规则来源标识：ruleId, category, description, docUrl |

### 2. JSON加载层

| 文件 | 包路径 | 说明 |
|---|---|---|
| AttrTypeSpecAdapter.java | rule.loader | Gson TypeAdapter，处理JSON双格式：纯字符串 `"string"` / 对象 `{"enum":["a","b"]}` |
| JsonRuleLoader.java | rule.loader | 从resources/rules/dsl_rules.json加载规则数据，提供loadElementRules/loadRuleSources/buildMap方法 |
| dsl_rules.json | resources/rules | 生产规则数据文件（含Var, Lockscreen, Wallpaper, Widget, ChargingSkin, Group 6个元素 + 3条RuleSource） |
| test_rules.json | test resources/rules | 测试专用规则数据（5个元素 + 2条RuleSource） |

### 3. Repository层

| 文件 | 包路径 | 说明 |
|---|---|---|
| RuleRepository.java | rule.repository | 接口：8个查询方法，单元素返回Optional<T> |
| RuleRepositoryImpl.java | rule.repository | 实现：Map<String, DslElementRule> + Map<String, RuleSource>缓存，构造时通过JsonRuleLoader填充 |

### 4. 测试

| 文件 | 测试数 | 覆盖范围 |
|---|---|---|
| AttrTypeSpecTest.java | 3 | Builder创建、null可选字段、equals/hashCode |
| DslElementRuleTest.java | 2 | Builder创建全字段、null inherits |
| RuleSourceTest.java | 2 | Builder创建全字段、equals/hashCode |
| AttrTypeSpecAdapterTest.java | 8 | 反序列化(纯字符串/数字/enum对象/type字段/aliases)、序列化(纯字符串/enum/aliases) |
| JsonRuleLoaderTest.java | 6 | 加载元素规则、加载RuleSource、解析AttrTypeSpec、解析Var字段、根元素空parents、文件不存在异常 |
| RuleRepositoryTest.java | 20 | 基础查询5 + 属性类型5 + 父子关系4 + RuleSource2 + 加载2 + 数据完整性2 |

### 5. 构建配置修改

| 文件 | 修改内容 |
|---|---|
| build.gradle (root) | 添加 `compileTestJava.options.encoding = 'UTF-8'`；添加 `test.resources.srcDirs`；添加 `processTestResources { duplicatesStrategy }`；移除过于宽泛的 `startsWith('Test')` 任务过滤（会误跳标准Gradle test编译任务） |
| feature/analysis/build.gradle | 添加JUnit 5依赖（jupiter-api/params/engine 5.9.3）；添加 `test { useJUnitPlatform() }` |

## 架构对齐

遵循 M2-RuleLibrary.md 三层划分，Core层仅实现3.1数据模型 + 3.4 JsonRuleLoader + 3.3 RuleRepository接口。Extension层（RuleCacheManager）和Optional层（RuleEditorUI）未在本阶段实现。

仅映射3.1节7个核心字段，JSON规则文件6.1节的scope/deviceSupport/constraints字段暂不映射到DslElementRule，留给M3/M4消费时按需扩展。

## 代码风格对齐

| AGENTS.md 规范 | 实际遵守 |
|---|---|
| 类名大驼峰 | ✅ DslElementRule, AttrTypeSpec, RuleSource, JsonRuleLoader, RuleRepositoryImpl |
| 方法名小驼峰 | ✅ getElementRule, getRootElementNames, getAttrTypeSpec |
| 常量 UPPER_SNAKE_CASE | 无常量类 |
| 包名全小写 | ✅ com.huawei.theme.analysis.rule.model/loader/repository |
| Optional防空 | ✅ getElementRule/getAttrTypeSpec/getRuleSource 返回 Optional<T> |
| @Data/@Builder注解 | ✅ 三个数据模型均使用 |
| List接口而非ArrayList | ✅ 所有字段和返回值使用List<> |
| 缩进4空格 | ✅ |
| 左括号不换行 | ✅ |
| 导入顺序(5组+空行) | ✅ java.* → 第三方 → 项目内部，组间空行分隔 |
| 运行时异常 | ✅ RuntimeException（无自定义异常类） |
| try-with-resources | ✅ JsonRuleLoader使用try-with-resources管理InputStream |
| UTF-8编码 | ✅ InputStreamReader指定StandardCharsets.UTF_8 |

## 设计决策记录

| 决策 | 选择 | 理由 |
|---|---|---|
| 模块结构 | 在feature:analysis内新增rule子包 | 减少模块管理复杂度，纯数据层无需独立模块 |
| JSON字段范围 | 仅映射3.1节7核心字段 | YAGNI，scope/constraints留给M3/M4 |
| 实现方案 | Gson+Map缓存+TypeAdapter | Gson已在依赖中，TypeAdapter优雅处理AttrTypeSpec双格式 |
| 异常类型 | RuntimeException | AGENTS.md规范：不抛受检异常，纯数据层异常场景简单 |

## 下一步 (Phase 1.3)

Phase 1.3 将实现 M3 语法分析模块的 Core 层，利用本阶段 RuleRepository 提供的规则数据进行DSL文件语法验证。
