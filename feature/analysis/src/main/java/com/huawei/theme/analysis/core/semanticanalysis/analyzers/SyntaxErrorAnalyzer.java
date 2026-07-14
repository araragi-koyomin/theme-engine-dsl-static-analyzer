package com.huawei.theme.analysis.core.semanticanalysis.analyzers;

import java.util.Collections;
import java.util.List;

import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

public class SyntaxErrorAnalyzer extends BaseXmlAnalyzer {

    private static final String RULE_ID = "SYN-SAX-001";

    public SyntaxErrorAnalyzer() {
        super(RULE_ID, DiagnosticSeverity.ERROR);
    }

    @Override
    protected List<Diagnostic> doAnalyze(DslElementNode elementNode, DslContext context) {
        if (!elementNode.isHasError()) {
            return Collections.emptyList();
        }
        String message = "XML parse error: " + (elementNode.getErrorMessage() != null
                ? elementNode.getErrorMessage() : "unknown parse error");
        return List.of(createDiagnostic(context, elementNode, message));
    }
}
