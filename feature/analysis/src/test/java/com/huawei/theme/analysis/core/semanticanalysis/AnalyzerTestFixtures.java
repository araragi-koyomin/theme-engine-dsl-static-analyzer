package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;

/**
 * Analyzer单元测试共享夹具：可配置的StubRuleRepository + AST构建助手。
 */
class AnalyzerTestFixtures {

    static DslElementNode element(String tagName, Map<String, String> literalAttrs) {
        DslElementNode node = new DslElementNode();
        node.setTagName(tagName);
        node.setLine(10);
        node.setColumn(5);
        List<DslAttributeNode> attrs = new ArrayList<>();
        if (literalAttrs != null) {
            for (Map.Entry<String, String> e : literalAttrs.entrySet()) {
                DslAttributeNode a = new DslAttributeNode();
                a.setName(e.getKey());
                a.setLine(10);
                a.setColumn(5);
                DslAttributeValueNode v = new DslAttributeValueNode();
                v.setRawValue(e.getValue());
                v.setLiteral(true);
                a.setValue(v);
                attrs.add(a);
            }
        }
        node.setAttributes(attrs);
        node.setChildElements(Collections.emptyList());
        return node;
    }

    static DslFileNode file(String rootTagName) {
        DslFileNode f = new DslFileNode();
        f.setFilePath("test.xml");
        f.setRootElement(element(rootTagName, Collections.emptyMap()));
        return f;
    }

    static DslContext context(RuleRepository repo, DslFileNode file) {
        return new DslContext(repo, null, "test.xml", file);
    }

    static DslElementRule rule(String name, List<String> requiredAttrs,
            Map<String, AttrTypeSpec> attrTypes, List<String> allowedParents) {
        return DslElementRule.builder()
                .elementName(name)
                .category("view")
                .requiredAttrs(requiredAttrs == null ? Collections.emptyList() : requiredAttrs)
                .attrTypes(attrTypes == null ? Collections.emptyMap() : attrTypes)
                .allowedParents(allowedParents == null ? Collections.emptyList() : allowedParents)
                .build();
    }

    static AttrTypeSpec numberSpec() {
        return AttrTypeSpec.builder().type("number").build();
    }

    static AttrTypeSpec enumSpec(String... values) {
        return AttrTypeSpec.builder().type("enum").enumValues(List.of(values)).build();
    }

    static RuleSource source(String ruleId, String docUrl) {
        return RuleSource.builder()
                .ruleId(ruleId)
                .category("SEM")
                .description("test source")
                .docUrl(docUrl)
                .build();
    }

    static RuleRepository stubRepo(Map<String, DslElementRule> rules, Map<String, RuleSource> sources) {
        return new StubRuleRepository(rules, sources);
    }

    private static class StubRuleRepository implements RuleRepository {
        private final Map<String, DslElementRule> rules;
        private final Map<String, RuleSource> sources;

        StubRuleRepository(Map<String, DslElementRule> rules, Map<String, RuleSource> sources) {
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
                    .collect(Collectors.toList());
        }

        @Override
        public Optional<AttrTypeSpec> getAttrTypeSpec(String elementName, String attrName) {
            return getElementRule(elementName)
                    .flatMap(r -> Optional.ofNullable(r.getAttrTypes().get(attrName)));
        }

        @Override
        public Optional<String> resolveAttrAlias(String elementName, String attrName) {
            return getElementRule(elementName).flatMap(r -> {
                Set<String> canonical = new HashSet<>();
                canonical.addAll(r.getRequiredAttrs());
                canonical.addAll(r.getOptionalAttrs());
                canonical.addAll(r.getAttrTypes().keySet());
                return canonical.contains(attrName) ? Optional.of(attrName) : Optional.empty();
            });
        }

        @Override
        public Set<String> getCanonicalAttrNames(String elementName) {
            return getElementRule(elementName).map(r -> {
                Set<String> s = new HashSet<>();
                s.addAll(r.getRequiredAttrs());
                s.addAll(r.getOptionalAttrs());
                s.addAll(r.getAttrTypes().keySet());
                return s;
            }).orElse(Collections.emptySet());
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
            return Optional.ofNullable(sources.get(ruleId));
        }
    }
}
