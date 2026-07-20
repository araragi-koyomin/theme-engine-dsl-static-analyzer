package com.huawei.theme.analysis.lsp;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the server emits semantic tokens for the whole document — XML
 * structure (tag, attribute, comment, declaration) and embedded expressions
 * (variable) — using standard LSP token types so any client (VS Code, Neovim,
 * Helix) auto-maps them to theme colors.
 */
class SemanticTokensProviderTest {

    private final RuleRepository repo = new RuleRepositoryFactory(null).create();
    private final SemanticTokensProvider provider = new SemanticTokensProvider(repo);

    private static final int TYPE_VARIABLE = 0;
    private static final int TYPE_FUNCTION = 1;
    private static final int TYPE_NUMBER = 2;
    private static final int TYPE_STRING = 3;
    private static final int TYPE_TAG = 4;
    private static final int TYPE_TAG_ROOT = 5;
    private static final int TYPE_TAG_VARIABLE = 6;
    private static final int TYPE_TAG_COMMAND = 7;
    private static final int TYPE_ATTRIBUTE = 8;
    private static final int TYPE_COMMENT = 9;
    private static final int TYPE_KEYWORD = 10;
    private static final int TYPE_VARIABLE_DEF = 11;

    @Test
    void legendUsesStandardTypesWithExpressionsFirst() {
        assertEquals("variable", SemanticTokensProvider.TOKEN_TYPES.get(0));
        assertEquals("function", SemanticTokensProvider.TOKEN_TYPES.get(1));
        assertEquals("number", SemanticTokensProvider.TOKEN_TYPES.get(2));
        assertEquals("string", SemanticTokensProvider.TOKEN_TYPES.get(3));
        assertEquals("tag", SemanticTokensProvider.TOKEN_TYPES.get(4));
        assertEquals("tagRoot", SemanticTokensProvider.TOKEN_TYPES.get(5));
        assertEquals("tagVariable", SemanticTokensProvider.TOKEN_TYPES.get(6));
        assertEquals("tagCommand", SemanticTokensProvider.TOKEN_TYPES.get(7));
        assertEquals("property", SemanticTokensProvider.TOKEN_TYPES.get(8));
        assertEquals("comment", SemanticTokensProvider.TOKEN_TYPES.get(9));
        assertEquals("keyword", SemanticTokensProvider.TOKEN_TYPES.get(10));
        assertEquals("variableDef", SemanticTokensProvider.TOKEN_TYPES.get(11));
        assertTrue(SemanticTokensProvider.TOKEN_MODIFIERS.isEmpty());
    }

    @Test
    void emitsTagAttributeAndExpressionTokens() {
        // '<'(0)T(1)e(2)x(3)t(4)' '(5)x(6)=(7)"(8)#(9)v(10)"(11)/(12)>(13)
        List<int[]> tokens = decode(provider.collect("test.xml", "<Text x=\"#v\"/>"));
        // tag "Text" at (0,1) len 4 — Text is category=view → default tag
        assertToken(tokens, 0, 1, 4, TYPE_TAG);
        // attribute "x" at (0,6) len 1
        assertToken(tokens, 0, 6, 1, TYPE_ATTRIBUTE);
        // expression variable "#v" at (0,9) len 2
        assertToken(tokens, 0, 9, 2, TYPE_VARIABLE);
    }

    @Test
    void emitsDifferentTagTypesByCategory() {
        // <Lockscreen> → root → tagRoot; <Var> → variable → tagVariable;
        // <Command> → command → tagCommand; <Text> → view → tag
        String text = "<Lockscreen><Var name=\"v\"/><Command target=\"x.visibility\"/><Text x=\"0\"/></Lockscreen>";
        List<int[]> tokens = decode(provider.collect("test.xml", text));
        assertTrue(hasToken(tokens, TYPE_TAG_ROOT));
        assertTrue(hasToken(tokens, TYPE_TAG_VARIABLE));
        assertTrue(hasToken(tokens, TYPE_TAG_COMMAND));
        assertTrue(hasToken(tokens, TYPE_TAG));
    }

    @Test
    void emitsLiteralAttrValueTokens() {
        // <Widget screenWidth="1080" align="center" enableMove="true" name="img"/>
        // "1080" → number, "center" → string, "true" → keyword, "img" → variableDef
        String text = "<Widget screenWidth=\"1080\" align=\"center\" enableMove=\"true\" name=\"img\"/>";
        List<int[]> tokens = decode(provider.collect("test.xml", text));
        assertTrue(hasToken(tokens, TYPE_NUMBER), "numeric attr value should emit number");
        assertTrue(hasToken(tokens, TYPE_STRING), "string attr value should emit string");
        assertTrue(hasToken(tokens, TYPE_KEYWORD), "boolean attr value should emit keyword");
        assertTrue(hasToken(tokens, TYPE_VARIABLE_DEF), "name attr value should emit variableDef");
    }

