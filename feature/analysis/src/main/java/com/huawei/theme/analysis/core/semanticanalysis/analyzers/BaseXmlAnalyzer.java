package com.huawei.theme.analysis.core.semanticanalysis.analyzers;

import com.huawei.theme.analysis.core.semanticanalysis.DslAnalyzer;
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import java.util.List;

public abstract class BaseXmlAnalyzer implements DslAnalyzer {

    protected String ruleId;
    protected String docUrl;

    protected DiagnosticSeverity severity;

    public BaseXmlAnalyzer(String ruleId, DiagnosticSeverity severity, String docUrl) {
        this.ruleId = ruleId;
        this.severity = severity;
        this.docUrl = docUrl;
    }

    protected Diagnostic createDiagnostic(String filePath, DslAstNode astNode, String message) {
        return Diagnostic.builder()
                .severity(severity)
                .ruleId(ruleId)
                .message(message)
                .filePath(filePath)
                .astNode(astNode)
                .suggestedFixes(List.of()) //TODO
                .ruleDocUrl(docUrl)
                .build();
    }

    @Override
    public abstract List<Diagnostic> analyze(DslAstNode element, DslContext context);

}
