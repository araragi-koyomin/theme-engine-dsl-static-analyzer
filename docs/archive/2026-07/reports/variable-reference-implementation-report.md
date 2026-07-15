---
module_ids: [M8]
doc_kind: report
status: archived
created: 2026-07-14
---
# ThemeDSL 变量引用（PsiReference）实现报告

## 问题概述

在 ThemeDSL 的 XML 文件中，用户变量通过 `<Var name="timeTest">` 声明，通过 `#timeTest`（数值访问）或 `@timeTest`（字符串访问）在表达式属性值中引用。目标是实现：

1. **跳转定义**（Ctrl+Click）：从 `#timeTest` 跳转到 `<Var name="timeTest">`
2. **查找用法**（Find Usages）：在声明或引用处查找所有用法
3. **重命名**（Rename）：从声明或引用处重命名变量，同步更新所有引用

## 核心挑战

### 1. DE 表达式是注入式语言（Injected Language）

`#timeTest` 出现在 XML 属性值中（如 `expression="#timeTest + 2"`），但该属性值通过 `MultiHostInjector` 被注入为 DslExpression（DE）语言的片段。注入后，`#timeTest` 的文本属于 **注入的 DE PSI**，而非宿主 XML PSI。

**后果**：宿主侧的 `PsiReferenceContributor`（注册在 `XmlAttributeValue` 上）被绕过——Ctrl+Click 在注入的 DE 片段中解析引用，而 DE 片段中最初没有引用。

### 2. XML 属性值不是 PsiNamedElement

`<Var name="timeTest">` 中的 `name` 属性值是 `XmlAttributeValueImpl`（平台 XML PSI），它：
- **不是** `PsiNamedElement` / `PsiNameIdentifierOwner`——平台不提供重命名/查找用法
- `getName()` 返回标签名 `"Var"`，不是变量名 `"timeTest"`——查找用法搜索 `"Var"` 而非 `"timeTest"`
- 平台的 `findElementAt` 返回宿主 `XmlAttributeValue`（不是 `PsiNamedElement`），重命名灰出、查找用法报"无法从此位置搜索"

### 3. ASTFactory 无法为 ThemeDSL 定制 XML PSI 节点

尝试通过 `ASTFactory`（`<lang.ast.factory language="ThemeDSL">`）将 `XmlAttributeValue` 替换为自定义的 `PsiNameIdentifierOwner` 子类。但 `IXmlElementType`（所有 XML 元素类型的基类）在构造函数中 **硬编码** `XMLLanguage.INSTANCE` 作为语言：

```java
// IXmlElementType.java (平台源码)
public IXmlElementType(String debugName) {
    super(debugName, XMLLanguage.INSTANCE);  // 硬编码 XMLLanguage
}
```

因此 `ASTFactory.factory(type)` 调用 `LanguageASTFactory.forLanguage(XMLLanguage.INSTANCE)` → 返回 XML 的 ASTFactory，**永远不会** 查询 ThemeDSL 的 ASTFactory。此方案对 XML 元素类型无效。

### 4. antlr4-intellij-adapter 的 WS→skip 导致 varName 文本包含尾随空格

DE 词法器的 `WS : [ \t\r\n]+ -> skip` 规则导致 `PsiBuilder` 将尾随空白粘合到前一个 token 的范围上。例如 `#timeTest + 2` 中，`varName` 节点的文本是 `"timeTest "`（含尾随空格），而 `<Var name="timeTest">` 的声明名是 `"timeTest"`（无空格），导致 `resolve()` 匹配失败。

**修复**：在 `DslVariableRefElement` 中对所有名称提取和范围计算进行 `trim()` 处理。

### 5. antlr4-intellij-adapter 的 SymtabUtils 在注入式 PSI 中抛出 CCE

`ANTLRPsiNode.getContext()` 调用 `SymtabUtils.getContextFor()`，后者将父节点链向上遍历寻找 `ScopeNode`。但注入的 DE PSI 的父节点链跨越到宿主 `XmlAttributeValueImpl`（不是 `ScopeNode`），导致 `ClassCastException`。

