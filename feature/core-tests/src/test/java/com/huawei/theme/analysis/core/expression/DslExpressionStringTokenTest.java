package com.huawei.theme.analysis.core.expression;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;

import com.huawei.theme.analysis.core.expression.generated.DslExpressionLexer;

import org.junit.jupiter.api.Test;

class DslExpressionStringTokenTest {

    @Test
    void tokenizeStringLiteral() {
        for (String input : new String[]{"'123'", "'hello'", "'a'", "''", "'123", "'12", "'"}) {
            DslExpressionLexer lexer = new DslExpressionLexer(CharStreams.fromString(input));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            tokens.fill();
            Vocabulary vocab = lexer.getVocabulary();
            System.out.println("Input: " + input);
            for (Token t : tokens.getTokens()) {
                if (t.getType() == Token.EOF) continue;
                System.out.println("  token: " + vocab.getDisplayName(t.getType())
                        + " text='" + t.getText() + "' [" + t.getStartIndex() + "," + t.getStopIndex() + "]");
            }
        }
    }
}
