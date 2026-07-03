package com.huawei.theme.analysis.lsp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