    @Test
    void emitsCommentToken() {
        // "<!--c-->" at (0,0) len 8
        List<int[]> tokens = decode(provider.collect("test.xml", "<!--c-->"));
        assertToken(tokens, 0, 0, 8, TYPE_COMMENT);
    }

    @Test
    void emitsXmlDeclarationToken() {
        // "<?xml version=\"1.0\"?>" at (0,0) len 21
        List<int[]> tokens = decode(provider.collect("test.xml", "<?xml version=\"1.0\"?>"));
        assertToken(tokens, 0, 0, 21, TYPE_KEYWORD);
    }

    @Test
    void emitsFunctionAndLiteralTokens() {
        // <Text x="sin(#v, '1')"/> -> function "sin" + variable #v + string '1'
        List<int[]> tokens = decode(provider.collect("test.xml", "<Text x=\"sin(#v, '1')\"/>"));
        assertTrue(hasToken(tokens, TYPE_TAG));            // "Text"
        assertTrue(hasToken(tokens, TYPE_ATTRIBUTE));     // "x"
        assertTrue(hasToken(tokens, TYPE_FUNCTION));      // "sin"
        assertTrue(hasToken(tokens, TYPE_VARIABLE));      // #v
        assertTrue(hasToken(tokens, TYPE_STRING));        // '1'
    }

    @Test
    void emitsTokensAcrossMultipleLines() {
        // FIX004 b2 LSP HIGH: was theater — used `hasToken(tokens, TYPE)` which
        // only checks a token of that type exists ANYWHERE, not that tokens span
        // multiple lines. The name "AcrossMultipleLines" was unverified. Canary:
        // mutate deltaEncode to emit deltaLine=0 always (collapse all tokens to
        // line 0) → original test still passed = theater confirmed. Now: use
        // assertToken to verify tokens are on SPECIFIC lines (0, 1, 2) so the
        // multi-line claim is actually verified.
        String text = "<?xml version=\"1.0\"?>\n"
                + "<!-- comment -->\n"
                + "<Lockscreen frameRate=\"60\"><Text x=\"#v\"/></Lockscreen>";
        List<int[]> tokens = decode(provider.collect("test.xml", text));
        // line 0: XML declaration keyword
        assertToken(tokens, 0, 0, 21, TYPE_KEYWORD);
        // line 1: comment
        assertToken(tokens, 1, 0, 16, TYPE_COMMENT);
        // line 2: Lockscreen(tagRoot) + frameRate attr + "60" number + Text tag + x attr + #v var
        assertToken(tokens, 2, 1, 10, TYPE_TAG_ROOT);  // "Lockscreen" — root tag
        assertToken(tokens, 2, 12, 9, TYPE_ATTRIBUTE);   // "frameRate"
        assertToken(tokens, 2, 23, 2, TYPE_NUMBER);      // "60" literal attr value
        assertToken(tokens, 2, 28, 4, TYPE_TAG);         // "Text"
        assertToken(tokens, 2, 33, 1, TYPE_ATTRIBUTE);   // "x"
        assertToken(tokens, 2, 36, 2, TYPE_VARIABLE);    // "#v"
    }

    @Test
    void malformedXmlStillEmitsCommentTokens() {
        // AST parse fails, but comment text-scan still recovers structural
        // comment ranges.
        List<int[]> tokens = decode(provider.collect("test.xml", "<!--c-->< unclosed"));
        assertToken(tokens, 0, 0, 8, TYPE_COMMENT);
    }

    /** Delta-decodes the LSP flat list into absolute {line, col, length, type}. */
    private static List<int[]> decode(List<Integer> data) {
        List<int[]> out = new ArrayList<>();
        int line = 0;
        int col = 0;
        for (int i = 0; i + 4 < data.size(); i += 5) {
            int deltaLine = data.get(i);
            int deltaStart = data.get(i + 1);
            int length = data.get(i + 2);
            int type = data.get(i + 3);
            line += deltaLine;
            col = (deltaLine == 0) ? col + deltaStart : deltaStart;
            out.add(new int[]{line, col, length, type});
        }
        return out;
    }

    private static void assertToken(List<int[]> tokens, int line, int col, int length, int type) {
        boolean found = false;
        for (int[] t : tokens) {
            if (t[0] == line && t[1] == col && t[2] == length && t[3] == type) {
                found = true;
                break;
            }
        }
        assertTrue(found, "expected token {line=" + line + ", col=" + col
                + ", len=" + length + ", type=" + type + "} not found in " + describe(tokens));
    }

    private static boolean hasToken(List<int[]> tokens, int type) {
        for (int[] t : tokens) {
            if (t[3] == type) {
                return true;
            }
        }
        return false;
    }

    private static String describe(List<int[]> tokens) {
        StringBuilder sb = new StringBuilder();
        for (int[] t : tokens) {
            sb.append('[').append(t[0]).append(',').append(t[1])
                    .append(',').append(t[2]).append(',').append(t[3]).append("] ");
        }
        return sb.toString();
    }
}
