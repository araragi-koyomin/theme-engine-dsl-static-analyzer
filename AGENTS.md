# AGENTS.md

## 开发方法论：SDD + TDD 联合驱动

所有代码开发均基于 SDD（规格驱动开发）+ TDD（测试驱动开发）联合方法论完成。**严格按阶段执行，不可跳过。**

### 核心原则

- **SDD 决定"做什么"**：需求澄清 → 规格定义 → 设计 → 任务拆分。产出 spec 与设计文档。
- **TDD 决定"怎么验证就对了"**：红灯 → 绿灯 → 重构。测试是验证实现符合 spec 的证据。
- **SDD 的 spec 是 TDD 测试用例的依据；TDD 的测试是 SDD validation 的证据。** 两者互为支撑，缺一不可。

### 六阶段开发流程

#### PHASE 1：需求澄清

理解用户需求，澄清模糊点，向用户提问直到无歧义。

- **产出文档**包含：背景、目标、范围、约束、验收标准。
- **验收标准必须可测试**——每条标准对应至少一个验收测试场景。
- **阶段切换**：完成后向用户展示产出物并请求确认，用户确认后进入 PHASE 2。

#### PHASE 2：规格定义

基于 PHASE 1 文档定义接口契约。**只定义"契约"，不定义内部实现。**

- **产出文档**包含：接口签名、数据结构、状态流转、业务规则。
- 契约要素：输入参数（类型/约束）、输出（类型/保证）、前置条件、后置条件、异常（类型/触发条件/处理策略）。
- **同步编写验收测试清单**：每个 spec 条目对应一组测试场景。
- **阶段切换**：完成后向用户展示产出物并请求确认。

#### PHASE 3：设计

基于 spec 进行模块/类/方法级别设计。**只设计到接口和协作关系，不设计算法和实现细节**（留给 TDD 探索）。

- **产出设计文档**包含：类图、时序图、模块职责、依赖关系（可用 PlantUML 制图）。
- **设计时考虑可测试性**：依赖注入、接口抽象、避免静态方法调用。
- **阶段切换**：完成后向用户展示产出物并请求确认。

#### PHASE 4：任务拆分

将 design 拆分为可执行的编码任务。

- **每个 task 对应一个 TDD 循环**，粒度控制在 15-30 分钟。
- **每个 task 标注**：对应的 spec 条目 + 测试场景清单。
- **产出 `tasks.md`**：包含任务列表、依赖关系、优先级。
- **必须向用户展示 task 列表，获得确认后再进入编码。**

#### PHASE 5：TDD 编码实现

对每个 task，严格遵循 **RED → GREEN → REFACTOR** 循环：

1. **RED（红灯）**：写测试，运行确认失败（测试存在但实现不存在或不符合预期）。
2. **GREEN（绿灯）**：写最小实现使测试通过。
3. **REFACTOR（重构）**：在测试保护下优化代码结构，确认测试仍绿。
- 每个 task 完成后 commit。

#### PHASE 6：一致性验证

逐项核对 spec 中每个契约，确认测试覆盖。

- 运行全量测试 + 覆盖率报告。
- 检查代码与设计文档的一致性（类结构、模块职责）。
- **产出一致性验证文档**：包含验证结果、偏差说明、覆盖率数据。
- 向用户展示并请求确认。

### 质量门禁

| 指标 | 要求 |
|---|---|
| spec 条目测试覆盖率 | 100%（每条 spec 必须有对应测试） |
| 单元测试通过率 | 100% |
| 代码行覆盖率 | > 80% |
| 编译告警 | 0 |
| spec/design/code 一致性 | 无未说明的偏差 |

### 阶段切换规则

- 每个 PHASE 完成后，**向用户展示产出物并请求确认**，用户确认后再进入下一 PHASE。
- 编码过程中发现 spec/design 有误，**回到对应 PHASE 修正后再继续**，不在代码中绕过。
- 不允许跳过 PHASE 或在未完成前一阶段时进入下一阶段。
- **所有阶段结束后，如果用户提出合入请求（merge/PR），必须先调用 reviewer agent 进行代码审查确认**，审查通过后方可执行合入操作。

### 沟通原则

- **禁止表演性同意**：不使用"你说得对""感谢回复""好的，没问题"等空洞附和。
- 面对用户提问或反馈，按以下优先级行动：
  1. **证明对错**：用技术性问题验证用户说法（如"这个接口在 X 场景下会怎样？"），而非无条件认同。
  2. **澄清问题**：如果不理解或存在歧义，直接提出澄清问题。
  3. **直接行动**：如果方向明确，立即开始修复/实现，行动优先于言语。
- **行动 > 言语**：不要用解释代替行动，能直接做的事不要先长篇说明。

---

## 文档管理

### 三层记忆架构

