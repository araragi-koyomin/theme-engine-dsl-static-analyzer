package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;
import com.huawei.theme.analysis.core.shared.model.FixActionType;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsertAttrGeneratorTest {

    private final MockRuleRepository mockRepo = new MockRuleRepository();
    private final InsertAttrGenerator generator = new InsertAttrGenerator(mockRepo);

    @Test
    void generatesInsertAttrForMissingRequiredAttr() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Var");
        elementNode.setLine(10);
        elementNode.setColumn(5);
        elementNode.setAttributes(Collections.emptyList());
        elementNode.setChildElements(Collections.emptyList());

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-REQ-001")
                .message("缺失必填属性: name")
                .filePath("test.xml")
                .astNode(elementNode)
                .build();

        List<FixAction> actions = generator.generate(diagnostic);

        assertEquals(1, actions.size());
        FixAction action = actions.get(0);
        assertEquals(FixActionType.ADD_ATTR, action.getFixType());
        assertEquals("name=\"\"", action.getReplacementText());
        assertEquals("添加必填属性: name", action.getDescription());
        assertEquals(10, action.getTargetRange().getStartLine());
        assertEquals(5, action.getTargetRange().getStartColumn());
    }

    @Test
    void generatesInsertAttrWithDefaultValue() {
        MockRuleRepository repoWithDefault = new MockRuleRepository(
                Map.of("Var", DslElementRule.builder()
                        .elementName("Var")
                        .requiredAttrs(List.of("name"))
                        .attrTypes(Map.of("name", AttrTypeSpec.builder()
                                .type("string")
                                .defaultValue("number")
                                .build()))
                        .build()),
                Map.of());
        InsertAttrGenerator gen = new InsertAttrGenerator(repoWithDefault);

        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Var");
        elementNode.setLine(20);
        elementNode.setColumn(8);
        elementNode.setAttributes(Collections.emptyList());
        elementNode.setChildElements(Collections.emptyList());

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-REQ-001")
                .message("缺失必填属性: name")
                .filePath("test.xml")
                .astNode(elementNode)
                .build();

        List<FixAction> actions = gen.generate(diagnostic);

        assertEquals(1, actions.size());
        assertEquals("name=\"number\"", actions.get(0).getReplacementText());
    }

    @Test
    void returnsEmptyWhenAstNodeIsNotDslElementNode() {
        DslFileNode fileNode = new DslFileNode();
        fileNode.setLine(10);
        fileNode.setColumn(5);

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-REQ-001")
                .message("缺失必填属性: name")
                .filePath("test.xml")
                .astNode(fileNode)
                .build();

        List<FixAction> actions = generator.generate(diagnostic);

        assertTrue(actions.isEmpty());
    }

    @Test
    void returnsEmptyWhenMessageDoesNotStartWithPrefix() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Var");
        elementNode.setLine(10);
        elementNode.setColumn(5);
        elementNode.setAttributes(Collections.emptyList());
        elementNode.setChildElements(Collections.emptyList());

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-REQ-001")
                .message("some other message")
                .filePath("test.xml")
                .astNode(elementNode)
                .build();

        List<FixAction> actions = generator.generate(diagnostic);

        assertTrue(actions.isEmpty());
    }

    @Test
    void returnsEmptyWhenElementNotInRepository() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setTagName("Unknown");
        elementNode.setLine(10);
        elementNode.setColumn(5);
        elementNode.setAttributes(Collections.emptyList());
        elementNode.setChildElements(Collections.emptyList());

        Diagnostic diagnostic = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-REQ-001")
                .message("缺失必填属性: something")
                .filePath("test.xml")
                .astNode(elementNode)
                .build();

        List<FixAction> actions = generator.generate(diagnostic);

        assertTrue(actions.isEmpty());
    }

    @Test
    void getRuleIdReturnsSEM_REQ_001() {
        assertEquals("SEM-REQ-001", generator.getRuleId());
    }

    private static class MockRuleRepository implements RuleRepository {
        private final Map<String, DslElementRule> rules;
        private final Map<String, RuleSource> sources;

        MockRuleRepository() {
            this(Map.of("Var", DslElementRule.builder()
                    .elementName("Var")
                    .requiredAttrs(List.of("name"))
                    .attrTypes(Map.of("name", AttrTypeSpec.builder()
                            .type("string")
                            .defaultValue(null)
                            .build()))
                    .build()),
                    Map.of());
        }

        MockRuleRepository(Map<String, DslElementRule> rules, Map<String, RuleSource> sources) {
            this.rules = rules;
            this.sources = sources;
        }

        @Override
        public Optional<DslElementRule> getElementRule(String elementName) {
            return Optional.ofNullable(rules.get(elementName));
        }

        @Override
        public List<DslElementRule> getAllElementRules() {
            return List.copyOf(rules.values());
        }

        @Override
        public List<String> getAllElementNames() {
            return List.copyOf(rules.keySet());
        }

        @Override
        public List<String> getRootElementNames() {
            return rules.values().stream()
                    .filter(r -> r.getAllowedParents().isEmpty())
                    .map(DslElementRule::getElementName)
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public Optional<AttrTypeSpec> getAttrTypeSpec(String elementName, String attrName) {
            return getElementRule(elementName)
                    .flatMap(r -> Optional.ofNullable(r.getAttrTypes().get(attrName)));
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
            return Optional.ofNullable(sources.get(ruleId));
        }
    }
}
