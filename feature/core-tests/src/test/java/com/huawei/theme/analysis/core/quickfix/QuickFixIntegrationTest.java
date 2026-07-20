package com.huawei.theme.analysis.core.quickfix;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;
import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.shared.model.FixActionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickFixIntegrationTest {

    private MockRuleRepository mockRepo;

    @BeforeEach
    void setUp() {
        FixActionRegistry.clear();
        mockRepo = new MockRuleRepository();
        FixActionRegistry.init(mockRepo);
    }

    @AfterEach
    void tearDown() {
        FixActionRegistry.clear();
    }

    @Test
    void semReq001ProducesInsertAttr() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Var");
        elementNode.setLine(5);
        elementNode.setColumn(1);
        elementNode.setAttributes(Collections.emptyList());

        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-REQ-001")
                .message("缺失必填属性: name")
                .filePath("test.xml")
                .astNode(elementNode)
                .build();

        QuickFixProvider provider = new QuickFixProviderImpl();
        List<FixAction> actions = provider.getFixActions(diag);

        assertFalse(actions.isEmpty());
        assertEquals(FixActionType.ADD_ATTR, actions.get(0).getFixType());
        assertTrue(actions.get(0).getReplacementText().contains("name"));
    }

    @Test
    void constraintFallbackProducesFixAction() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("VideoCommand");
        elementNode.setLine(8);
        elementNode.setColumn(3);

        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-CMD-001")
                .message("约束冲突")
                .filePath("test.xml")
                .astNode(elementNode)
                .suggestedFixes(List.of(
                        SuggestedFix.builder().text("移除play属性").type("REMOVE_ATTR").target("play").build(),
                        SuggestedFix.builder().text("移除sound属性").type("REMOVE_ATTR").target("sound").build()))
                .build();

        QuickFixProvider provider = new QuickFixProviderImpl();
        List<FixAction> actions = provider.getFixActions(diag);

        assertFalse(actions.isEmpty());
    }

    @Test
    void synExpr001ProducesFixExpression() {
        DslAttributeNode attrNode = new DslAttributeNode();
        attrNode.setName("x");
        attrNode.setLine(5);
        attrNode.setColumn(10);
        DslAttributeValueNode valueNode = new DslAttributeValueNode();
        valueNode.setRawValue("-#steps");
        attrNode.setValue(valueNode);

        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SYN-EXPR-001")
                .message("表达式错误")
                .filePath("test.xml")
                .astNode(attrNode)
                .build();

        QuickFixProvider provider = new QuickFixProviderImpl();
        List<FixAction> actions = provider.getFixActions(diag);

        assertFalse(actions.isEmpty());
        assertEquals(FixActionType.FIX_EXPRESSION, actions.get(0).getFixType());
    }

    @Test
    void semEnum001ProducesReplaceEnum() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Text");
        elementNode.setLine(10);
        elementNode.setColumn(3);

        DslAttributeNode categoryAttr = new DslAttributeNode();
        categoryAttr.setName("category");
        categoryAttr.setLine(10);
        categoryAttr.setColumn(15);
        DslAttributeValueNode valueNode = new DslAttributeValueNode();
        valueNode.setRawValue("InvalidValue");
        categoryAttr.setValue(valueNode);
        categoryAttr.setParent(elementNode);
        elementNode.setAttributes(List.of(categoryAttr));

        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-ENUM-001")
                .message("枚举值错误: category=InvalidValue, 合法值: [Normal, Charging, BatteryLow, BatteryFull]")
                .filePath("test.xml")
                .astNode(categoryAttr)
                .build();

        QuickFixProvider provider = new QuickFixProviderImpl();
        List<FixAction> actions = provider.getFixActions(diag);

        assertFalse(actions.isEmpty());
        assertEquals(FixActionType.REPLACE_ENUM, actions.get(0).getFixType());
        assertNotNull(actions.get(0).getCandidates());
        assertFalse(actions.get(0).getCandidates().isEmpty());
    }

    private static class MockRuleRepository implements RuleRepository {

        @Override
        public Optional<DslElementRule> getElementRule(String elementName) {
            if ("Var".equals(elementName)) {
                return Optional.of(DslElementRule.builder()
                        .elementName("Var")
                        .requiredAttrs(List.of("name"))
                        .attrTypes(Map.of("name", AttrTypeSpec.builder()
                                .type("string")
                                .defaultValue(null)
                                .build()))
                        .build());
            }
            if ("Text".equals(elementName)) {
                return Optional.of(DslElementRule.builder()
                        .elementName("Text")
                        .attrTypes(Map.of("category", AttrTypeSpec.builder()
                                .type("string")
                                .enumValues(List.of("Normal", "Charging", "BatteryLow", "BatteryFull"))
                                .build()))
                        .build());
            }
            return Optional.empty();
        }

        @Override
        public List<DslElementRule> getAllElementRules() {
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
        public Optional<AttrTypeSpec> getAttrTypeSpec(String elementName, String attrName) {
            if ("Var".equals(elementName) && "name".equals(attrName)) {
                return Optional.of(AttrTypeSpec.builder()
                        .type("string")
                        .defaultValue(null)
                        .build());
            }
            if ("Text".equals(elementName) && "category".equals(attrName)) {
                return Optional.of(AttrTypeSpec.builder()
                        .type("string")
                        .enumValues(List.of("Normal", "Charging", "BatteryLow", "BatteryFull"))
                        .build());
            }
            return Optional.empty();
        }

        @Override
        public Optional<String> resolveAttrAlias(String elementName, String attrName) {
            return Optional.empty();
        }

        @Override
        public Set<String> getCanonicalAttrNames(String elementName) {
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
        public List<RuleConstraint> getConstraints(String elementName) {
            return Collections.emptyList();
        }

        @Override
        public Optional<DslGlobalVar> getGlobalVar(String varName) {
            return Optional.empty();
        }

        @Override
        public List<DslGlobalVar> getAllGlobalVars() {
            return Collections.emptyList();
        }

        @Override
        public Optional<RuleSource> getRuleSource(String ruleId) {
            return Optional.empty();
        }

        @Override
        public com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary getFunctionSignatureLibrary() {
            return null;
        }
    }
}
