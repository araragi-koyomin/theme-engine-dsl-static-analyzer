package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.shared.model.FixActionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClampValueGeneratorTest {

    private final ClampValueGenerator generator = new ClampValueGenerator();

    @Test
    void generatesClampValueFromSuggestedFixes() {
        DslAttributeValueNode alphaValue = new DslAttributeValueNode();
        alphaValue.setRawValue("300");
        alphaValue.setLiteral(true);

        DslAttributeNode alphaAttr = new DslAttributeNode();
        alphaAttr.setName("alpha");
        alphaAttr.setLine(10);
        alphaAttr.setColumn(15);
        alphaAttr.setValue(alphaValue);

        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Image");
        elementNode.setLine(10);
        elementNode.setColumn(3);
        elementNode.setAttributes(List.of(alphaAttr));
        elementNode.setChildElements(Collections.emptyList());

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-ATTR-001")
                .message("alpha值超出范围")
                .filePath("test.xml")
                .astNode(elementNode)
                .suggestedFixes(List.of(
                        SuggestedFix.builder().text("设置alpha值在0-255范围内").type("CLAMP_VALUE").target("alpha").range("0-255").build()))
                .build();

        List<FixAction> actions = generator.generate(diagnostic);

        assertEquals(1, actions.size());
        assertEquals(FixActionType.CLAMP_VALUE, actions.get(0).getFixType());
        assertEquals("alpha=\"255\"", actions.get(0).getReplacementText());
        assertEquals(10, actions.get(0).getTargetRange().getStartLine());
        assertEquals(15, actions.get(0).getTargetRange().getStartColumn());
    }

    @Test
    void returnsEmptyWhenNoSuggestedFixes() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Image");
        elementNode.setLine(10);
        elementNode.setColumn(3);
        elementNode.setAttributes(Collections.emptyList());
        elementNode.setChildElements(Collections.emptyList());

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-ATTR-001")
                .message("alpha值超出范围")
                .filePath("test.xml")
                .astNode(elementNode)
                .suggestedFixes(Collections.emptyList())
                .build();

        List<FixAction> actions = generator.generate(diagnostic);

        assertTrue(actions.isEmpty());
    }

    @Test
    void returnsEmptyWhenNoClampIntent() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Image");
        elementNode.setLine(10);
        elementNode.setColumn(3);
        elementNode.setAttributes(Collections.emptyList());
        elementNode.setChildElements(Collections.emptyList());

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-ATTR-001")
                .message("alpha值超出范围")
                .filePath("test.xml")
                .astNode(elementNode)
                .suggestedFixes(List.of(
                        SuggestedFix.builder().text("移除alpha属性").type("REMOVE_ATTR").target("alpha").build()))
                .build();

        List<FixAction> actions = generator.generate(diagnostic);

        assertTrue(actions.isEmpty());
    }

    @Test
    void getRuleIdReturnsSEM_ATTR_001() {
        assertEquals("SEM-ATTR-001", generator.getRuleId());
    }

    @Test
    void extractUpperBoundFromRange() {
        assertEquals("255", generator.extractUpperBound("0-255"));
    }

    @Test
    void extractUpperBoundFromSingleValue() {
        assertEquals("100", generator.extractUpperBound("100"));
    }

    @Test
    void extractUpperBoundFromNull() {
        assertEquals(null, generator.extractUpperBound(null));
    }
}
