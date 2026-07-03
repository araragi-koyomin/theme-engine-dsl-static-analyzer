package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
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

class ConstraintFixGeneratorTest {

    private ConstraintFixGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ConstraintFixGenerator();
    }

    @Test
    void generatesAddAttrFromSuggestedFixes() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Image");
        elementNode.setLine(5);
        elementNode.setColumn(1);
        elementNode.setAttributes(Collections.emptyList());
        elementNode.setChildElements(Collections.emptyList());

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-IMG-SRC")
                .message("Image缺少src属性")
                .filePath("test.xml")
                .astNode(elementNode)
                .suggestedFixes(List.of(
                        SuggestedFix.builder().text("添加src属性指定图片路径").type("ADD_ATTR").target("src").value("指定图片路径").build(),
                        SuggestedFix.builder().text("添加srcExp属性指定图片源表达式").type("ADD_ATTR").target("srcExp").value("指定图片源表达式").build()))
                .build();

        List<FixAction> actions = generator.generate(diagnostic);

        assertEquals(2, actions.size());
        assertEquals(FixActionType.ADD_ATTR, actions.get(0).getFixType());
        assertEquals(FixActionType.ADD_ATTR, actions.get(1).getFixType());
        assertEquals("src=\"指定图片路径\"", actions.get(0).getReplacementText());
        assertEquals("srcExp=\"指定图片源表达式\"", actions.get(1).getReplacementText());
    }

    @Test
    void generatesSetValueFromSuggestedFixes() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Image");
        elementNode.setLine(12);
        elementNode.setColumn(3);
        elementNode.setAttributes(Collections.emptyList());
        elementNode.setChildElements(Collections.emptyList());

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-ATTR-005")
                .message("scaleType值不合法")
                .filePath("test.xml")
                .astNode(elementNode)
                .suggestedFixes(List.of(
                        SuggestedFix.builder().text("设置scaleType=center_crop").type("SET_VALUE").target("scaleType").value("center_crop").build()))
                .build();

        List<FixAction> actions = generator.generate(diagnostic);

        assertEquals(1, actions.size());
        assertEquals(FixActionType.SET_VALUE, actions.get(0).getFixType());
        assertEquals("scaleType=\"center_crop\"", actions.get(0).getReplacementText());
        assertEquals(12, actions.get(0).getTargetRange().getStartLine());
    }

    @Test
    void generatesClampValueFromSuggestedFixes() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Group");
        elementNode.setLine(10);
        elementNode.setColumn(1);
        elementNode.setAttributes(Collections.emptyList());
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
    }

    @Test
    void skipsWhenNoSuggestedFixes() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Image");
        elementNode.setLine(5);
        elementNode.setColumn(1);
        elementNode.setAttributes(Collections.emptyList());
        elementNode.setChildElements(Collections.emptyList());

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-IMG-SRC")
                .message("Image缺少src属性")
                .filePath("test.xml")
                .astNode(elementNode)
                .suggestedFixes(Collections.emptyList())
                .build();

        List<FixAction> actions = generator.generate(diagnostic);

        assertTrue(actions.isEmpty());
    }

    @Test
    void generatesRemoveAttrFromSuggestedFixes() {
        DslAttributeNode playAttr = new DslAttributeNode();
        playAttr.setName("play");
        playAttr.setLine(20);
        playAttr.setColumn(10);
        playAttr.setValue(new DslAttributeValueNode());

        DslAttributeNode soundAttr = new DslAttributeNode();
        soundAttr.setName("sound");
        soundAttr.setLine(21);
        soundAttr.setColumn(5);
        soundAttr.setValue(new DslAttributeValueNode());

        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("VideoCommand");
        elementNode.setLine(18);
        elementNode.setColumn(1);
        elementNode.setAttributes(List.of(playAttr, soundAttr));
        elementNode.setChildElements(Collections.emptyList());

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-CMD-002")
                .message("VideoCommand不允许play和sound属性")
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
        assertEquals("", actions.get(0).getReplacementText());
        assertEquals("", actions.get(1).getReplacementText());
        assertEquals(20, actions.get(0).getTargetRange().getStartLine());
        assertEquals(10, actions.get(0).getTargetRange().getStartColumn());
        assertEquals(21, actions.get(1).getTargetRange().getStartLine());
        assertEquals(5, actions.get(1).getTargetRange().getStartColumn());
    }
}
