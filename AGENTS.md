# AGENTS.md

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
./gradlew :feature:lsp:buildVscodeExtension  # 构建 VS Code 客户端 .vsix（含 server jar，需 Node/npm）
```

> `:feature:lsp:buildVscodeExtension` 不在默认 `build` 内（依赖 Node/npm）；构建、安装、配置见 `feature/lsp/clients/vscode/README.md`。

## 测试命令

```bash
./gradlew test                              # 运行所有测试
./gradlew :modulename:test                  # 运行特定模块测试
./gradlew :modulename:test --tests "ClassName"  # 运行单个测试类
./gradlew :modulename:test --tests "ClassName.methodName"  # 运行单个测试方法
```

## Bash 命令约束

- **禁止在 Bash 命令中使用 PowerShell 管道过滤**（如 `| Select-String`、`| Where-Object`、`2>&1 | ...`）。这类管道会导致进程结束检测失败，造成无限等待。
- 如需搜索文件内容，使用 Grep 工具而非 Bash 管道；如需过滤输出，使用 Grep 工具的 include/path 参数。

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
