package com.huawei.theme.analysis.core.macro;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.cli.PipelineMode;
import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.DiagnosticProviderImpl;
import com.huawei.theme.analysis.core.semanticanalysis.SymbolTableBuilderImpl;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacroExpanderTest {

    private final RuleRepository repo = new JsonRuleLoader()
            .loadFromDirectory(System.getProperty("user.dir") + "/src/main/resources/rules");
    private final AstBuilder astBuilder = new AstBuilder(repo);
    private final MacroExpander expander = new MacroExpander(repo);

    private DslElementNode buildRoot(String xml) {
        DslFileNode ast = astBuilder.getDslAst("test.xml", xml);
        return ast.getRootElement();
    }

    private DslAttributeNode attr(DslElementNode node, String name) {
        return node.getAttributes().stream()
                .filter(a -> name.equals(a.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("attr not found: " + name));
    }

    @Test
    void forExpandsAndInterpolates() {
        DslElementNode root = buildRoot("<Lockscreen>\n"
                + "  <For name=\"i\" from=\"1\" to=\"3\">\n"
                + "    <Var name=\"index_%{i}\" expression=\"%{2*i}\" type=\"number\"/>\n"
                + "  </For>\n"
                + "</Lockscreen>");

        DemacroedAst result = expander.expand(toFile(root));
        DslElementNode dRoot = result.getDemacroed().getRootElement();
        assertNotNull(dRoot);
        assertEquals("Lockscreen", dRoot.getTagName());
        // The <For> is gone; its body expanded 3× as direct children.
        assertEquals(3, dRoot.getChildElements().size());

        String[] expectedNames = {"index_1", "index_2", "index_3"};
        for (int i = 0; i < 3; i++) {
            DslElementNode v = dRoot.getChildElements().get(i);
            assertEquals("Var", v.getTagName());
            assertEquals(expectedNames[i], attr(v, "name").getValue().getRawValue());
        }
    }

    @Test
    void interpolatedExpressionReEmbeddedAsParsedLiteral() {
        DslElementNode root = buildRoot("<Lockscreen>\n"
                + "  <For name=\"i\" from=\"2\" to=\"2\">\n"
                + "    <Var name=\"v\" expression=\"%{2*i}\" type=\"number\"/>\n"
                + "  </For>\n"
                + "</Lockscreen>");
        DemacroedAst result = expander.expand(toFile(root));
        DslElementNode v = result.getDemacroed().getRootElement().getChildElements().get(0);
        DslAttributeValueNode value = attr(v, "expression").getValue();
        // The %{2*i} with i=2 interpolates to "4", then re-embeds as a parsed literal (not the stale %{...} parse failure).
        Optional<ExpressionAstNode> expr = value.getExpression();
        assertTrue(expr.isPresent(), "interpolated expression must be re-embedded as a parsed ExpressionAstNode");
        assertFalse(value.isLiteral(), "expression attr should not be literal after re-embed");
    }

    @Test
    void demacroedToNormalMapIsManyToOne() {
        DslElementNode root = buildRoot("<Lockscreen>\n"
                + "  <For name=\"i\" from=\"1\" to=\"3\">\n"
                + "    <Var name=\"v_%{i}\"/>\n"
                + "  </For>\n"
                + "</Lockscreen>");
        DemacroedAst result = expander.expand(toFile(root));
        DslElementNode dRoot = result.getDemacroed().getRootElement();
        DslElementNode origVar = root.getChildElements().get(0).getChildElements().get(0); // the <Var> inside <For>

        List<DslElementNode> copies = result.getDemacroedNodes(origVar);
        assertEquals(3, copies.size(), "one <Var> normal node -> 3 demacroed copies");
        for (DslElementNode copy : copies) {
            Optional<DslElementNode> back = result.getNormalNode(copy);
            assertTrue(back.isPresent());
            assertSame(origVar, back.get(), "every demacroed copy maps back to the original <Var>");
        }
    }

    @Test
    void demacroedNodesInheritOriginalSourcePosition() {
        DslElementNode root = buildRoot("<Lockscreen>\n"
                + "  <For name=\"i\" from=\"1\" to=\"2\">\n"
                + "    <Var name=\"v_%{i}\"/>\n"
                + "  </For>\n"
                + "</Lockscreen>");
        DslElementNode origVar = root.getChildElements().get(0).getChildElements().get(0);
        DemacroedAst result = expander.expand(toFile(root));
        for (DslElementNode copy : result.getDemacroedNodes(origVar)) {
            assertEquals(origVar.getLine(), copy.getLine());
            assertEquals(origVar.getColumn(), copy.getColumn());
        }
    }

    @Test
    void nestedForExpandsInnerFirst() {
        DslElementNode root = buildRoot("<Lockscreen>\n"
                + "  <For name=\"i\" from=\"1\" to=\"2\">\n"
                + "    <For name=\"j\" from=\"10\" to=\"11\">\n"
                + "      <Var name=\"v_%{i}_%{j}\"/>\n"
                + "    </For>\n"
                + "  </For>\n"
                + "</Lockscreen>");
        DemacroedAst result = expander.expand(toFile(root));
        DslElementNode dRoot = result.getDemacroed().getRootElement();
        assertEquals(4, dRoot.getChildElements().size());
        assertEquals("v_1_10", attr(dRoot.getChildElements().get(0), "name").getValue().getRawValue());
        assertEquals("v_1_11", attr(dRoot.getChildElements().get(1), "name").getValue().getRawValue());
        assertEquals("v_2_10", attr(dRoot.getChildElements().get(2), "name").getValue().getRawValue());
        assertEquals("v_2_11", attr(dRoot.getChildElements().get(3), "name").getValue().getRawValue());
    }

    @Test
    void fromGreaterThanToProducesEmptyExpansion() {
        DslElementNode root = buildRoot("<Lockscreen>\n"
                + "  <For name=\"i\" from=\"5\" to=\"3\">\n"
                + "    <Var name=\"v_%{i}\"/>\n"
                + "  </For>\n"
                + "</Lockscreen>");
        DemacroedAst result = expander.expand(toFile(root));
        DslElementNode dRoot = result.getDemacroed().getRootElement();
        assertTrue(dRoot.getChildElements().isEmpty(), "from>to => no iterations");
        assertTrue(result.getMacroDiagnostics().isEmpty(), "from>to is not an error, just empty");
    }

    @Test
    void nonIntegerFromProducesMacro002() {
        DslElementNode root = buildRoot("<Lockscreen>\n"
                + "  <For name=\"i\" from=\"abc\" to=\"3\">\n"
                + "    <Var name=\"v_%{i}\"/>\n"
                + "  </For>\n"
                + "</Lockscreen>");
        DemacroedAst result = expander.expand(toFile(root));
        assertEquals(1, result.getMacroDiagnostics().size());
        Diagnostic d = result.getMacroDiagnostics().get(0);
        assertEquals(ForHandler.RULE_FOR_INVALID, d.getRuleId());
    }

    @Test
    void undefinedInterpolationVariableProducesMacro001() {
        DslElementNode root = buildRoot("<Lockscreen>\n"
                + "  <Var name=\"v_%{undefinedVar}\"/>\n"
                + "</Lockscreen>");
        DemacroedAst result = expander.expand(toFile(root));
        assertEquals(1, result.getMacroDiagnostics().size());
        Diagnostic d = result.getMacroDiagnostics().get(0);
        assertEquals(CompileTimeInterpolator.RULE_INTERP_FAIL, d.getRuleId());
        // literal %{undefinedVar} preserved in the demacroed value
        DslElementNode v = result.getDemacroed().getRootElement().getChildElements().get(0);
        assertTrue(attr(v, "name").getValue().getRawValue().contains("%{undefinedVar}"));
    }

    @Test
    void noMacrosPassesThroughUnchanged() {
        DslElementNode root = buildRoot("<Lockscreen><Var name=\"v\"/></Lockscreen>");
        DemacroedAst result = expander.expand(toFile(root));
        DslElementNode dRoot = result.getDemacroed().getRootElement();
        assertEquals("Lockscreen", dRoot.getTagName());
        assertEquals(1, dRoot.getChildElements().size());
        assertEquals("Var", dRoot.getChildElements().get(0).getTagName());
        assertEquals("v", attr(dRoot.getChildElements().get(0), "name").getValue().getRawValue());
        assertTrue(result.getMacroDiagnostics().isEmpty());
    }

    /**
     * Decided behavior: a STATIC-named {@code <Var>} inside a {@code <For>} expands to N copies,
     * which {@code buildGlobal} flags as duplicate declarations (SEM-REF-003). The N diagnostics
     * share the original source position, so {@link DiagnosticDedup} collapses them to exactly 1.
     * This is intentional — it nudges users to make names unique via {@code %{i}} (the {@code index_%{i}}
     * pattern produces no error). Pins the decision so a future change cannot silently flip it.
     */
    @Test
    void staticNamedVarInForFlagsSingleDedupedDuplicate() {
        DslElementNode root = buildRoot("<Lockscreen>\n"
                + "  <For name=\"i\" from=\"1\" to=\"3\">\n"
                + "    <Var name=\"v\" type=\"number\"/>\n"
                + "  </For>\n"
                + "</Lockscreen>");
        DemacroedAst demacroed = expander.expand(toFile(root));
        List<Diagnostic> diags = new DiagnosticProviderImpl().analyze(
                demacroed.getDemacroed(), repo, new SymbolTableBuilderImpl(),
                PipelineMode.FULL, InspectionConfig.builder().build(), null);
        diags = DiagnosticDedup.dedup(diags);
        long dup = diags.stream().filter(d -> "SEM-REF-003".equals(d.getRuleId())).count();
        assertEquals(1, dup, "static-named <Var> in <For>: 3 copies -> 1 deduped SEM-REF-003");
    }

    @Test
    void demacoedNodeCarriesItsCompileTimeScope() {
        DslElementNode root = buildRoot("<Lockscreen>\n"
                + "  <For name=\"i\" from=\"1\" to=\"3\">\n"
                + "    <Var name=\"v_%{i}\"/>\n"
                + "  </For>\n"
                + "</Lockscreen>");
        DslElementNode origVar = root.getChildElements().get(0).getChildElements().get(0);
        DemacroedAst result = expander.expand(toFile(root));
        List<DslElementNode> copies = result.getDemacroedNodes(origVar);
        assertEquals(3, copies.size());
        assertEquals(BigDecimal.valueOf(1), result.getCompileScope(copies.get(0)).get("i"));
        assertEquals(BigDecimal.valueOf(2), result.getCompileScope(copies.get(1)).get("i"));
        assertEquals(BigDecimal.valueOf(3), result.getCompileScope(copies.get(2)).get("i"));
    }

    private DslFileNode toFile(DslElementNode root) {
        DslFileNode f = new DslFileNode();
        f.setFilePath("test.xml");
        f.setText("");
        f.setLine(1);
        f.setColumn(0);
        f.setRootElement(root);
        if (root != null) {
            root.setParent(f);
        }
        return f;
    }
}
