package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.quickfix.CandidateItem;
import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionGenerator;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.shared.model.FixActionType;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplaceEnumGeneratorTest {

    private final RuleRepository mockRepo = new RuleRepository() {
        @Override
        public Optional<AttrTypeSpec> getAttrTypeSpec(String elementName, String attrName) {
            if ("Text".equals(elementName) && "category".equals(attrName)) {
                return Optional.of(AttrTypeSpec.builder()
                        .type("string")
                        .enumValues(List.of("Normal", "Charging", "BatteryLow", "BatteryFull"))
                        .build());
            }
            return Optional.empty();
        }

        @Override
        public Optional<com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule> getElementRule(String elementName) {
            return Optional.empty();
        }

        @Override
        public List<com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule> getAllElementRules() {
            return Collections.emptyList();
        }

        @Override
        public List<String> getAllElementNames() {
            return Collections.emptyList();
        }

        @Override
        public List<String> getRootElementNames() {
            return Collections.emptyList();
        }

        @Override
        public Optional<String> resolveAttrAlias(String elementName, String attrName) {
            return Optional.empty();
        }

        @Override
        public java.util.Set<String> getCanonicalAttrNames(String elementName) {
            return Collections.emptySet();
        }

        @Override
        public List<String> getAllowedParents(String elementName) {
            return Collections.emptyList();
        }

        @Override
        public List<String> getAllowedChildren(String elementName) {
            return Collections.emptyList();
        }

        @Override
        public List<com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint> getConstraints(String elementName) {
            return Collections.emptyList();
        }

        @Override
        public Optional<com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar> getGlobalVar(String varName) {
            return Optional.empty();
        }

        @Override
        public List<com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar> getAllGlobalVars() {
            return Collections.emptyList();
        }

        @Override
        public Optional<com.huawei.theme.analysis.core.rulelibrary.model.RuleSource> getRuleSource(String ruleId) {
            return Optional.empty();
        }
    };

    @Test
    void generatesFixActionWithCandidatesForEnumError() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Text");
        elementNode.setLine(10);
        elementNode.setColumn(5);
        elementNode.setAttributes(new java.util.ArrayList<>());

        DslAttributeNode attrNode = new DslAttributeNode();
        attrNode.setName("category");
        attrNode.setLine(10);
        attrNode.setColumn(5);
        attrNode.setParent(elementNode);

        DslAttributeValueNode valueNode = new DslAttributeValueNode();
        valueNode.setRawValue("InvalidValue");
        attrNode.setValue(valueNode);

        elementNode.getAttributes().add(attrNode);

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-ENUM-001")
                .message("枚举值错误: category=InvalidValue")
                .filePath("test.xml")
                .astNode(attrNode)
                .build();

        FixActionGenerator generator = new ReplaceEnumGenerator(mockRepo);
        List<FixAction> actions = generator.generate(diagnostic);

        assertEquals(1, actions.size());
        FixAction action = actions.get(0);
        assertEquals(FixActionType.REPLACE_ENUM, action.getFixType());
        assertEquals(4, action.getCandidates().size());

        CandidateItem firstCandidate = action.getCandidates().get(0);
        assertEquals("替换为 Normal", firstCandidate.getDescription());
        assertEquals("category=\"Normal\"", firstCandidate.getPreviewText());
    }

    @Test
    void returnsEmptyWhenAstNodeIsNotAttributeOrElement() {
        DslAstNode otherNode = new DslAttributeValueNode();
        otherNode.setLine(1);
        otherNode.setColumn(1);

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-ENUM-001")
                .message("枚举值错误")
                .filePath("test.xml")
                .astNode(otherNode)
                .build();

        FixActionGenerator generator = new ReplaceEnumGenerator(mockRepo);
        List<FixAction> actions = generator.generate(diagnostic);
        assertTrue(actions.isEmpty());
    }

    @Test
    void returnsEmptyWhenAttributeHasNoParentElement() {
        DslAttributeNode attrNode = new DslAttributeNode();
        attrNode.setName("category");
        attrNode.setLine(1);
        attrNode.setColumn(1);

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-ENUM-001")
                .message("枚举值错误: category=InvalidValue")
                .filePath("test.xml")
                .astNode(attrNode)
                .build();

        FixActionGenerator generator = new ReplaceEnumGenerator(mockRepo);
        List<FixAction> actions = generator.generate(diagnostic);
        assertTrue(actions.isEmpty());
    }
}
