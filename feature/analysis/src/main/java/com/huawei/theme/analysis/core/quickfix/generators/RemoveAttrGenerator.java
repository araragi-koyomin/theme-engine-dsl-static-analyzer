package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionGenerator;
import com.huawei.theme.analysis.core.quickfix.FixActionIntent;
import com.huawei.theme.analysis.core.quickfix.SuggestedFixParser;
import com.huawei.theme.analysis.core.shared.model.FixActionType;
import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;

public class RemoveAttrGenerator implements FixActionGenerator {

    private static final String RULE_ID = "SEM-CMD-001";

    @Override
    public String getRuleId() {
        return RULE_ID;
    }

    @Override
    public List<FixAction> generate(Diagnostic diagnostic) {
        DslAstNode astNode = diagnostic.getAstNode();
        if (!(astNode instanceof DslElementNode elementNode)) {
            return Collections.emptyList();
        }
        List<SuggestedFix> suggestedFixes = diagnostic.getSuggestedFixes();
        if (suggestedFixes == null || suggestedFixes.isEmpty()) {
            return Collections.emptyList();
        }
        List<FixActionIntent> intents = SuggestedFixParser.parse(suggestedFixes);
        List<FixAction> actions = new ArrayList<>();
        for (FixActionIntent intent : intents) {
            if (intent.getActionType() == FixActionType.REMOVE_ATTR) {
                DslAttributeNode attrNode = findAttribute(elementNode, intent.getTargetName());
                if (attrNode != null) {
                    actions.add(buildRemoveAttrAction(attrNode, intent));
                } else {
                    actions.add(buildRemoveAttrActionFromName(elementNode, intent));
                }
            }
        }
        return actions;
    }

    private DslAttributeNode findAttribute(DslElementNode element, String targetName) {
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

    private FixAction buildRemoveAttrAction(DslAttributeNode attr, FixActionIntent intent) {
        TextRange targetRange = TextRange.builder()
                .startLine(attr.getLine())
                .startColumn(attr.getColumn())
                .endLine(attr.getLine())
                .endColumn(attr.getColumn())
                .build();
        return FixAction.builder()
                .fixType(FixActionType.REMOVE_ATTR)
                .targetRange(targetRange)
                .replacementText("")
                .description(intent.getDescription() != null ? intent.getDescription() : "移除" + attr.getName() + "属性")
                .build();
    }

    private FixAction buildRemoveAttrActionFromName(DslElementNode element, FixActionIntent intent) {
        TextRange targetRange = TextRange.builder()
                .startLine(element.getLine())
                .startColumn(element.getColumn())
                .endLine(element.getLine())
                .endColumn(element.getColumn())
                .build();
        return FixAction.builder()
                .fixType(FixActionType.REMOVE_ATTR)
                .targetRange(targetRange)
                .replacementText("")
                .description(intent.getDescription() != null ? intent.getDescription() : "移除" + intent.getTargetName() + "属性")
                .build();
    }
}
