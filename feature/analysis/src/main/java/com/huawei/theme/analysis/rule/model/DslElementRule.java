package com.huawei.theme.analysis.rule.model;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DslElementRule {
    String elementName;
    List<String> requiredAttrs;
    List<String> optionalAttrs;
    Map<String, AttrTypeSpec> attrTypes;
    List<String> allowedParents;
    List<String> allowedChildren;
    String inherits;
}
