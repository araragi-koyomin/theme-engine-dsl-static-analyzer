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

### LL-006: 在 main 分支直接开发 + 分支命名不一致
- 状态：validated
- 坑：FIX002 全部 SDD 文档（phase1-4 + 审计报告 + skill 修改）直接写在 main 分支上，且 bugfix 用了 `feature/` 前缀而非 `fix/`
- 根因：SDD 流程未将"创建隔离分支"作为 PHASE 1 的前置步骤；分支前缀未区分 fix/feature；doc-management skill 定义了 `FIX00N`/`FEAT00N` 目录命名但分支前缀未同步
- 触发条件：任何开发工作开始时未先切分支；bugfix 用 feature/ 前缀
- 修复：创建 `fix/FIX002-undefined-str-ref` 分支；SOP §2.1 补充 `fix/<name>` 前缀
- 防护：开发前 `git branch --show-current` 确认不在 main；SOP §2.1 已区分 fix/feature 分支
- 来源锚点：FIX002 session, 用户指出"为什么到现在为止都还在main？！"

### LL-007: main 分支编译断裂未被发现
- 状态：validated
- 坑：`6262bfd`（07-14）引入 `buildUndefinedElementRefDiagnostic` 重复 `refText` 声明（line 343+357），main 分支不可编译，一天无人发现
- 根因：commit 直接合入 main 未经 `compileJava` 验证；代码 review 仅靠肉眼未发现同方法内重复局部变量
- 触发条件：commit 未跑编译即合入 main；代码 review 替代编译器
- 修复：FIX002 Task 1 拆双变量修复
- 防护：合入 main 前必须跑 `./gradlew --no-daemon :feature:analysis:compileJava`；CI 阻断编译失败 PR
- 来源锚点：commit 6262bfd, VarRefAnalyzer.java:343+357

### LL-008: 测试剧场——测试名与断言矛盾掩盖 bug
- 状态：validated
- 坑：`undefinedStringRefProducesSEM_REF_001` 测试名声称"produces SEM-REF-001"，断言却写 `assertTrue(diagnostics.isEmpty())`，把 `@` 跳过 bug 编码为"正确"。全套件审计发现 15 CRITICAL + 34 HIGH 同类问题（详见审计报告）
- 根因：测试名为意图、断言为实现快照，二者不一致时实现 bug 被固化；无静态检查发现名/断言矛盾
- 触发条件：先写实现的错误行为再写测试匹配该行为（而非匹配 spec）；测试名含"Produces X"但断言 `isEmpty()`
- 修复：FIX002 Task 3 修正断言；审计报告 `docs/development/reports/test-theater-audit-2026-07-15.md`；FIX004 治理
- 防护：测试名与断言必须一致；禁用 `if(errorCount>0)` guard 包裹全部断言；"合法文件"断言必须 `errorCount==0`；禁用 stub 当 SUT；subagent 冷启动 review 兜底
- 来源锚点：VarRefAnalyzerTest.java:66, 审计报告 15 CRITICAL

### LL-009: SDD 产出物交叉自洽检查不足
- 状态：validated
- 坑：(1) PHASE 1 影响表写"+1 SEM-REF-001"与已确认的"声明 Var"策略矛盾；(2) `@ishour12` 被误判为未声明（实为预制全局变量）；(3) tasks.md endColumn 算术错误（16 字符算成 17）
- 根因：(1) 文档各 section 独立编写未交叉校验；(2) 检索 fixture `@` 引用时未对照 `global_vars.json` 预制全局清单；(3) 字符串长度靠心算而非程序验证
- 触发条件：SDD 文档多 section 并行编写；检索结果未与权威数据源交叉核对；数值计算未验证
- 修复：逐项修正——影响表改"声明 Var"、`@ishour12` 标注预制全局、endColumn 16→19
- 防护：PHASE 切换前自检产出物内部一致性；检索引用时必须对照 `global_vars.json`；字符串长度用 `len()` 而非心算；subagent 冷启动 review 兜底
- 来源锚点：FIX002 phase1 §3, phase4 Task 2, 用户指出 `@ishour12` 是预制全局
