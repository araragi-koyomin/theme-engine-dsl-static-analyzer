# 沉浸式智慧锁屏 Showcase 样例

## 备战入口

- `SHOWCASE-PREPARATION.md`：个人讲稿、按 Theme Engine Next 分类的扩展示例、尖锐问答和现场红线。
- [Showcase 演示 PPT](../outputs/theme-engine-dsl-static-analysis-showcase.pptx)：5 页技术骨干版演示材料，含逐页讲者备注。
- `script_rule_dsl_gap_probe.xml`：FIX005 最小复现探针，仅用于理解当前规则执行边界，不作为成功演示脚本。
- `docs/development/reports/showcase-risk-audit-2026-07-15.md`：规则能力、产品范围、已实现规则过严问题和 Rule ID 来源一致性审计。

## 定位

这是一组面向静态分析 Showcase 的“生产形态”脚本：结构和能力组合参考 Theme Engine Next 文档，
但资源路径均为占位符，未附带图片、视频、字体和真机主题包，因此不宣称已经通过主题引擎运行验证。

- `script_immersive_lockscreen_faulty.xml`：在合理业务结构中埋入 8 个可被当前分析器稳定检出的错误。
- `script_immersive_lockscreen_clean.xml`：对应修正版，用来证明同等复杂度的合法脚本不会被误报。
- `SHOWCASE-PREPARATION.md`：个人讲稿、按 Theme Engine Next 分类的扩展方法、风险清单和尖锐问题答法。
- `script_rule_dsl_gap_probe.xml`：FIX005 的最小复现探针，只用于缺陷验证，**不要当作成功能力现场演示**。

验证基线：`origin/main@3857eb7`，Fat JAR 构建成功。faulty 为 `5 errors / 3 warnings / exit 1`，
clean 为 `0 errors / 0 warnings / exit 0`。

## 业务能力组成

样例不是错误标签拼盘，而是一套沉浸式锁屏：

- `screenWidth` 与系统宽高变量驱动自适应布局；
- `Weather` 提供天气图标和温度；
- `MediaController`、`MediaIcon`、`MediaCommand` 提供音乐信息与收藏操作；
- `MultiLayer` 和 `Layer` 提供 HarmonyOS 5.0 穿越动效；
- `SourceImage` 提供帧动画解锁；
- `Video`、`VideoCommand` 提供环境视频与交互；
- `ParticleView` 提供跟手粒子；
- `ExternalCommands` 在亮屏/熄屏时启停动效。

## 故障地图

| 位置 | Rule ID | 生产语义 |
|---|---|---|
| `cached_greeting` | `SEM-PERSIST-001` | 基于当前小时计算的问候语被持久化，可能在时间变化后保留旧值 |
| `accent_palette` | `SEM-VAR-003` | 数组同时声明 `values` 和 `size`，引擎优先级会掩盖配置意图 |
| `fallback_background` | `SEM-ATTR-005` | 背景图使用 `fill`，不符合 `isBackground` 的裁剪契约 |
| `MultiLayer` | `SEM-3D-001` | 重力模式配置了只对滑动/调距模式生效的参数 |
| `unlock_frames` | `SEM-SRCIMG-001` | 帧解锁启用 `direction=0`，但未提供可工作的循环和解锁帧配置 |
| `VideoCommand` | `SEM-CMD-001` | 同一次视频命令同时控制 `play` 和 `sound`，违反互斥契约 |
| `StyleCommand` | `SEM-CMD-004` | 换肤索引要求静态数字，却绑定了运行时表达式 |
| `ParticleView` | `SEM-EFFECT-PV-002` | 粒子尺寸超过 120px 上限，可能增加无收益的性能开销 |

## 文档与规则依据

- `docs/DSL-Rule-Spec.md`
- `docs/themes_engine_next/raw_markdown/themes-engine-next-lock-0000002244659534.md`
- `docs/themes_engine_next/raw_markdown/themes-engine-next-3d-multilayer-0000002490002442.md`
- `docs/themes_engine_next/raw_markdown/themes-engine-next-2da-particleview-0000002471395152.md`
- `docs/themes_engine_next/raw_markdown/themes-engine-next-base-weather-0000002504275029.md`
- `docs/themes_engine_next/raw_markdown/themes-engine-next-base-mediacontroller-0000002471235098.md`
- `docs/themes_engine_next/raw_markdown/themes-engine-next-base-sourceimage-0000002504274941.md`
- `feature/analysis/src/main/resources/rules/elements/`

## 演示命令

Windows 中文终端当前建议显式指定 UTF-8：

```powershell
$jar = "feature/analysis/build/cli/dsl-analyzer.jar"

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 `
    -jar $jar --format terminal --no-color `
    showcase/script_immersive_lockscreen_faulty.xml

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 `
    -jar $jar --format terminal --no-color `
    showcase/script_immersive_lockscreen_clean.xml
```

流水线或平台接入时将 `--format terminal` 改为 `--format json`，并读取退出码：

- `0`：分析完成，无 error；
- `1`：分析完成，存在 error；
- `2`：参数、加载或内部执行异常。

静态分析只负责可证明的结构、规则、引用和类型约束，不替代资源完整性、主题打包、引擎运行、
动画效果、功耗和真机兼容性测试。

## 推荐现场顺序

1. 先运行 clean，展示复杂合法脚本为 0 诊断。
2. 再运行 faulty，展示汇总为 5E/3W。
3. 只展开三个高价值问题：`SEM-3D-001`、`SEM-SRCIMG-001`、`SEM-PERSIST-001`。
4. 切换 JSON 输出，说明同一结果可以进入现有流水线、PR 报告或质量平台。
5. 最后指出：样例中部分更复杂的文档规则尚不能由当前 Rule DSL 执行，扩展能力仍有明确边界。