| 层 | 位置 | 内容 | 规则 |
|---|---|---|---|
| **热层** | `docs/BACKLOG.md` | 模块状态(M0-M8) + 阶段状态(P0-P3) + 文档待办 | 只放活跃项,完成后移除 |
| **温层** | `docs/architecture/` + `docs/development/` | 模块深度文档 + SDD 文档 + 开发报告 | 活跃文档,有 frontmatter |
| **冷层** | `docs/archive/YYYY-MM/` | 已完成的计划/规格/设计文档 | 归档≠删除,镜像源目录结构 |

### Frontmatter 规范

所有 `docs/**/*.md` 文件（`archive/` 和 `themes_engine_next/` 豁免）必须有 YAML frontmatter：

```yaml
---
module_ids: [M3, M4]        # 关联模块：M0-M8, PSI, CLI, CORE, E2E
phase: P0                  # 关联阶段：P0/P1/P2/P3（无关联则省略）
doc_kind: architecture     # architecture|spec|plan|report|guide|decision|note|template
status: active             # active|stale|superseded|archived
created: 2026-07-15
---
```

### 归档规则

- 计划执行完 → `docs/archive/YYYY-MM/`
- 讨论收敛 → 归档
- Bug 修好 → 归档
- 设计被采纳并实现 → 归档（活文档如 Architecture.md 保留）
- **归档 ≠ 删除**

### 命名规范

| 类型 | 格式 | 示例 |
|---|---|---|
| 模块文档 | `MX-Name.md` | `M3-SyntaxAnalysis.md` |
| 计划 | `YYYY-MM-DD-slug.md` | `2026-07-14-p0-bugfix-plan.md` |
| SDD Phase | `phaseN-title.md` | `phase2-spec.md` |
| 报告 | `slug.md` | `dev-summary-2026-07-14.md` |

### 自动化守护

```bash
bash scripts/check-frontmatter.sh      # 检查所有 .md 有 frontmatter
bash scripts/check-doc-dir-size.sh      # 活跃目录 >15 文件 warn, >25 error
```

### 文档模板

新建文档时参考 `docs/knowledge/doc-templates.md`。

---

## 环境要求

- Java 17
- Gradle 8.2
- gradle-intellij-plugin 1.13.3

## 基本构建

```bash
./gradlew clean build          # 构建整个项目（跳过测试）
./gradlew :modulename:build    # 构建特定模块
./gradlew clean                # 清理构建产物
./gradlew :feature:lsp:buildLspFatJar        # 仅构建 LSP server fat jar
./gradlew :feature:clients:intellij:buildPlugin  # 构建 IntelliJ LSP 客户端插件 zip（:feature:analysis 为纯 core 引擎）
./gradlew :feature:lsp:buildVscodeExtension  # 构建 VS Code 客户端 .vsix（含 server jar，需 Node/npm）
```

> `:feature:lsp:buildVscodeExtension` 不在默认 `build` 内（依赖 Node/npm）；构建、安装、配置见 `feature/clients/vscode/README.md`。

## 测试命令

```bash
./gradlew --no-daemon test                                          # 运行所有测试
./gradlew --no-daemon :modulename:test                              # 运行特定模块测试
./gradlew --no-daemon :modulename:test --tests "ClassName"          # 运行单个测试类
./gradlew --no-daemon :modulename:test --tests "ClassName.method"   # 运行单个测试方法
```

## E2E 分层测试与 CI 门禁

### 分层测试体系

| 层 | 命令 | 用途 | 门禁 |
|---|---|---|---|
| L1-L3 单元/管线/In-Process Golden | `./gradlew --no-daemon :feature:analysis:test` | 单元测试 + L3 golden 匹配（ruleId+severity+count 严格，行号 ±2 近似） | 本地/CI 阻断 |
| Core 隔离检查 | `./gradlew --no-daemon :feature:analysis:checkCoreIntellijDependency` | core 无 com.intellij import | CI 阻断 |
| Fat jar 装配 | `./gradlew --no-daemon :feature:analysis:buildFatJar` | 打包 core+GSON+ANTLR fat jar | CI 阻断 |
| L4 真实子进程 E2E | `./gradlew --no-daemon :feature:analysis:e2e` | `java -jar` 子进程 + golden 匹配（positionAgnostic 模式：仅校验 ruleId+severity+count） | CI 阻断 |

### CI 门禁总和命令

```bash
./gradlew --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e
```

全绿方可合并。本地快速开发可只跑 `./gradlew --no-daemon :feature:analysis:test`（不含 fat jar 子进程）。

### Golden 文件维护

