package com.huawei.theme.analysis.core.ruledsl;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvaluationContextTest {

    @Test
    void evaluationContextBuilder() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementName("Var")
                .elementCategory("variable")
                .elementAttrs(Map.of("name", "steps_value", "type", "number"))
                .scope(Map.of("Lockscreen", true, "Widget", false))
                .deviceSupport(Map.of("barPhone", true))
                .build();
        assertEquals("Var", ctx.getElementName());
        assertEquals("variable", ctx.getElementCategory());
        assertEquals(2, ctx.getElementAttrs().size());
        assertEquals("number", ctx.getElementAttrs().get("type"));
    }
}
