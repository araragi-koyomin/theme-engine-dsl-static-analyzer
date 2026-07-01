package com.huawei.theme.analysis.core.semanticanalysis.analyzers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

/**
 * 枚举值分析器，规则SEM-ENUM-001。
 *
 * <p>对带enumValues的属性，若字面量值不在合法集合中则报告错误。
 * 表达式值跳过。属性无AttrTypeSpec或无enumValues时跳过。</p>
 */
public class EnumValueAnalyzer extends BaseXmlAnalyzer {

    private static final String RULE_ID = "SEM-ENUM-001";

    public EnumValueAnalyzer() {
        super(RULE_ID, DiagnosticSeverity.ERROR);
    }

    @Override
    protected List<Diagnostic> doAnalyze(DslElementNode elementNode, DslContext context) {
        if (elementNode.getAttributes() == null) {
            return Collections.emptyList();
        }
        RuleRepository ruleRepo = context.getRuleRepository();
        String tagName = elementNode.getTagName();
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (DslAttributeNode attr : elementNode.getAttributes()) {
            Optional<AttrTypeSpec> specOpt = ruleRepo.getAttrTypeSpec(tagName, attr.getName());
            if (specOpt.isEmpty()) {
                continue;
            }
            AttrTypeSpec spec = specOpt.get();
            if (spec.getEnumValues() == null || spec.getEnumValues().isEmpty()) {
                continue;
            }
            DslAttributeValueNode value = attr.getValue();
            if (value == null || !value.isLiteral()) {
                continue;
            }
            String rawValue = value.getRawValue();
            if (rawValue == null) {
                continue;
            }
            if (!spec.getEnumValues().contains(rawValue)) {
                diagnostics.add(createDiagnostic(context, attr,
                        "枚举值错误: " + attr.getName() + "=" + rawValue + ", 合法值: " + spec.getEnumValues()));
            }
        }
        return diagnostics;
    }
}
