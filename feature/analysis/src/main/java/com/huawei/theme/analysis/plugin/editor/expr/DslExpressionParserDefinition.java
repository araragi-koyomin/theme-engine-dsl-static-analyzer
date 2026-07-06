package com.huawei.theme.analysis.plugin.editor.expr;

import java.util.List;

import com.huawei.theme.analysis.plugin.editor.reference.DslVariableRefElement;
import org.antlr.intellij.adaptor.lexer.ANTLRLexerAdaptor;
import org.antlr.intellij.adaptor.lexer.GapFillingLexerAdaptor;
import org.antlr.intellij.adaptor.lexer.PSIElementTypeFactory;
import org.antlr.intellij.adaptor.lexer.RuleIElementType;
import org.antlr.intellij.adaptor.parser.ANTLRParserAdaptor;
import org.antlr.intellij.adaptor.psi.ANTLRPsiNode;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTree;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionLexer;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionParser;
import org.jetbrains.annotations.NotNull;

public class DslExpressionParserDefinition implements ParserDefinition {

    private final RuleIElementType atVarRefType;
    private final RuleIElementType hashVarRefType;

    public DslExpressionParserDefinition() {
        PSIElementTypeFactory.defineLanguageIElementTypes(
                DslExpressionLanguage.INSTANCE,
                DslExpressionLexer.VOCABULARY,
                DslExpressionParser.ruleNames
        );
        List<RuleIElementType> ruleTypes = PSIElementTypeFactory.getRuleIElementTypes(DslExpressionLanguage.INSTANCE);
        atVarRefType = ruleTypes.get(DslExpressionParser.RULE_atVarRef);
        hashVarRefType = ruleTypes.get(DslExpressionParser.RULE_hashVarRef);
    }

    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        DslExpressionLexer lexer = new DslExpressionLexer(null);
        lexer.removeErrorListeners();
        // Wrap in GapFillingLexerAdaptor so every character is covered by a token
        // (WHITE_SPACE / BAD_CHARACTER for gaps left by WS->skip and lexer error
        // recovery). This is required by the injection's LeafPatcher, which asserts
        // leaf texts add up to the whole injected text. PSITokenSource skips the
        // synthetic tokens so the ANTLR parser doesn't see them.
        return new GapFillingLexerAdaptor(
                new ANTLRLexerAdaptor(DslExpressionLanguage.INSTANCE, lexer));
    }

    @Override
    public PsiParser createParser(Project project) {
        return new ANTLRParserAdaptor(DslExpressionLanguage.INSTANCE, new DslExpressionParser(null)) {
            @Override
            protected ParseTree parse(Parser parser, IElementType root) {
                return ((DslExpressionParser) parser).expression();
            }
        };
    }

    @Override
    public IFileElementType getFileNodeType() {
        return new IFileElementType(DslExpressionLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public TokenSet getCommentTokens() {
        return TokenSet.EMPTY;
    }

    @NotNull
    @Override
    public TokenSet getStringLiteralElements() {
        return TokenSet.EMPTY;
    }

    @NotNull
    @Override
    public PsiElement createElement(ASTNode node) {
        IElementType type = node.getElementType();
        if (type == atVarRefType || type == hashVarRefType) {
            return new DslVariableRefElement(node);
        }
        return new ANTLRPsiNode(node);
    }

    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new PsiFileBase(viewProvider, DslExpressionLanguage.INSTANCE) {
            @NotNull
            @Override
            public FileType getFileType() {
                return DslExpressionFileType.INSTANCE;
            }
        };
    }
}
