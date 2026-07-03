package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionGenerator;
import com.huawei.theme.analysis.core.shared.model.FixActionType;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;

public class InsertAttrGenerator implements FixActionGenerator {

    private static final String RULE_ID = "SEM-REQ-001";
    private final RuleRepository ruleRepository;

    public InsertAttrGenerator(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

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
        String tagName = elementNode.getTagName();
        if (ruleRepository.getElementRule(tagName).isEmpty()) {
            return Collections.emptyList();
        }
        String missingAttr = extractMissingAttrName(diagnostic.getMessage());
        if (missingAttr == null) {
            return Collections.emptyList();
        }
        Optional<AttrTypeSpec> specOpt = ruleRepository.getAttrTypeSpec(tagName, missingAttr);
        String defaultValue = specOpt.map(AttrTypeSpec::getDefaultValue).orElse(null);
        String replacementText = buildAttrInsertion(missingAttr, defaultValue);
        TextRange targetRange = TextRange.builder()
                .startLine(elementNode.getLine())
                .startColumn(elementNode.getColumn())
                .endLine(elementNode.getLine())
                .endColumn(elementNode.getColumn())
                .build();
        return List.of(FixAction.builder()
                .fixType(FixActionType.ADD_ATTR)
                .targetRange(targetRange)
                .replacementText(replacementText)
                .description("添加必填属性: " + missingAttr)
                .build());
    }

    private String extractMissingAttrName(String message) {
        String prefix = "缺失必填属性: ";
        if (message != null && message.startsWith(prefix)) {
            return message.substring(prefix.length());
        }
        return null;
    }

    private String buildAttrInsertion(String attrName, String defaultValue) {
        if (defaultValue != null) {
            return attrName + "=\"" + defaultValue + "\"";
        }
        return attrName + "=\"\"";
    }
}
