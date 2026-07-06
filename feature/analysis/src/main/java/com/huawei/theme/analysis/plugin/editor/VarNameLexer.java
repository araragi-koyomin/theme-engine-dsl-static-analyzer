package com.huawei.theme.analysis.plugin.editor;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Lexer for the VarName language: emits one {@link VarNameElementTypes#ID} token for a
 *  leading identifier run (letters/digits/_/.), {@link TokenType#BAD_CHARACTER} otherwise. */
class VarNameLexer extends LexerBase {

    private CharSequence buffer;
    private int bufferEnd;
    private int tokenStart;
    private int tokenEnd;
    private IElementType tokenType;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.bufferEnd = endOffset;
        this.tokenStart = startOffset;
        this.tokenEnd = startOffset;
        advance();
    }

    @Override
    public void advance() {
        if (tokenEnd >= bufferEnd) {
            tokenType = null;
            tokenStart = tokenEnd;
            return;
        }
        tokenStart = tokenEnd;
        char c = buffer.charAt(tokenStart);
        if (isIdentStart(c)) {
            int i = tokenStart + 1;
            while (i < bufferEnd && isIdentPart(buffer.charAt(i))) {
                i++;
            }
            tokenEnd = i;
            tokenType = VarNameElementTypes.ID;
        } else {
            tokenEnd = tokenStart + 1;
            tokenType = TokenType.BAD_CHARACTER;
        }
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    @Nullable
    public IElementType getTokenType() {
        return tokenType;
    }

    @Override
    public int getTokenStart() {
        return tokenStart;
    }

    @Override
    public int getTokenEnd() {
        return tokenEnd;
    }

    @NotNull
    @Override
    public CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return bufferEnd;
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }
}
