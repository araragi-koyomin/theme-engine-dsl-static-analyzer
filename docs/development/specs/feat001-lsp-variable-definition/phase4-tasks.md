---
module_ids: [M8]
phase: P1
doc_kind: plan
status: active
created: 2026-07-17
---
# FEAT001 LSP 变量定义跳转 — PHASE 4 任务拆分

> 阶段：PHASE 4（任务拆分）
> 状态：待用户确认
> 依据：`docs/development/specs/feat001-lsp-variable-definition/phase2-spec.md`（SPEC）+ `phase3-design.md`（设计）

## 任务列表

| Task | 标题 | SPEC | AC | 测试场景 | 依赖 | 估时 |
|---|---|---|---|---|---|---|
| T1 | DefinitionProvider 命中路径 + Range 映射 | SPEC-1, SPEC-4 | AC-2, AC-3, AC-9 | TS-1.1, TS-1.5, TS-1.11, TS-4.1 | 无 | 20min |
| T2 | DefinitionProvider 空场景与边界守卫 | SPEC-1 | AC-4, AC-5, AC-6, AC-7 | TS-1.2~1.4, TS-1.6~1.10 | T1 | 20min |
| T3 | DslTextDocumentService.definition 编排 + 接线 | SPEC-2 | AC-8 | TS-2.1~2.4 | T1, T2 | 25min |
| T4 | DslLanguageServer capability 声明 | SPEC-3 | AC-1 | TS-3.1 | 无 | 10min |
| T5 | 全量门禁验证 | 全部 | AC-10 | — | T1~T4 | 10min |

## 依赖关系图

```
T1 (命中路径) ──────┐
                    ├─→ T3 (service 编排) ──┐
T2 (空场景) ────────┘                        │
                                             ├─→ T5 (门禁)
T4 (capability) ─────────────────────────────┘
```

T1/T2/T4 可并行启动；T3 依赖 T1+T2（需 DefinitionProvider 可用）；T5 依赖全部。

## 各任务详细说明

### T1: DefinitionProvider 命中路径 + Range 映射

- **SPEC**: SPEC-1, SPEC-4
- **AC**: AC-2（`#var` 命中）、AC-3（`@var` 命中）、AC-9（数组访问命中）
- **文件**:
  - 新建 `feature/lsp/src/main/java/com/huawei/theme/analysis/lsp/DefinitionProvider.java`
  - 新建 `feature/lsp/src/test/java/com/huawei/theme/analysis/lsp/DefinitionProviderTest.java`
- **变更**: `DefinitionProvider` 类骨架 + `definition` 主方法 + `extractVarName`/`findVarDefinition`/`isVariableElement`/`toLocation` 私有方法
- **RED**: `DefinitionProviderTest` 写命中用例：
  - `#foo` 引用 + ast 含 `<Var name="foo">` → 返回 1 个 Location，Range == name 值节点区间（TS-1.1）
  - `@foo` 引用 → 同 `#foo`，跳到同名 Var（TS-1.1 变体，验证 prefix 不参与匹配）
  - `#arr` ARRAY_ACCESS + ast 含 `<Var name="arr">` → 返回 Location（TS-1.5）
  - Range 精确覆盖变量名文本（TS-1.11 / TS-4.1：`<Var name="foo">` 的 Range 覆盖 `foo` 3 字符）
- **GREEN**: 实现类——构造注入 `RuleRepository`；`extractVarName` cast `ExpressionNode` 取 `getVariableName()`；`findVarDefinition` 文档序前序递归；`isVariableElement` 走 `getElementRule().category=="variable"`；`toLocation` 用 `mapper.toPosition`
- **REFACTOR**: 提取 `findVarDefinition` 递归，避免主方法臃肿

### T2: DefinitionProvider 空场景与边界守卫

- **SPEC**: SPEC-1
- **AC**: AC-4（未声明→空）、AC-5（全局变量→空）、AC-6（同名返第一个）、AC-7（非引用→空）
- **文件**: 同 T1（增量测试 + 补全守卫）
- **RED**: `DefinitionProviderTest` 增补用例：
  - `#foo` 引用 + ast 无匹配 Var → 空（TS-1.2）
  - `ctx.exprNode == null` → 空（TS-1.3）
  - `ctx.exprNode = LITERAL` → 空（TS-1.4）
  - varName 命中全局变量（repo.getGlobalVar 命中，如 `system.time.hour`）→ 空（TS-1.6）
  - ast 含两个同名 `<Var name="foo">` → 返回**第一个**的 Range（TS-1.7）
  - `ctx == null` → 空（TS-1.8）
  - `ast == null` → 空（TS-1.9）
  - varName 为空字符串 → 空（TS-1.10）
