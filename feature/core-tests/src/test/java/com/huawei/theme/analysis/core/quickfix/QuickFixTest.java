package com.huawei.theme.analysis.core.quickfix;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.shared.diagnostic.TextRange;
import com.huawei.theme.analysis.core.shared.model.FixActionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickFixTest {

    @Test
    void candidateItemBuilder() {
        CandidateItem item = CandidateItem.builder()
                .description("替换为 <Theme>")
                .previewText("<Theme/>")
                .similarityScore(0.87)
                .build();
        assertEquals("替换为 <Theme>", item.getDescription());
        assertEquals("<Theme/>", item.getPreviewText());
        assertEquals(0.87, item.getSimilarityScore(), 1e-9);
    }

    @Test
    void fixActionDefaultsCandidatesEmpty() {
        FixAction action = FixAction.builder()
                .fixType(FixActionType.ADD_ATTR)
                .targetRange(TextRange.builder().startLine(5).startColumn(1).endLine(5).endColumn(1).build())
                .replacementText("<Var name=\"steps_value\" expression=\"0\"/>")
                .description("声明Var name=steps_value")
                .build();
        assertTrue(action.getCandidates().isEmpty());
    }

    @Test
    void fixActionBuilderWithCandidates() {
        CandidateItem item = CandidateItem.builder()
                .description("替换为 Theme")
                .previewText("<Theme/>")
                .similarityScore(0.9)
                .build();
        FixAction action = FixAction.builder()
                .fixType(FixActionType.UNKNOWN)
                .targetRange(TextRange.builder().startLine(3).startColumn(2).endLine(3).endColumn(15).build())
                .replacementText("<Theme/>")
                .candidates(List.of(item))
                .description("替换未知元素")
                .build();
        assertEquals(FixActionType.UNKNOWN, action.getFixType());
        assertEquals(1, action.getCandidates().size());
        assertEquals("替换为 Theme", action.getCandidates().get(0).getDescription());
        assertEquals(3, action.getTargetRange().getStartLine());
        assertEquals(15, action.getTargetRange().getEndColumn());
    }

    @Test
    void quickFixProviderTransfersDiagnosticToFixActions() {
        Diagnostic diag = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-REF-001")
                .message("引用未定义变量 #steps_value")
                .filePath("theme.xml")
                .line(15)
                .column(3)
                .build();
        QuickFixProvider provider = diagnostic -> List.of(
                FixAction.builder()
                        .fixType(FixActionType.ADD_ATTR)
                        .targetRange(TextRange.builder().startLine(5).startColumn(1).endLine(5).endColumn(1).build())
                        .replacementText("<Var name=\"steps_value\" expression=\"0\"/>")
                        .description("声明Var name=steps_value")
                        .build()
        );
        List<FixAction> actions = provider.getFixActions(diag);
        assertEquals(1, actions.size());
        assertEquals(FixActionType.ADD_ATTR, actions.get(0).getFixType());
        assertEquals("声明Var name=steps_value", actions.get(0).getDescription());
        assertEquals(5, actions.get(0).getTargetRange().getStartLine());
    }
}
