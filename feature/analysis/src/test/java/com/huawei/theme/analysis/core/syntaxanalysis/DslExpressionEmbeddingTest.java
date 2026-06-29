package com.huawei.theme.analysis.core.syntaxanalysis;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.expression.ExpressionNode;
import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslExpressionEmbeddingTest {

    private final RuleRepository ruleRepository = loadRules();
    private final DslAstProvider provider = new AstBuilder(ruleRepository);

    private static RuleRepository loadRules() {
        String dir = System.getProperty("user.dir") + "/src/main/resources/rules";
        return new JsonRuleLoader().loadFromDirectory(dir);
    }

    private DslElementNode build(String xml) {
        return provider.getDslAst("test.xml", xml).getRootElement();
    }

    private DslAttributeValueNode attrValue(DslElementNode node, String name) {
        return node.getAttributes().stream()
                .filter(a -> name.equals(a.getName()))
                .map(DslAttributeNode::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("attr not found: " + name));
    }

    private ExpressionNode expr(DslElementNode node, String name) {
        Optional<ExpressionAstNode> opt = attrValue(node, name).getExpression();
        assertTrue(opt.isPresent(), "expected expression for attr " + name);
        return (ExpressionNode) opt.get();
    }

    private void assertLiteral(DslElementNode node, String name) {
        DslAttributeValueNode v = attrValue(node, name);
        assertTrue(v.isLiteral());
        assertTrue(v.getExpression().isEmpty());
    }

    @Test
    void varExpressionLiteralValue() {
        DslElementNode var = build("<Var name=\"v\" expression=\"1\" type=\"number\"/>");
        assertLiteral(var, "expression");
        assertEquals("1", attrValue(var, "expression").getRawValue());
    }

    @Test
    void varExpressionHashVariableRef() {
        DslElementNode var = build("<Var name=\"v\" expression=\"#battery_level\" type=\"number\"/>");
        ExpressionNode e = expr(var, "expression");
        assertEquals(ExpressionKind.VARIABLE_REF, e.getKind());
        assertEquals("#", e.getPrefix());
        assertEquals("battery_level", e.getVariableName());
    }

    @Test
    void varExpressionFunctionCall() {
        DslElementNode var = build("<Var name=\"v\" expression=\"sin(#x)\" type=\"number\"/>");
        ExpressionNode e = expr(var, "expression");
        assertEquals(ExpressionKind.FUNCTION_CALL, e.getKind());
        assertEquals("sin", e.getFunctionName());
        assertEquals(1, e.getChildren().size());
        assertEquals(ExpressionKind.VARIABLE_REF, e.getChildren().get(0).getKind());
        assertEquals("x", e.getChildren().get(0).getVariableName());
    }

    @Test
    void varExpressionParseFailureIsNotLiteral() {
        DslElementNode var = build("<Var name=\"v\" expression=\"#x+\" type=\"number\"/>");
        DslAttributeValueNode v = attrValue(var, "expression");
        assertFalse(v.isLiteral());
        assertTrue(v.getExpression().isEmpty());
    }

    @Test
    void numberAttrLiteralValue() {
        DslElementNode image = build("<Image x=\"0\"/>");
        assertLiteral(image, "x");
    }

    @Test
    void numberAttrBinaryExpr() {
        DslElementNode image = build("<Image x=\"#screen_width/2\"/>");
        ExpressionNode e = expr(image, "x");
        assertEquals(ExpressionKind.BINARY_EXPR, e.getKind());
        assertEquals("/", e.getOperator());
        assertEquals(ExpressionKind.VARIABLE_REF, e.getChildren().get(0).getKind());
        assertEquals("screen_width", e.getChildren().get(0).getVariableName());
        assertEquals(ExpressionKind.LITERAL, e.getChildren().get(1).getKind());
        assertEquals("2", e.getChildren().get(1).getLiteralValue());
    }

    @Test
    void numberAttrBinaryExprWithOperatorBeforeHash() {
        DslElementNode image = build("<Image x=\"10*#ratio\"/>");
        ExpressionNode e = expr(image, "x");
        assertEquals(ExpressionKind.BINARY_EXPR, e.getKind());
        assertEquals("*", e.getOperator());
        assertEquals(ExpressionKind.LITERAL, e.getChildren().get(0).getKind());
        assertEquals("10", e.getChildren().get(0).getLiteralValue());
        assertEquals(ExpressionKind.VARIABLE_REF, e.getChildren().get(1).getKind());
        assertEquals("ratio", e.getChildren().get(1).getVariableName());
    }

    @Test
    void stringAttrHexColorLiteral() {
        DslElementNode text = build("<Text color=\"#FFFFFF\"/>");
        assertLiteral(text, "color");
        assertEquals("#FFFFFF", attrValue(text, "color").getRawValue());
    }

    @Test
    void stringAttrHexColorWithAlphaLiteral() {
        DslElementNode text = build("<Text color=\"#FFFFFFFF\"/>");
        assertLiteral(text, "color");
    }

    @Test
    void stringAttrHashNonColorIsVariableRef() {
        DslElementNode text = build("<Text color=\"#num\"/>");
        ExpressionNode e = expr(text, "color");
        assertEquals(ExpressionKind.VARIABLE_REF, e.getKind());
        assertEquals("#", e.getPrefix());
        assertEquals("num", e.getVariableName());
    }

    @Test
    void stringAttrAtVariableRef() {
        DslElementNode text = build("<Text color=\"@colorVar\"/>");
        ExpressionNode e = expr(text, "color");
        assertEquals(ExpressionKind.VARIABLE_REF, e.getKind());
        assertEquals("@", e.getPrefix());
        assertEquals("colorVar", e.getVariableName());
    }

    @Test
    void stringAttrStringLiteral() {
        DslElementNode text = build("<Text textExp=\"'hello'\"/>");
        ExpressionNode e = expr(text, "textExp");
        assertEquals(ExpressionKind.LITERAL, e.getKind());
        assertEquals("hello", e.getLiteralValue());
    }

    @Test
    void stringAttrConcatenation() {
        DslElementNode text = build("<Text textExp=\"'hello'+@name\"/>");
        ExpressionNode e = expr(text, "textExp");
        assertEquals(ExpressionKind.BINARY_EXPR, e.getKind());
        assertEquals("+", e.getOperator());
        assertEquals(ExpressionKind.LITERAL, e.getChildren().get(0).getKind());
        assertEquals("hello", e.getChildren().get(0).getLiteralValue());
        assertEquals(ExpressionKind.VARIABLE_REF, e.getChildren().get(1).getKind());
        assertEquals("@", e.getChildren().get(1).getPrefix());
        assertEquals("name", e.getChildren().get(1).getVariableName());
    }

    @Test
    void stringAttrLeadingHashWithOperatorParsesAsExpression() {
        DslElementNode text = build("<Text color=\"#num*10\"/>");
        DslAttributeValueNode v = attrValue(text, "color");
        assertFalse(v.isLiteral());
        assertTrue(v.getExpression().isPresent(), "grammar should still parse #num*10 (SEM-EXPR-003 flagged later by #22)");
        ExpressionNode e = (ExpressionNode) v.getExpression().get();
        assertEquals(ExpressionKind.BINARY_EXPR, e.getKind());
        assertEquals("*", e.getOperator());
    }

    @Test
    void noArgBuilderTreatsAllAsLiteral() {
        DslAstProvider noRepo = new AstBuilder();
        DslElementNode var = noRepo.getDslAst("t.xml", "<Var expression=\"#battery_level\"/>").getRootElement();
        assertLiteral(var, "expression");
    }

    @Test
    void validWidgetFixtureParsesBatteryExpression() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/dsl/valid_widget.xml")) {
            assertTrue(is != null, "fixture not found");
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            DslElementNode root = provider.getDslAst("valid_widget.xml", content).getRootElement();
            DslElementNode var = root.getChildElements().get(0);
            assertEquals("Var", var.getTagName());
            ExpressionNode e = expr(var, "expression");
            assertEquals(ExpressionKind.VARIABLE_REF, e.getKind());
            assertEquals("#", e.getPrefix());
            assertEquals("battery_level", e.getVariableName());
        }
    }
}
