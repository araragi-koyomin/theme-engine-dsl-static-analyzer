package com.huawei.theme.analysis.syntax;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DslSyntaxAnnotatorTest {

    private DslSyntaxAnnotator annotator;

    @BeforeEach
    void setUp() {
        annotator = new DslSyntaxAnnotator();
    }

    @Test
    void syn001_ideaMessageTagNotClosed() {
        assertEquals(DslSyntaxConstants.SYN_001,
                annotator.mapErrorToRuleId("Tag '<Lockscreen>' is not closed"));
    }

    @Test
    void syn001_ideaMessageElementNotClosed() {
        assertEquals(DslSyntaxConstants.SYN_001,
                annotator.mapErrorToRuleId("Element is not closed"));
    }

    @Test
    void syn001_ideaMessageExpectedEndTag() {
        assertEquals(DslSyntaxConstants.SYN_001,
                annotator.mapErrorToRuleId("Expected: end tag </Lockscreen>"));
    }

    @Test
    void syn001_ideaMessageUnclosed() {
        assertEquals(DslSyntaxConstants.SYN_001,
                annotator.mapErrorToRuleId("Unclosed tag found"));
    }

    @Test
    void syn001_caseInsensitive_uppercaseInputToLowercased() {
        String original = "TAG IS NOT CLOSED";
        assertEquals(DslSyntaxConstants.SYN_001,
                annotator.mapErrorToRuleId(original.toLowerCase(Locale.ENGLISH)));
    }

    @Test
    void syn001_caseInsensitive_mixedCaseInputToLowercased() {
        String original = "Element Is Not Closed";
        assertEquals(DslSyntaxConstants.SYN_001,
                annotator.mapErrorToRuleId(original.toLowerCase(Locale.ENGLISH)));
    }

    @Test
    void syn003_ideaMessageAttributeQuoted() {
        assertEquals(DslSyntaxConstants.SYN_003,
                annotator.mapErrorToRuleId("Attribute value must be quoted"));
    }

    @Test
    void syn003_ideaMessageQuotation() {
        assertEquals(DslSyntaxConstants.SYN_003,
                annotator.mapErrorToRuleId("Missing quotation marks around attribute value"));
    }

    @Test
    void syn003_containsQuoteKeyword() {
        assertEquals(DslSyntaxConstants.SYN_003,
                annotator.mapErrorToRuleId("unquoted attribute value"));
    }

    @Test
    void syn003_caseInsensitiveInputToLowercased() {
        String original = "ATTRIBUTE VALUE MUST BE QUOTED";
        assertEquals(DslSyntaxConstants.SYN_003,
                annotator.mapErrorToRuleId(original.toLowerCase(Locale.ENGLISH)));
    }

    @Test
    void syn002_containsNesting() {
        assertEquals(DslSyntaxConstants.SYN_002,
                annotator.mapErrorToRuleId("Invalid nesting of elements"));
    }

    @Test
    void syn002_containsNested() {
        assertEquals(DslSyntaxConstants.SYN_002,
                annotator.mapErrorToRuleId("Incorrectly nested tag structure"));
    }

    @Test
    void precedence_closedKeywordTakesPriorityOverQuote() {
        String desc = "Element is not closed because attribute value must be quoted";
        assertEquals(DslSyntaxConstants.SYN_001,
                annotator.mapErrorToRuleId(desc.toLowerCase()));
    }

    @Test
    void precedence_quoteKeywordBeforeNesting() {
        String desc = "Invalid nesting: attribute value must be quoted";
        assertEquals(DslSyntaxConstants.SYN_003,
                annotator.mapErrorToRuleId(desc.toLowerCase()));
    }

    @Test
    void falsePositive_enclosedShouldMatchSyn001() {
        assertEquals(DslSyntaxConstants.SYN_001,
                annotator.mapErrorToRuleId("tag is enclosed in brackets"));
    }

    @Test
    void falsePositive_disclosedShouldMatchSyn001() {
        assertEquals(DslSyntaxConstants.SYN_001,
                annotator.mapErrorToRuleId("information has been disclosed"));
    }

    @Test
    void unknownError_unexpectedToken() {
        assertNull(annotator.mapErrorToRuleId("unexpected token"));
    }

    @Test
    void unknownError_xmlProcessingError() {
        assertNull(annotator.mapErrorToRuleId("xml processing error"));
    }

    @Test
    void edgeCase_emptyString() {
        assertNull(annotator.mapErrorToRuleId(""));
    }

    @Test
    void edgeCase_singleWordClosed() {
        assertEquals(DslSyntaxConstants.SYN_001,
                annotator.mapErrorToRuleId("closed"));
    }

    @Test
    void edgeCase_singleWordQuote() {
        assertEquals(DslSyntaxConstants.SYN_003,
                annotator.mapErrorToRuleId("quote"));
    }

    @Test
    void edgeCase_singleWordNesting() {
        assertEquals(DslSyntaxConstants.SYN_002,
                annotator.mapErrorToRuleId("nesting"));
    }

    @Test
    void annotationFormat_shouldCombineRuleIdWithOriginalDescription() {
        String ruleId = DslSyntaxConstants.SYN_001;
        String originalDesc = "Tag is not closed";
        String formatted = ruleId + ": " + originalDesc;
        assertEquals("SYN-001: Tag is not closed", formatted);
    }
}
