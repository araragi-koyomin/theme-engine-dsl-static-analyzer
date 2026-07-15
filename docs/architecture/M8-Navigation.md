---
module_ids: [M8]
doc_kind: architecture
status: active
created: 2026-06-17
---
# M8 导航与重构模块 - 架构设计

## 1. 模块职责

提供IDEA导航与重构能力：Ctrl+Click跳转定义、查找所有引用、重命名重构。基于Core层符号表，通过PSI Adapter桥接为IDEA PsiReference体系。

**单一职责**：IDEA导航与重构交互。

**仅Plugin层**：M8完全依赖IDEA PsiReference API和PSI Adapter，不存在于CLI模式。

## 2. 三层划分

| 层级 | 功能 | 说明 |
|---|---|---|
| **Core** | Ctrl+Click跳转定义（#varName/@varName → Var声明PsiElement） | MVP必交 |
| **Extension** | 查找所有引用 + 重命名重构 | 正式版本 |
| **Optional** | 批量重命名预览 | 后续迭代 |

## 3. 核心组件

### 3.1 PsiReference实现

对属性值中#varName/@varName文本创建PsiReference，resolve()返回对应Var声明的PsiElement：

```java
public class DslVariableReference extends PsiReferenceBase<PsiElement> {
    String varName;
    ReferenceKind kind;              // # (数值) | @ (字符串)

    @Override
    PsiElement resolve() {
        // 1. PSI Adapter → Core SymbolTable
        // 2. 查找VarDeclaration.name == varName
        // 3. VarDeclaration.astNode → PSI Adapter.getPsiElement(astNode)
        // 4. 返回Var声明的PsiElement
    }

    @Override
    Object[] getVariants() {
        // 返回当前文件中所有Var声明名称作为自动补全候选
    }
}
```

**PsiReference注册**：DslReferenceContributor在plugin.xml中注册，对DSL属性值中的#/@变量文本创建DslVariableReference。

### 3.2 Ctrl+Click跳转定义（Core层）

**跳转目标**：

| 引用类型 | 跳转目标 | 示例 |
|---|---|---|
| `#varName` | Var声明的PsiElement | `#steps_value` → `<Var name="steps_value" .../>` |
| `@varName` | Var声明的PsiElement | `@background_image` → `<Var name="background_image" .../>` |

**跳转流程**：

1. 用户Ctrl+Click属性值中的#/@varName
2. PsiReference.resolve()查找Core SymbolTable
3. VarDeclaration.astNode → PSI Adapter.getPsiElement()
4. IDEA导航到Var声明的PsiElement位置

**Core层符号表保障**：VarDeclaration.astNode字段存储对应的AST节点，PSI Adapter通过offsetToAst反向查找获得PsiElement。

### 3.3 查找所有引用（Extension层）

基于IDEA FindUsagesProvider API：

```java
public class DslFindUsagesProvider implements FindUsagesProvider {
    // 对Var声明PsiElement查找所有#/@引用
    // Core SymbolTable.references → PSI Adapter映射 → PsiElement列表
}
```

**查找流程**：

1. 用户在Var声明处执行Find Usages
2. FindUsagesProvider查询Core SymbolTable.references
3. 所有VarReference.astNode → PSI Adapter.getPsiElement()
4. IDEA展示所有引用位置列表

### 3.4 重命名重构（Extension层）

基于IDEA RenamePsiElementProcessor API：

```java
public class DslRenameProcessor extends RenamePsiElementProcessor {
    // 重命名Var声明 → 同步更新所有#/@引用
    // Core SymbolTable → PSI Adapter → PSI Tree文本替换
}
```

**重命名流程**：

1. 用户在Var声明处执行Rename（Shift+F6）
2. RenameProcessor查询Core SymbolTable获取所有引用
3. 所有VarReference → PSI Adapter映射 → PsiElement位置
4. WriteCommandAction批量替换：`#oldName` → `#newName`，`@oldName` → `@newName`
5. 重构完成后刷新Core SymbolTable（PSI变更触发重新分析）

**重命名一致性保障**：#varName和@varName使用同一Var声明，重命名时两种引用类型同步更新。

### 3.5 批量重命名预览（Optional层）

重命名前展示预览对话框：

- 列出所有将被修改的引用位置
- 用户可勾选/取消特定引用位置
- 确认后执行批量替换

## 4. 模块依赖

| 上游依赖 | 说明 |
|---|---|
| PSI Adapter | DslPsiBridge（AstNode→PsiElement映射） + SymbolTableAdapter（符号表→Reference映射） |
| Core M4 语义分析 | SymbolTable（VarDeclaration + VarReference，供跳转/查找/重命名定位） |

| 下游消费 | 说明 |
|---|---|
| 无 | 导航与重构是Plugin层终端交互层，不向其他模块提供接口 |

## 5. CLI相关

### 5.1 CLI与M8的关系

**M8不存在于CLI模式**。CLI jar不打包plugin/**中的导航代码。所有导航功能（跳转定义、查找引用、重命名）仅在IDEA环境中可用。

**CLI替代方案**：

| M8功能 | CLI替代 | 说明 |
|---|---|---|
| Ctrl+Click跳转定义 | 无直接替代 | CLI是单次分析输出工具，无交互导航能力 |
| 查找所有引用 | `--verbose`模式输出符号表 | JSON输出中包含Var引用位置(line+column) |
| 重命名重构 | 无替代 | CLI不做代码修改 |

### 5.2 CLI输出中的符号表信息

`--verbose`模式输出符号表内容摘要，提供M8导航功能的CLI降级版：

```json
{
  "symbolTable": {
    "declarations": [
      {"name":"steps_value","type":"number","line":5,"column":3,"isConstAttr":false}
    ],
    "references": [
      {"name":"steps_value","kind":"#","line":15,"column":10},
      {"name":"steps_value","kind":"#","line":20,"column":8}
    ]
  }
}
```

### 5.3 CLI参数不受M8影响

M8的所有参数和配置仅在IDEA环境中生效，不影响CLI参数和输出。

## 6. 设计要点

- **仅Plugin层**：M8完全依赖IDEA PsiReference API，不存在于CLI jar中
- **PSI Adapter桥接**：所有Core符号表数据通过PSI Adapter映射为PsiElement，M8不直接依赖Core内部实现
- **VarDeclaration.astNode桥接**：跳转定位依赖VarDeclaration的astNode字段→PSI Adapter.getPsiElement()
- **重命名一致性**：#varName和@varName引用同一Var声明，重命名时两种引用同步更新
- **FindUsages+Rename复用IDEA原生API**：不自建查找/重命名机制，复用IDEA FindUsagesProvider和RenamePsiElementProcessor
