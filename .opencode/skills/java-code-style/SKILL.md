---
name: java-code-style
description: Use when writing, editing, or reviewing Java source code in this project. Not for build configuration, documentation, or non-Java files.
---

# Java Code Style Guide

## 命名约定

- 类名：大驼峰（`DslElementRule`）
- 方法名：小驼峰（`getElementRule`）
- 常量：UPPER_SNAKE_CASE（`MAX_RETRY_COUNT`）
- 变量：小驼峰（`elementName`）
- 包名：全小写（`com.example.dsl.rule`）

## 导入顺序

1. `java.*` 标准库
2. `javax.*` 扩展库
3. 第三方库（如 `com.google.gson.*`）
4. IntelliJ Platform API（如 `com.intellij.openapi.*`）
5. 项目内部包

每组之间用空行分隔，组内按字母顺序排列。

## 类型使用

- 优先使用接口类型（`List<>` 而非 `ArrayList<>`）
- 使用 `Optional<T>` 处理可能为 null 的值
- 使用 `CompletableFuture<T>` 进行异步操作
- 使用 `@Data` 或 `@Builder` 注解简化 POJO（Lombok）

## 错误处理

- 使用 try-catch 捕获异常，并通过 `LogUtil` 记录错误
- 工具类方法在异常时返回默认值或 null
- 不抛出受检异常，使用运行时异常
- 在 catch 块中记录完整的异常信息

## 日志规范

使用 `LogUtil` 记录日志，日志级别：`d()` 调试信息、`i()` 一般信息、`w()` 警告信息、`e()` 错误信息。

```java
private static final LogUtil LOGGER = LogUtil.getInstance(MyClass.class);
LOGGER.d("操作完成: " + e.getMessage());
```

## 代码组织

- 工具类：静态方法，私有构造函数
- 任务类：继承 `CompletableFuture<T, U>`，实现 `run()` 方法
- 常量类：所有字段为 `public static final`
- 内部类：使用 `private static class` 封装相关逻辑

## 格式规范

- 缩进：4 空格
- 大括号：左括号不换行
- 行宽：不超过 120 字符
- 方法之间、逻辑块之间使用空行分隔
- 不添加注释，除非明确要求

## 异步任务模式

```java
public class MyTask extends CompletableFuture<Integer, TaskArgs<MyData>> {
    private static final LogUtil LOGGER = LogUtil.getInstance(MyTask.class);

    @Override
    protected CompletableFuture<Integer> run(TaskArgs<MyData> taskArgs) {
        return CompletableFuture.completedFuture(result);
    }
}
```

## 事件驱动模式

```java
Dispatcher.instance().send(EventId.MY_EVENT, data);
Dispatcher.instance().register(EventId.MY_EVENT, (event) -> { });
```

## 文件操作

- 路径使用 `Paths.get()` 或 `\\`
- 始终使用 UTF-8 编码
- 使用 try-with-resources 管理流

## 字符串处理

- 使用 `isEmpty()` 检查空字符串
- 使用 `equals()` 进行安全比较
