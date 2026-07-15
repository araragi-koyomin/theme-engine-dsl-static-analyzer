package com.huawei.theme.analysis.lsp;

import org.junit.jupiter.api.Test;

import org.eclipse.lsp4j.Range;

import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionMapperTest {

    @Test
    void oneBasedLineToZeroBased() {
        PositionMapper m = new PositionMapper("a\nbc\nde");
        // core line 1 (1-based) -> LSP line 0, column 0 -> character 0
        assertEquals(0, m.toPosition(1, 0).getLine());
        assertEquals(0, m.toPosition(1, 0).getCharacter());
        // core line 2 -> LSP line 1, column 1 -> character 1
        assertEquals(1, m.toPosition(2, 1).getLine());
        assertEquals(1, m.toPosition(2, 1).getCharacter());
        // core line 3 -> LSP line 2
        assertEquals(2, m.toPosition(3, 0).getLine());
    }

    @Test
    void clampsColumnBeyondLineEnd() {
        PositionMapper m = new PositionMapper("abc\nxy");
        // line 1 has 3 chars; column 100 should clamp to 3
        assertEquals(3, m.toPosition(1, 100).getCharacter());
        // line 2 has 2 chars; column 100 clamps to 2
        assertEquals(2, m.toPosition(2, 100).getCharacter());
    }

    @Test
    void clampsLineBeyondDocumentEnd() {
        PositionMapper m = new PositionMapper("ab\ncd");
        // line 99 -> last line (index 1)
        assertEquals(1, m.toPosition(99, 0).getLine());
    }

    @Test
    void toOffsetRoundTrip() {
        // "ab\ncde\nf" -> line 0: ab, line 1: cde, line 2: f
        PositionMapper m = new PositionMapper("ab\ncde\nf");
        // line 1 char 2 -> offset 3 (start of line 1) + 2 = 5 (the 'e')
        assertEquals(5, m.toOffset(1, 2));
        // line 2 char 0 -> offset 7 (start of line 2, the 'f')
        assertEquals(7, m.toOffset(2, 0));
    }

    @Test
    void toRangeUsesExplicitEndCoordsWithoutAstNode() {
        // VarRefAnalyzer-style diagnostic: position set directly, astNode null.
        // Previously rendered zero-width (end==start) because computeEnd fell
        // back to astNode text only; must now use the explicit end coords.
        PositionMapper m = new PositionMapper("abcdefgh");
        Diagnostic d = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("SEM-REF-001")
                .message("引用未定义变量 x")
                .filePath("test.xml")
                .line(1)
                .column(3)
                .endLine(1)
                .endColumn(7)
                .build();
        Range r = m.toRange(d);
        assertEquals(0, r.getStart().getLine());
        assertEquals(3, r.getStart().getCharacter());
        assertEquals(0, r.getEnd().getLine());
        assertEquals(7, r.getEnd().getCharacter());
        assertTrue(r.getEnd().getCharacter() > r.getStart().getCharacter(),
                "range must be non-zero-width for astNode-less diagnostics");
    }

    @Test
    void toRangeFallsBackToAstNodeTextWhenEndUnset() {
        // astNode set but endLine/endColumn left at 0: extend by text length
        // (historical single-line behavior).
        PositionMapper m = new PositionMapper("abcdefgh");
        DslElementNode node = new DslElementNode();
        node.setText("abc");
        node.setLine(1);
        node.setColumn(2);
        node.setEndLine(1);
        node.setEndColumn(5);
        Diagnostic d = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("X")
                .message("m")
                .filePath("test.xml")
                .line(1)
                .column(2)
                .build();
        d.setAstNode(node);
        // endLine/endColumn are 0 here -> astNode text fallback (start + 3)
        Range r = m.toRange(d);
        assertEquals(2, r.getStart().getCharacter());
        assertEquals(5, r.getEnd().getCharacter());
    }

    @Test
    void toRangePointDiagnosticWhenNoEndNoAstNode() {
        // No end coords and no astNode -> genuinely zero-width (point).
        PositionMapper m = new PositionMapper("abcdefgh");
        Diagnostic d = Diagnostic.builder()
                .severity(DiagnosticSeverity.ERROR)
                .ruleId("X")
                .message("m")
                .filePath("test.xml")
                .line(1)
                .column(3)
                .build();
        Range r = m.toRange(d);
        assertEquals(r.getStart(), r.getEnd());
    }
}
