package com.huawei.theme.analysis.lsp;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;

import com.huawei.theme.analysis.core.expression.ExpressionNode;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionProviderTest {

    private static final String URI = "file:///test.xml";

    private final RuleRepository repo = new RuleRepositoryFactory(null).create();
    private final DefinitionProvider provider = new DefinitionProvider(repo);

    private static DslFileNode parse(String text) {
        return new AstBuilder(null).getDslAst("test.xml", text);
    }

    private static ContextResolver.Context valueCtx(ExpressionAstNode exprNode) {
        return new ContextResolver.Context(
                ContextResolver.PositionType.ATTRIBUTE_VALUE,
                "Text", "", "x", exprNode, null);
    }

    private static DslElementNode findVar(DslElementNode root, String varName) {
        if (root == null) {
            return null;
        }
        if ("Var".equals(root.getTagName()) && nameEquals(root, varName)) {
            return root;
        }
        List<DslElementNode> children = root.getChildElements();
        if (children != null) {
            for (DslElementNode child : children) {
                DslElementNode found = findVar(child, varName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static DslElementNode findVarByOrder(DslElementNode root, String varName, int ordinal) {
        return findVarByOrder(root, varName, ordinal, new int[]{0});
    }

    private static DslElementNode findVarByOrder(DslElementNode root, String varName,
                                                 int ordinal, int[] counter) {
        if (root == null) {
            return null;
        }
        if ("Var".equals(root.getTagName()) && nameEquals(root, varName)) {
            if (counter[0] == ordinal) {
                return root;
            }
            counter[0]++;
        }
        List<DslElementNode> children = root.getChildElements();
        if (children != null) {
            for (DslElementNode child : children) {
                DslElementNode found = findVarByOrder(child, varName, ordinal, counter);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean nameEquals(DslElementNode element, String name) {
        List<DslAttributeNode> attrs = element.getAttributes();
        if (attrs == null) {
            return false;
        }
        for (DslAttributeNode attr : attrs) {
            if ("name".equals(attr.getName()) && attr.getValue() != null
                    && name.equals(attr.getValue().getRawValue())) {
                return true;
            }
        }
        return false;
    }

    private static DslAttributeValueNode nameValueOf(DslElementNode varElement) {
        for (DslAttributeNode attr : varElement.getAttributes()) {
            if ("name".equals(attr.getName())) {
                return attr.getValue();
            }
        }
        return null;
    }

    private static Range expectedRange(DslElementNode varElement, PositionMapper mapper) {
        DslAttributeValueNode value = nameValueOf(varElement);
        return new Range(
                mapper.toPosition(value.getLine(), value.getColumn()),
                mapper.toPosition(value.getEndLine(), value.getEndColumn()));
    }

    // ---- T1: 命中路径 + Range 映射 ----

    @Test
    void numberVariableRefJumpsToVarDefinition() {
        String text = "<Lockscreen>\n  <Var name=\"foo\" expression=\"1\" type=\"number\"/>\n</Lockscreen>";
        DslFileNode ast = parse(text);
        DslElementNode var = findVar(ast.getRootElement(), "foo");
        ExpressionNode expr = ExpressionNode.variableRef("#", "foo", "#foo", 1, 0);
        PositionMapper mapper = new PositionMapper(text);
        List<Location> locations = provider.definition(valueCtx(expr), ast, URI, mapper);
        assertEquals(1, locations.size());
        Location loc = locations.get(0);
        assertEquals(URI, loc.getUri());
        assertEquals(expectedRange(var, mapper), loc.getRange());
    }

    @Test
    void stringVariableRefJumpsToSameVar() {
        String text = "<Lockscreen>\n  <Var name=\"bg\" expression=\"'x'\" type=\"string\"/>\n</Lockscreen>";
        DslFileNode ast = parse(text);
        DslElementNode var = findVar(ast.getRootElement(), "bg");
        ExpressionNode expr = ExpressionNode.variableRef("@", "bg", "@bg", 1, 0);
        PositionMapper mapper = new PositionMapper(text);
        List<Location> locations = provider.definition(valueCtx(expr), ast, URI, mapper);
        assertEquals(1, locations.size());
        assertEquals(expectedRange(var, mapper), locations.get(0).getRange());
    }

    @Test
    void arrayAccessJumpsToVarDefinition() {
        String text = "<Lockscreen>\n  <Var name=\"arr\" type=\"number[]\" size=\"3\"/>\n</Lockscreen>";
        DslFileNode ast = parse(text);
        DslElementNode var = findVar(ast.getRootElement(), "arr");
        ExpressionNode idx = ExpressionNode.literal("0", "0", 1, 2);
        ExpressionNode expr = ExpressionNode.arrayAccess("#", "arr", idx, "#arr[0]", 1, 0);
        PositionMapper mapper = new PositionMapper(text);
        List<Location> locations = provider.definition(valueCtx(expr), ast, URI, mapper);
        assertEquals(1, locations.size());
        assertEquals(expectedRange(var, mapper), locations.get(0).getRange());
    }

    @Test
    void rangeCoversExactlyTheVariableNameText() {
        String text = "<Lockscreen>\n  <Var name=\"foo\" expression=\"1\"/>\n</Lockscreen>";
        DslFileNode ast = parse(text);
        ExpressionNode expr = ExpressionNode.variableRef("#", "foo", "#foo", 1, 0);
        PositionMapper mapper = new PositionMapper(text);
        List<Location> locations = provider.definition(valueCtx(expr), ast, URI, mapper);
        assertEquals(1, locations.size());
        Range r = locations.get(0).getRange();
        int startOff = mapper.toOffset(r.getStart().getLine(), r.getStart().getCharacter());
        int endOff = mapper.toOffset(r.getEnd().getLine(), r.getEnd().getCharacter());
        assertEquals("foo", text.substring(startOff, endOff));
    }

    // ---- T2: 空场景与边界守卫 ----

    @Test
    void undefinedVariableRefReturnsEmpty() {
        String text = "<Lockscreen>\n  <Var name=\"foo\" expression=\"1\"/>\n</Lockscreen>";
        DslFileNode ast = parse(text);
        ExpressionNode expr = ExpressionNode.variableRef("#", "bar", "#bar", 1, 0);
        PositionMapper mapper = new PositionMapper(text);
        List<Location> locations = provider.definition(valueCtx(expr), ast, URI, mapper);
        assertTrue(locations.isEmpty());
    }

    @Test
    void nullExprNodeReturnsEmpty() {
        String text = "<Lockscreen>\n  <Var name=\"foo\" expression=\"1\"/>\n</Lockscreen>";
        DslFileNode ast = parse(text);
        PositionMapper mapper = new PositionMapper(text);
        List<Location> locations = provider.definition(valueCtx(null), ast, URI, mapper);
        assertTrue(locations.isEmpty());
    }

    @Test
    void literalExprNodeReturnsEmpty() {
        String text = "<Lockscreen>\n  <Var name=\"foo\" expression=\"1\"/>\n</Lockscreen>";
        DslFileNode ast = parse(text);
        ExpressionAstNode literal = ExpressionNode.literal("123", "123", 1, 0);
        PositionMapper mapper = new PositionMapper(text);
        List<Location> locations = provider.definition(valueCtx(literal), ast, URI, mapper);
        assertTrue(locations.isEmpty());
    }

    @Test
    void globalVariableRefReturnsEmpty() {
        // touch_x is a built-in global variable (global_vars.json); it has no
        // <Var name="touch_x"> definition in the file -> definition is empty.
        String text = "<Lockscreen>\n  <Var name=\"foo\" expression=\"1\"/>\n</Lockscreen>";
        DslFileNode ast = parse(text);
        ExpressionNode expr = ExpressionNode.variableRef("#", "touch_x", "#touch_x", 1, 0);
        PositionMapper mapper = new PositionMapper(text);
        List<Location> locations = provider.definition(valueCtx(expr), ast, URI, mapper);
        assertTrue(locations.isEmpty());
    }

    @Test
    void duplicateVarReturnsFirstInDocumentOrder() {
        String text = "<Lockscreen>\n"
                + "  <Var name=\"dup\" expression=\"1\"/>\n"
                + "  <Var name=\"dup\" expression=\"2\"/>\n"
                + "</Lockscreen>";
        DslFileNode ast = parse(text);
        DslElementNode first = findVarByOrder(ast.getRootElement(), "dup", 0);
        DslElementNode second = findVarByOrder(ast.getRootElement(), "dup", 1);
        ExpressionNode expr = ExpressionNode.variableRef("#", "dup", "#dup", 1, 0);
        PositionMapper mapper = new PositionMapper(text);
        List<Location> locations = provider.definition(valueCtx(expr), ast, URI, mapper);
        assertEquals(1, locations.size());
        assertEquals(expectedRange(first, mapper), locations.get(0).getRange());
        // sanity: the two declarations really are at different positions
        assertTrue(!expectedRange(first, mapper).equals(expectedRange(second, mapper)));
    }

    @Test
    void nullCtxReturnsEmpty() {
        String text = "<Lockscreen>\n  <Var name=\"foo\" expression=\"1\"/>\n</Lockscreen>";
        DslFileNode ast = parse(text);
        PositionMapper mapper = new PositionMapper(text);
        List<Location> locations = provider.definition(null, ast, URI, mapper);
        assertTrue(locations.isEmpty());
    }

    @Test
    void nullAstReturnsEmpty() {
        String text = "<Lockscreen>\n  <Var name=\"foo\" expression=\"1\"/>\n</Lockscreen>";
        ExpressionNode expr = ExpressionNode.variableRef("#", "foo", "#foo", 1, 0);
        PositionMapper mapper = new PositionMapper(text);
        List<Location> locations = provider.definition(valueCtx(expr), null, URI, mapper);
        assertTrue(locations.isEmpty());
    }

    @Test
    void emptyVarNameReturnsEmpty() {
        String text = "<Lockscreen>\n  <Var name=\"foo\" expression=\"1\"/>\n</Lockscreen>";
        DslFileNode ast = parse(text);
        ExpressionNode expr = ExpressionNode.variableRef("#", "", "#", 1, 0);
        PositionMapper mapper = new PositionMapper(text);
        List<Location> locations = provider.definition(valueCtx(expr), ast, URI, mapper);
        assertTrue(locations.isEmpty());
    }
}
