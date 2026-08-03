package com.huawei.theme.analysis.core.syntaxanalysis;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.expression.ExpressionNode;
import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionEmbedderTest {

    private final RuleRepository repo = new JsonRuleLoader()
            .loadFromDirectory(System.getProperty("user.dir") + "/src/main/resources/rules");

    private DslAttributeValueNode newValue() {
        DslAttributeValueNode v = new DslAttributeValueNode();
        v.setLine(3);
        v.setColumn(20);
        return v;
    }

    @Test
    void embedsHashVarRefForVarExpressionAttr() {
        DslAttributeValueNode v = newValue();
        ExpressionEmbedder.embed(v, "#battery_level", "Var", "expression", 3, 20, repo);
        assertFalse(v.isLiteral());
        Optional<ExpressionAstNode> opt = v.getExpression();
        assertTrue(opt.isPresent());
        ExpressionNode e = (ExpressionNode) opt.get();
        assertEquals(ExpressionKind.VARIABLE_REF, e.getKind());
        assertEquals("#", e.getPrefix());
        assertEquals("battery_level", e.getVariableName());
        assertEquals(3, e.getLine());
        assertEquals(20, e.getColumn());
    }

    @Test
    void literalWhenRepoIsNull() {
        DslAttributeValueNode v = newValue();
        ExpressionEmbedder.embed(v, "#battery_level", "Var", "expression", 3, 20, null);
        assertTrue(v.isLiteral());
        assertTrue(v.getExpression().isEmpty());
    }

    @Test
    void parseFailureLeavesNonLiteralWithEmptyExpression() {
        DslAttributeValueNode v = newValue();
        ExpressionEmbedder.embed(v, "#x+", "Image", "x", 3, 20, repo);
        assertFalse(v.isLiteral());
        assertTrue(v.getExpression().isEmpty());
    }
}
