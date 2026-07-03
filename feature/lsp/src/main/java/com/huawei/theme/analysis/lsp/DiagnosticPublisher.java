package com.huawei.theme.analysis.lsp;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/**
 * Converts core diagnostics to LSP diagnostics.
 *
 * <p>Core {@link com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic}
 * carries 1-based line / 0-based column plus a rule id; this maps them to an
 * LSP {@link Diagnostic} with range, severity, source and code.</p>
 */
final class DiagnosticPublisher {

    static final String SOURCE = "dsl-analyzer";

    List<Diagnostic> toLspDiagnostics(
            List<com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic> core,
            PositionMapper mapper) {
        List<Diagnostic> result = new ArrayList<>(core.size());
        for (var d : core) {
            Range range = mapper.toRange(d);
            DiagnosticSeverity sev = toLspSeverity(d.getSeverity());
            String message = d.getMessage() == null ? "" : d.getMessage();
            Diagnostic lsp = new Diagnostic(range, message, sev, SOURCE);
            if (d.getRuleId() != null && !d.getRuleId().isEmpty()) {
                lsp.setCode(Either.forLeft(d.getRuleId()));
            }
            result.add(lsp);
        }
        return result;
    }

    private static DiagnosticSeverity toLspSeverity(
            com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity severity) {
        if (severity == null) {
            return DiagnosticSeverity.Hint;
        }
        switch (severity) {
            case ERROR:
                return DiagnosticSeverity.Error;
            case WARNING:
                return DiagnosticSeverity.Warning;
            case INFO:
            default:
                return DiagnosticSeverity.Information;
        }
    }
}
