package com.huawei.theme.analysis.rule.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleSourceTest {

    @Test
    void builder_shouldCreateRuleSource_withAllFields() {
        RuleSource source = RuleSource.builder()
                .ruleId("SEM-PERSIST-001")
                .category("SEM")
                .description("禁止对时间/日期/星期变量使用persist")
                .docUrl("https://dsl-docs.example.com/rules/SEM-PERSIST-001")
                .build();
        assertEquals("SEM-PERSIST-001", source.getRuleId());
        assertEquals("SEM", source.getCategory());
        assertEquals("禁止对时间/日期/星期变量使用persist", source.getDescription());
        assertEquals("https://dsl-docs.example.com/rules/SEM-PERSIST-001", source.getDocUrl());
    }

    @Test
    void data_shouldGenerateEqualsAndHashCode() {
        RuleSource source1 = RuleSource.builder()
                .ruleId("SYN-001").category("SYN")
                .description("XML标签未闭合")
                .docUrl("https://dsl-docs.example.com/rules/SYN-001")
                .build();
        RuleSource source2 = RuleSource.builder()
                .ruleId("SYN-001").category("SYN")
                .description("XML标签未闭合")
                .docUrl("https://dsl-docs.example.com/rules/SYN-001")
                .build();
        assertEquals(source1, source2);
    }
}
