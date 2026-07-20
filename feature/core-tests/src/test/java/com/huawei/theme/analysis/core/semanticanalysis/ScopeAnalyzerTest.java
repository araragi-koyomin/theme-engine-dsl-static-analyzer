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
import com.huawei.theme.analysis.core.semanticanalysis.analyzers.ScopeAnalyzer;
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopeAnalyzerTest {

    private static final String SCOPE_001_URL =
            "https://developer.huawei.com/consumer/cn/doc/content/themes-engine-next-scope-0000002279698481";

    private final ScopeAnalyzer analyzer = new ScopeAnalyzer();

    private static DslElementNode element(String tagName) {
        DslElementNode node = new DslElementNode();
        node.setTagName(tagName);
        node.setLine(10);
        node.setColumn(5);
        node.setAttributes(Collections.emptyList());
        node.setChildElements(Collections.emptyList());
        return node;
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

    private static DslElementRule rule(String name, Map<String, Boolean> scope) {
        return DslElementRule.builder()
                .elementName(name)
                .category("view")
                .scope(scope)
                .build();
    }

    private static RuleSource source(String ruleId, String docUrl) {
        return RuleSource.builder()
                .ruleId(ruleId)
                .category("SEM")
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
            return Optional.ofNullable(ruleSources.get(ruleId));
        }

        @Override
        public com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary getFunctionSignatureLibrary() {
            return null;
        }
    }

    @Test
    void scopeNotSupportedProducesSEM_SCOPE_001() {
        DslElementNode player = element("Player");
        DslFileNode fileNode = file("Wallpaper");

        DslElementRule playerRule = rule("Player", Map.of("Wallpaper", false, "Lockscreen", true));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Player", playerRule),
                Map.of("SEM-SCOPE-001", source("SEM-SCOPE-001", SCOPE_001_URL))
        );

        List<Diagnostic> diagnostics = analyzer.analyze(player, context(ruleRepo, fileNode));

        assertEquals(1, diagnostics.size());
        Diagnostic diag = diagnostics.get(0);
        assertEquals(DiagnosticSeverity.ERROR, diag.getSeverity());
        assertEquals("SEM-SCOPE-001", diag.getRuleId());
        assertEquals("元素不支持当前应用位置：'Player'不允许在'Wallpaper'中使用", diag.getMessage());
        assertEquals("test.xml", diag.getFilePath());
        assertEquals(10, diag.getLine());
        assertEquals(5, diag.getColumn());
        assertEquals(SCOPE_001_URL, diag.getRuleDocUrl());
    }

    @Test
    void scopeSupportedNoViolation() {
        DslElementNode player = element("Player");
        DslFileNode fileNode = file("Lockscreen");

        DslElementRule playerRule = rule("Player", Map.of("Lockscreen", true, "Wallpaper", false));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Player", playerRule),
                Map.of("SEM-SCOPE-001", source("SEM-SCOPE-001", SCOPE_001_URL))
        );

        List<Diagnostic> diagnostics = analyzer.analyze(player, context(ruleRepo, fileNode));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void scopeMapMissingCurrentScopeProducesSEM_SCOPE_001() {
        DslElementNode player = element("Player");
        DslFileNode fileNode = file("Wallpaper");

        DslElementRule playerRule = rule("Player", Map.of("Lockscreen", true));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Player", playerRule),
                Map.of("SEM-SCOPE-001", source("SEM-SCOPE-001", SCOPE_001_URL))
        );

        List<Diagnostic> diagnostics = analyzer.analyze(player, context(ruleRepo, fileNode));

        assertEquals(1, diagnostics.size());
        assertEquals("SEM-SCOPE-001", diagnostics.get(0).getRuleId());
    }

    @Test
    void emptyScopeSkipsCheck() {
        DslElementNode player = element("Player");
        DslFileNode fileNode = file("Wallpaper");

        DslElementRule playerRule = rule("Player", Collections.emptyMap());
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Player", playerRule),
                Map.of("SEM-SCOPE-001", source("SEM-SCOPE-001", SCOPE_001_URL))
        );

        List<Diagnostic> diagnostics = analyzer.analyze(player, context(ruleRepo, fileNode));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void elementNotInRuleRepositoryReturnsEmpty() {
        DslElementNode node = element("Unknown");
        DslFileNode fileNode = file("Lockscreen");
        RuleRepository ruleRepo = new StubRuleRepository(
                Collections.emptyMap(),
                Map.of("SEM-SCOPE-001", source("SEM-SCOPE-001", SCOPE_001_URL))
        );

        List<Diagnostic> diagnostics = analyzer.analyze(node, context(ruleRepo, fileNode));

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void nullRootNodeSkipsScopeCheck() {
        DslElementNode player = element("Player");

        DslElementRule playerRule = rule("Player", Map.of("Wallpaper", false));
        RuleRepository ruleRepo = new StubRuleRepository(
                Map.of("Player", playerRule),
                Map.of("SEM-SCOPE-001", source("SEM-SCOPE-001", SCOPE_001_URL))
        );

        List<Diagnostic> diagnostics = analyzer.analyze(player, context(ruleRepo, null));

        assertTrue(diagnostics.isEmpty());
    }
}
