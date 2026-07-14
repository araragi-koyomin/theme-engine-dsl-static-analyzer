package com.huawei.theme.analysis.core.batchinspection.model;
import java.util.Collections;
import java.util.List;
import com.huawei.theme.analysis.core.quickfix.FixAction;
import com.huawei.theme.analysis.core.quickfix.CandidateItem;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.shared.model.FixActionType;
import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileDiagnosticResultTest {
    @Test
    void builderCreatesResultWithAllFields() {
        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-REF-001")
                .message("test message")
                .filePath("test.xml")
                .line(1)
                .column(0)
                .build();
        FixAction fix = FixAction.builder()
                .fixType(FixActionType.ADD_ATTR)
                .targetRange(TextRange.builder().startLine(1).startColumn(0).endLine(1).endColumn(10).build())
                .replacementText("type=\"string\"")
                .description("add type attr")
                .build();
        FileDiagnosticResult result = FileDiagnosticResult.builder()
                .filePath("test.xml")
                .diagnostics(List.of(diag))
                .fixActions(List.of(fix))
                .build();
        assertEquals("test.xml", result.getFilePath());
        assertEquals(1, result.getDiagnostics().size());
        assertEquals(1, result.getFixActions().size());
        assertEquals(FixActionType.ADD_ATTR, result.getFixActions().get(0).getFixType());
        assertEquals("add type attr", result.getFixActions().get(0).getDescription());
    }

    @Test
    void builderDefaultValues() {
        FileDiagnosticResult result = FileDiagnosticResult.builder()
                .filePath("test.xml")
                .diagnostics(List.of())
                .fixActions(List.of())
                .build();
        assertNotNull(result.getFilePath());
        assertEquals(0, result.getDiagnostics().size());
        assertEquals(0, result.getFixActions().size());
    }

    @Test
    void builderWithMultipleDiagnosticsAndFixActions() {
        Diagnostic diag1 = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR).ruleId("E1").message("err1")
                .filePath("f.xml").line(1).column(0).build();
        Diagnostic diag2 = Diagnostic.builder()
                .severity(DiagnosticSeverity.WARNING).ruleId("W1").message("warn1")
                .filePath("f.xml").line(5).column(3).build();
        Diagnostic diag3 = Diagnostic.builder()
                .severity(DiagnosticSeverity.INFO).ruleId("I1").message("info1")
                .filePath("f.xml").line(10).column(1).build();
        FixAction fix1 = FixAction.builder()
                .fixType(FixActionType.SET_VALUE).replacementText("100")
                .description("clamp to max").build();
        FixAction fix2 = FixAction.builder()
                .fixType(FixActionType.REPLACE_ENUM).replacementText("dark")
                .description("replace enum value").build();
        FileDiagnosticResult result = FileDiagnosticResult.builder()
                .filePath("f.xml")
                .diagnostics(List.of(diag1, diag2, diag3))
                .fixActions(List.of(fix1, fix2))
                .build();
        assertEquals(3, result.getDiagnostics().size());
        assertEquals(2, result.getFixActions().size());
        assertEquals(DiagnosticSeverity.ERROR, result.getDiagnostics().get(0).getSeverity());
        assertEquals(DiagnosticSeverity.WARNING, result.getDiagnostics().get(1).getSeverity());
        assertEquals(DiagnosticSeverity.INFO, result.getDiagnostics().get(2).getSeverity());
        assertEquals(FixActionType.SET_VALUE, result.getFixActions().get(0).getFixType());
        assertEquals(FixActionType.REPLACE_ENUM, result.getFixActions().get(1).getFixType());
    }

    @Test
    void builderWithEmptyDiagnostics() {
        FileDiagnosticResult result = FileDiagnosticResult.builder()
                .filePath("clean.xml")
                .diagnostics(List.of())
                .fixActions(List.of())
                .build();
        assertEquals(0, result.getDiagnostics().size());
        assertEquals(0, result.getFixActions().size());
    }

    @Test
    void builderWithDiagnosticContainingSuggestedFixes() {
        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-REF-001")
                .message("undefined var")
                .filePath("test.xml")
                .line(5).column(3)
                .suggestedFixes(List.of(
                        com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix.builder()
                                .text("declare variable").build(),
                        com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix.builder()
                                .text("use global var").build()
                ))
                .build();
        FileDiagnosticResult result = FileDiagnosticResult.builder()
                .filePath("test.xml")
                .diagnostics(List.of(diag))
                .fixActions(List.of())
                .build();
        assertEquals(2, result.getDiagnostics().get(0).getSuggestedFixes().size());
        assertEquals("declare variable", result.getDiagnostics().get(0).getSuggestedFixes().get(0).getText());
    }

    @Test
    void builderWithFixActionContainingCandidates() {
        FixAction fix = FixAction.builder()
                .fixType(FixActionType.USE_ALTERNATIVE)
                .targetRange(TextRange.builder().startLine(5).startColumn(3).endLine(5).endColumn(10).build())
                .description("use alternative value")
                .candidates(List.of(
                        CandidateItem.builder().description("option A").previewText("10").similarityScore(0.9).build(),
                        CandidateItem.builder().description("option B").previewText("20").similarityScore(0.7).build()
                ))
                .build();
        FileDiagnosticResult result = FileDiagnosticResult.builder()
                .filePath("test.xml")
                .diagnostics(List.of())
                .fixActions(List.of(fix))
                .build();
        assertEquals(2, result.getFixActions().get(0).getCandidates().size());
        assertEquals("option A", result.getFixActions().get(0).getCandidates().get(0).getDescription());
        assertEquals(0.9, result.getFixActions().get(0).getCandidates().get(0).getSimilarityScore());
    }
}
