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
 * 属性字面量类型分析器，规则SEM-TYPE-003。
 *
 * <p>对type="number"的属性，若值为纯字面量(isLiteral=true)且无法解析为Double则报告错误。
 * 表达式值(isLiteral=false)跳过，交由M4 SEM-TYPE-001/002处理。
 * 属性无AttrTypeSpec时跳过（未知属性由SYN-004处理）。</p>
 */
public class LiteralTypeAnalyzer extends BaseXmlAnalyzer {

    private static final String RULE_ID = "SEM-TYPE-003";

    public LiteralTypeAnalyzer() {
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
            DslAttributeValueNode value = attr.getValue();
            if (value == null) {
                continue;
            }
            if (!value.isLiteral()) {
                continue;
            }
            String rawValue = value.getRawValue();
            if (rawValue == null || !"number".equals(spec.getType())) {
                continue;
            }
            try {
                Double.parseDouble(rawValue);
            } catch (NumberFormatException e) {
                diagnostics.add(createDiagnostic(context, attr,
                        "属性值类型错误: " + attr.getName() + " 期望 number, 实际 " + rawValue));
            }
        }
        return diagnostics;
    }
}
