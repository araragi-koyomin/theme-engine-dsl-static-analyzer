---
module_ids: [CORE]
doc_kind: note
status: active
created: 2026-07-15
---

# Lessons Learned

> 踩过坑的结构化记录。每条必须有可追溯的来源锚点和可执行的防护机制。
> "注意不要"不是防护,"跑 xxx 测试"才是。

### LL-001: verbose 方法接线遗漏
- 状态：validated
- 坑：VerboseCollector 建了 5 个 record 方法,但只接了 2 个(recordStageTime + recordAnalyzerCount),3 个未接线(recordAstStats/recordSymbolStats/recordTypeInference)
- 根因：PHASE 3 设计的时序图只画了 2 个调用点,遗漏了其余 3 个。TDD 测试只验 `[verbose]` 前缀存在,不验内容非 0
- 触发条件：任何"设计→实现"流程中,时序图遗漏的调用点不会被自动发现
- 修复：PHASE 6 一致性验证发现,补充接线 + 强化测试
- 防护：verbose E2E 测试断言 5 类信息非 0/non-(none)
- 来源锚点：commit 06aac1c, phase6-validation.md GAP-2/3/4
- 原理：TDD 验"你想到的",PHASE 6 验"你没想到的"——设计遗漏 → 实现遗漏 → 测试太弱 → 全量门禁通过 → 手动跑才暴露

### LL-002: 并行 subagent 文件冲突
- 状态：validated
- 坑：5 个 subagent 并行开发,共享同一工作目录。subagent A 的临时测试文件阻塞 subagent B 的编译
- 根因：subagent 共享文件系统,无隔离。一个 subagent 的未完成测试(语法错误)阻塞其他 subagent 的 gradle 编译
- 触发条件：多个 subagent 同时编译/测试,且其中一个产出了不完整的源文件
- 修复：受影响的 subagent 临时移走冲突文件,完成后恢复。被 cancel 的 subagent 的已完成工作被其他 subagent 的 commit 意外打包
- 防护：并行 subagent 应避免共享编译输出目录；或使用 git worktree 隔离工作空间
- 来源锚点：feature/p0-bugfix, T1/T8 并行,T2 被 cancel 但代码被 T8 commit 打包

### LL-003: PowerShell BOM 导致编译失败
- 状态：validated
- 坑：subagent 用 PowerShell 写入 Java 源文件时自动加了 UTF-8 BOM(\ufeff),javac 拒绝
- 根因：PowerShell 的 `Set-Content` / `Out-File` 默认加 BOM；Java 编译器不接受 BOM
- 触发条件：任何通过 PowerShell 写入的 .java 文件
- 修复：用 `[System.IO.File]::WriteAllBytes()` 重写文件,手动跳过前 3 字节(BOM)
- 防护：写文件后用 `git diff --check` 或编译验证；AGENTS.md 文件操作约束补充 BOM 注意事项
- 来源锚点：commit ba6dae5, GoldenMatcherExpectedFixesTest.java BOM 修复

### LL-004: Gradle Daemon 不退出导致卡死
- 状态：validated
- 坑：subagent 执行 `./gradlew test` 后,Gradle Daemon 不退出,subagent 永远等不到结束信号
- 根因：Gradle 默认启用 Daemon 守护进程,构建结束后 Daemon 不退出
- 触发条件：任何不带 `--no-daemon` 的 gradlew 命令
- 修复：所有 gradlew 命令加 `--no-daemon`；所有 bash 命令设 timeout
- 防护：AGENTS.md 写入 Gradle Daemon 约束 + timeout 要求
- 来源锚点：AGENTS.md "Gradle Daemon 约束（关键）"章节

### LL-005: fat jar StAX 位置分歧
- 状态：validated
- 坑：fat jar(无 IntelliJ SDK,用 JRE StAX)与 in-process(有 IntelliJ SDK StAX)的 `getCharacterOffset()` 语义不同,导致诊断行列号偏移
- 根因：JRE StAX 在 START_ELEMENT 末尾报告 offset,IntelliJ StAX 在开头报告。`findTagStart` 的 forward-first 扫描误中下一个兄弟元素
- 触发条件：任何 fat jar(L4)与 in-process(L3)的位置精确匹配
- 修复：`findTagStart` 改为 backward-first + `lastIndexOf` 兜底；L4 golden 用 positionAgnostic 模式(仅校验 ruleId+severity+count)
- 防护：L4 测试 positionAgnostic 模式 + AstBuilder.findTagStart 回归测试
- 来源锚点：commit 55a6585, a1b3de5, AstBuilder.java findTagStart
- 原理：Core-Plugin 隔离导致同一 API 在不同运行环境行为不同——L4 测试的唯一价值就是捕获此类分歧
