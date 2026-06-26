package com.huawei.theme.analysis.core.ruledsl;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EvaluationContext {
    Map<String, String> elementAttrs;
    String elementName;
    String elementCategory;
    Map<String, Boolean> scope;
    Map<String, Boolean> deviceSupport;
}
