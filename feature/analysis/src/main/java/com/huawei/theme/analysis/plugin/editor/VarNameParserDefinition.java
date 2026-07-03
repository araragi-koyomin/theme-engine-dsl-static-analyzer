package com.huawei.theme.analysis.plugin.editor;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/** Parser definition for the VarName language: a single {@code varName} node wrapping an ID. */
public class VarNameParserDefinition implements ParserDefinition {

    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        return new VarNameLexer();
    }

    @Override
    public PsiParser createParser(Project project) {
        return (root, builder) -> {
            PsiBuilder.Marker marker = builder.mark();
            while (!builder.eof()) {
                builder.advanceLexer();
            }
            marker.done(VarNameElementTypes.VAR_NAME);
            return builder.getTreeBuilt();
        };
    }

    @Override
    public IFileElementType getFileNodeType() {
        return new IFileElementType(VarNameLanguage.INSTANCE);
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
        if (node.getElementType() == VarNameElementTypes.VAR_NAME) {
            return new VarNameElement(node);
        }
        return new ASTWrapperPsiElement(node);
    }

    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new PsiFileBase(viewProvider, VarNameLanguage.INSTANCE) {
            @NotNull
            @Override
            public FileType getFileType() {
                return VarNameFileType.INSTANCE;
            }
        };
    }
}
