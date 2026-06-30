package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;
import com.huawei.theme.analysis.core.semanticanalysis.analyzers.ParentChildAnalyzer;
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParentChildAnalyzerTest {

    private static final String SYN_002_URL =
            "https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-lock-0000002244659534";

    private final ParentChildAnalyzer analyzer = new ParentChildAnalyzer();

    private static DslElementNode element(String tagName) {
        DslElementNode node = new DslElementNode();
        node.setTagName(tagName);
        node.setLine(10);
        node.setColumn(5);
        node.setAttributes(Collections.emptyList());
        node.setChildElements(Collections.emptyList());
        return node;
    }

    private static DslElementNode withParent(DslElementNode child, DslElementNode parent) {
        child.setParent(parent);
        return child;
    }

    private static DslFileNode file(String rootTagName) {
        DslFileNode fileNode = new DslFileNode();
        fileNode.setFilePath("test.xml");
        fileNode.setRootElement(element(rootTagName));
        return fileNode;
    }

    private static DslContext context(RuleRepository ruleRepo, DslFileNode fileNode) {
        return new DslContext(ruleRepo, null, "test.xml", fileNode);
    }

    private static DslElementRule rule(String name, List<String> allowedParents) {
        return DslElementRule.builder()
                .elementName(name)
                .category("view")
                .allowedParents(allowedParents)
                .scope(Collections.emptyMap())
                .build();
    }

    private static RuleSource source(String ruleId, String docUrl) {
        return RuleSource.builder()
                .ruleId(ruleId)
                .category("SYN")
                .description("test source")
                .docUrl(docUrl)
                .build();
    }

    private static class StubRuleRepository implements RuleRepository {

        private final Map<String, DslElementRule> elementRules;
        private final Map<String, RuleSource> ruleSources;

        StubRuleRepository(Map<String, DslElementRule> elementRules, Map<String, RuleSource> ruleSources) {
            this.elementRules = elementRules;
            this.ruleSources = ruleSources;
        }

        @Override
        public Optional<DslElementRule> getElementRule(String elementName) {
            return Optional.ofNullable(elementRules.get(elementName));
        }

        @Override
        public List<DslElementRule> getAllElementRules() {
            return List.copyOf(elementRules.values());
        }

        @Override
        public List<String> getAllElementNames() {
            return List.copyOf(elementRules.keySet());
        }

        @Override
        public List<String> getRootElementNames() {
            return Collections.emptyList();
        }

        @Override
        public Optional<AttrTypeSpec> getAttrTypeSpec(String elementName, String attrName) {
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
            return getElementRule(elementName)
                    .map(DslElementRule::getAllowedParents)
                    .orElse(Collections.emptyList());
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
            return Optional.ofNullable(ruleSources.get(ruleId));
        }
    }

    @Test
    void parentNotAllowedProducesSYN_002() {
        DslElementNode button = withParent(element("Button"), element("Text"));
        DslFileNode fileNode = file("Lockscreen");

        DslElementRule buttonRule = rule("Button", List.of("Lockscreen", "Widget"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Button", buttonRule),
                Map.of("SYN-002", source("SYN-002", SYN_002_URL))
        );

        List<Diagnostic> diagnostics = analyzer.analyze(button, context(ruleRepo, fileNode));

        assertEquals(1, diagnostics.size());
        Diagnostic diag = diagnostics.get(0);
        assertEquals(DiagnosticSeverity.ERROR, diag.getSeverity());
        assertEquals("SYN-002", diag.getRuleId());
        assertEquals("标签嵌套违反父子约束：'Button'的父元素'Text'不在允许列表[Lockscreen, Widget]中",
                diag.getMessage());
        assertEquals("test.xml", diag.getFilePath());
        assertEquals(10, diag.getLine());
        assertEquals(5, diag.getColumn());
        assertEquals(SYN_002_URL, diag.getRuleDocUrl());
    }

    @Test
    void parentAllowedNoViolation() {
        DslElementNode button = withParent(element("Button"), element("Lockscreen"));
        DslFileNode fileNode = file("Lockscreen");

        DslElementRule buttonRule = rule("Button", List.of("Lockscreen", "Widget"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Button", buttonRule),
                Map.of("SYN-002", source("SYN-002", SYN_002_URL))
        );

        List<Diagnostic> diagnostics = analyzer.analyze(button, context(ruleRepo, fileNode));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void missingParentProducesSYN_002() {
        DslElementNode button = element("Button");
        DslFileNode fileNode = file("Lockscreen");

        DslElementRule buttonRule = rule("Button", List.of("Lockscreen", "Widget"));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Button", buttonRule),
                Map.of("SYN-002", source("SYN-002", SYN_002_URL))
        );

        List<Diagnostic> diagnostics = analyzer.analyze(button, context(ruleRepo, fileNode));

        assertEquals(1, diagnostics.size());
        assertEquals("SYN-002", diagnostics.get(0).getRuleId());
        assertTrue(diagnostics.get(0).getMessage().contains("缺少父元素"));
    }

    @Test
    void rootElementSkipsParentCheck() {
        DslElementNode lockscreen = withParent(element("Lockscreen"), element("Anything"));
        DslFileNode fileNode = file("Lockscreen");

        DslElementRule rootRule = rule("Lockscreen", Collections.emptyList());
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Lockscreen", rootRule),
                Map.of("SYN-002", source("SYN-002", SYN_002_URL))
        );

        List<Diagnostic> diagnostics = analyzer.analyze(lockscreen, context(ruleRepo, fileNode));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void elementNotInRuleRepositoryReturnsEmpty() {
        DslElementNode node = element("Unknown");
        DslFileNode fileNode = file("Lockscreen");
        RuleRepository ruleRepo = new StubRuleRepository(
                Collections.emptyMap(),
                Map.of("SYN-002", source("SYN-002", SYN_002_URL))
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo, fileNode));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void nonElementNodeReturnsEmpty() {
        DslFileNode fileNode = file("Lockscreen");
        RuleRepository ruleRepo = new StubRuleRepository(
                Collections.emptyMap(),
                Collections.emptyMap()
        );

        List<Diagnostic> diagnostics = analyzer.analyze(fileNode, context(ruleRepo, fileNode));

        assertTrue(diagnostics.isEmpty());
    }
}
