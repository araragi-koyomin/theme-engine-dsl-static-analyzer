package com.huawei.theme.analysis.plugin.editor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.antlr.intellij.adaptor.lexer.ANTLRLexerAdaptor;
import org.antlr.intellij.adaptor.lexer.GapFillingLexerAdaptor;
import org.antlr.intellij.adaptor.lexer.PSIElementTypeFactory;
import org.antlr.intellij.adaptor.lexer.TokenIElementType;
import org.jetbrains.annotations.NotNull;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;

import com.huawei.theme.analysis.core.expression.generated.DslExpressionLexer;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionParser;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/**
 * Syntax highlighter for the DslExpression language (.{@code de} files).
 *
 * <p>It reuses the ANTLR-generated {@link DslExpressionLexer} through the
 * {@link ANTLRLexerAdaptor} and maps each lexer {@link IElementType} to an
 * IntelliJ {@link TextAttributesKey}. Token categories follow the grammar
 * definition in {@code DslExpression.g4}:</p>
 *
 * <ul>
 *     <li>{@code NUMBER} / {@code STRING} - numeric and string literals</li>
 *     <li>{@code ID} - function name identifier</li>
 *     <li>{@code VAR_ID} - variable name (after a {@code #} / {@code @} prefix)</li>
 *     <li>{@code '#'} / {@code '@'} - variable prefix sigils</li>
 *     <li>{@code + - * / %} - arithmetic operators</li>
 *     <li>{@code () [] {}} - parentheses, brackets and braces</li>
 *     <li>{@code ,} - argument separator</li>
 * </ul>
 */
public class DslExpressionSyntaxHighlighter extends SyntaxHighlighterBase {

    public static final TextAttributesKey NUMBER =
            createTextAttributesKey("DSL_EXPR_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey STRING =
            createTextAttributesKey("DSL_EXPR_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey IDENTIFIER =
            createTextAttributesKey("DSL_EXPR_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey VARIABLE_NAME =
            createTextAttributesKey("DSL_EXPR_VARIABLE_NAME", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey VAR_PREFIX =
            createTextAttributesKey("DSL_EXPR_VAR_PREFIX", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    public static final TextAttributesKey OPERATOR =
            createTextAttributesKey("DSL_EXPR_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    public static final TextAttributesKey PARENTHESES =
            createTextAttributesKey("DSL_EXPR_PARENS", DefaultLanguageHighlighterColors.PARENTHESES);
    public static final TextAttributesKey BRACKETS =
            createTextAttributesKey("DSL_EXPR_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);
    public static final TextAttributesKey BRACES =
            createTextAttributesKey("DSL_EXPR_BRACES", DefaultLanguageHighlighterColors.BRACES);
    public static final TextAttributesKey COMMA =
            createTextAttributesKey("DSL_EXPR_COMMA", DefaultLanguageHighlighterColors.COMMA);
    public static final TextAttributesKey BAD_CHARACTER =
            createTextAttributesKey("DSL_EXPR_BAD_CHAR", HighlighterColors.BAD_CHARACTER);

    private static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];

    private static final Map<IElementType, TextAttributesKey> HIGHLIGHTS = new HashMap<>();

    static {
        PSIElementTypeFactory.defineLanguageIElementTypes(
                DslExpressionLanguage.INSTANCE,
                DslExpressionLexer.VOCABULARY,
                DslExpressionParser.ruleNames);
        Map<String, Integer> tokenNameToType =
                PSIElementTypeFactory.getTokenNameToTypeMap(DslExpressionLanguage.INSTANCE);
        List<TokenIElementType> tokenElementTypes =
                PSIElementTypeFactory.getTokenIElementTypes(DslExpressionLanguage.INSTANCE);

        bind(tokenNameToType, tokenElementTypes, "NUMBER", NUMBER);
        bind(tokenNameToType, tokenElementTypes, "STRING", STRING);
        bind(tokenNameToType, tokenElementTypes, "ID", IDENTIFIER);
        bind(tokenNameToType, tokenElementTypes, "VAR_ID", VARIABLE_NAME);
        bind(tokenNameToType, tokenElementTypes, "'#'", VAR_PREFIX);
        bind(tokenNameToType, tokenElementTypes, "'@'", VAR_PREFIX);
        bind(tokenNameToType, tokenElementTypes, "'+'", OPERATOR);
        bind(tokenNameToType, tokenElementTypes, "'-'", OPERATOR);
        bind(tokenNameToType, tokenElementTypes, "'*'", OPERATOR);
        bind(tokenNameToType, tokenElementTypes, "'/'", OPERATOR);
        bind(tokenNameToType, tokenElementTypes, "'%'", OPERATOR);
        bind(tokenNameToType, tokenElementTypes, "'('", PARENTHESES);
        bind(tokenNameToType, tokenElementTypes, "')'", PARENTHESES);
        bind(tokenNameToType, tokenElementTypes, "'['", BRACKETS);
        bind(tokenNameToType, tokenElementTypes, "']'", BRACKETS);
        bind(tokenNameToType, tokenElementTypes, "'{'", BRACES);
        bind(tokenNameToType, tokenElementTypes, "'}'", BRACES);
        bind(tokenNameToType, tokenElementTypes, "','", COMMA);
    }

    DslExpressionSyntaxHighlighter() {
    }

    private static void bind(Map<String, Integer> tokenNameToType,
                             List<TokenIElementType> tokenElementTypes,
                             String tokenName,
                             TextAttributesKey key) {
        Integer type = tokenNameToType == null ? null : tokenNameToType.get(tokenName);
        if (type == null || tokenElementTypes == null || type < 0 || type >= tokenElementTypes.size()) {
            return;
        }
        HIGHLIGHTS.put(tokenElementTypes.get(type), key);
    }

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        DslExpressionLexer antlrLexer = new DslExpressionLexer(null);
        // Highlighting runs on partial/malformed input as the user types; silence
        // ANTLR's "token recognition error" log (gaps are filled below as bad chars).
        antlrLexer.removeErrorListeners();
        return new GapFillingLexerAdaptor(
                new ANTLRLexerAdaptor(DslExpressionLanguage.INSTANCE, antlrLexer));
    }

    @NotNull
    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        TextAttributesKey key = HIGHLIGHTS.get(tokenType);
        if (key == null) {
            return EMPTY_KEYS;
        }
        return new TextAttributesKey[]{key};
    }
}
