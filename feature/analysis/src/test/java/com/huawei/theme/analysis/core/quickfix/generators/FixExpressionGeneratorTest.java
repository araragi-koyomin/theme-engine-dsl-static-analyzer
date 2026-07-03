package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionGenerator;
import com.huawei.theme.analysis.core.shared.model.FixActionType;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixExpressionGeneratorTest {

    private final FixActionGenerator generator = new FixExpressionGenerator();

    @Test
    void generatesFixExpressionForUnaryMinusVar() {
        DslAttributeValueNode valueNode = new DslAttributeValueNode();
        valueNode.setRawValue("-#steps");
        valueNode.setLiteral(false);

        DslAttributeNode attrNode = new DslAttributeNode();
        attrNode.setName("x");
        attrNode.setLine(5);
        attrNode.setColumn(10);
        attrNode.setValue(valueNode);

        Diagnostic diagnostic = Diagnostic.builder()
                .ruleId("SYN-EXPR-001")
                .astNode(attrNode)
                .build();

        List<FixAction> actions = generator.generate(diagnostic);

        assertEquals(1, actions.size());
        FixAction action = actions.get(0);
        assertEquals(FixActionType.FIX_EXPRESSION, action.getFixType());
        assertEquals("x=\"-1*#steps\"", action.getReplacementText());
    }

    @Test
    void returnsEmptyWhenNoMatch() {
        DslAttributeValueNode valueNode = new DslAttributeValueNode();
        valueNode.setRawValue("#steps+1");
        valueNode.setLiteral(false);

        DslAttributeNode attrNode = new DslAttributeNode();
        attrNode.setName("x");
        attrNode.setLine(5);
        attrNode.setColumn(10);
        attrNode.setValue(valueNode);

        Diagnostic diagnostic = Diagnostic.builder()
                .ruleId("SYN-EXPR-001")
                .astNode(attrNode)
                .build();

        List<FixAction> actions = generator.generate(diagnostic);

        assertTrue(actions.isEmpty());
    }

    @Test
    void returnsEmptyWhenAstNodeNotAttribute() {
        DslElementNode elementNode = new DslElementNode();
        elementNode.setLine(1);
        elementNode.setColumn(1);

        Diagnostic diagnostic = Diagnostic.builder()
                .ruleId("SYN-EXPR-001")
                .astNode(elementNode)
                .build();

        List<FixAction> actions = generator.generate(diagnostic);

        assertTrue(actions.isEmpty());
    }
}