- **GREEN**: 加守卫——`ctx==null`/`ast==null`/root==null 早退；`extractVarName` 用 `instanceof ExpressionNode` 守卫 + kind 判定；`getGlobalVar` 命中早退；`findVarDefinition` 返回首个匹配即停
- **REFACTOR**: 无（T1 已结构化）

### T3: DslTextDocumentService.definition 编排 + 接线

- **SPEC**: SPEC-2
- **AC**: AC-8（文档未打开→空）
- **文件**:
  - `feature/lsp/src/main/java/com/huawei/theme/analysis/lsp/DslTextDocumentService.java`（加字段 + rebuildProviders 构造 + definition 方法）
  - `feature/lsp/src/test/java/com/huawei/theme/analysis/lsp/DslTextDocumentServiceDefinitionTest.java`（新建）
- **变更**:
  - 加 `volatile DefinitionProvider definitionProvider` 字段
  - `rebuildProviders` 加 `this.definitionProvider = new DefinitionProvider(ruleRepository)`
  - 实现 `definition(DefinitionParams)`：取 uri/text；text==null 早退空；`new PositionMapper` + `toOffset`；`analysisService.parse` + `AstContextResolver.resolve`（兜底 `ContextResolver.resolve`）；调 `definitionProvider.definition` 包 `Either.forLeft`
- **RED**: `DslTextDocumentServiceDefinitionTest`：
  - 手写 no-op `LanguageClient` stub（内部类实现 `publishDiagnostics` 空体，其余方法默认）
  - 已 open 文档 + 光标在 `#foo`（已声明）→ `Either.forLeft` 含 1 Location（TS-2.1）
  - 未 open uri → `Either.forLeft` 空（TS-2.2）
  - 光标在标签名上 → 空（TS-2.3）
  - 光标在 `#foo`（未声明）→ 空（TS-2.4）
- **GREEN**: 实现 definition 方法 + 接线
- **REFACTOR**: 若 `hover`/`definition` 的 uri/text/offset 取法重复，提取私有 `documentTextOrEmpty(uri)`（可选，仅当降低重复且不破坏 hover 时）

### T4: DslLanguageServer capability 声明

- **SPEC**: SPEC-3
- **AC**: AC-1（definitionProvider == true）
- **文件**:
  - `feature/lsp/src/main/java/com/huawei/theme/analysis/lsp/DslLanguageServer.java`（initialize 加 1 行）
  - `feature/lsp/src/test/java/com/huawei/theme/analysis/lsp/DslLanguageServerCapabilitiesTest.java`（新建）
- **RED**: `DslLanguageServerCapabilitiesTest`：构造 server，调 `initialize(空 params)`，断言 `result.getCapabilities().getDefinitionProvider().getLeft() == true`
- **GREEN**: 加 `caps.setDefinitionProvider(Either.forLeft(true))`
- **REFACTOR**: 无

### T5: 全量门禁验证

- **SPEC**: 全部
- **AC**: AC-10
- **命令**: `./gradlew --no-daemon :feature:lsp:test :feature:analysis:test`
- **步骤**: 跑门禁，确认 BUILD SUCCESSFUL；修复任何残余失败；同步更新 BACKLOG FEAT001 状态至 PHASE 6
- **依赖**: T1~T4 全部完成

## 优先级与并行度

| 优先级 | 任务 | 可并行? |
|---|---|---|
| P1 | T1, T4 | ✅ 无依赖，可并行 |
| P2 | T2 | ❌ 依赖 T1（同文件增量） |
| P3 | T3 | ❌ 依赖 T1+T2 |
| P4 | T5 | ❌ 依赖全部 |

## TDD 循环约束

- 每个 Task 严格 **RED → GREEN → REFACTOR**：
  - RED：先写测试，跑 `:feature:lsp:test` 确认**失败**（编译错或断言失败）
  - GREEN：写最小实现使测试通过
  - REFACTOR：测试保护下优化，确认仍绿
- 每个 Task 完成后 **commit**（commit message 体现 task 编号 + 红/绿状态）
- 全程 `--no-daemon`，命令时限 120s（构建）

---

> **阶段切换**：PHASE 4 完成。请用户确认 task 列表，确认后进入 PHASE 5（TDD 编码实现）。
