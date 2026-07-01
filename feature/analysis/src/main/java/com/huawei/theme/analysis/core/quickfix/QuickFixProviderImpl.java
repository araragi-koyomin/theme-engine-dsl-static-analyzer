package com.huawei.theme.analysis.core.quickfix;

import java.util.Collections;
import java.util.List;

import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

public class QuickFixProviderImpl implements QuickFixProvider {

    @Override
    public List<FixAction> getFixActions(Diagnostic diagnostic) {
        if (diagnostic == null) {
            return Collections.emptyList();
        }
        return FixActionRegistry.getGenerator(diagnostic.getRuleId())
                .map(generator -> generator.generate(diagnostic))
                .orElse(Collections.emptyList());
    }
}
