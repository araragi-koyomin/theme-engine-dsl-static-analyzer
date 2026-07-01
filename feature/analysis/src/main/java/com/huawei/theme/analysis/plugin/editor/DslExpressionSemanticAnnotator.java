package com.huawei.theme.analysis.plugin.editor;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.antlr.intellij.adaptor.lexer.PSIElementTypeFactory;
import org.antlr.intellij.adaptor.lexer.RuleIElementType;
import org.antlr.intellij.adaptor.lexer.TokenIElementType;
import org.antlr.v4.runtime.Token;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;

import com.huawei.theme.analysis.core.expression.generated.DslExpressionLexer;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionParser;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/**
 * Semantic (PSI-based) highlighter for the DslExpression language.
 *
 * <p>The lexer-based {@link DslExpressionSyntaxHighlighter} cannot tell a
 * function name apart from a variable name: both lex as {@code ID}. This
 * annotator disambiguates them using the parse tree:</p>
 *
 * <ul>
 *     <li>{@code @name} ({@code atVarRef}) - string variable</li>
 *     <li>{@code #name} ({@code hashVarRef}) - numeric variable</li>
 *     <li>{@code name(...)} ({@code functionCall}) - function call</li>
 * </ul>
 *
 * <p>It is invoked by the platform on every PSI element; only the three rule
 * composites above are processed, coloring their sigil (for variables) and
 * the first identifier leaf. The annotations are silent
 * ({@link HighlightSeverity#INFORMATION}) so they only recolor text without
 * adding inspection markers; they override the lexer's generic identifier
 * color.</p>
 */
public class DslExpressionSemanticAnnotator implements Annotator {

    public static final TextAttributesKey FUNCTION =
            createTextAttributesKey("DSL_EXPR_FUNCTION", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
    public static final TextAttributesKey STRING_VARIABLE =
            createTextAttributesKey("DSL_EXPR_STRING_VARIABLE", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey NUMERIC_VARIABLE =
            createTextAttributesKey("DSL_EXPR_NUMERIC_VARIABLE", DefaultLanguageHighlighterColors.NUMBER);

    private static final RuleIElementType AT_VAR_REF;
    private static final RuleIElementType HASH_VAR_REF;
    private static final RuleIElementType FUNCTION_CALL;
    private static final int AT_TOKEN;
    private static final int HASH_TOKEN;
    private static final int ID_TOKEN = DslExpressionParser.ID;
    private static final int VAR_ID_TOKEN = DslExpressionParser.VAR_ID;

    static {
        PSIElementTypeFactory.defineLanguageIElementTypes(
                DslExpressionLanguage.INSTANCE,
                DslExpressionLexer.VOCABULARY,
                DslExpressionParser.ruleNames);
        List<RuleIElementType> ruleTypes =
                PSIElementTypeFactory.getRuleIElementTypes(DslExpressionLanguage.INSTANCE);
        AT_VAR_REF = ruleTypes.get(DslExpressionParser.RULE_atVarRef);
        HASH_VAR_REF = ruleTypes.get(DslExpressionParser.RULE_hashVarRef);
        FUNCTION_CALL = ruleTypes.get(DslExpressionParser.RULE_functionCall);

        Map<String, Integer> tokenNames =
                PSIElementTypeFactory.getTokenNameToTypeMap(DslExpressionLanguage.INSTANCE);
        AT_TOKEN = tokenNames.get("'@'");
        HASH_TOKEN = tokenNames.get("'#'");
    }

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        IElementType nodeType = element.getNode() == null ? null : element.getNode().getElementType();
        if (nodeType == AT_VAR_REF) {
            colorVariable(element, holder, STRING_VARIABLE);
        } else if (nodeType == HASH_VAR_REF) {
            colorVariable(element, holder, NUMERIC_VARIABLE);
        } else if (nodeType == FUNCTION_CALL) {
            PsiElement name = firstLeaf(element, DslExpressionSemanticAnnotator::isName);
            if (name != null) {
                apply(holder, name, FUNCTION);
            }
        }
    }

    private static void colorVariable(PsiElement varRef, AnnotationHolder holder, TextAttributesKey key) {
        PsiElement sigil = firstLeaf(varRef, DslExpressionSemanticAnnotator::isSigil);
        if (sigil != null) {
            apply(holder, sigil, key);
        }
        PsiElement name = firstLeaf(varRef, DslExpressionSemanticAnnotator::isName);
        if (name != null) {
            apply(holder, name, key);
        }
    }

    private static void apply(AnnotationHolder holder, PsiElement leaf, TextAttributesKey key) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(leaf)
                .textAttributes(key)
                .create();
    }

    @Nullable
    private static PsiElement firstLeaf(PsiElement root, Predicate<PsiElement> pred) {
        for (PsiElement c = root.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof LeafPsiElement) {
                if (pred.test(c)) {
                    return c;
                }
            } else {
                PsiElement found = firstLeaf(c, pred);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean isSigil(PsiElement leaf) {
        int t = antlrType(leaf);
        return t == AT_TOKEN || t == HASH_TOKEN;
    }

    private static boolean isName(PsiElement leaf) {
        int t = antlrType(leaf);
        return t == ID_TOKEN || t == VAR_ID_TOKEN;
    }

    private static int antlrType(PsiElement leaf) {
        IElementType t = leaf.getNode().getElementType();
        return t instanceof TokenIElementType tet ? tet.getANTLRTokenType() : Token.INVALID_TYPE;
    }
}
