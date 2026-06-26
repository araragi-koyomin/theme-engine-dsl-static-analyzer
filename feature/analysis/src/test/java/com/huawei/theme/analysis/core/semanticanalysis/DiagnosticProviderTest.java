package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DiagnosticProviderTest {

    @Test
    void analyzeFileReturnsDiagnosticList() {
        DiagnosticProvider provider = new StubDiagnosticProvider();
        List<Diagnostic> diagnostics = provider.analyzeFile("test.xml", "<Var/>");
        assertFalse(diagnostics.isEmpty());
        assertEquals(DiagnosticSeverity.ERROR, diagnostics.get(0).getSeverity());
        assertEquals("SEM-SCOPE-001", diagnostics.get(0).getRuleId());
    }

    @Test
    void analyzeFileReturnsEmptyListForValidContent() {
        DiagnosticProvider provider = new StubDiagnosticProvider();
        List<Diagnostic> diagnostics = provider.analyzeFile("valid.xml", "");
        assertEquals(0, diagnostics.size());
    }

    private static class StubDiagnosticProvider implements DiagnosticProvider {

        @Override
        public List<Diagnostic> analyzeFile(String filePath, String content) {
            if (content.isEmpty()) {
                return List.of();
            }
            return List.of(Diagnostic.builder()
                    .severity(DiagnosticSeverity.ERROR)
                    .ruleId("SEM-SCOPE-001")
                    .message("scope not allowed")
                    .filePath(filePath)
                    .line(1)
                    .column(0)
                    .build());
        }
    }
}
