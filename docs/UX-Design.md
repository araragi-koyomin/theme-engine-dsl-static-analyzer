---
module_ids: [CORE]
doc_kind: guide
status: active
created: 2026-06-15
---
# 主题引擎DSL静态分析工具 - UX设计文档

## 1. UX设计原则

- **原生感**：所有交互沿用IDEA原生视觉与操作习惯，降低学习成本
- **渐进披露**：默认展示精简信息，按需展开详情，避免信息过载
- **数据驱动一致性**：所有诊断与修复操作通过规则库统一驱动，交互模式一致

## 2. 交互入口总览

| 入口 | 交互类型 | 触发方式 | 覆盖范围 |
|---|---|---|---|
| 编辑器内标注 | 实时检测 | 编辑即触发 | 当前文件 |
| 悬浮提示 | 信息展示 | 鼠标悬停波浪线处 | 当前文件 |
| Quick Fix | 修复操作 | Alt+Enter / 点击波浪线 | 当前文件 |
| DSL诊断面板 | 批量展示 | 打开底部面板 | 项目级 |
| 右键菜单批量检查 | 批量检测 | 右键文件/目录/项目节点 | 指定范围 |
| 报告导出 | 导出操作 | 面板底部导出按钮 | 项目级 |

## 3. 文件识别体验

### 3.1 DSL文件视觉标识
- **项目树图标**：DSL文件在Project View中使用自定义图标，与普通XML文件图标区分
- **识别机制**：文件扩展名(.xml) + 根元素声明双重判定
- **静默识别**：无弹窗、无提示条，仅在项目树中通过图标区分

### 3.2 非DSL文件行为
- 普通XML文件不触发Theme Engine规则检查
- DSL规则检查仅对已识别的DSL文件生效

## 4. 编辑器内交互

### 4.1 错误标注（波浪线）

沿用IDEA原生诊断配色方案：

| 严重级别 | 波浪线颜色 | IDEA原生标识 |
|---|---|---|
| Error | 红色波浪线 | 与IDEA Error一致 |
| Warning | 黄色波浪线 | 与IDEA Warning一致 |
| Info | 蓝色波浪线 | 与IDEA Info一致 |

标注范围精确到具体元素/属性，错误元素和错误属性值分别标注。

### 4.2 悬浮提示（Hover Tooltip）

鼠标悬停在波浪线标注处时显示精简版悬浮提示：

```
┌─────────────────────────────────────────────┐
│  [图标] 未知元素: <UnknownComponnt>         │
│                                             │
│  建议修复: 替换为 <UnknownComponent>         │
│                                             │
│  规则: SEM-001 · 📎 查看规则文档             │
└─────────────────────────────────────────────┘
```

**内容规范：**
- **错误摘要**：一行描述错误类型和当前值
- **建议修复**：一行描述推荐修复动作
- **规则来源**：规则ID（如SEM-001）+ 可点击的规则文档链接

**不展示的内容**（避免信息过载）：
- 组件/属性的完整说明
- 规则置信度
- 详细枚举列表

### 4.3 Quick Fix交互

#### 4.3.1 无需确认的Quick Fix

触发方式：Alt+Enter 或点击波浪线标注

直接执行的修复类型：
- 补闭合标签
- 补属性引号
- 删除多余结束标签
- 插入必填属性占位值/默认值
- 数字/布尔/路径格式归一化

交互流程：
```
用户按下Alt+Enter → 展示Quick Fix列表 → 选择修复项 → 直接执行 → 文件更新
```

#### 4.3.2 需确认的Quick Fix（下拉选择列表）

需确认的修复类型：
- 替换为最接近合法组件名
- 替换为别名属性 / 删除属性 / 转为通用属性
- 单位换算或删除错误单位
- 替换为最接近合法枚举值
- clamp到合法范围

交互流程：
```
用户按下Alt+Enter → 展示Quick Fix列表 → 选择修复项
  → 弹出下拉候选列表
  → 每个候选项显示: 简要描述 + 预览图标
  → 用户选中某候选 → 展示diff预览
  → 用户确认 → 执行修复 → 文件更新
```

