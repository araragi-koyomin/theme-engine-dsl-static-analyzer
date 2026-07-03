package com.huawei.theme.analysis.core.quickfix.generators;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.FixActionGenerator;
import com.huawei.theme.analysis.core.shared.model.FixActionType;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;

public class FixExpressionGenerator implements FixActionGenerator {

    private static final String RULE_ID = "SYN-EXPR-001";
    private static final Pattern UNARY_MINUS_VAR = Pattern.compile("-#(\\w+)");

    @Override
    public String getRuleId() {
        return RULE_ID;
    }

    @Override
    public List<FixAction> generate(Diagnostic diagnostic) {
        DslAstNode astNode = diagnostic.getAstNode();
        if (!(astNode instanceof DslAttributeNode attrNode)) {
            return Collections.emptyList();
        }
        DslAttributeValueNode valueNode = attrNode.getValue();
        if (valueNode == null || valueNode.getRawValue() == null) {
            return Collections.emptyList();
        }
        String rawValue = valueNode.getRawValue();
        Matcher m = UNARY_MINUS_VAR.matcher(rawValue);
        if (!m.find()) {
            return Collections.emptyList();
        }

        String fixedValue = m.replaceAll("-1*#$1");
        return List.of(FixAction.builder()
                .fixType(FixActionType.FIX_EXPRESSION)
                .targetRange(TextRange.builder()
                        .startLine(attrNode.getLine())
                        .startColumn(attrNode.getColumn())
                        .endLine(attrNode.getLine())
                        .endColumn(attrNode.getColumn() + attrNode.getName().length() + 3 + rawValue.length())
                        .build())
                .replacementText(attrNode.getName() + "=\"" + fixedValue + "\"")
                .description("将 -#var 修正为 -1*#var")
                .build());
    }
}
