---
name: gradle-build-test
description: Use when running Gradle builds, tests, or E2E gate commands in this project. Not for writing code or managing documentation.
---

# Gradle Build & Test Commands

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

## E2E 分层测试体系

| 层 | 命令 | 用途 | 门禁 |
|---|---|---|---|
| L1-L3 单元/管线/In-Process Golden | `./gradlew --no-daemon :feature:analysis:test` | 单元测试 + L3 golden 匹配（ruleId+severity+count 严格，行号 ±2 近似） | 本地/CI 阻断 |
| Core 隔离检查 | `./gradlew --no-daemon :feature:analysis:checkCoreIntellijDependency` | core 无 com.intellij import | CI 阻断 |
| Fat jar 装配 | `./gradlew --no-daemon :feature:analysis:buildFatJar` | 打包 core+GSON+ANTLR fat jar | CI 阻断 |
| L4 真实子进程 E2E | `./gradlew --no-daemon :feature:analysis:e2e` | `java -jar` 子进程 + golden 匹配（positionAgnostic 模式：仅校验 ruleId+severity+count） | CI 阻断 |

## CI 门禁总和命令

```bash
./gradlew --no-daemon clean :feature:analysis:test :feature:analysis:checkCoreIntellijDependency :feature:analysis:buildFatJar :feature:analysis:e2e
```

全绿方可合并。

**本地快速开发**可只跑 `./gradlew --no-daemon :feature:analysis:test`——此命令仅跑 L1-L3 单元/golden 测试，**不含 L4 fat jar 子进程测试**（L4 需 `:feature:analysis:e2e` 单独触发，L4 测试在 `test` task 中被 Assumption 跳过）。

## 特殊构建

```bash
./gradlew --no-daemon :feature:analysis:buildLspFatJar        # 构建 LSP server fat jar
./gradlew --no-daemon :feature:clients:intellij:buildPlugin  # 构建 IntelliJ 插件 zip
./gradlew --no-daemon :feature:lsp:buildVscodeExtension       # 构建 VS Code .vsix（需 Node/npm）
```

## Golden 文件维护

- 每个 `fixtures/**/*.xml` 和 `dsl/**/*.xml` 必须有同名 `.expected.json`（由 `FixtureCoverageTest` 强制）
- 新增 fixture：同时写 `.xml` 与 `.expected.json`
- 策略变更导致诊断变化：同步更新对应 `.expected.json`
- golden 匹配策略：L3 = ruleId+severity+count 严格 + 行号 ±2 近似 + mustNotTrigger；L4 = positionAgnostic（仅 ruleId+severity+count）
- Golden 文件格式与 CLI `--format json` 输出同构，可用 `GoldenDumper` 工具生成草稿后人工复核
