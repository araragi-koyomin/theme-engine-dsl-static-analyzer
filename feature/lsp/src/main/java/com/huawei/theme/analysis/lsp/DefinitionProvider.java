package com.huawei.theme.analysis.lsp;

import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;

import com.huawei.theme.analysis.core.expression.ExpressionNode;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;

/**
 * Resolves a variable-reference cursor to its {@code <Var name="...">} definition
 * location within the current file. Mirrors {@link HoverProvider}'s structural
 * conventions: final class, constructor-injected {@link RuleRepository}, pure
 * function (no state, all dependencies via parameters).
 */
final class DefinitionProvider {

    private final RuleRepository ruleRepository;

    DefinitionProvider(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    List<Location> definition(ContextResolver.Context ctx, DslFileNode ast,
                              String uri, PositionMapper mapper) {
        if (ctx == null || ctx.exprNode == null) {
            return List.of();
        }
        if (ast == null || ast.getRootElement() == null) {
            return List.of();
        }
        ExpressionAstNode exprNode = ctx.exprNode;
        ExpressionKind kind = exprNode.getKind();
        if (kind != ExpressionKind.VARIABLE_REF && kind != ExpressionKind.ARRAY_ACCESS) {
            return List.of();
        }
        String varName = ((ExpressionNode) exprNode).getVariableName();
        if (varName == null || varName.isEmpty()) {
            return List.of();
        }
        DslAttributeValueNode nameValue = findVarNameValue(ast.getRootElement(), varName);
        if (nameValue == null) {
            return List.of();
        }
        return List.of(toLocation(nameValue, uri, mapper));
    }

    private DslAttributeValueNode findVarNameValue(DslElementNode element, String varName) {
        if (element == null) {
            return null;
        }
        if (isVariableElement(element.getTagName())) {
            DslAttributeValueNode v = matchNameAttr(element, varName);
            if (v != null) {
                return v;
            }
        }
        List<DslElementNode> children = element.getChildElements();
        if (children != null) {
            for (DslElementNode child : children) {
                DslAttributeValueNode v = findVarNameValue(child, varName);
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    private DslAttributeValueNode matchNameAttr(DslElementNode element, String varName) {
        List<DslAttributeNode> attrs = element.getAttributes();
        if (attrs == null) {
            return null;
        }
        for (DslAttributeNode attr : attrs) {
            if ("name".equals(attr.getName()) && attr.getValue() != null
                    && varName.equals(attr.getValue().getRawValue())) {
                return attr.getValue();
            }
        }
        return null;
    }

    private boolean isVariableElement(String tagName) {
        if (tagName == null) {
            return false;
        }
        Optional<DslElementRule> rule = ruleRepository.getElementRule(tagName);
        return rule.isPresent() && "variable".equals(rule.get().getCategory());
    }

    private Location toLocation(DslAttributeValueNode nameValue, String uri, PositionMapper mapper) {
        Range range = new Range(
                mapper.toPosition(nameValue.getLine(), nameValue.getColumn()),
                mapper.toPosition(nameValue.getEndLine(), nameValue.getEndColumn()));
        return new Location(uri, range);
    }
}
