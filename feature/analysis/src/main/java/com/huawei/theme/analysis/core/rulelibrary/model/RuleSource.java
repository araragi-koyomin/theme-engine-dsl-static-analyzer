package com.huawei.theme.analysis.core.rulelibrary.model;

import lombok.Builder;
import lombok.Data;

/**
 * 规则来源追溯条目，将规则ID映射到官方规范文档URL。
 *
 * <p>PRD要求每条诊断附带规则来源和文档链接（ruleDocUrl字段）。
 * 此模型提供ruleId→docUrl的独立映射表，以数据形式存储而非硬编码在各Analyzer中，
 * 支持零代码扩展——新增规则只需追加rule_sources.json条目。</p>
 *
 * <p>消费方：Diagnostic.ruleDocUrl字段、IDEA悬浮提示（规则文档链接）、CLI --verbose输出。</p>
 */
@Data
@Builder
public class RuleSource {
    /** 规则唯一标识，与RuleConstraint.ruleId/Diagnostic.ruleId一致 */
    String ruleId;
    /** 规则分类：SYN(语法类)/SEM(语义类)。便于按类别筛选和分组展示 */
    String category;
    /** 规则的一句话描述，IDEA悬浮提示可展示 */
    String description;
    /** 指向华为开发者官网规范页面URL，来源于DSL-Rule-Spec §7.2的URL映射 */
    String docUrl;
}
