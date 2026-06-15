package com.huawei.theme.analysis.rule.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RuleSource {
    String ruleId;
    String category;
    String description;
    String docUrl;
}
