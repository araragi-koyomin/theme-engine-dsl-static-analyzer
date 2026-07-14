# AGENTS.md

## 环境要求

- Java 17
- Gradle 8.2
- gradle-intellij-plugin 1.13.3

## 基本构建

```bash
./gradlew --no-daemon clean build          # 构建整个项目（跳过测试）
./gradlew --no-daemon :modulename:build    # 构建特定模块
./gradlew --no-daemon clean                # 清理构建产物
```

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
