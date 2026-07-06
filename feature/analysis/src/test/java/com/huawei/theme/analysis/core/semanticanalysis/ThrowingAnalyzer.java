package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.ArrayList;
import java.util.List;

import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

public class ThrowingAnalyzer implements DslAnalyzer {
    @Override
    public List<Diagnostic> analyze(DslAstNode element, DslContext context) {
        throw new RuntimeException("Simulated analyzer failure for testing");
    }
}
