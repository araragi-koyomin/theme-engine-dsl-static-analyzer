package com.huawei.theme.analysis.core.semanticanalysis.analyzers;

import java.util.Collections;
import java.util.List;

import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;
import com.huawei.theme.analysis.core.semanticanalysis.DslAnalyzer;
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

/**
 * 单规则XML分析器基类。
 *
 * <p>每个子类绑定一条规则（ruleId+severity），只需实现doAnalyze聚焦检测逻辑。
 * 基类负责DslAstNode到DslElementNode的类型收敛，并通过RuleRepository.getRuleSource
 * 动态查询ruleDocUrl，使docUrl与rule_sources.json保持单一数据源。</p>
 */
public abstract class BaseXmlAnalyzer implements DslAnalyzer {

    private final String ruleId;
    private final DiagnosticSeverity severity;

    protected BaseXmlAnalyzer(String ruleId, DiagnosticSeverity severity) {
        this.ruleId = ruleId;
        this.severity = severity;
    }

    @Override
    public List<Diagnostic> analyze(DslAstNode element, DslContext context) {
        if (!(element instanceof DslElementNode elementNode)) {
            return Collections.emptyList();
        }
        return doAnalyze(elementNode, context);
    }

    protected Diagnostic createDiagnostic(DslContext context, DslAstNode astNode, String message) {
        String docUrl = context.getRuleRepository()
                .getRuleSource(ruleId)
                .map(RuleSource::getDocUrl)
                .orElse(null);

        return Diagnostic.builder()
                .severity(severity)
                .ruleId(ruleId)
                .message(message)
                .filePath(context.getFilePath())
                .astNode(astNode)
                .suggestedFixes(List.of())
                .ruleDocUrl(docUrl)
                .build();
    }

    protected abstract List<Diagnostic> doAnalyze(DslElementNode element, DslContext context);
}
