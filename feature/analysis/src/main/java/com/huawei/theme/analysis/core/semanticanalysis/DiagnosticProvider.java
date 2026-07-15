package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.List;

import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.cli.PipelineMode;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

public interface DiagnosticProvider {
    List<Diagnostic> analyze(
            DslFileNode ast,
            RuleRepository ruleRepo,
            SymbolTableBuilder symbolTableBuilder,
            PipelineMode mode,
            InspectionConfig config,
            VerboseCollector collector);
}
