---
module_ids: [PSI]
doc_kind: architecture
status: active
created: 2026-06-17
---
# PSI Adapter模块 - 架构设计

## 1. 模块职责

DslAst ↔ PsiElement双向桥接。将Core层的AST/Diagnostic/FixAction/SymbolTable数据映射为IDEA PSI层类型，供M6/M8/M5-UI消费。

**单一职责**：Core数据 ↔ IDEA PSI类型双向转换与映射。

**关键定位**：Plugin层所有模块通过PSI Adapter间接访问Core层数据，不直接依赖Core内部实现。

## 2. 三层划分

| 层级 | 功能 | 说明 |
|---|---|---|
| **Core** | DslPsiBridge offset双向映射 + Diagnostic转换 | MVP必交 |
| **Extension** | 缓存 + 增量映射更新 | 性能增强 |
| **Optional** | 多文件映射管理 | 后续迭代 |

## 3. 核心组件

### 3.1 DslPsiBridge — offset双向映射

不重新解析XML，在IDEA原生XML PSI Tree上叠加DSL语义标注。DslAstNode记录对应PSI元素的offset/范围。

```java
public class DslPsiBridge {
    Map<DslAstNode, Integer> astToOffset;     // AST节点 → PSI文本offset
    Map<Integer, DslAstNode> offsetToAst;     // PSI文本offset → AST节点

    DslAstNode getAstNode(PsiElement psiElement);  // 通过psiElement.getTextOffset()查offset
    PsiElement getPsiElement(DslAstNode astNode);   // 通过offset在PSI Tree中定位
    Diagnostic mapDiagnostic(Diagnostic coreDiagnostic);
}
```

**Map key使用Integer**：DslPsiBridge用text offset(Integer)作Map key，避免DslAstNode对象作为key时的hash/equals问题。

**映射构建流程**：

1. Core层M3产出DslFileNode（含line/column定位）
2. Plugin层获取PsiFile（IDEA XML PSI Tree）
3. DslPsiBridge遍历DslFileNode，通过line/column计算text offset
4. 建立astToOffset和offsetToAst双向映射

### 3.2 Diagnostic→Annotation转换

将Core层Diagnostic(filePath+line+column)映射为IDEA Annotation(PsiElement+offset)：

```java
public Diagnostic mapDiagnostic(Diagnostic coreDiagnostic) {
    // 1. 通过filePath找到对应的PsiFile
    // 2. 通过line+column计算PsiFile中的text offset
    // 3. 通过offset定位PsiElement
    // 4. 返回映射后的IDEA Annotation信息
}
```

**M6消费**：Annotator通过mapDiagnostic()将Core Diagnostic转换为IDEA Annotation标注。

### 3.3 FixAction→IntentionAction桥接

将Core层FixAction(TextRange)映射为IDEA WriteCommandAction(offset范围)：

```java
public class FixActionAdapter {
    // 无需确认类：直接将FixAction的TextRange映射为PSI offset范围
    //               执行WriteCommandAction文本替换
    // 需确认类：弹出CandidateSelectionDialog，选中后执行FixAction
}
```

**TextRange→PSI offset映射**：

1. FixAction.targetRange.startLine/startColumn → PSI text offset
2. FixAction.targetRange.endLine/endColumn → PSI text offset
3. WriteCommandAction在offset范围内替换文本

**M5-UI消费**：M6模块的M5-UI部分通过FixActionAdapter将FixAction桥接为IntentionAction。

### 3.4 PsiDslFileMatcherAdapter

将IDEA VirtualFile/PsiFile适配为Core层DslFileMatcher的String参数：

```java
public class PsiDslFileMatcherAdapter implements DslFileMatcher {
    // VirtualFile → filePath + content
    // PsiFile → filePath + psiFile.getText()
}
```

### 3.5 SymbolTable→PsiReference桥接

将Core层SymbolTable的VarDeclaration/VarReference映射为IDEA PsiReference：

```java
public class SymbolTableAdapter {
    // VarDeclaration.astNode → PsiElement（通过offsetToAst反向查找）
    // VarReference.astNode → PsiReference目标范围
}
```

**M8消费**：导航模块通过SymbolTableAdapter获取Var声明的PsiElement用于跳转/查找引用/重命名。

### 3.6 缓存管理（Extension层）

- 映射结果随PsiFile缓存
- PsiFile内容变更时清除缓存重新构建映射
- 增量映射更新：仅重建变更部分的offset映射

### 3.7 多文件映射管理（Optional层）

- 多个DSL文件的映射统一管理
- 项目级映射缓存池

## 4. 模块依赖

| 上游依赖 | 说明 |
|---|---|
| Core M3 语法分析 | DslAstProvider → DslFileNode（AST供offset映射） |
| Core M4 语义分析 | Diagnostic列表 + SymbolTable（供Annotation+Reference映射） |
| Core M5 修复逻辑 | FixAction + TextRange（供IntentionAction映射） |
| Core M1 文件识别 | DslFileMatcher接口（供PsiDslFileMatcherAdapter适配） |

| 下游消费 | 提供接口 | 说明 |
|---|---|---|
| M6 UI交互 | DslPsiBridge + mapDiagnostic + FixActionAdapter | Annotation标注+Quick Fix交互 |
| M8 导航与重构 | DslPsiBridge + SymbolTableAdapter | PsiReference+跳转+查找引用+重命名 |
| M5-UI (M6模块内) | FixActionAdapter | IntentionAction桥接 |

**隔离保障**：Plugin层M6/M8不直接依赖Core层内部实现，所有Core数据访问通过PSI Adapter桥接。

## 5. CLI相关

### 5.1 CLI与PSI Adapter的关系

**PSI Adapter不存在于CLI模式**。CLI jar只打包core/**，不包含plugin/**中的PSI Adapter。CLI管线直接消费Core层的Diagnostic/FixAction数据，无需PSI桥接。

| 维度 | CLI管线 | Plugin管线 |
|---|---|---|
| AST消费 | 直接消费DslFileNode | PSI Adapter映射为PsiElement后消费 |
| Diagnostic消费 | 直接输出(filePath+line+column) | PSI Adapter映射为Annotation(PsiElement+offset) |
| FixAction消费 | 直接输出建议文本 | PSI Adapter映射为WriteCommandAction(offset范围) |
| 符号表消费 | 不直接消费（CLI无导航功能） | PSI Adapter映射为PsiReference |

### 5.2 CLI参数不受PSI Adapter影响

PSI Adapter的所有参数和逻辑仅在IDEA环境中生效，不影响CLI参数和输出。

## 6. 设计要点

- **不重新解析XML**：在IDEA原生XML PSI Tree上叠加DSL语义标注，不维护独立的PSI解析体系
- **Integer作Map key**：DslPsiBridge用text offset(Integer)作Map key，避免DslAstNode对象hash/equals问题
- **Core/Plugin隔离桥梁**：Plugin层所有模块通过PSI Adapter间接访问Core数据，不直接依赖Core内部实现
- **CLI模式无PSI Adapter**：CLI jar不打包PSI Adapter，Core层数据直接消费
- **双向映射**：astToOffset + offsetToAst支持AST→PSI和PSI→AST双向查找
- **增量更新**：PsiFile变更时仅重建变更部分映射，保证性能
