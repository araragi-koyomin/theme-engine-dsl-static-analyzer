package com.huawei.theme.analysis.rule.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AttrTypeSpec {
    String type;
    List<String> enumValues;
    List<String> aliases;
}
