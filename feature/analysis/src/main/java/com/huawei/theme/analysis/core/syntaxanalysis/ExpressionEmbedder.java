package com.huawei.theme.analysis.core.syntaxanalysis;

import java.util.Optional;

import com.huawei.theme.analysis.core.expression.ExpressionNode;
import com.huawei.theme.analysis.core.expression.ExpressionParser;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;

public final class ExpressionEmbedder {

    private ExpressionEmbedder() {
    }

    public static void embed(DslAttributeValueNode value, String attrValue, String tagName,
                             String attrName, int valueDocLine, int valueDocCol,
                             RuleRepository ruleRepository) {
        ExpressionAstNode exprNode = null;
        boolean parseAttempted = false;
        if (ruleRepository != null) {
            Optional<AttrTypeSpec> specOpt = ruleRepository.getAttrTypeSpec(tagName, attrName);
            if (specOpt.isPresent() && specOpt.get().isSupportsExpression()) {
                String expressionKind = specOpt.get().getExpressionKind();
                if (ExpressionParser.hasExpressionSyntax(attrValue, attrName)) {
                    parseAttempted = true;
                    exprNode = ExpressionParser.parseExpression(attrValue, expressionKind);
                }
            }
        }
        if (exprNode != null) {
            offsetExprToDocument((ExpressionNode) exprNode, valueDocLine, valueDocCol);
            value.setExpression(Optional.of(exprNode));
            value.setLiteral(false);
        } else if (parseAttempted) {
            value.setExpression(Optional.empty());
            value.setLiteral(false);
        } else {
            value.setExpression(Optional.empty());
            value.setLiteral(true);
        }
    }

    public static void offsetExprToDocument(ExpressionNode node, int valueDocLine, int valueDocCol) {
        if (node == null) {
            return;
        }
        int line = node.getLine();
        int col = node.getColumn();
        int endLine = node.getEndLine();
        int endCol = node.getEndColumn();
        node.setLine(valueDocLine + line - 1);
        node.setColumn(line == 1 ? valueDocCol + col : col);
        node.setEndLine(valueDocLine + endLine - 1);
        node.setEndColumn(endLine == 1 ? valueDocCol + endCol : endCol);
        if (node.getChildren() != null) {
            for (ExpressionNode child : node.getChildren()) {
                offsetExprToDocument(child, valueDocLine, valueDocCol);
            }
        }
        if (node.getIndexExpression() != null) {
            offsetExprToDocument(node.getIndexExpression(), valueDocLine, valueDocCol);
        }
    }
}
