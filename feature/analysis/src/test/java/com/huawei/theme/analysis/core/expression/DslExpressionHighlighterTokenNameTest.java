package com.huawei.theme.analysis.core.expression;

import java.util.LinkedHashSet;
import java.util.Set;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;

import com.huawei.theme.analysis.core.expression.generated.DslExpressionLexer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the ANTLR-generated {@link DslExpressionLexer} emits token
 * display-names that match the keys bound by {@code DslExpressionSyntaxHighlighter}.
 *
 * <p>The highlighter maps tokens by their vocabulary display name
 * (e.g. {@code "NUMBER"}, {@code "'+'"}). If the ANTLR grammar ever changes its
 * literal-naming convention, the highlighter's static binding table would silently
 * stop matching. This test fails in that case so the binding keys stay in sync.</p>
 */
class DslExpressionHighlighterTokenNameTest {

    private static final String SAMPLE =
            "'x'+{sin(#a)/2-3*4%5+max(#p,#q)}+@Scenarios.topId[1]";

    @Test
    void lexerEmitsAllTokenNamesBoundByHighlighter() {
        DslExpressionLexer lexer = new DslExpressionLexer(CharStreams.fromString(SAMPLE));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();

        Vocabulary vocabulary = lexer.getVocabulary();
        Set<String> emitted = new LinkedHashSet<>();
        for (Token t : tokens.getTokens()) {
            if (t.getType() == Token.EOF) {
                continue;
            }
            emitted.add(vocabulary.getDisplayName(t.getType()));
        }

        String[] boundByHighlighter = {
                "NUMBER", "STRING", "ID", "VAR_ID",
                "'#'", "'@'",
                "'+'", "'-'", "'*'", "'/'", "'%'",
                "'('", "')'", "'['", "']'", "'{'", "'}'", "','"
        };
        for (String name : boundByHighlighter) {
            assertTrue(emitted.contains(name),
                    "Highlighter binds '" + name + "' but lexer never emitted it. Emitted: " + emitted);
        }
    }
}
