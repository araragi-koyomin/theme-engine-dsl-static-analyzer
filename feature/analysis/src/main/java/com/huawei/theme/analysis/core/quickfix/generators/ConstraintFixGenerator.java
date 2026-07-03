package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionGenerator;
import com.huawei.theme.analysis.core.quickfix.FixActionIntent;
import com.huawei.theme.analysis.core.shared.model.FixActionType;
import com.huawei.theme.analysis.core.quickfix.SuggestedFixParser;
import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;

public class ConstraintFixGenerator implements FixActionGenerator {

    @Override
    public String getRuleId() {
        return "*";
    }

    @Override
    public List<FixAction> generate(Diagnostic diagnostic) {
        List<SuggestedFix> suggestedFixes = diagnostic.getSuggestedFixes();
        if (suggestedFixes == null || suggestedFixes.isEmpty()) {
            return Collections.emptyList();
        }
        DslAstNode astNode = diagnostic.getAstNode();
        if (!(astNode instanceof DslElementNode elementNode)) {
            return Collections.emptyList();
        }
        List<FixActionIntent> intents = SuggestedFixParser.parse(suggestedFixes);
        List<FixAction> actions = new ArrayList<>();
        for (FixActionIntent intent : intents) {
            actions.add(intentToFixAction(intent, elementNode));
        }
        return actions;
    }

    private FixAction intentToFixAction(FixActionIntent intent, DslElementNode elementNode) {
        FixActionType fixType = intent.getActionType();
        TextRange targetRange = buildRange(intent, elementNode);
        String replacementText = buildReplacement(intent);
        String description = intent.getDescription();
        return FixAction.builder()
                .fixType(fixType)
                .targetRange(targetRange)
                .replacementText(replacementText)
                .candidates(Collections.emptyList())
                .description(description)
                .build();
    }

    private TextRange buildRange(FixActionIntent intent, DslElementNode elementNode) {
        if (intent.getActionType() == FixActionType.REMOVE_ATTR) {
            DslAttributeNode attrNode = findAttr(elementNode, intent.getTargetName());
            if (attrNode != null) {
                return TextRange.builder()
                        .startLine(attrNode.getLine())
                        .startColumn(attrNode.getColumn())
                        .endLine(attrNode.getLine())
                        .endColumn(attrNode.getColumn())
                        .build();
            }
        }
        return TextRange.builder()
                .startLine(elementNode.getLine())
                .startColumn(elementNode.getColumn())
                .endLine(elementNode.getLine())
                .endColumn(elementNode.getColumn())
                .build();
    }

    private String buildReplacement(FixActionIntent intent) {
        FixActionType type = intent.getActionType();
        switch (type) {
            case REMOVE_ATTR:
            case REMOVE_CHILD:
            case DELETE_NODE:
                return "";
            case SET_VALUE:
            case CLAMP_VALUE:
                return intent.getTargetName() + "=\"" + intent.getTargetValue() + "\"";
            case ADD_ATTR:
                String value = intent.getTargetValue() != null ? intent.getTargetValue() : "";
                return intent.getTargetName() + "=\"" + value + "\"";
            case ADD_CHILD:
            case ADD_DECLARATION:
                return "<" + intent.getTargetName() + "/>";
            default:
                return "";
        }
    }

    private DslAttributeNode findAttr(DslElementNode element, String targetName) {
        String attrName = targetName.contains("/") ? targetName.split("/")[0] : targetName;
        List<DslAttributeNode> attributes = element.getAttributes();
        if (attributes == null) {
            return null;
        }
        for (DslAttributeNode attr : attributes) {
            if (attrName.equals(attr.getName())) {
                return attr;
            }
        }
        return null;
    }
}
