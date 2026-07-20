package com.huawei.theme.analysis.core.rulecenter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictConditionAcceptorTest {

    private final StrictConditionAcceptor acceptor = new StrictConditionAcceptor(
            new ConditionCapabilityRegistry());

    @Test
    void acceptsCompleteBaseGrammarAndReturnsNormalizedCondition() {
        String condition = "element.attrs['src'] != null AND element.attrs['srcExp'] != null";

        ConditionAcceptance acceptance = acceptor.accept(condition);

        assertTrue(acceptance.isAccepted());
        assertEquals(ConditionAcceptanceStatus.ACCEPTED, acceptance.getStatus());
        assertEquals(condition, acceptance.getNormalizedCondition());
        assertTrue(acceptance.getSyntaxErrors().isEmpty());
    }

    @Test
    void acceptsRegisteredEvaluatorExtensionsAfterStrictNormalization() {
        ConditionAcceptance contains = acceptor.accept(
                "containsExpression(element.attrs['index'])");
        ConditionAcceptance children = acceptor.accept(
                "element.children.filter(c -> c.tagName == 'Trigger').size() == 0");

        assertTrue(contains.isAccepted());
        assertEquals("'1'=='1'", contains.getNormalizedCondition());
        assertTrue(children.isAccepted());
        assertEquals("'1'=='1'", children.getNormalizedCondition());
    }

    @Test
    void rejectsUnsupportedGrammarWithStableReasonCode() {
        assertUnsupported("element.attrs['src'].endsWith('.mp4')");
        assertUnsupported("number(element.attrs['duration']) <= 30");
        assertUnsupported("sin(element.attrs['x']) > 0");
        assertUnsupported("strContains(element.attrs['src'], '.mp4')");
    }

    @Test
    void rejectsIncompleteAndTrailingInputInsteadOfAcceptingAValidPrefix() {
        ConditionAcceptance incomplete = acceptor.accept("element.attrs['src'] !=");
        ConditionAcceptance trailing = acceptor.accept(
                "element.attrs['src'] != null element.attrs['other'] == null");
        ConditionAcceptance brokenExtension = acceptor.accept(
                "containsExpression(element.attrs['src']) AND broken");

        assertFalse(incomplete.isAccepted());
        assertFalse(trailing.isAccepted());
        assertFalse(brokenExtension.isAccepted());
        assertFalse(incomplete.getSyntaxErrors().isEmpty());
        assertFalse(trailing.getSyntaxErrors().isEmpty());
        assertFalse(brokenExtension.getSyntaxErrors().isEmpty());
    }

    @Test
    void acceptsSyntacticallyValidConditionEvenWhenItCouldEvaluateFalse() {
        ConditionAcceptance acceptance = acceptor.accept("element.attrs['missing'] != null");

        assertTrue(acceptance.isAccepted());
        assertEquals(ConditionAcceptanceStatus.ACCEPTED, acceptance.getStatus());
    }

    private void assertUnsupported(String condition) {
        ConditionAcceptance acceptance = acceptor.accept(condition);

        assertFalse(acceptance.isAccepted(), condition);
        assertEquals(
                ConditionAcceptanceStatus.UNSUPPORTED_CONDITION_GRAMMAR,
                acceptance.getStatus());
        assertFalse(acceptance.getSyntaxErrors().isEmpty());
    }
}
