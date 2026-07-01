package com.huawei.theme.analysis.plugin.editor;

import com.intellij.lexer.Lexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the DslExpression highlighting lexer produces a contiguous token
 * sequence covering the whole buffer, even when the ANTLR grammar skips
 * whitespace (the {@code "5 -"} case that crashed {@code LexerEditorHighlighter}
 * with {@code IndexOutOfBoundsException}).
 */
class DslExpressionHighlightingLexerTest {

    @Test
    void coversBufferContiguouslyWithSkippedWhitespace() {
        Lexer lexer = new DslExpressionSyntaxHighlighterFactory()
                .getSyntaxHighlighter(null, null).getHighlightingLexer();

        String text = "5 -";
        lexer.start(text, 0, text.length(), 0);

        int prevEnd = 0;
        IElementType wsType = null;
        int count = 0;
        while (lexer.getTokenType() != null) {
            assertEquals(prevEnd, lexer.getTokenStart(),
                    "gap/discontinuity before token #" + count + " at " + lexer.getTokenStart());
            if (TokenType.WHITE_SPACE.equals(lexer.getTokenType())) {
                wsType = lexer.getTokenType();
            }
            prevEnd = lexer.getTokenEnd();
            lexer.advance();
            count++;
        }

        assertEquals(text.length(), prevEnd,
                "last token must end at buffer end (" + text.length() + ") but ended at " + prevEnd);
        assertNotNull(wsType, "expected a synthesized WHITE_SPACE token for the skipped space");
    }
}
