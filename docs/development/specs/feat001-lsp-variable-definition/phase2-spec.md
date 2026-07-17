---
module_ids: [M8]
phase: P1
doc_kind: spec
status: active
created: 2026-07-17
---
# FEAT001 LSP 变量定义跳转 — PHASE 2 规格定义

> 阶段：PHASE 2（规格定义）
> 状态：待用户确认
> 依据：`docs/development/specs/feat001-lsp-variable-definition/phase1-requirements.md`

## 契约总览

| SPEC # | 契约名 | 涉及类/接口 |
|---|---|---|
| SPEC-1 | `DefinitionProvider` 变量引用→定义解析 | `DefinitionProvider`（新建） |
| SPEC-2 | `DslTextDocumentService.definition` LSP 入口 | `DslTextDocumentService`（改） |
| SPEC-3 | `DslLanguageServer.initialize` capabilities 声明 | `DslLanguageServer`（改） |
| SPEC-4 | 位置映射契约（name 属性值区间 → LSP Range） | `PositionMapper`（复用，不改） |

---

## SPEC-1：DefinitionProvider 变量引用→定义解析

### 接口签名

```java
package com.huawei.theme.analysis.lsp;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;

final class DefinitionProvider {

    DefinitionProvider(RuleRepository ruleRepository);

    /**
     * 解析光标处变量引用的目标定义位置。
     *
     * @param ctx    光标上下文（由 AstContextResolver/ContextResolver 解析）
     * @param ast    当前文件 AST（用于遍历查找 Var 声明）；可为 null（解析失败）
     * @param uri    当前文件 URI（写入返回的 Location.uri）
     * @param mapper 当前文件文本的 PositionMapper（核心坐标→LSP 位置）
     * @return 不可变 Location 列表：0 或 1 个元素；绝不返回 null
     */
    List<Location> definition(ContextResolver.Context ctx,
                              DslFileNode ast,
                              String uri,
                              PositionMapper mapper);
}
```

### 输入参数

| 参数 | 类型 | 约束 |
|---|---|---|
| `ctx` | `ContextResolver.Context` | 可为 null（调用方传 null 时返回空） |
| `ast` | `DslFileNode` | 可为 null（XML 解析失败）；rootElement 可为 null |
| `uri` | `String` | 非 null；当前文件 URI（写入 Location.uri） |
| `mapper` | `PositionMapper` | 非 null；与 ast 对应同一份文本 |

### 输出保证

| 输入条件 | 输出 |
|---|---|
| `ctx == null` 或 `ast == null` 或 `ast.getRootElement() == null` | 空 List |
| `ctx.exprNode == null`（光标不在表达式 token 上） | 空 List |
| `ctx.exprNode.getKind()` 既非 `VARIABLE_REF` 也非 `ARRAY_ACCESS` | 空 List |
| varName（取自 `ExpressionNode.getVariableName()`）为 null 或空 | 空 List |
| varName 命中全局变量（`ruleRepository.getGlobalVar(varName).isPresent()`） | 空 List（文件内无定义） |
| 遍历 ast 找到 ≥1 个匹配 Var 声明 | **仅第一个**匹配的 `Location`（文档顺序最前） |
| 遍历 ast 无匹配 Var 声明 | 空 List |

### 「匹配 Var 声明」定义

一个 `DslElementNode element` 匹配当且仅当同时满足：

1. `ruleRepository.getElementRule(element.getTagName())` 命中且 `rule.getCategory()` == `"variable"`（与 `HoverProvider.isVariableTag` 同一判定）
2. `element.getAttributes()` 中存在 `DslAttributeNode attr` 满足：
   - `"name".equals(attr.getName())`
   - `attr.getValue() != null` 且 `attr.getValue().getRawValue() != null`
   - `attr.getValue().getRawValue().equals(varName)`

### 跳转目标 Range

匹配元素的 `name` 属性的**值节点**（`DslAttributeNode.getValue()` 返回的 `DslAttributeValueNode`）区间：

- `start = mapper.toPosition(value.getLine(), value.getColumn())`
- `end = mapper.toPosition(value.getEndLine(), value.getEndColumn())`
- `Range = new Range(start, end)`

> **契约**：核心 AST 节点坐标为 1-based line / 0-based column，end 为开区间；`PositionMapper.toPosition` 转为 LSP 0-based line / 0-based char，LSP `Range` 的 end 同样是 exclusive——二者语义一致，无需 ±1 调整。

### varName 提取契约

`ctx.exprNode` 的静态类型为 `ExpressionAstNode`（接口，无 `getVariableName()`）。本 provider **cast 到具体类 `com.huawei.theme.analysis.core.expression.ExpressionNode`**（与 `HoverProvider.hoverVariableRef` 同一做法）后调用 `getVariableName()`：