**下拉候选列表原型：**
```
┌──────────────────────────────────────┐
│  替换为 <Theme>              [预览] │
│  替换为 <Style>              [预览] │
│  替换为 <Color>              [预览] │
│  删除该元素                  [预览] │
└──────────────────────────────────────┘
```

**候选排序规则：**
- 优先级：完全匹配 > 编辑距离匹配 > 语义匹配
- 候选数量上限：5条，超出截断

**预览交互：**
用户点击候选项的[预览]按钮或选中后，在编辑器内以IDEA原生diff视图展示修复前后对比，确认后执行。

## 5. DSL诊断面板

### 5.1 面板位置与布局

- **位置**：IDEA底部工具窗口区域（与Problems视图同级）
- **标签名**：DSL Analysis
- **图标**：使用插件自定义图标

### 5.2 面板结构

```
┌──────────────────────────────────────────────────────────┐
│  DSL Analysis                                   [⚙] [×] │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  ▼ Errors (3)                                           │
│    ── UnknownComponent in theme.xml:15                   │
│    ── Missing required attr 'name' in theme.xml:22       │
│    ── Invalid enum value in theme.xml:30                 │
│                                                          │
│  ▼ Warnings (2)                                         │
│    ── Component not allowed here in layout.xml:8         │
│    ── Unit mismatch 'pt' expected 'dp' in layout.xml:14 │
│                                                          │
│  ▼ Info (1)                                             │
│    ── Deprecated attribute in theme.xml:18               │
│                                                          │
├──────────────────────────────────────────────────────────┤
│  [▶ Run Analysis]            [📥 Export ▾]  |  6 issues │
└──────────────────────────────────────────────────────────┤
```

### 5.3 面板交互

| 操作 | 行为 |
|---|---|---|
| 点击问题条目 | 编辑器跳转到对应代码行并高亮 |
| 双击问题条目 | 同单击行为 |
| 展开分组 | 点击分组头展开/折叠该级别所有问题 |
| 右键问题条目 | 弹出上下文菜单（Quick Fix / 复制 / 查看规则文档） |

### 5.4 面板底部工具栏

| 按钮 | 功能 |
|---|---|---|
| ▶ Run Analysis | 重新执行当前范围的分析 |
| 📥 Export ▾ | 点击展开导出格式选择：Markdown / JSON |

**导出格式选择下拉：**
```
┌─────────────────┐
│  Export as...   │
│  Markdown       │
│  JSON           │
└─────────────────┘
```

选择后生成报告文件保存到项目根目录，并通过IDEA通知气泡提示文件位置。

### 5.5 面板状态

| 状态 | 面板表现 |
|---|---|---|
| 无问题 | 显示"No DSL issues found" |
| 分析进行中 | 面板内容实时刷新，底部显示问题计数更新 |
| 未打开DSL文件 | 面板空置，提示"Open a DSL file to see analysis results" |

## 6. 批量检查交互

### 6.1 触发方式

右键菜单触发，在项目树中的不同节点右键：

| 右键目标 | 菜单项 | 检查范围 |
|---|---|---|
| DSL文件 | "Check DSL Rules" | 当前文件 |
| 目录 | "Check DSL Rules in Directory" | 目录下所有DSL文件 |
| 项目根节点 | "Check DSL Rules in Project" | 全项目DSL文件 |

### 6.2 执行进度

使用IDEA原生进度条机制：
- 底部状态栏显示进度条 + 百分比文字 + "Checking DSL rules..."
- 与IDEA原生Inspect Code进度体验一致
- 不可取消（分析任务短，≤5s/100文件）

### 6.3 结果展示

检查完成后：
- DSL诊断面板自动打开并刷新为检查结果
- 面板按严重级别分组展示所有发现的问题
- IDEA通知气泡提示检查完成摘要："Found X errors, Y warnings, Z info"

## 7. 快捷键方案

沿用IDEA原生快捷键，不新增自定义快捷键：

