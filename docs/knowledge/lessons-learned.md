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

### LL-010: squash merge 规约未在 GitHub 强制
- 状态：validated
- 坑：SOP §2.3 写了 squash merge 规约，但 GitHub PR 默认 "Create a merge commit" 按钮（绿按钮），用户点绿按钮直接 merge 了 9 个 commit 而非 1 个 squash commit
- 根因：规约写在文档但未在 GitHub 仓库设置中强制（branch protection / merge strategy）
- 触发条件：SDD 规约定义了合入策略但未在工具层面强制
- 修复：main 已 merge（不回退历史改写风险大），后续 PR 需用户手动选 "Squash and merge" 下拉
- 防护：GitHub 仓库 Settings → General → Pull Request merges 关闭 "Allow merge commits" 只留 "Allow squash merging"；或 branch protection rule 要求 squash
- 来源锚点：FIX002 PR #88, SOP §2.3 step 5

### LL-011: SDD 流程未能阻止过度工程化
- 状态：validated
- 坑：FIX006（diagnostic 定位精确化）用 6 阶段 SDD + 15 commit + 2 轮 review + 双模式设计（模式 A `astNode(attr)` / 模式 B `positionFromExpr`）+ 零宽保护 + 0,0 回退 + 5 golden 更新 + quickfix 回归修复，最终在真实 DSL 脚本上验证零效果——三个分支（main / fix:typeanalyzer / fix:FIX006）输出完全一致。contributor 用 1 个 commit 在 1 个文件完成 TypeAnalyzer 部分，效果相同。
- 根因：SDD 的 PHASE 1-4（需求/规格/设计/任务）把一个"改 `.astNode()` 参数"的简单改动放大为多契约设计，引入了不必要的复杂度（双模式、helper、零宽保护）。设计文档让过度设计被"正当化"——每多一个"模式"都有 spec 条目背书。
- 触发条件：用 SDD 重流程处理本质上简单的改动；设计阶段引入"通用框架"思维（positionFromExpr helper、模式分类）而非最小改动
- 修复：废弃整个分支
- 防护：改动前先用真实 DSL 脚本跑 CLI 对比预期效果——如果 main 和改后输出无差异，改动无意义，不应进入 SDD。SDD PHASE 1 应包含"在真实输入上验证改动确实产生可见差异"的前置检查。简单改动（改参数/改常量/改一行）不应走完整 6 阶段 SDD。
- 来源锚点：FIX006 全流程，用户用 `script_test.xml` 验证零效果后废弃

### LL-012: 未在真实输入上验证就投入开发
- 状态：validated
- 坑：FIX006 从 PHASE 1 到 PHASE 6 + review，全程用单元测试和 golden fixture 验证，但从未在真实 DSL 脚本上跑 CLI 对比。直到用户用 `script_test.xml` 测试才发现三个分支输出完全一致——ConstraintAnalyzer 改了 condition 正则提取首属性，但单行元素的属性 col 与元素 col 相同，改动被吞掉。
- 根因：测试 fixture 是为"触发规则"设计的最小输入，不反映真实 DSL 的元素结构（单行多属性、属性在同一行）。golden 的 `lineTolerance: 2` 和"不检查 col"进一步掩盖了差异。SDD 流程中无"真实输入端到端验证"环节。
- 触发条件：仅用单元测试/fixture 验证，跳过真实 DSL 脚本的 CLI 输出对比
- 修复：用户介入，用 `script_test.xml` 对比三个分支，发现零差异后废弃
- 防护：PHASE 5 GREEN 后、PHASE 6 前，必须用至少一个真实 DSL 脚本跑 `java -jar dsl-analyzer.jar --format json` 对比 main 和改后输出。如果输出无差异，改动无效，应废弃或重新评估。
- 来源锚点：FIX006，用户用 `script_test.xml` + `type_inference_edge_cases.xml` 对比

### LL-013: review 发现的 bug 被跳过修复直接推进
- 状态：validated
- 坑：FIX006 review 发现 Important 级 quickfix 回归（ClampValueGenerator/ConstraintFixGenerator 的 `instanceof DslElementNode` 在 astNode 改为 DslAttributeNode 后失败），跳过修复直接登记到 BACKLOG 并推进到文档状态更新。用户指出后修复，但首次"验证"用的仍是不覆盖该场景的既有测试——直到被指出后才补端到端测试。
- 根因：将"记录到 backlog"等同于"已处理"；用不覆盖 bug 场景的测试验证修复
- 触发条件：review 标记 Important 后未立即修复就推进
- 修复：用户两次纠正——(1) 修复 generator (2) 补端到端测试
- 防护：review 的 Important/Critical 必须在推进前修复并用覆盖该 bug 场景的测试验证，不可用不相关的既有测试替代
- 来源锚点：FIX006 review Important, 用户两次纠正

### LL-014: JaCoCo 0% 覆盖率门禁剧场
- 状态：validated
- 坑：JaCoCo `jacocoTestReport` 一直报 LINE covered=0/6594，但 `test.exec` 非空、880 测试在跑。被当门禁存在数月，无人发现它报 0。加 `jacocoTestCoverageVerification` 时设 minimum=1.0，build 仍 SUCCESSFUL——门禁不 trip，是剧场。
- 根因：gradle-intellij 插件强制 test JVM 用 `com.intellij.util.lang.PathClassLoader`，JaCoCo load-time agent 无法记录经该 classloader 加载的项目类（test.exec 只含 gradle-infra 类）；又 `jacocoTestCoverageVerification` 默认用 `sourceSets.main.output`（core-tests 无 main → 空 classDirectories）→ trivially pass。无门禁 canary，故 0% 报告长期"绿"。
- 触发条件：IntelliJ 插件模块套 JaCoCo 但无"门禁能 trip"的 canary；verification 任务用空 classDirectories
- 修复：迁 core 单测到无 intellij 插件的 `feature:core-tests`（默认 classloader）→ JaCoCo 记录非零（LINE 4274/5180 = 82.5%）；给 verification 显式设 classDirectories(core/**) + executionData；加 `jacocoTestCoverageVerification`（minimum=0.80）+ 门禁 canary（minimum=1.0 验证可 trip）
- 防护：每个 CI 门禁引入时必须喂已知坏输入证明能 trip（门禁 canary，见 AGENTS.md"可证伪性原则"）
- 来源锚点：审计 R1 + commit 4423ac2（门禁 canary 抓到空 classDirectories 剧场 bug）

### LL-015: 可证伪性原则（Anti-Theater Canaries）
- 状态：validated
- 坑：流程层（FIX006 6 阶段 SDD 零效果，LL-011）、测试层（假绿测掩盖真 bug，LL-008）、门禁层（0% 覆盖率"绿"，LL-014）三处同病——"通过"信号不等于"在干活"。
- 根因：缺"可证伪性"——整套流程没有机制能反证"这个绿/这个流程/这个门禁"其实没在干活。全是"看起来对"，没有"能反证它在量/在干活"的通道。
- 触发条件：任何产物加"通过"信号却不加"能 fail"的反证通道
- 修复：给改动/测试/门禁各加一条 canary（见 AGENTS.md"可证伪性原则"节）：改动 canary（真实 DSL 脚本跑 jar diff）、测试 canary（注入 bug 确认测试 fail）、门禁 canary（喂坏输入确认门禁 trip）
- 防护：meta-canary——每条规则必须含"运行命令+信号"，无具体命令/信号的规则=剧场，不许入文
- 来源锚点：本 reform（docs/development/specs/quality-gates/）+ lesson.md LL-011/012/013