```java
ExpressionNode expr = (ExpressionNode) ctx.exprNode;
String varName = expr.getVariableName();
```

> **设计决策**：不扩展 `ExpressionAstNode` 接口（避免改动 core 层稳定接口）。`AstContextResolver.findExprTokenByText` 产出的 `exprNode` 运行时即为 `ExpressionNode` 实例，cast 安全。

### 遍历顺序契约

对 AST 做**文档序前序遍历**（先根后子，子节点按 `getChildElements()` 列表顺序），返回第一个匹配元素。`AstBuilder` 产出的 `childElements` 已是文档出现顺序，故直接递归即可。

### 后置条件

- 返回值非 null（始终为 List，可能空）
- 返回的 `Location.uri` == 入参 `uri`
- 返回的 `Location.range` 精确覆盖 name 属性值文本（变量名）
- 同一文件内多次调用结果确定（纯函数，无状态副作用）

### 异常

- 不主动抛异常；`ctx.exprNode` 运行时类型不符时（理论上不会发生，因 `findExprTokenByText` 只产出 `ExpressionNode`）以 `instanceof` 守卫返回空，**不靠 try-catch 兜底**
- `ruleRepository.getElementRule(null)` 等异常情况由 `getElementRule` 自身契约保证（返回 empty Optional，不抛）

### 测试场景

| 场景 | 输入 | 期望 |
|---|---|---|
| TS-1.1 | ctx.exprNode = VARIABLE_REF("foo")，ast 含 `<Var name="foo">` | 返回 1 个 Location，Range == name 值区间 |
| TS-1.2 | ctx.exprNode = VARIABLE_REF("foo")，ast 无匹配 Var | 空 List |
| TS-1.3 | ctx.exprNode == null | 空 List |
| TS-1.4 | ctx.exprNode = LITERAL | 空 List（非 VARIABLE_REF/ARRAY_ACCESS） |
| TS-1.5 | ctx.exprNode = ARRAY_ACCESS("arr")，ast 含 `<Var name="arr">` | 返回 1 个 Location（跳到 arr） |
| TS-1.6 | varName 命中全局变量（ruleRepository.getGlobalVar 命中） | 空 List |
| TS-1.7 | ast 含两个同名 `<Var name="foo">` | 返回**第一个**的 Location |
| TS-1.8 | ctx == null | 空 List |
| TS-1.9 | ast == null | 空 List |
| TS-1.10 | varName 为空字符串 | 空 List |
| TS-1.11 | 匹配元素的 name 属性值节点区间 → Location.range 精确覆盖变量名文本 | Range.start.line/column == value.line-1/value.column |

---

## SPEC-2：DslTextDocumentService.definition LSP 入口

### 接口签名（lsp4j 0.21.2 TextDocumentService 默认方法覆写）

```java
@Override
public CompletableFuture<Either<List<? extends Location>, List<? extends DefinitionLink>>>
        definition(DefinitionParams params);
```

### 输入参数

| 参数 | 来源 | 约束 |
|---|---|---|
| `params.getTextDocument().getUri()` | LSP 客户端 | 非 null；file scheme |
| `params.getPosition().getLine()` | LSP 客户端 | 0-based |
| `params.getPosition().getCharacter()` | LSP 客户端 | 0-based |

### 处理流程

```
1. uri  = params.getTextDocument().getUri()
2. text = documents.get(uri)
3. 若 text == null → return Either.forLeft(List.of())   // 文档未打开
4. mapper = new PositionMapper(text)
5. offset = mapper.toOffset(position.line, position.character)
6. ast = analysisService.parse(uri, text)               // 复用 parse（与 completion 一致）
7. ctx = new AstContextResolver(text).resolve(offset, ast)
8. 若 ctx == null → ctx = new ContextResolver(text).resolve(offset)   // 文本兜底
9. locations = definitionProvider.definition(ctx, ast, uri, mapper)
10. return Either.forLeft(locations)
```

> **设计决策**：不复用私有 `resolveContext(uri, text, offset)`，因为该方法不返回 ast，而 definition 需要把 ast 传给 DefinitionProvider 遍历。故在 `definition` 内联 parse + 双层解析（AST 优先、文本兜底），与 `completion` 方法的 parse 用法一致。

### 输出保证

| 条件 | 输出 |
|---|---|
| 文档未打开（text == null） | `Either.forLeft(空 List)` |
| AST 解析失败（ast == null） | 经 ctx 兜底后调 provider，provider 见 ast==null 返回空 |
| 光标不在表达式变量引用上 | provider 返回空 |
| 命中变量定义 | `Either.forLeft([Location])` |

