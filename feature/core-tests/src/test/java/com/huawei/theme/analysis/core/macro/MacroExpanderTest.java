package com.huawei.theme.analysis.core.macro;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.cli.PipelineMode;
import com.huawei.theme.analysis.core.expression.ExpressionNode;
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
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void foreachExpandsOverListWithStringVar() {
        DslElementNode root = buildRoot("<Lockscreen>\n"
                + "  <Foreach name=\"side\" in=\"left,right,middle\">\n"
                + "    <Var name=\"v_%{side}\"/>\n"
                + "  </Foreach>\n"
                + "</Lockscreen>");
        DemacroedAst result = expander.expand(toFile(root));
        DslElementNode dRoot = result.getDemacroed().getRootElement();
        assertEquals(3, dRoot.getChildElements().size());
        assertEquals("v_left", attr(dRoot.getChildElements().get(0), "name").getValue().getRawValue());
        assertEquals("v_right", attr(dRoot.getChildElements().get(1), "name").getValue().getRawValue());
        assertEquals("v_middle", attr(dRoot.getChildElements().get(2), "name").getValue().getRawValue());
    }

    @Test
    void foreachSkipsEmptyListItems() {
        DslElementNode root = buildRoot("<Lockscreen>\n"
                + "  <Foreach name=\"s\" in=\"a,,c,\">\n"
                + "    <Var name=\"v_%{s}\"/>\n"
                + "  </Foreach>\n"
                + "</Lockscreen>");
        DemacroedAst result = expander.expand(toFile(root));
        DslElementNode dRoot = result.getDemacroed().getRootElement();
        assertEquals(2, dRoot.getChildElements().size());
        assertEquals("v_a", attr(dRoot.getChildElements().get(0), "name").getValue().getRawValue());
        assertEquals("v_c", attr(dRoot.getChildElements().get(1), "name").getValue().getRawValue());
    }

    @Test
    void foreachMissingInEmitsMacro004() {
        DslElementNode root = buildRoot("<Lockscreen>\n"
                + "  <Foreach name=\"s\">\n"
                + "    <Var name=\"v\"/>\n"
                + "  </Foreach>\n"
                + "</Lockscreen>");
        DemacroedAst result = expander.expand(toFile(root));
        assertEquals(1, result.getMacroDiagnostics().size());
        assertEquals(ForeachHandler.RULE_FOREACH_INVALID, result.getMacroDiagnostics().get(0).getRuleId());
    }

    @Test
    void ifKeepsBodyWhenCondTrueRemovesWhenFalse() {
        DslElementNode root = buildRoot("<Lockscreen>\n"
                + "  <For name=\"i\" from=\"1\" to=\"3\">\n"
                + "    <If cond=\"i%2==1\">\n"
                + "      <Var name=\"odd_%{i}\"/>\n"
                + "    </If>\n"
                + "  </For>\n"
                + "</Lockscreen>");
        DemacroedAst result = expander.expand(toFile(root));
        DslElementNode dRoot = result.getDemacroed().getRootElement();
        // i=1 (odd) kept, i=2 (even) removed, i=3 (odd) kept
        assertEquals(2, dRoot.getChildElements().size());
        assertEquals("odd_1", attr(dRoot.getChildElements().get(0), "name").getValue().getRawValue());
        assertEquals("odd_3", attr(dRoot.getChildElements().get(1), "name").getValue().getRawValue());
    }

    @Test
    void ifRemovesBodyWhenCondConstantlyFalse() {
        DslElementNode root = buildRoot("<Lockscreen>\n"
                + "  <If cond=\"1==2\">\n"
                + "    <Var name=\"x\"/>\n"
                + "  </If>\n"
                + "</Lockscreen>");
        DemacroedAst result = expander.expand(toFile(root));
        assertTrue(result.getDemacroed().getRootElement().getChildElements().isEmpty());
        assertTrue(result.getMacroDiagnostics().isEmpty());
    }

    @Test
    void ifCondFailureEmitsMacro003AndDropsBody() {
        DslElementNode root = buildRoot("<Lockscreen>\n"
                + "  <If cond=\"undefinedVar\">\n"
                + "    <Var name=\"x\"/>\n"
                + "  </If>\n"
                + "</Lockscreen>");
        DemacroedAst result = expander.expand(toFile(root));
        assertEquals(1, result.getMacroDiagnostics().size());
        assertEquals(IfHandler.RULE_IF_COND_FAIL, result.getMacroDiagnostics().get(0).getRuleId());
        assertTrue(result.getDemacroed().getRootElement().getChildElements().isEmpty());
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

    @Test
    void includeExpandsSubFileWithParams() throws Exception {
        Path dir = Files.createTempDirectory("macro-include-");
        writeFile(dir, "function_greeting.xml",
                "<Group><Var name=\"msg_%{who}\" type=\"string\"/></Group>");
        String main = "<Lockscreen><Include name=\"function_greeting.xml\" who=\"World\"/></Lockscreen>";
        DslFileNode ast = astBuilder.getDslAst(dir.resolve("main.xml").toString(), main);
        DemacroedAst result = expander.expand(ast);
        DslElementNode dRoot = result.getDemacroed().getRootElement();
        assertEquals(1, dRoot.getChildElements().size());
        DslElementNode group = dRoot.getChildElements().get(0);
        assertEquals("Group", group.getTagName());
        DslElementNode v = group.getChildElements().get(0);
        assertEquals("Var", v.getTagName());
        assertEquals("msg_World", attr(v, "name").getValue().getRawValue());
        assertTrue(result.getMacroDiagnostics().isEmpty());
    }

    @Test
    void includeChildScopeContainsOnlyExplicitParameters() throws Exception {
        Path dir = Files.createTempDirectory("macro-include-scope-");
        writeFile(dir, "function_scope.xml",
                "<Group><Var name=\"explicit_%{arg}\"/><Var name=\"outer_%{i}\"/></Group>");
        String main = "<Lockscreen><For name=\"i\" from=\"7\" to=\"7\">"
                + "<Include name=\"function_scope.xml\" arg=\"value_%{i}\"/>"
                + "</For></Lockscreen>";

        DemacroedAst result = expander.expand(
                astBuilder.getDslAst(dir.resolve("script.xml").toString(), main));

        IncludeInstance instance = result.getIncludeInstances().get(0);
        assertEquals(Map.of("arg", "value_7"), instance.getCompileScope());
        DslElementNode group = result.getDemacroed().getRootElement().getChildElements().get(0);
        assertEquals("explicit_value_7",
                attr(group.getChildElements().get(0), "name").getValue().getRawValue());
        assertEquals("outer_%{i}",
                attr(group.getChildElements().get(1), "name").getValue().getRawValue());
        assertTrue(result.getMacroDiagnostics().stream()
                .anyMatch(d -> CompileTimeInterpolator.RULE_INTERP_FAIL.equals(d.getRuleId())));
    }

    @Test
    void includeRecursionIntoAnotherFunction() throws Exception {
        Path dir = Files.createTempDirectory("macro-include-rec-");
        writeFile(dir, "function_greeting.xml",
                "<Group><Var name=\"msg_%{who}\" type=\"string\"/></Group>");
        writeFile(dir, "function_nested.xml",
                "<Group><Include name=\"function_greeting.xml\" who=\"Inner\"/></Group>");
        String main = "<Lockscreen><Include name=\"function_nested.xml\"/></Lockscreen>";
        DslFileNode ast = astBuilder.getDslAst(dir.resolve("main.xml").toString(), main);
        DemacroedAst result = expander.expand(ast);
        DslElementNode dRoot = result.getDemacroed().getRootElement();
        // outer Group (function_nested) -> inner Group (function_greeting) -> <Var name="msg_Inner">
        DslElementNode outer = dRoot.getChildElements().get(0);
        assertEquals("Group", outer.getTagName());
        DslElementNode inner = outer.getChildElements().get(0);
        assertEquals("Group", inner.getTagName());
        DslElementNode v = inner.getChildElements().get(0);
        assertEquals("msg_Inner", attr(v, "name").getValue().getRawValue());
    }

    @Test
    void includeSubNodesRemapToIncludePosition() throws Exception {
        Path dir = Files.createTempDirectory("macro-include-pos-");
        writeFile(dir, "function_greeting.xml",
                "<Group><Var name=\"msg_%{who}\" type=\"string\"/></Group>");
        String main = "<Lockscreen>\n  <Include name=\"function_greeting.xml\" who=\"World\"/>\n</Lockscreen>";
        DslFileNode ast = astBuilder.getDslAst(dir.resolve("main.xml").toString(), main);
        DemacroedAst result = expander.expand(ast);
        // The <Include> is at line 2; all demacoed sub nodes must be remapped to line 2 so that
        // sub-file diagnostics land on the include site.
        DslElementNode group = result.getDemacroed().getRootElement().getChildElements().get(0);
        assertEquals(2, group.getLine());
        DslElementNode v = group.getChildElements().get(0);
        assertEquals(2, v.getLine());
    }

    @Test
    void includeInvalidNameEmitsMacro006() throws Exception {
        Path dir = Files.createTempDirectory("macro-include-inv-");
        String main = "<Lockscreen><Include name=\"not_a_function.xml\"/></Lockscreen>";
        DslFileNode ast = astBuilder.getDslAst(dir.resolve("main.xml").toString(), main);
        DemacroedAst result = expander.expand(ast);
        assertTrue(result.getMacroDiagnostics().stream()
                .anyMatch(d -> IncludeHandler.RULE_INCLUDE_INVALID_NAME.equals(d.getRuleId())));
    }

    @Test
    void includeFileNotFoundEmitsMacro007() throws Exception {
        Path dir = Files.createTempDirectory("macro-include-nf-");
        String main = "<Lockscreen><Include name=\"function_nonexistent.xml\"/></Lockscreen>";
        DslFileNode ast = astBuilder.getDslAst(dir.resolve("main.xml").toString(), main);
        DemacroedAst result = expander.expand(ast);
        assertTrue(result.getMacroDiagnostics().stream()
                .anyMatch(d -> IncludeHandler.RULE_INCLUDE_NOT_FOUND.equals(d.getRuleId())));
    }

    @Test
    void includeSubNodeRecordsOwningFilePath() throws Exception {
        Path dir = Files.createTempDirectory("macro-include-file-");
        writeFile(dir, "function_greeting.xml",
                "<Group><Var name=\"msg_%{who}\" type=\"string\"/></Group>");
        String main = "<Lockscreen><Include name=\"function_greeting.xml\" who=\"World\"/></Lockscreen>";
        DslFileNode ast = astBuilder.getDslAst(dir.resolve("main.xml").toString(), main);
        DemacroedAst result = expander.expand(ast);
        DslElementNode group = result.getDemacroed().getRootElement().getChildElements().get(0);
        // The demacoed Group's normal node belongs to the sub-file, not the main.
        Optional<DslElementNode> normalGroup = result.getNormalNode(group);
        assertTrue(normalGroup.isPresent());
        assertEquals(dir.resolve("function_greeting.xml").toString().replace('\\', '/'),
                result.getFilePathOfNormalNode(normalGroup.get()).replace('\\', '/'));
    }

    @Test
    void includeUsesNormalAstFactoryForSubFile() throws Exception {
        Path dir = Files.createTempDirectory("macro-include-factory-");
        writeFile(dir, "function_greeting.xml", "<Group><Var name='v'/></Group>");
        List<String> builtPaths = new ArrayList<>();
        NormalAstFactory factory = (path, content) -> {
            builtPaths.add(path);
            return new AstBuilder(repo).getDslAst(path, content);
        };
        MacroExpander customExpander = new MacroExpander(repo,
                List.of(new ForHandler(), new ForeachHandler(), new IfHandler(), new IncludeHandler()),
                MacroFileLoader.DISK, factory);
        String main = "<Lockscreen>"
                + "<Include name='function_greeting.xml' who='World'/>"
                + "<Include name='function_greeting.xml' who='Again'/>"
                + "</Lockscreen>";
        DslFileNode ast = astBuilder.getDslAst(dir.resolve("main.xml").toString(), main);
        customExpander.expand(ast);
        assertEquals(1, builtPaths.stream().filter(p -> p.endsWith("function_greeting.xml")).count(),
                "one source file must be parsed once per root context");
    }

    @Test
    void includeBudgetReportsErrorOnOneThousandAndFirstExpansion() throws Exception {
        Path dir = Files.createTempDirectory("macro-include-budget-");
        writeFile(dir, "function_empty.xml", "<Group/>");
        StringBuilder main = new StringBuilder("<Lockscreen>");
        for (int i = 0; i <= MacroExpander.MAX_TOTAL_INCLUDE_EXPANSIONS; i++) {
            main.append("<Include name=\"function_empty.xml\"/>");
        }
        main.append("</Lockscreen>");

        DemacroedAst result = expander.expand(astBuilder.getDslAst(
                dir.resolve("script.xml").toString(), main.toString()));

        assertEquals(1, result.getMacroDiagnostics().stream()
                .filter(d -> MacroExpander.RULE_INCLUDE_BUDGET.equals(d.getRuleId())).count());
        assertEquals(MacroExpander.MAX_TOTAL_INCLUDE_EXPANSIONS,
                result.getIncludeInstances().size());
    }

    @Test
    void recursiveIncludeStopsWithDiagnosticInsteadOfStackOverflow() throws Exception {
        Path dir = Files.createTempDirectory("macro-include-recursion-");
        writeFile(dir, "function_loop.xml",
                "<Group><Include name=\"function_loop.xml\"/></Group>");
        String main = "<Lockscreen><Include name=\"function_loop.xml\"/></Lockscreen>";

        DemacroedAst result = expander.expand(
                astBuilder.getDslAst(dir.resolve("script.xml").toString(), main));

        assertTrue(result.getMacroDiagnostics().stream()
                .anyMatch(d -> MacroExpander.RULE_INCLUDE_BUDGET.equals(d.getRuleId())));
        assertTrue(result.getIncludeInstances().size() <= MacroExpander.MAX_INCLUDE_NESTING_DEPTH);
    }

    private static void writeFile(Path dir, String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
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

    @Test
    void totalLoopBudgetAllowsExactlyOneHundredThousandIterations() {
        DslElementNode root = buildRoot("<Lockscreen>"
                + "<For name=\"i\" from=\"1\" to=\"100000\"/>"
                + "</Lockscreen>");

        DemacroedAst result = expander.expand(toFile(root));

        assertTrue(result.getMacroDiagnostics().isEmpty());
    }

    @Test
    void totalLoopBudgetReportsErrorOnOneHundredThousandAndFirstIteration() {
        DslElementNode root = buildRoot("<Lockscreen>"
                + "<For name=\"i\" from=\"1\" to=\"100001\"/>"
                + "</Lockscreen>");

        DemacroedAst result = expander.expand(toFile(root));

        assertEquals(1, result.getMacroDiagnostics().size());
        assertEquals(MacroExpander.RULE_EXPANSION_BUDGET,
                result.getMacroDiagnostics().get(0).getRuleId());
        assertEquals(DiagnosticSeverity.ERROR, result.getMacroDiagnostics().get(0).getSeverity());
    }

    @Test
    void siblingLoopsShareOneTotalBudget() {
        DslElementNode root = buildRoot("<Lockscreen>"
                + "<For name=\"i\" from=\"1\" to=\"60000\"/>"
                + "<For name=\"j\" from=\"1\" to=\"60000\"/>"
                + "</Lockscreen>");

        DemacroedAst result = expander.expand(toFile(root));

        assertEquals(1, result.getMacroDiagnostics().size());
        assertEquals(MacroExpander.RULE_EXPANSION_BUDGET,
                result.getMacroDiagnostics().get(0).getRuleId());
    }

    @Test
    void maxIntegerUpperBoundDoesNotOverflow() {
        DslElementNode root = buildRoot("<Lockscreen>"
                + "<For name=\"i\" from=\"2147483647\" to=\"2147483647\">"
                + "<Var name=\"v_%{i}\"/>"
                + "</For>"
                + "</Lockscreen>");

        DemacroedAst result = expander.expand(toFile(root));

        assertTrue(result.getMacroDiagnostics().isEmpty());
        assertEquals(1, result.getDemacroed().getRootElement().getChildElements().size());
        assertEquals("v_2147483647", attr(result.getDemacroed().getRootElement().getChildElements().get(0), "name")
                .getValue().getRawValue());
    }

    @Test
    void interpolatedExpressionKeepsOriginalSourceCoordinates() {
        DslElementNode root = buildRoot("<Lockscreen>\n"
                + "  <For name=\"i\" from=\"1\" to=\"1\">\n"
                + "    <Var name=\"v_1\" expression=\"#v_%{i} + #missing\"/>\n"
                + "  </For>\n"
                + "</Lockscreen>");
        DslElementNode originalVar = root.getChildElements().get(0).getChildElements().get(0);
        ExpressionNode originalExpression = (ExpressionNode) attr(originalVar, "expression")
                .getValue().getExpression().orElseThrow();
        ExpressionNode originalFirst = findVariable(originalExpression, "v_%{i}");
        ExpressionNode originalMissing = findVariable(originalExpression, "missing");

        DemacroedAst result = expander.expand(toFile(root));
        DslElementNode expandedVar = result.getDemacroed().getRootElement().getChildElements().get(0);
        ExpressionNode expandedExpression = (ExpressionNode) attr(expandedVar, "expression")
                .getValue().getExpression().orElseThrow();
        ExpressionNode expandedFirst = findVariable(expandedExpression, "v_1");
        ExpressionNode expandedMissing = findVariable(expandedExpression, "missing");

        assertEquals(originalFirst.getColumn(), expandedFirst.getColumn());
        assertEquals(originalFirst.getEndColumn(), expandedFirst.getEndColumn());
        assertEquals(originalMissing.getColumn(), expandedMissing.getColumn());
        assertEquals(originalMissing.getEndColumn(), expandedMissing.getEndColumn());
    }

    @Test
    void unclosedInterpolationProducesMacro001() {
        DslElementNode root = buildRoot("<Lockscreen><Var name=\"v_%{missing\"/></Lockscreen>");

        DemacroedAst result = expander.expand(toFile(root));

        assertEquals(1, result.getMacroDiagnostics().size());
        assertEquals(CompileTimeInterpolator.RULE_INTERP_FAIL,
                result.getMacroDiagnostics().get(0).getRuleId());
    }

    @Test
    void builtResultIsImmutableAndDetachedFromBuilderInputs() {
        DslFileNode file = new DslFileNode();
        DslElementNode normal = new DslElementNode();
        DslElementNode demacroed = new DslElementNode();
        DslElementNode later = new DslElementNode();
        Map<String, Object> scope = new HashMap<>();
        scope.put("i", BigDecimal.ONE);
        Diagnostic diagnostic = Diagnostic.builder()
                .ruleId("TEST")
                .severity(DiagnosticSeverity.ERROR)
                .message("test")
                .filePath("test.xml")
                .line(1)
                .column(0)
                .build();
        DemacroedAst.Builder builder = DemacroedAst.builder("test.xml");
        builder.put(demacroed, normal);
        builder.recordScope(demacroed, scope);
        builder.diagnostics().add(diagnostic);

        DemacroedAst result = builder.build(file);
        scope.put("later", BigDecimal.TEN);
        builder.put(later, normal);
        builder.diagnostics().clear();

        assertEquals(List.of(demacroed), result.getDemacroedNodes(normal));
        assertFalse(result.getCompileScope(demacroed).containsKey("later"));
        assertEquals(List.of(diagnostic), result.getMacroDiagnostics());
        assertThrows(UnsupportedOperationException.class,
                () -> result.getDemacroedNodes(normal).clear());
        assertThrows(UnsupportedOperationException.class,
                () -> result.getCompileScope(demacroed).put("x", BigDecimal.ZERO));
        assertThrows(UnsupportedOperationException.class,
                () -> result.getMacroDiagnostics().clear());
        result.getMacroDiagnostics().get(0).setMessage("polluted");
        assertEquals("test", result.getMacroDiagnostics().get(0).getMessage());
    }

    private static ExpressionNode findVariable(ExpressionNode node, String name) {
        if (name.equals(node.getVariableName())) {
            return node;
        }
        for (ExpressionNode child : node.getChildren()) {
            ExpressionNode found = findVariableOrNull(child, name);
            if (found != null) {
                return found;
            }
        }
        ExpressionNode found = findVariableOrNull(node.getIndexExpression(), name);
        if (found != null) {
            return found;
        }
        throw new AssertionError("variable not found: " + name);
    }

    private static ExpressionNode findVariableOrNull(ExpressionNode node, String name) {
        if (node == null) {
            return null;
        }
        if (name.equals(node.getVariableName())) {
            return node;
        }
        for (ExpressionNode child : node.getChildren()) {
            ExpressionNode found = findVariableOrNull(child, name);
            if (found != null) {
                return found;
            }
        }
        return findVariableOrNull(node.getIndexExpression(), name);
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
