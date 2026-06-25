package com.huawei.theme.analysis.core.syntaxanalysis;

import com.huawei.theme.analysis.core.shared.ast.DslFileNode;

public interface DslAstProvider {
    DslFileNode getDslAst(String filePath, String content);
}