**修复**：修改 `SymtabUtils.getContextFor()`，当父节点不是 `ScopeNode` 时返回 `null` 而非强转。

## 尝试的方案

### 方案 A：宿主侧 PsiReferenceContributor（仅 XmlAttributeValue）

| 操作 | 状态 | 说明 |
|------|------|------|
| 跳转定义 | ❌ 失败 | DE 注入后，`#timeTest` 在注入的 DE PSI 中，宿主侧引用被绕过 |
| 查找用法 | ❌ 失败 | 同上 |
| 重命名 | ❌ 失败 | 同上 |

**失败原因**：注入式语言将 `#timeTest` 的文本"捕获"到注入的 DE PSI 中，宿主侧的 `XmlAttributeValue.getReferences()` 不再被 Ctrl+Click 查询。

### 方案 B：DE 侧自定义 PSI（DslVariableRefElement）

通过 `DslExpressionParserDefinition.createElement()` 分发 `atVarRef`/`hashVarRef` 规则到自定义的 `DslVariableRefElement extends ANTLRPsiNode implements PsiReference`。

| 操作 | 状态 | 说明 |
|------|------|------|
| 跳转定义 | ✅ 工作 | `DslVariableRefElement.resolve()` → 通过 `InjectedLanguageManager.getInjectionHost()` 找到宿主文件 → 搜索 `<Var name="...">` |
| 查找用法 | ❌ 失败 | `ReferencesSearch` 不扫描注入式 PSI 片段（它是独立的 PsiFile，不在宿主文件搜索范围内） |
| 重命名 | ✅ 工作 | `handleElementRename()` 通过宿主 `XmlAttributeValue` 的 `ElementManipulator` 重写名称 |

（补充：这部分声明侧基本上啥都干不了。引用侧应该可以工作）

**查找用法失败原因**：`ReferencesSearch.search(tag)` 基于 **文本搜索**（搜索元素名 `"Var"`），而非扫描所有 `PsiReferenceProvider`。注入的 DE 片段是独立的 `PsiFile`，不在宿主文件的文本搜索范围内。

### 方案 C：宿主侧 PsiReferenceContributor + DE 侧引用（双轨制）

宿主侧 `PsiReferenceContributor` 在 `XmlAttributeValue` 上提供 `DslVariableReference`（扫描 `@x`/`#x`），DE 侧 `DslVariableRefElement` 处理跳转。

| 操作        | 状态 | 说明 |
|-----------|------|------|
| 跳转定义      | ✅ 工作 | DE 侧 `DslVariableRefElement.resolve()` |
| 查找用法（引用测） | ✅ 工作 | `ReferencesSearch` 扫描宿主 PSI → 查询 `XmlAttributeValue.getReferences()` → 找到宿主侧引用 |
| 重命名（引用侧）  | ✅ 工作 | `DslVariableRefElement.handleElementRename()` |
| 重命名（声明侧）  | ❌ 失败 | `XmlAttributeValue` 不是 `PsiNamedElement`，重命名灰出 |

（仍然，声明侧啥都做不了）

**声明侧重命名失败原因**：平台的 `PsiElementRenameHandler.canRename()` 检查 `element instanceof PsiNamedElement`。`XmlAttributeValue` 不是 `PsiNamedElement`，即使 `RenamePsiElementProcessor.canProcessElement()` 返回 `true`，`canRename` 也可能不到达处理器。

### 方案 D：声明侧自引用（VarNameSelfReference）

为name的属性值增加了一个新的injected language，这个language只负责处理变量名，他的PsiNode实现了PsiNamedElement。


在 `<Var name="timeTest">` 的 `name` 属性值上添加一个**自引用**（`PsiReference`），`resolve()` 返回注入的 `VarNameElement`（`PsiNameIdentifierOwner`）。