### 前置条件

- `definitionProvider` 字段已由 `rebuildProviders` 初始化（与 hoverProvider/completionProvider 同期构造）
- `analysisService` 已初始化

### 后置条件

- 不修改 documents/cachedDiagnostics 等状态
- 不触发诊断重算（definition 是只读查询）
- 调用 `definitionProvider.definition` 时传入的 `mapper` 与 `text` 同源（保证坐标一致）

### 异常

- `params == null` → 由 lsp4j 框架保证不发生
- 内部 parse 异常被 `AnalysisService.parse` 已有的 try-catch 吞为 null（不变）
- provider 内部不抛（见 SPEC-1），故 definition 方法整体不抛

### 测试场景

| 场景 | 输入 | 期望 |
|---|---|---|
| TS-2.1 | 已 open 文档，光标在 `#foo` 上，foo 已声明 | Either.forLeft 含 1 个 Location |
| TS-2.2 | 未 open 的 uri | Either.forLeft 空 List |
| TS-2.3 | 已 open 文档，光标在标签名上 | Either.forLeft 空 List |
| TS-2.4 | 已 open 文档，光标在 `#foo` 上，foo 未声明 | Either.forLeft 空 List |

---

## SPEC-3：DslLanguageServer.initialize capabilities 声明

### 接口契约

`DslLanguageServer.initialize(InitializeParams)` 在构造 `ServerCapabilities caps` 时，新增：

```java
caps.setDefinitionProvider(Either.forLeft(true));
```

### 前置条件

- 无（与 hoverProvider/codeActionProvider 声明同期，在 `initialize` 返回前设置）

### 后置条件

- `InitializeResult.capabilities.definitionProvider` == `Either.forLeft(true)`
- VS Code 客户端据此对 DSL 文档启用 Go to Definition（F12 / Ctrl+Click）

### 测试场景

| 场景 | 输入 | 期望 |
|---|---|---|
| TS-3.1 | 构造 DslLanguageServer，调 initialize(params) | 返回的 capabilities.getDefinitionProvider().getLeft() == true |

---

## SPEC-4：位置映射契约（name 属性值区间 → LSP Range）

### 契约

`DslAttributeValueNode`（Var 元素 name 属性的值节点）携带：

| 字段 | 语义 |
|---|---|
| `line` / `column` | 值文本起始（1-based line / 0-based column，闭） |
| `endLine` / `endColumn` | 值文本末尾之后（1-based line / 0-based column，开） |

经 `PositionMapper.toPosition(line, column)` / `toPosition(endLine, endColumn)` 转换为 LSP `Position`：

| 核心坐标 | LSP Position |
|---|---|
| `line` (1-based) | `line - 1` (0-based) |
| `column` (0-based) | `character` (0-based，UTF-16 code unit，与 JDK String 一致) |

`Range = new Range(toPosition(line, column), toPosition(endLine, endColumn))`，LSP Range.end 为 exclusive，与核心 end 开区间语义一致。

### 前置条件

- `DslAttributeValueNode` 的 line/column/endLine/endColumn 由 `AstBuilder`（SAX locator）填充，已验证可靠（见 `PositionMapper` 既有诊断映射用法）

### 后置条件

- 返回的 Range 精确覆盖变量名文本（如 `name="steps_value"` 的 Range 覆盖 `steps_value` 这 11 个字符）

### 测试场景

| 场景 | 输入 | 期望 |
|---|---|---|
| TS-4.1 | `<Var name="foo" type="number"/>` 的 name 值节点 | toPosition 后 Range 覆盖 `foo`（3 字符），start.column 指向首字符 'f' |

---

## 验收测试清单汇总（PHASE 1 AC ↔ SPEC ↔ 测试场景）

| AC（PHASE 1） | 对应 SPEC | 测试场景 |
|---|---|---|
| AC-1 | SPEC-3 | TS-3.1 |
| AC-2 | SPEC-1 + SPEC-4 | TS-1.1, TS-1.11, TS-4.1 |
| AC-3 | SPEC-1 | TS-1.1（`@` 前缀同 `#`，prefix 不参与匹配，仅 varName 匹配） |
| AC-4 | SPEC-1 | TS-1.2 |
| AC-5 | SPEC-1 | TS-1.6 |
| AC-6 | SPEC-1 | TS-1.7 |
| AC-7 | SPEC-1 | TS-1.3, TS-1.4 |
| AC-8 | SPEC-2 | TS-2.2 |
| AC-9 | SPEC-1 | TS-1.5 |
| AC-10 | 全部 | 全量门禁 |

---

> **阶段切换**：PHASE 2 完成。请用户确认以上规格契约，确认后进入 PHASE 3（设计）。
