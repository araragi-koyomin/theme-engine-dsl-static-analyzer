package com.huawei.theme.analysis.core.quickfix.generators;

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

public class ClampValueGenerator implements FixActionGenerator {

    private static final String RULE_ID = "SEM-ATTR-001";

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
        FixActionIntent clampIntent = null;
        for (FixActionIntent intent : intents) {
            if (intent.getActionType() == FixActionType.CLAMP_VALUE) {
                clampIntent = intent;
                break;
            }
        }
        if (clampIntent == null) {
            return Collections.emptyList();
        }
        String attrName = clampIntent.getTargetName();
        String upperBound = extractUpperBound(clampIntent.getTargetValue());
        DslAttributeNode attr = findAttribute(elementNode, attrName);
        if (attr == null) {
            return Collections.emptyList();
        }
        String replacementText = attrName + "=\"" + upperBound + "\"";
        TextRange targetRange = buildAttrRange(attr);
        return List.of(FixAction.builder()
                .fixType(FixActionType.CLAMP_VALUE)
                .targetRange(targetRange)
                .replacementText(replacementText)
                .description("限制" + attrName + "值在有效范围内")
                .build());
    }

    String extractUpperBound(String range) {
        if (range == null) {
            return null;
        }
        if (range.contains("-")) {
            return range.substring(range.lastIndexOf("-") + 1);
        }
        return range;
    }

    DslAttributeNode findAttribute(DslElementNode elementNode, String attrName) {
        List<DslAttributeNode> attributes = elementNode.getAttributes();
        if (attributes == null) {
            return null;
        }
        for (DslAttributeNode attr : attributes) {
            if (attr.getName() != null && attr.getName().equals(attrName)) {
                return attr;
            }
        }
        return null;
    }

    TextRange buildAttrRange(DslAttributeNode attr) {
        int startLine = attr.getLine();
        int startColumn = attr.getColumn();
        int nameLength = attr.getName() != null ? attr.getName().length() : 0;
        int valueLength = attr.getValue() != null && attr.getValue().getRawValue() != null
                ? attr.getValue().getRawValue().length() : 0;
        int totalLength = nameLength + 2 + valueLength + 1;
        int endColumn = startColumn + totalLength;
        return TextRange.builder()
                .startLine(startLine)
                .startColumn(startColumn)
                .endLine(startLine)
                .endColumn(endColumn)
                .build();
    }
}