| 操作 | 状态 | 说明 |
|------|------|------|
| 跳转定义（引用侧） | ✅ 工作 | `DslVariableRefElement.resolve()` → `VarNameElement` |
| 跳转定义（声明侧 Ctrl+Click） | ⚠️ 跳转到自身 | 自引用 `resolve()` → `VarNameElement`（在同一属性值内）→ 平台导航到同位置 |
| 查找用法（声明侧） | ✅ 工作 | 自引用 `resolve()` → `VarNameElement`（name="timeTest"）→ 文本搜索 "timeTest" → 找到 `#timeTest` |
| 查找用法（引用侧） | ✅ 工作 | `DslVariableRefElement.resolve()` → `VarNameElement` |
| 重命名（声明侧） | ✅ 工作 | 自引用 → `resolve()` → `VarNameElement` → `setName()` |
| 重命名（引用侧） | ✅ 工作 | `DslVariableRefElement` → `resolve()` → `VarNameElement` → `setName()` + `handleElementRename()` |

**Ctrl+Click 跳转自身的原因**：自引用的 `resolve()` 返回 `VarNameElement`，它位于同一 `name` 属性值的注入片段内。平台将其映射回宿主偏移量 → 光标停留在同一位置。

### 方案 E：自定义 ASTFactory（ThemeDslVarNameAttributeValue）

注册 `<lang.ast.factory language="ThemeDSL">`，将 `XML_ATTRIBUTE_VALUE` 的 PSI 节点替换为自定义的 `ThemeDslVarNameAttributeValue extends XmlAttributeValueImpl implements PsiNameIdentifierOwner`。

| 操作 | 状态 | 说明 |
|------|------|------|
| 全部 | ❌ 失败 | `IXmlElementType` 硬编码 `XMLLanguage.INSTANCE`，`ASTFactory.factory(type)` 返回 XML 的工厂，ThemeDSL 的工厂不被查询 |

（补充：其实并不是全部失败，也是声明侧操作失败，因为Psi根本没有被修改！）

**失败原因**：XML 元素类型的语言在 `IXmlElementType` 构造函数中硬编码为 `XMLLanguage.INSTANCE`，与 ThemeDSL 文件的语言无关。`LanguageASTFactory.forLanguage(type.getLanguage())` 永远返回 XML 的 `ASTFactory`。

## 最终采用方案（方案 D：自引用 + VarName 注入）

### 架构

```
<Var name="timeTest">                      <Image expression="#timeTest + 2">
         │                                          │
         │ XmlAttributeValue                        │ XmlAttributeValue
         │ (宿主)                                   │ (宿主)
         │                                          │
    ┌─────┴──────┐                           ┌──────┴───────┐
    │ VarName    │                           │ DE 注入片段   │
    │ 注入片段   │                           │ DslVariable  │
    │            │                           │ RefElement   │
    │ VarName    │ ◄──── resolve() ──────── │ (PsiReference)│
    │ Element    │                           │              │
    │ (PsiName   │                           │ resolve()    │
    │ Identifier │ ◄──── resolve() ──────── │ → VarName    │
    │ Owner)     │                           │   Element    │
    └────────────┘                           └──────────────┘
         ▲
         │ 自引用 (VarNameSelfReference)
         │ resolve() → VarNameElement
         │
    ┌─────┴──────┐
    │XmlAttribute│
    │Value (宿主) │
    │ name=      │
    │ "timeTest" │
    └────────────┘
```

### 组件清单

