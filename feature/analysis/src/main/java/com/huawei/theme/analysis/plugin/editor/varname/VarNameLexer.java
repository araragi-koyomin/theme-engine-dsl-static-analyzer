package com.huawei.theme.analysis.plugin.editor.varname;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Lexer for the VarName language: emits one {@link VarNameElementTypes#ID} token for a
 *  leading identifier run (letters/digits/_/.), {@link TokenType#BAD_CHARACTER} otherwise.
 *  The ID run may contain compile-time interpolation {@code %{...}} segments (so a
 *  declaration like {@code <Var name="index_%{i}">} produces a single ID whose text is
 *  the full raw {@code index_%{i}}, not a truncated {@code index_}). */
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
            while (i < bufferEnd) {
                char ch = buffer.charAt(i);
                if (isIdentPart(ch)) {
                    i++;
                    continue;
                }
                // Consume a compile-time interpolation %{...} as part of the identifier.
                if (ch == '%' && i + 1 < bufferEnd && buffer.charAt(i + 1) == '{') {
                    int close = indexOfCloseBrace(buffer, i + 2, bufferEnd);
                    if (close < 0) {
                        break;
                    }
                    i = close + 1;
                    continue;
                }
                break;
            }
            tokenEnd = i;
            tokenType = VarNameElementTypes.ID;
        } else {
            tokenEnd = tokenStart + 1;
            tokenType = TokenType.BAD_CHARACTER;
        }
    }

    private static int indexOfCloseBrace(CharSequence buffer, int from, int to) {
        for (int i = from; i < to; i++) {
            if (buffer.charAt(i) == '}') {
                return i;
            }
        }
        return -1;
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
