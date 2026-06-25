package com.huawei.theme.analysis.core.ruledsl;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultRuleDslEvaluatorTest {

    private boolean evaluate(String condition, EvaluationContext context) {
        DefaultRuleDslEvaluator evaluator = new DefaultRuleDslEvaluator();
        return evaluator.evaluate(condition, context);
    }

    @Test
    void nullCheckAttributeExists() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("play", "true"))
                .elementName("Animation")
                .build();
        assertTrue(evaluate("element.attrs['play'] != null", ctx));
    }

    @Test
    void nullCheckAttributeAbsent() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("play", "true"))
                .elementName("Animation")
                .build();
        assertFalse(evaluate("element.attrs['sound'] != null", ctx));
    }

    @Test
    void nullEqualsNullAttributeAbsent() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("play", "1"))
                .elementName("Animation")
                .build();
        assertTrue(evaluate("element.attrs['nonexistent'] == null", ctx));
    }

    @Test
    void nullNotEqualsNullBothNull() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("play", "1"))
                .elementName("Animation")
                .build();
        assertFalse(evaluate("element.attrs['nonexistent'] != null", ctx));
    }

    @Test
    void stringEqualityMatch() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("clip", "true"))
                .elementName("Animation")
                .build();
        assertTrue(evaluate("element.attrs['clip'] == 'true'", ctx));
    }

    @Test
    void stringEqualityNotMatch() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("clip", "false"))
                .elementName("Animation")
                .build();
        assertFalse(evaluate("element.attrs['clip'] == 'true'", ctx));
    }

    @Test
    void andConditionBothPresent() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("play", "1", "sound", "2"))
                .elementName("Animation")
                .build();
        assertTrue(evaluate("element.attrs['play'] != null AND element.attrs['sound'] != null", ctx));
    }

    @Test
    void andConditionOneAbsent() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("play", "1"))
                .elementName("Animation")
                .build();
        assertFalse(evaluate("element.attrs['play'] != null AND element.attrs['sound'] != null", ctx));
    }

    @Test
    void orConditionLeftFalseRightTrue() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("loop", "true"))
                .elementName("Animation")
                .build();
        assertTrue(evaluate("element.attrs['loop'] != 'true' OR element.attrs['unlockTo'] == null", ctx));
    }

    @Test
    void parenthesizedOrWithinAnd() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("direction", "0", "loop", "true"))
                .elementName("Animation")
                .build();
        assertTrue(evaluate("element.attrs['direction'] == '0' AND (element.attrs['loop'] != 'true' OR element.attrs['unlockTo'] == null)", ctx));
    }

    @Test
    void notCondition() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("play", "1"))
                .elementName("Animation")
                .build();
        assertTrue(evaluate("NOT element.attrs['play'] == null", ctx));
    }

    @Test
    void notConditionNegatesTrue() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("play", "1"))
                .elementName("Animation")
                .build();
        assertFalse(evaluate("NOT element.attrs['play'] != null", ctx));
    }

    @Test
    void inConditionMatch() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("type", "date"))
                .elementName("Var")
                .build();
        assertTrue(evaluate("element.attrs['type'] IN ['time','date','week']", ctx));
    }

    @Test
    void inConditionNotInSet() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("type", "other"))
                .elementName("Var")
                .build();
        assertFalse(evaluate("element.attrs['type'] IN ['time','date','week']", ctx));
    }

    @Test
    void notInConditionNotInSet() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("action", "hover"))
                .elementName("Var")
                .build();
        assertTrue(evaluate("element.attrs['action'] NOT IN ['down','up','click']", ctx));
    }

    @Test
    void notInConditionInSet() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("action", "down"))
                .elementName("Var")
                .build();
        assertFalse(evaluate("element.attrs['action'] NOT IN ['down','up','click']", ctx));
    }

    @Test
    void tagNameMatch() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of())
                .elementName("Var")
                .build();
        assertTrue(evaluate("element.tagName == 'Var'", ctx));
    }

    @Test
    void tagNameNotMatch() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of())
                .elementName("Image")
                .build();
        assertFalse(evaluate("element.tagName == 'Var'", ctx));
    }

    @Test
    void numericComparisonGreater() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("intensity", "3"))
                .elementName("Animation")
                .build();
        assertTrue(evaluate("element.attrs['intensity'] > 1", ctx));
    }

    @Test
    void numericComparisonNotGreater() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("intensity", "0.5"))
                .elementName("Animation")
                .build();
        assertFalse(evaluate("element.attrs['intensity'] > 1", ctx));
    }

    @Test
    void numericComparisonGeq() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("intensity", "2"))
                .elementName("Animation")
                .build();
        assertTrue(evaluate("element.attrs['intensity'] >= 2", ctx));
    }

    @Test
    void numericComparisonLeq() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("intensity", "1"))
                .elementName("Animation")
                .build();
        assertTrue(evaluate("element.attrs['intensity'] <= 1", ctx));
    }

    @Test
    void unparseableConditionReturnsFalse() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("time", "10"))
                .elementName("Var")
                .build();
        assertFalse(evaluate("parseInt(element.attrs['time']) < 1", ctx));
    }

    @Test
    void andOrPrecedenceOrTrue() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("a", "1"))
                .elementName("Var")
                .build();
        assertTrue(evaluate("element.attrs['a'] != null OR element.attrs['b'] != null AND element.attrs['c'] != null", ctx));
    }

    @Test
    void andOrPrecedenceAllAbsent() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of())
                .elementName("Var")
                .build();
        assertFalse(evaluate("element.attrs['a'] != null OR element.attrs['b'] != null AND element.attrs['c'] != null", ctx));
    }

    @Test
    void inConditionNullValue() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("other", "x"))
                .elementName("Var")
                .build();
        assertFalse(evaluate("element.attrs['type'] IN ['time','date','week']", ctx));
    }

    @Test
    void notInConditionNullValue() {
        EvaluationContext ctx = EvaluationContext.builder()
                .elementAttrs(Map.of("other", "x"))
                .elementName("Var")
                .build();
        assertTrue(evaluate("element.attrs['type'] NOT IN ['time','date','week']", ctx));
    }
}
