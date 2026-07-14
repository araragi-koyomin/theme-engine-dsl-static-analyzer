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
        DslAttributeValueNode v = attrValue(var, "expression");
        assertFalse(v.isLiteral());
        assertTrue(v.getExpression().isPresent());
        ExpressionNode e = (ExpressionNode) v.getExpression().get();
        assertEquals(ExpressionKind.LITERAL, e.getKind());
        assertEquals("1", e.getLiteralValue());
        assertEquals("1", v.getRawValue());
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
        DslAttributeValueNode v = attrValue(image, "x");
        assertFalse(v.isLiteral());
        assertTrue(v.getExpression().isPresent());
        ExpressionNode e = (ExpressionNode) v.getExpression().get();
        assertEquals(ExpressionKind.LITERAL, e.getKind());
        assertEquals("0", e.getLiteralValue());
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

    @Test
    void stringConcatWithBracedNumericSubExpression() {
        DslElementNode text = build("<Text textExp=\"'val: '+{10*#num}\"/>");
        ExpressionNode e = expr(text, "textExp");
        assertEquals(ExpressionKind.BINARY_EXPR, e.getKind());
        assertEquals("+", e.getOperator());
        assertEquals(ExpressionKind.LITERAL, e.getChildren().get(0).getKind());
        assertEquals("val: ", e.getChildren().get(0).getLiteralValue());
        ExpressionNode inner = e.getChildren().get(1);
        assertEquals(ExpressionKind.BINARY_EXPR, inner.getKind());
        assertEquals("*", inner.getOperator());
        assertEquals("10", inner.getChildren().get(0).getLiteralValue());
        assertEquals("num", inner.getChildren().get(1).getVariableName());
    }

    @Test
    void stringConcatMultipleTerms() {
        DslElementNode text = build("<Text textExp=\"'a'+'b'+'c'\"/>");
        ExpressionNode e = expr(text, "textExp");
        assertEquals(ExpressionKind.BINARY_EXPR, e.getKind());
        assertEquals("+", e.getOperator());
        assertEquals(ExpressionKind.BINARY_EXPR, e.getChildren().get(0).getKind());
        assertEquals(ExpressionKind.LITERAL, e.getChildren().get(1).getKind());
        assertEquals("c", e.getChildren().get(1).getLiteralValue());
        ExpressionNode inner = e.getChildren().get(0);
        assertEquals(ExpressionKind.LITERAL, inner.getChildren().get(0).getKind());
        assertEquals(ExpressionKind.LITERAL, inner.getChildren().get(1).getKind());
        assertEquals("a", inner.getChildren().get(0).getLiteralValue());
        assertEquals("b", inner.getChildren().get(1).getLiteralValue());
    }

    @Test
    void stringConcatWithoutBracesForOperatorFails() {
        DslElementNode text = build("<Text textExp=\"'val: '+10*#num\"/>");
        DslAttributeValueNode v = attrValue(text, "textExp");
        assertFalse(v.isLiteral());
        assertTrue(v.getExpression().isEmpty(), "10*#num in concat must be braced; parse should fail");
    }

    @Test
    void stringConcatHashVarsIsConcatNotAddition() {
        DslElementNode text = build("<Text textExp=\"'a'+#x\"/>");
        ExpressionNode e = expr(text, "textExp");
        assertEquals(ExpressionKind.BINARY_EXPR, e.getKind());
        assertEquals("+", e.getOperator());
        assertEquals(ExpressionKind.LITERAL, e.getChildren().get(0).getKind());
        assertEquals("a", e.getChildren().get(0).getLiteralValue());
        assertEquals(ExpressionKind.VARIABLE_REF, e.getChildren().get(1).getKind());
        assertEquals("#", e.getChildren().get(1).getPrefix());
    }

    @Test
    void stringAttrPureNumericValueCoerced() {
        DslElementNode text = build("<Text textExp=\"10*#num\"/>");
        ExpressionNode e = expr(text, "textExp");
        assertEquals(ExpressionKind.BINARY_EXPR, e.getKind());
        assertEquals("*", e.getOperator());
    }

    @Test
    void numericContextRejectsAtVarRef() {
        DslElementNode image = build("<Image x=\"@varName\"/>");
        DslAttributeValueNode v = attrValue(image, "x");
        assertFalse(v.isLiteral());
        assertTrue(v.getExpression().isEmpty(), "@var not allowed in numeric context");
    }

    @Test
    void numericContextHashVarRefAndArithmetic() {
        DslElementNode image = build("<Image x=\"#a+#b\"/>");
        ExpressionNode e = expr(image, "x");
        assertEquals(ExpressionKind.BINARY_EXPR, e.getKind());
        assertEquals("+", e.getOperator());
        assertEquals("a", e.getChildren().get(0).getVariableName());
        assertEquals("b", e.getChildren().get(1).getVariableName());
    }

    @Test
    void shadowColorHexLiteral() {
        DslElementNode text = build("<Text shadowColor=\"#FF0000\"/>");
        assertLiteral(text, "shadowColor");
    }

    @Test
    void nonColorStringAttrHashHexTreatedAsVariableRef() {
        DslElementNode text = build("<Text textExp=\"#FFFFFF\"/>");
        ExpressionNode e = expr(text, "textExp");
        assertEquals(ExpressionKind.VARIABLE_REF, e.getKind());
        assertEquals("#", e.getPrefix());
        assertEquals("FFFFFF", e.getVariableName());
    }

    @Test
    void unaryMinusDirectlyOnHashVarRejected() {
        DslElementNode image = build("<Image x=\"-#w\"/>");
        DslAttributeValueNode v = attrValue(image, "x");
        assertFalse(v.isLiteral());
        assertTrue(v.getExpression().isEmpty(), "-#w is not supported (SEM-EXPR-001)");
    }

    @Test
    void unaryMinusOnHashVarInsideLargerExprRejected() {
        DslElementNode image = build("<Image x=\"-#w+#b\"/>");
        DslAttributeValueNode v = attrValue(image, "x");
        assertFalse(v.isLiteral());
        assertTrue(v.getExpression().isEmpty(), "-#w anywhere is not supported");
    }

    @Test
    void unaryMinusOnNumberTimesHashVarAccepted() {
        DslElementNode image = build("<Image x=\"-1*#w\"/>");
        ExpressionNode e = expr(image, "x");
        assertEquals(ExpressionKind.BINARY_EXPR, e.getKind());
        assertEquals("*", e.getOperator());
        assertEquals(ExpressionKind.UNARY_EXPR, e.getChildren().get(0).getKind());
        assertEquals("-", e.getChildren().get(0).getOperator());
        assertEquals("1", e.getChildren().get(0).getChildren().get(0).getLiteralValue());
        assertEquals(ExpressionKind.VARIABLE_REF, e.getChildren().get(1).getKind());
        assertEquals("w", e.getChildren().get(1).getVariableName());
    }

    @Test
    void zeroMinusHashVarAccepted() {
        DslElementNode image = build("<Image x=\"0-#w\"/>");
        ExpressionNode e = expr(image, "x");
        assertEquals(ExpressionKind.BINARY_EXPR, e.getKind());
        assertEquals("-", e.getOperator());
        assertEquals("0", e.getChildren().get(0).getLiteralValue());
        assertEquals(ExpressionKind.VARIABLE_REF, e.getChildren().get(1).getKind());
        assertEquals("w", e.getChildren().get(1).getVariableName());
    }

    @Test
    void unaryMinusOnNumberAccepted() {
        DslElementNode image = build("<Image x=\"-5\"/>");
        ExpressionNode e = expr(image, "x");
        assertEquals(ExpressionKind.UNARY_EXPR, e.getKind());
        assertEquals("-", e.getOperator());
        assertEquals("5", e.getChildren().get(0).getLiteralValue());
    }

    @Test
    void stringConcatWithBracedFunctionCallsAndDottedVar() {
        DslElementNode text = build(
                "<Text textExp=\"'number/hour/'+{int(#system.time.hour1)}+'_'+{int(#aniTime)}+'.png'\"/>");
        ExpressionNode e = expr(text, "textExp");

        // e = (... + '.png')
        assertEquals(ExpressionKind.BINARY_EXPR, e.getKind());
        assertEquals("+", e.getOperator());
        assertEquals(ExpressionKind.LITERAL, e.getChildren().get(1).getKind());
        assertEquals(".png", e.getChildren().get(1).getLiteralValue());

        // e0 = (... + {int(#aniTime)})
        ExpressionNode e0 = e.getChildren().get(0);
        assertEquals(ExpressionKind.BINARY_EXPR, e0.getKind());
        assertEquals("+", e0.getOperator());
        ExpressionNode aniCall = e0.getChildren().get(1);
        assertEquals(ExpressionKind.FUNCTION_CALL, aniCall.getKind());
        assertEquals("int", aniCall.getFunctionName());
        assertEquals(1, aniCall.getChildren().size());
        assertEquals(ExpressionKind.VARIABLE_REF, aniCall.getChildren().get(0).getKind());
        assertEquals("#", aniCall.getChildren().get(0).getPrefix());
        assertEquals("aniTime", aniCall.getChildren().get(0).getVariableName());

        // e1 = (... + '_')
        ExpressionNode e1 = e0.getChildren().get(0);
        assertEquals(ExpressionKind.BINARY_EXPR, e1.getKind());
        assertEquals("+", e1.getOperator());
        assertEquals(ExpressionKind.LITERAL, e1.getChildren().get(1).getKind());
        assertEquals("_", e1.getChildren().get(1).getLiteralValue());

        // e2 = ('number/hour/' + {int(#system.time.hour1)})
        ExpressionNode e2 = e1.getChildren().get(0);
        assertEquals(ExpressionKind.BINARY_EXPR, e2.getKind());
        assertEquals("+", e2.getOperator());
        assertEquals(ExpressionKind.LITERAL, e2.getChildren().get(0).getKind());
        assertEquals("number/hour/", e2.getChildren().get(0).getLiteralValue());
        ExpressionNode hourCall = e2.getChildren().get(1);
        assertEquals(ExpressionKind.FUNCTION_CALL, hourCall.getKind());
        assertEquals("int", hourCall.getFunctionName());
        assertEquals(1, hourCall.getChildren().size());
        assertEquals(ExpressionKind.VARIABLE_REF, hourCall.getChildren().get(0).getKind());
        assertEquals("#", hourCall.getChildren().get(0).getPrefix());
        assertEquals("system.time.hour1", hourCall.getChildren().get(0).getVariableName());
    }

    @Test
    void stringConcatWithBracedFunctionCallsAndDottedVarFails() {
        DslElementNode text = build("<Text textExp=\"number/hour/{int(#system.time.hour1)}_{int(#aniTime)}.png\"/>");
        DslAttributeValueNode v = attrValue(text, "textExp");
        assertFalse(v.isLiteral());
        assertTrue(v.getExpression().isEmpty(), "number/hour/{int(#system.time.hour1)}_{int(#aniTime)}.png needs ''; parse should fail");
    }

    @Test
    void expressionRangeIsDocumentRelative() {
        // 行3: `  <Var name="v" expression="#battery_level + 1"/>`
        // '#'位于文档(3,28)，'1'末尾(开区间)在(3,46)
        DslElementNode root = build("<?xml version=\"1.0\"?>\n"
                + "<Lockscreen>\n"
                + "  <Var name=\"v\" expression=\"#battery_level + 1\"/>\n"
                + "</Lockscreen>");
        DslElementNode var = root.getChildElements().get(0);
        ExpressionNode e = expr(var, "expression");

        // 根表达式区间应覆盖整个值 "#battery_level + 1"，且为文档绝对坐标
        assertEquals(ExpressionKind.BINARY_EXPR, e.getKind());
        assertEquals(3, e.getLine());
        assertEquals(28, e.getColumn());
        assertEquals(3, e.getEndLine());
        assertEquals(46, e.getEndColumn());

        // 左操作数 #battery_level：文档(3,28)->(3,42)
        ExpressionNode left = e.getChildren().get(0);
        assertEquals(ExpressionKind.VARIABLE_REF, left.getKind());
        assertEquals(3, left.getLine());
        assertEquals(28, left.getColumn());
        assertEquals(3, left.getEndLine());
        assertEquals(42, left.getEndColumn());

        // 右操作数字面量 1：文档(3,45)->(3,46)
        ExpressionNode right = e.getChildren().get(1);
        assertEquals(ExpressionKind.LITERAL, right.getKind());
        assertEquals(3, right.getLine());
        assertEquals(45, right.getColumn());
        assertEquals(3, right.getEndLine());
        assertEquals(46, right.getEndColumn());
    }
}
