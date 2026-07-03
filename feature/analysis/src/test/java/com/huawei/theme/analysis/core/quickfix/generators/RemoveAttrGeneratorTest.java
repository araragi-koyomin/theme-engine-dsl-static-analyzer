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

class RemoveAttrGeneratorTest {

    private final RemoveAttrGenerator generator = new RemoveAttrGenerator();

    @Test
    void generatesRemoveAttrFromSuggestedFixes() {
        DslAttributeValueNode playValue = new DslAttributeValueNode();
        playValue.setRawValue("true");
        playValue.setLiteral(true);

        DslAttributeNode playAttr = new DslAttributeNode();
        playAttr.setName("play");
        playAttr.setLine(8);
        playAttr.setColumn(10);
        playAttr.setValue(playValue);

        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("VideoCommand");
        elementNode.setLine(8);
        elementNode.setColumn(3);
        elementNode.setAttributes(List.of(playAttr));
        elementNode.setChildElements(Collections.emptyList());

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-CMD-001")
                .message("禁止的属性")
                .filePath("test.xml")
                .astNode(elementNode)
                .suggestedFixes(List.of(
                        SuggestedFix.builder().text("移除play属性").type("REMOVE_ATTR").target("play").build(),
                        SuggestedFix.builder().text("移除sound属性").type("REMOVE_ATTR").target("sound").build()))
                .build();

        List<FixAction> actions = generator.generate(diagnostic);

        assertEquals(2, actions.size());
        assertEquals(FixActionType.REMOVE_ATTR, actions.get(0).getFixType());
        assertEquals(FixActionType.REMOVE_ATTR, actions.get(1).getFixType());
        assertTrue(actions.get(0).getDescription().contains("play"));
        assertTrue(actions.get(1).getDescription().contains("sound"));
        assertEquals("", actions.get(0).getReplacementText());
        assertEquals("", actions.get(1).getReplacementText());
    }

    @Test
    void skipsNonRemoveAttrIntents() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Image");
        elementNode.setLine(5);
        elementNode.setColumn(1);
        elementNode.setAttributes(Collections.emptyList());
        elementNode.setChildElements(Collections.emptyList());

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-REQ-001")
                .message("缺少属性")
                .filePath("test.xml")
                .astNode(elementNode)
                .suggestedFixes(List.of(
                        SuggestedFix.builder().text("添加src属性指定图片路径").type("ADD_ATTR").target("src").build()))
                .build();

        List<FixAction> actions = generator.generate(diagnostic);

        assertTrue(actions.isEmpty());
    }

    @Test
    void getRuleIdReturnsSEM_CMD_001() {
        assertEquals("SEM-CMD-001", generator.getRuleId());
    }

    @Test
    void returnsEmptyWhenAstNodeIsNotDslElementNode() {
        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-CMD-001")
                .message("test")
                .filePath("test.xml")
                .line(10)
                .column(5)
                .suggestedFixes(List.of(
                        SuggestedFix.builder().text("移除play属性").type("REMOVE_ATTR").target("play").build()))
                .build();

        List<FixAction> actions = generator.generate(diagnostic);

        assertTrue(actions.isEmpty());
    }

    @Test
    void returnsEmptyWhenSuggestedFixesIsNull() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("VideoCommand");
        elementNode.setLine(8);
        elementNode.setColumn(3);
        elementNode.setAttributes(Collections.emptyList());
        elementNode.setChildElements(Collections.emptyList());

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-CMD-001")
                .message("test")
                .filePath("test.xml")
                .astNode(elementNode)
                .suggestedFixes(null)
                .build();

        List<FixAction> actions = generator.generate(diagnostic);

        assertTrue(actions.isEmpty());
    }

    @Test
    void usesElementPositionWhenAttributeNotFound() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("VideoCommand");
        elementNode.setLine(8);
        elementNode.setColumn(3);
        elementNode.setAttributes(Collections.emptyList());
        elementNode.setChildElements(Collections.emptyList());

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-CMD-001")
                .message("禁止的属性")
                .filePath("test.xml")
                .astNode(elementNode)
                .suggestedFixes(List.of(
                        SuggestedFix.builder().text("移除sound属性").type("REMOVE_ATTR").target("sound").build()))
                .build();

        List<FixAction> actions = generator.generate(diagnostic);

        assertEquals(1, actions.size());
        assertEquals(FixActionType.REMOVE_ATTR, actions.get(0).getFixType());
        assertEquals(8, actions.get(0).getTargetRange().getStartLine());
        assertEquals(3, actions.get(0).getTargetRange().getStartColumn());
        assertTrue(actions.get(0).getDescription().contains("sound"));
    }
}
