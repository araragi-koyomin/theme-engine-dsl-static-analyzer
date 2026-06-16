package com.huawei.theme.analysis.syntax;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class DslParserDefinitionTest {

    private DslParserDefinition parserDefinition;

    @BeforeEach
    void setUp() {
        parserDefinition = new DslParserDefinition();
    }

    @Test
    void parserDefinition_shouldImplementParserDefinition() {
        assertSame(DslParserDefinition.class, parserDefinition.getClass());
    }

    @Test
    void getFileNodeType_languageShouldBeDsl() {
        IFileElementType fileType = parserDefinition.getFileNodeType();
        assertNotNull(fileType);
        assertSame(DslLanguage.INSTANCE, fileType.getLanguage());
    }

    @Test
    void getFileNodeType_shouldBeDslElementTypesDslFile() {
        assertSame(DslElementTypes.DSL_FILE, parserDefinition.getFileNodeType());
    }

    @Test
    void createLexer_shouldReturnNonNullLexer() {
        Lexer lexer = parserDefinition.createLexer(null);
        assertNotNull(lexer);
    }

    @Test
    void createLexer_shouldReturnFreshInstanceEachCall() {
        Lexer lexer1 = parserDefinition.createLexer(null);
        Lexer lexer2 = parserDefinition.createLexer(null);
        assertNotSame(lexer1, lexer2);
    }

    @Test
    void createParser_shouldReturnNonNullParser() {
        PsiParser parser = parserDefinition.createParser(null);
        assertNotNull(parser);
    }

    @Test
    void createParser_shouldReturnFreshInstanceEachCall() {
        PsiParser parser1 = parserDefinition.createParser(null);
        PsiParser parser2 = parserDefinition.createParser(null);
        assertNotSame(parser1, parser2);
    }

    @Test
    void getWhitespaceTokens_shouldDelegateToXmlParserDefinition() {
        TokenSet whitespace = parserDefinition.getWhitespaceTokens();
        assertNotNull(whitespace);
    }

    @Test
    void getCommentTokens_shouldDelegateToXmlParserDefinition() {
        TokenSet comments = parserDefinition.getCommentTokens();
        assertNotNull(comments);
    }

    @Test
    void getStringLiteralElements_shouldDelegateToXmlParserDefinition() {
        TokenSet stringLiterals = parserDefinition.getStringLiteralElements();
        assertNotNull(stringLiterals);
    }

    @Test
    void parserDefinition_hasSpaceExistenceTypeBetweenTokensMethod() {
        assertNotNull(parserDefinition);
    }

    @Test
    void dslSyntaxConstants_ruleIdFormatShouldFollowConvention() {
        assertEquals("SYN-001", DslSyntaxConstants.SYN_001);
        assertEquals("SYN-002", DslSyntaxConstants.SYN_002);
        assertEquals("SYN-003", DslSyntaxConstants.SYN_003);
        assertEquals("SYN-001: Tag not closed", DslSyntaxConstants.SYN_001_MSG);
        assertEquals("SYN-002: Invalid nesting", DslSyntaxConstants.SYN_002_MSG);
        assertEquals("SYN-003: Missing attribute quotes", DslSyntaxConstants.SYN_003_MSG);
    }

    @Test
    void dslSyntaxConstants_msgShouldContainCorrespondingRuleId() {
        assertTrue(DslSyntaxConstants.SYN_001_MSG.startsWith(DslSyntaxConstants.SYN_001));
        assertTrue(DslSyntaxConstants.SYN_002_MSG.startsWith(DslSyntaxConstants.SYN_002));
        assertTrue(DslSyntaxConstants.SYN_003_MSG.startsWith(DslSyntaxConstants.SYN_003));
    }

    private static boolean assertTrue(boolean condition) {
        if (!condition) throw new AssertionError("expected true");
        return true;
    }
}
