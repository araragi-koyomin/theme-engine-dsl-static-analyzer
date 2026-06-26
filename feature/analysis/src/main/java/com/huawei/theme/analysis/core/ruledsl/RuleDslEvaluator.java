package com.huawei.theme.analysis.core.ruledsl;

public interface RuleDslEvaluator {
    boolean evaluate(String condition, EvaluationContext context);
}
