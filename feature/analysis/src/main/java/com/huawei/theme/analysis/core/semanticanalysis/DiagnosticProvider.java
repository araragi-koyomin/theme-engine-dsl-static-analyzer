package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.List;


import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

public interface DiagnosticProvider {
    List<Diagnostic> analyze(DslFileNode ast);
}
