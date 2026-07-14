package com.huawei.theme.analysis.core.semanticanalysis;

import java.util.List;

import com.huawei.theme.analysis.core.cli.InspectionConfig;
import com.huawei.theme.analysis.core.cli.PipelineMode;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DiagnosticProviderTest {

    private final AstBuilder astBuilder = new AstBuilder();

    @Test
    void analyzeReturnsDiagnosticList() {
        DslFileNode ast = astBuilder.getDslAst("test.xml", "<Var/>");
        DiagnosticProvider provider = new StubDiagnosticProvider();
        List<Diagnostic> diagnostics = provider.analyze(ast, null, null,
                PipelineMode.FULL, InspectionConfig.builder().build(), null); //TODO
        assertFalse(diagnostics.isEmpty());
        assertEquals(DiagnosticSeverity.ERROR, diagnostics.get(0).getSeverity());
        assertEquals("SEM-SCOPE-001", diagnostics.get(0).getRuleId());
        assertEquals("test.xml", diagnostics.get(0).getFilePath());
    }

    @Test
    void analyzeReturnsEmptyListForEmptyAst() {
        DslFileNode ast = astBuilder.getDslAst("valid.xml", "");
        DiagnosticProvider provider = new StubDiagnosticProvider();
        List<Diagnostic> diagnostics = provider.analyze(ast, null, null,
                PipelineMode.FULL, InspectionConfig.builder().build(), null); //TODO
        assertEquals(0, diagnostics.size());
    }

    private static class StubDiagnosticProvider implements DiagnosticProvider {

        @Override
        public List<Diagnostic> analyze(DslFileNode ast, RuleRepository ruleRepo, SymbolTableBuilder symbolTableBuilder,
                                        PipelineMode mode, InspectionConfig config, VerboseCollector collector) {
            if (ast.getRootElement() == null || ast.getRootElement().isHasError()) {
                return List.of();
            }
            return List.of(Diagnostic.builder()
                    .severity(DiagnosticSeverity.ERROR)
                    .ruleId("SEM-SCOPE-001")
                    .message("scope not allowed")
                    .filePath(ast.getFilePath())
                    .line(ast.getRootElement().getLine())
                    .column(ast.getRootElement().getColumn())
                    .build());
        }
    }
}
