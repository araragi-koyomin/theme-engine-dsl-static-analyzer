package com.huawei.theme.analysis.syntax;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;

public class DslParserDefinition implements ParserDefinition {
    private final XMLParserDefinition xmlParserDefinition = new XMLParserDefinition();

    @Override
    public Lexer createLexer(Project project) {
        return xmlParserDefinition.createLexer(project);
    }

    @Override
    public PsiParser createParser(Project project) {
        return xmlParserDefinition.createParser(project);
    }

    @Override
    public IFileElementType getFileNodeType() {
        return DslElementTypes.DSL_FILE;
    }

    @Override
    public PsiElement createElement(ASTNode node) {
        return xmlParserDefinition.createElement(node);
    }

    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new DslFile(viewProvider);
    }

    @Override
    public TokenSet getWhitespaceTokens() {
        return xmlParserDefinition.getWhitespaceTokens();
    }

    @Override
    public TokenSet getCommentTokens() {
        return xmlParserDefinition.getCommentTokens();
    }

    @Override
    public TokenSet getStringLiteralElements() {
        return xmlParserDefinition.getStringLiteralElements();
    }

    @Override
    public SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return xmlParserDefinition.spaceExistenceTypeBetweenTokens(left, right);
    }
}
