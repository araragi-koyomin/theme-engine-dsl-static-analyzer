package com.huawei.theme.analysis.core.rulecenter;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionCapabilityRegistryTest {

    private final ConditionCapabilityRegistry registry = new ConditionCapabilityRegistry();

    @Test
    void registersOnlyTheGrammarAndTwoEvaluatorExtensionsActuallyImplemented() {
        assertEquals(
                Set.of(
                        ConditionCapability.BASE_GRAMMAR,
                        ConditionCapability.CONTAINS_EXPRESSION,
                        ConditionCapability.CHILDREN_TAG_COUNT),
                registry.registeredCapabilities());
    }

    @Test
    void recognizesBaseGrammarAndContainsExpressionWithoutChangingBusinessOperands() {
        ConditionCapabilityAnalysis base = registry.analyze(
                "element.attrs['src'] != null AND element.attrs['srcExp'] != null");
        ConditionCapabilityAnalysis contains = registry.analyze(
                "element.attrs['index'] != null AND containsExpression(element.attrs['index'])");

        assertTrue(base.isExtensionShapeSupported());
        assertEquals(Set.of(ConditionCapability.BASE_GRAMMAR), base.getCapabilities());
        assertEquals(
                "element.attrs['src'] != null AND element.attrs['srcExp'] != null",
                base.getNormalizedCondition());
        assertTrue(contains.isExtensionShapeSupported());
        assertEquals(
                Set.of(ConditionCapability.BASE_GRAMMAR, ConditionCapability.CONTAINS_EXPRESSION),
                contains.getCapabilities());
        assertEquals(
                "element.attrs['index'] != null AND '1'=='1'",
                contains.getNormalizedCondition());
    }

    @Test
    void recognizesOnlyFixedChildrenWhereOrFilterTagNameSizeShape() {
        ConditionCapabilityAnalysis filter = registry.analyze(
                "element.children.filter(c -> c.tagName == 'Trigger').size() == 0");
        ConditionCapabilityAnalysis where = registry.analyze(
                "element.children.where(c -> c.tagName == 'StereoGroup').size() > 10");

        assertTrue(filter.isExtensionShapeSupported());
        assertTrue(where.isExtensionShapeSupported());
        assertEquals(
                Set.of(ConditionCapability.BASE_GRAMMAR, ConditionCapability.CHILDREN_TAG_COUNT),
                filter.getCapabilities());
        assertEquals("'1'=='1'", filter.getNormalizedCondition());
        assertEquals("'1'=='1'", where.getNormalizedCondition());
    }

    @Test
    void rejectsUnregisteredMethodsConversionsAndLambdaDialects() {
        assertUnsupported("element.attrs['src'].endsWith('.mp4')");
        assertUnsupported("number(element.attrs['duration']) <= 30");
        assertUnsupported("element.children.where(c => c.tagName == 'Trigger').count() == 0");
        assertUnsupported("element.parent.children.filter(c -> c.tagName == 'Button').size() == 0");
    }

    @Test
    void rejectsNullBlankAndPartiallyMatchedBuiltIns() {
        assertUnsupported(null);
        assertUnsupported("");
        assertUnsupported("containsExpression(element.attrs[index])");
        assertUnsupported("element.children.filter(c -> c.attrs['action'] == 'pause').size() == 0");
    }

    private void assertUnsupported(String condition) {
        ConditionCapabilityAnalysis analysis = registry.analyze(condition);

        assertFalse(analysis.isExtensionShapeSupported(), condition);
        assertEquals(ConditionCapabilityRejection.UNREGISTERED_EXTENSION, analysis.getRejection());
    }
}
