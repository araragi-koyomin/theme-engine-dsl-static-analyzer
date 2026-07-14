package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.huawei.theme.analysis.core.quickfix.CandidateItem;
import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionGenerator;
import com.huawei.theme.analysis.core.shared.model.FixActionType;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;

public class ReplaceEnumGenerator implements FixActionGenerator {

    private static final String RULE_ID = "SEM-ENUM-001";
    private final RuleRepository ruleRepository;

    public ReplaceEnumGenerator(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Override
    public String getRuleId() {
        return RULE_ID;
    }

    @Override
    public List<FixAction> generate(Diagnostic diagnostic) {
        DslAstNode astNode = diagnostic.getAstNode();
        DslAttributeNode attrNode = null;
        DslElementNode parentElement = null;

        if (astNode instanceof DslAttributeNode attr) {
            attrNode = attr;
            if (attr.getParent() instanceof DslElementNode elem) {
                parentElement = elem;
            }
        } else if (astNode instanceof DslElementNode elem) {
            parentElement = elem;
            attrNode = findAttrFromMessage(elem, diagnostic.getMessage());
        }

        if (attrNode == null || parentElement == null) {
            return Collections.emptyList();
        }

        String tagName = parentElement.getTagName();
        String attrName = attrNode.getName();
        Optional<AttrTypeSpec> specOpt = ruleRepository.getAttrTypeSpec(tagName, attrName);
        if (specOpt.isEmpty() || specOpt.get().getEnumValues() == null || specOpt.get().getEnumValues().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> enumValues = specOpt.get().getEnumValues();
        List<CandidateItem> candidates = new ArrayList<>();
        for (String value : enumValues) {
            candidates.add(CandidateItem.builder()
                    .description("替换为 " + value)
                    .previewText(attrName + "=\"" + value + "\"")
                    .similarityScore(1.0)
                    .build());
        }

        return List.of(FixAction.builder()
                .fixType(FixActionType.REPLACE_ENUM)
                .targetRange(TextRange.builder()
                        .startLine(attrNode.getLine())
                        .startColumn(attrNode.getColumn())
                        .endLine(attrNode.getLine())
                        .endColumn(attrNode.getColumn() + attrNode.getName().length() + 3
                                + (attrNode.getValue() != null && attrNode.getValue().getRawValue() != null
                                ? attrNode.getValue().getRawValue().length() : 0))
                        .build())
                .replacementText(attrName + "=\"" + enumValues.get(0) + "\"")
                .candidates(candidates)
                .description("替换枚举值: " + attrName)
                .build());
    }

    private DslAttributeNode findAttrFromMessage(DslElementNode elementNode, String message) {
        String prefix = "枚举值错误: ";
        if (message == null || !message.startsWith(prefix)) {
            return null;
        }
        String rest = message.substring(prefix.length());
        int eqIdx = rest.indexOf('=');
        if (eqIdx < 0) {
            return null;
        }
        String attrName = rest.substring(0, eqIdx);
        if (elementNode.getAttributes() != null) {
            for (DslAttributeNode attr : elementNode.getAttributes()) {
                if (attr.getName().equals(attrName)) {
                    return attr;
                }
            }
        }
        return null;
    }
}
