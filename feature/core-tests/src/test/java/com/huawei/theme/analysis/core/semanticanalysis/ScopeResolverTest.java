package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.semanticanalysis.model.SymbolTable;
import com.huawei.theme.analysis.core.semanticanalysis.model.VarDeclaration;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopeResolverTest {

    private final RuleRepository repo = new JsonRuleLoader()
            .loadFromDirectory(System.getProperty("user.dir") + "/src/main/resources/rules");
    private final AstBuilder astBuilder = new AstBuilder(repo);
    private final ScopeResolver resolver = new ScopeResolverImpl(new SymbolTableBuilderImpl());

    private DslElementNode child(DslElementNode parent, String tagName) {
        return parent.getChildElements().stream()
                .filter(c -> tagName.equals(c.getTagName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("child not found: " + tagName));
    }

    @Test
    void scopeOfExposesIndexFlagLocalInsideArraySubtree() {
        DslFileNode ast = astBuilder.getDslAst("t.xml",
                "<Lockscreen>\n"
                        + "  <Array indexFlag=\"i\" frequency=\"3\">\n"
                        + "    <Text x=\"#i\"/>\n"
                        + "  </Array>\n"
                        + "</Lockscreen>");
        DslElementNode root = ast.getRootElement();
        DslElementNode array = child(root, "Array");
        DslElementNode text = child(array, "Text");

        SymbolTable textScope = resolver.scopeOf(ast, repo, text);
        Optional<VarDeclaration> i = textScope.lookup("i");
        assertTrue(i.isPresent(), "indexFlag local 'i' must be visible inside Array subtree");
        assertEquals("i", i.get().getName());
        assertEquals("indexFlag", i.get().getHostAttrName());
        assertEquals(array, i.get().getAstNode());
    }

    @Test
    void scopeOfArrayOwnAttributesDoesNotSeeIndexFlagLocal() {
        DslFileNode ast = astBuilder.getDslAst("t.xml",
                "<Lockscreen>\n"
                        + "  <Array indexFlag=\"i\" frequency=\"3\"/>\n"
                        + "</Lockscreen>");
        DslElementNode root = ast.getRootElement();
        DslElementNode array = child(root, "Array");

        SymbolTable arrayScope = resolver.scopeOf(ast, repo, array);
        assertTrue(arrayScope.lookup("i").isEmpty(),
                "indexFlag of Array is visible in Array's children, not Array's own attributes");
    }

    @Test
    void visibleDeclarationsIncludesGlobalsAndUserVars() {
        DslFileNode ast = astBuilder.getDslAst("t.xml",
                "<Lockscreen>\n"
                        + "  <Var name=\"myVar\" type=\"number\" expression=\"1\"/>\n"
                        + "  <Array indexFlag=\"idx\">\n"
                        + "    <Text/>\n"
                        + "  </Array>\n"
                        + "</Lockscreen>");
        DslElementNode root = ast.getRootElement();
        DslElementNode array = child(root, "Array");
        DslElementNode text = child(array, "Text");

        SymbolTable textScope = resolver.scopeOf(ast, repo, text);
        List<VarDeclaration> visible = textScope.visibleDeclarations();

        assertTrue(visible.stream().anyMatch(d -> "myVar".equals(d.getName()) && "name".equals(d.getHostAttrName())),
                "user <Var> myVar must be visible");
        assertTrue(visible.stream().anyMatch(d -> "idx".equals(d.getName()) && "indexFlag".equals(d.getHostAttrName())),
                "indexFlag idx must be visible in its subtree");
        assertTrue(visible.stream().anyMatch(VarDeclaration::isGlobal),
                "preset global vars must be visible");
    }

    @Test
    void rootScopeIsGlobal() {
        DslFileNode ast = astBuilder.getDslAst("t.xml", "<Lockscreen/>");
        SymbolTable rootScope = resolver.scopeOf(ast, repo, ast.getRootElement());
        assertNull(rootScope.getParent(), "root scope has no parent (it IS the global table)");
        assertTrue(rootScope.lookup("battery_level").isPresent(),
                "root scope exposes preset globals");
    }
}