- 每个 `fixtures/**/*.xml` 和 `dsl/**/*.xml` 必须有同名 `.expected.json`（由 `FixtureCoverageTest` 强制）
- 新增 fixture：同时写 `.xml` 与 `.expected.json`
- 策略变更导致诊断变化：同步更新对应 `.expected.json`，commit message 说明变更原因
- golden 匹配策略：L3 = ruleId+severity+count 严格 + 行号 ±2 近似 + mustNotTrigger；L4 = positionAgnostic（仅 ruleId+severity+count，因 fat jar JRE StAX 与 in-process IntelliJ StAX 行号语义不同）
- Golden 文件格式与 CLI `--format json` 输出同构，可用 `GoldenDumper` 工具生成草稿后人工复核

## Bash 命令约束

- **禁止在 Bash 命令中使用 PowerShell 管道过滤**（如 `| Select-String`、`| Where-Object`、`2>&1 | ...`）。这类管道会导致进程结束检测失败，造成无限等待。
- 如需搜索文件内容，使用 Grep 工具而非 Bash 管道；如需过滤输出，使用 Grep 工具的 include/path 参数。

### Gradle Daemon 约束（关键）

- **所有 `./gradlew` 命令必须加 `--no-daemon` 参数**。Gradle 默认启用 Daemon 守护进程，构建结束后 Daemon 不退出，`gradlew` 进程会一直等待它关闭，导致永远收不到结束信号、进程卡死。
- 示例：`./gradlew --no-daemon :feature:analysis:test --tests "ClassName"`
- **所有 Bash 命令必须设置时限**：普通任务（不涉及 plugin 层、antlr-intellij-adaptor、jar 构建）设 30 秒上限；构建/打包任务设 120 秒上限。一旦超时自动终止进程，避免卡死。
- 上述规则同样适用于 subagent 内执行的命令——subagent 无法自主判断 Gradle 是否卡死，必须靠 timeout 兜底。

## 代码风格指南

### 4.1 命名约定

- 类名：大驼峰（`DslElementRule`）
- 方法名：小驼峰（`getElementRule`）
- 常量：UPPER_SNAKE_CASE（`MAX_RETRY_COUNT`）
- 变量：小驼峰（`elementName`）
- 包名：全小写（`com.example.dsl.rule`）

### 4.2 导入顺序

1. `java.*` 标准库
2. `javax.*` 扩展库
3. 第三方库（如 `com.google.gson.*`）
4. IntelliJ Platform API（如 `com.intellij.openapi.*`）
5. 项目内部包

每组之间用空行分隔，组内按字母顺序排列。

### 4.3 类型使用

- 优先使用接口类型（`List<>` 而非 `ArrayList<>`）
- 使用 `Optional<T>` 处理可能为 null 的值
- 使用 `CompletableFuture<T>` 进行异步操作
- 使用 `@Data` 或 `@Builder` 注解简化 POJO（Lombok）

### 4.4 错误处理

- 使用 try-catch 捕获异常，并通过 `LogUtil` 记录错误
- 工具类方法在异常时返回默认值或 null（如 `StringUtils.parseInt`）
- 不抛出受检异常，使用运行时异常
- 在 catch 块中记录完整的异常信息

### 4.5 日志规范

使用 `LogUtil` 记录日志，日志级别：`d()` 调试信息、`i()` 一般信息、`w()` 警告信息、`e()` 错误信息。要求日志格式简洁描述、包含关键参数。

```java
private static final LogUtil LOGGER = LogUtil.getInstance(InstallThemeTask.class);
LOGGER.d("安装结构: " + e.getMessage());
```

### 4.6 代码组织

- 工具类：静态方法，私有构造函数
- 任务类：继承 `CompletableFuture<T, U>`，实现 `run()` 方法
- 常量类：所有字段为 `public static final`
- 内部类：使用 `private static class` 封装相关逻辑

### 4.7 格式规范

- 缩进：4 空格
- 大括号：左括号不换行
- 行宽：不超过 120 字符
- 方法之间、逻辑块之间使用空行分隔

### 4.8 异步任务模式

所有异步任务继承 `CompletableFuture<T, U>`：

```java
public class MyTask extends CompletableFuture<Integer, TaskArgs<MyData>> {
    private static final LogUtil LOGGER = LogUtil.getInstance(MyTask.class);

    @Override
    protected CompletableFuture<Integer> run(TaskArgs<MyData> taskArgs) {
        // 实现任务逻辑
        return CompletableFuture.completedFuture(result);
    }
}
```

### 4.9 事件驱动模式

使用 `Dispatcher` 进行组件间通信：

```java
// 发送事件
Dispatcher.instance().send(EventId.MY_EVENT, data);

// 注册事件处理器
Dispatcher.instance().register(EventId.MY_EVENT, (event) -> {
    // 处理事件
});
```

### 4.10 文件操作

- 使用 `FileUtil` 进行文件操作
- 路径使用 `\\` 或 `Paths.get()`
- 始终使用 UTF-8 编码
- 使用 try-with-resources 管理流

### 4.11 字符串处理

- 使用 `StringUtils` 进行字符串操作
- 使用 `isEmpty()` 检查空字符串
- 使用 `equals()` 进行安全比较
