# P0 已知问题(非阻塞)

> 以下问题经 reviewer 审查确认为 pre-existing 或基础设施问题,不在 P0 范围内,留作后续处理。

## M-1: per-analyzer INTERNAL-ANALYZER-ERROR 是 WARNING 级

- **位置**: `DiagnosticProviderImpl.java:133`
- **描述**: 单个 analyzer 抛异常时产出 WARNING 级诊断。`--quiet` 模式过滤 WARNING → analyzer 异常在 quiet 模式被"吞"。
- **根因**: pre-existing 代码,P0 前就有。P0-4 只改了 Runner 层 catch(AST/语义/修复→ERROR+hasInternalError),未动 Inner 层 per-analyzer catch(软降级:一个 analyzer 挂了继续跑其他)。
- **影响**: quiet 模式下 analyzer 异常不可见。非 quiet 模式下 WARNING 诊断可见。
- **处理**: 留作 P2 analyzer 韧性改进。设计决策:per-analyzer 失败应为软降级(WARNING+继续)还是硬错误(ERROR+exit 2)。

## M-3: JaCoCo 覆盖率 0%

- **位置**: `build.gradle:103-119`
- **描述**: gradle-intellij-plugin 的 `instrumentCode` 修改字节码,与 JaCoCo agent 的字节码插桩冲突,覆盖率数据全部为 0。
- **影响**: 质量门禁 ">80% 行覆盖率" 无法自动验证。
- **处理**: 独立任务。需研究 gradle-intellij-plugin + JaCoCo 集成方案(可能需 disable instrumentTestCode 或配置 JaCoCo useInstrumentedClasses)。

## M-4: mode fixture golden 在 FULL 模式下验证

- **位置**: `fixtures/mode/semantic_only_test.expected.json`
- **描述**: `GoldenDiagnosticMatchTest` 对所有 golden 文件跑 FULL 模式。`semantic_only_test.expected.json` 的 expectedFixes 只在 FULL 模式生效(fixActions 只在 `mode==FULL` 时生成)。
- **影响**: 命名有误导性,但测试逻辑正确——FULL 模式 golden 验证 FULL 行为,`ModeGoldenTest` 独立验证模式前缀过滤。
- **处理**: 可重命名为 `semantic_only_test_full.expected.json` 或加文档说明。非紧急。

## M-6: runOnFile 抛异常 vs runOnDirectory 静默跳过

- **位置**: `BatchInspectionRunnerImpl.java:76` (throws) vs `:102-106` (silent skip)
- **描述**: 文件不可读时,`runOnFile` 抛 `BatchInspectionException`,但 `runOnDirectory` 创建空结果不设 `hasInternalError`。
- **根因**: pre-existing,P0 前就有。
- **影响**: 目录扫描时某文件不可读会静默跳过,不报错。
- **处理**: 留作 P2。统一行为:目录模式中文件不可读也应设 `hasInternalError=true`。