| 文件 | 职责 |
|------|------|
| `DslVariableRefElement` | DE 侧 `atVarRef`/`hashVarRef` 节点，实现 `PsiReference`；`resolve()` 通过 `InjectedLanguageManager` 找到宿主文件中的 `<Var>` 声明 |
| `VarNameLanguage` / `VarNameFileType` / `VarNameElementTypes` / `VarNameLexer` / `VarNameParserDefinition` | 注入式 VarName 微语言（单标识符） |
| `VarNameElement` | 注入的 `PsiNameIdentifierOwner`，`getName()`/`setName()` 操作变量名 |
| `ThemeDslVarNameInjector` | `MultiHostInjector`，将 VarName 语言注入 `<Var>` 的 `name` 属性值 |
| `ThemeDslVariableReferenceContributor` | 宿主侧 `PsiReferenceContributor`；对 `<Var>` name 值提供自引用，对表达式属性提供 `DslVariableReference`（`@x`/`#x`） |
| `VarNameSelfReference` | 宿主 `XmlAttributeValue` 上的自引用，`resolve()` → 注入的 `VarNameElement`；`handleElementRename()` 为空操作（声明通过 `setName` 重命名） |
| `DslExpressionParserDefinition` | `createElement()` 分发 `atVarRef`/`hashVarRef` → `DslVariableRefElement` |
| `ThemeDslVarRenameProcessor` | `RenamePsiElementProcessor`，处理 `<Var>` 标签和 `name` 属性值的重命名，`substituteElementToRename()` 重定向到 `name` 值 |
| `SymtabUtils`（适配器库） | 修复注入式 PSI 中的 `getContextFor()` CCE |
| `ErrorStrategyAdaptor`（适配器库） | 修复 `recoverInline()` 不添加 ErrorNode 的问题 |

### 已知限制

| 限制 | 原因 | 替代方案 |
|------|------|----------|
| Ctrl+Click 声明处跳转到自身 | 自引用 `resolve()` 返回同一属性值内的 `VarNameElement` | 使用 **Alt+F7**（Find Usages）查看用法弹窗 |
| Ctrl+Click 声明处不显示用法弹窗 | Java 的"点击声明显示用法"行为要求声明是原生 `PsiNameIdentifierOwner`（无引用）；自引用破坏了此行为 | 使用 **Alt+F7** |
| `ASTFactory` 无法定制 XML PSI | `IXmlElementType` 硬编码 `XMLLanguage.INSTANCE` | 需修改平台源码或使用 `PsiAugmentProvider`（未尝试） |
| 全局变量（如 `#w`、`#h`）无法跳转 | 全局变量无 PSI 声明（规则库内置），`resolve()` 返回 `null` | 引用为软引用（`isSoft()=true`），不报错 |
| `ReferencesSearch` 不扫描注入式 DE 片段 | 注入的 DE PSI 是独立 `PsiFile`，不在宿主文件搜索范围 | 宿主侧 `DslVariableReference` 补偿（在宿主 PSI 上提供引用） |

### 适配器库修复（antlr4-intellij-adaptor）

| 修复 | 文件 | 问题 |
|------|------|------|
| `SymtabUtils.getContextFor()` CCE | `SymtabUtils.java:51` | 注入式 PSI 的父节点链跨越到宿主 `XmlAttributeValueImpl`（非 `ScopeNode`），强转失败 |
| `ErrorStrategyAdaptor.recoverInline()` 丢失错误 | `ErrorStrategyAdaptor.java` | ANTLR 的 `recoverInline` 对 missing token 返回 conjured token 但不添加 ErrorNode，导致 `varName` 产生空节点 |
| `GapFillingLexerAdaptor` 间隙 | `GapFillingLexerAdaptor.java`（新增） | `WS -> skip` 和词法错误恢复导致 token 序列有间隙，`LexerEditorHighlighter` 报 "Unexpected termination offset" |

## 结论

当前采用 **方案 D（自引用 + VarName 注入）**，实现了：

- ✅ 跳转定义（从引用侧 `#timeTest`）
- ✅ 查找用法（从声明侧和引用侧）
- ✅ 重命名（从声明侧和引用侧）
- ⚠️ Ctrl+Click 声明处跳转到自身（使用 Alt+F7 替代查看用法弹窗）

要完全实现 Java 风格的"Ctrl+Click 声明显示用法弹窗"，需要让 `XmlAttributeValue` 原生成为 `PsiNameIdentifierOwner`，但由于 `IXmlElementType` 硬编码 `XMLLanguage`，`ASTFactory` 方案不可行。可能的后续方向：
- `PsiAugmentProvider`（为 XML 元素动态添加接口实现）
- `FindUsagesHandlerFactory`（自定义查找用法处理器）
- 修改 `XMLParserDefinition`（平台级，需 fork）