| 操作 | 快捷键 | 说明 |
|---|---|---|
| 触发Quick Fix | Alt+Enter | IDEA原生Show Intention Actions |
| 导航到下一个错误 | F2 / Shift+F2 | IDEA原生Next/Previous Error |
| 悬浮提示 | Ctrl+F1 (Windows) | IDEA原生Show Error Description |

## 8. 报告导出格式

### 8.1 Markdown报告示例

```markdown
# DSL Static Analysis Report
Generated: 2026-06-15 14:30

## Errors (3)

| # | File | Line:Col | Code | Message | Suggested Fix | Rule |
|---|---|---|---|---|---|---|
| 1 | theme.xml | 15:3 | SEM-001 | Unknown element `<UnknownComponnt>` | Replace with `<UnknownComponent>` | [SEM-001](https://dsl-docs.example.com/rules/SEM-001) |
| 2 | theme.xml | 22:1 | SEM-004 | Missing required attribute 'name' | Insert `name=""` | [SEM-004](https://dsl-docs.example.com/rules/SEM-004) |
| 3 | theme.xml | 30:5 | SEM-008 | Invalid enum value 'invalidType' | Replace with 'validType' | [SEM-008](https://dsl-docs.example.com/rules/SEM-008) |

## Warnings (2)
...

## Info (1)
...
```

### 8.2 JSON报告示例

```json
{
  "generated": "2026-06-15T14:30:00",
  "summary": { "errors": 3, "warnings": 2, "info": 1 },
  "issues": [
    {
      "severity": "error",
      "file": "theme.xml",
      "line": 15, "col": 3,
      "code": "SEM-001",
      "message": "Unknown element <UnknownComponnt>",
      "suggestedFix": "Replace with <UnknownComponent>",
      "ruleUrl": "https://dsl-docs.example.com/rules/SEM-001"
    }
  ]
}
```

## 9. 状态流转图

```
打开DSL文件
  │
  ├─→ 项目树图标变为DSL图标（静默）
  │
  ├─→ Annotator实时检测 → 波浪线标注
  │     │
  │     ├─→ 用户悬停 → 精简版Tooltip
  │     │     └─→ 点击规则链接 → 打开在线文档
  │     │
  │     └─→ 用户Alt+Enter → Quick Fix列表
  │           │
  │           ├─→ 无需确认类 → 直接执行
  │           │
  │           └─→ 需确认类 → 下拉候选列表
  │                 └─→ 选中候选 → diff预览 → 确认 → 执行
  │
  ├─→ DSL诊断面板同步展示当前文件问题
  │     │
  │     ├─→ 点击问题条目 → 编辑器跳转定位
  │     │
  │     ├─→ 右键问题条目 → Quick Fix / 查看规则文档
  │     │
  │     └─→ 底部导出按钮 → 选择格式 → 生成报告文件
  │
  └─→ 右键菜单批量检查
        │
        ├─→ IDEA原生进度条
        │
        └─→ 完成 → 面板刷新 → 通知气泡摘要
              └─→ 面板内查看/导出
```

## 10. 异常与边界场景

| 场景 | 处理方式 |
|---|---|
| 大文件编辑卡顿 | 异步后台分析，不阻塞编辑器；响应时间≤50ms |
| 规则库加载失败 | 面板显示"Rule library unavailable"，仅保留基础XML语法检测 |
| 无网络访问规则文档 | 规则链接正常展示，点击后浏览器打开但可能无法访问 |
| 修复冲突（多处同时修复） | 按代码位置从上到下依次执行，每处独立修复 |
| 非DSL XML文件误识别 | 双重识别机制过滤，不会误触发 |

## 11. 与IDEA原生功能的协调

| 原生功能 | DSL插件行为 |
|---|---|
| IDEA Problems视图 | DSL问题同时出现在Problems视图，DSL面板是专项聚合视图 |
| IDEA Inspect Code | DSL批量检查是独立功能，不与Inspect Code冲突 |
| IDEA XML编辑器 | 基础XML语法检测利用内置XML PSI API，不重复检测 |
| IDEA Code Format | Quick Fix不触发代码格式化，仅做最小修改 |
