package org.antlr.intellij.adaptor.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link Lexer} wrapper that guarantees a <em>continuous</em> token sequence
 * (no gaps), as required by IntelliJ's {@code LexerEditorHighlighter} and
 * {@code ValidatingLexerWrapper} (both throw on uncovered offsets).
 *
 * <p>ANTLR lexers commonly use {@code -> skip} on whitespace, and ANTLR's
 * lexer error recovery also consumes characters without emitting a token.
 * Both leave gaps in the token stream: e.g. input {@code "5 -"} lexes to a
 * {@code NUMBER} at {@code [0,1)} and a {@code '-'} at {@code [2,3)}, leaving
 * offset {@code 1..2} (the space) uncovered, which makes the editor highlighter
 * fail with {@code "Unexpected termination offset"} / {@code IndexOutOfBoundsException}.
 * The {@link ANTLRLexerAdaptor} does not synthesize tokens for such gaps.</p>
 *
 * <p>This wrapper fills every gap between consecutive delegate tokens (and any
 * trailing gap before EOF) with a synthetic {@link TokenType#WHITE_SPACE} token
 * when the gap is all whitespace, or a {@link TokenType#BAD_CHARACTER} token
 * otherwise, so the whole {@code [startOffset, endOffset)} range is covered.</p>
 *
 * <p><strong>Scope.</strong> Use this only for the highlighting / indexing
 * lexer. The PSI builder is gap-tolerant and its {@code PSITokenSource} casts
 * every token to a {@link TokenIElementType}, so synthetic whitespace tokens
 * must NOT be fed through {@code ParserDefinition#createLexer}.</p>
 */
public class GapFillingLexerAdaptor extends LexerBase {

    private final Lexer delegate;

    private CharSequence buffer;
    private int bufferEnd;

    /** End offset of the last emitted (real or synthetic) token. */
    private int lastEmittedEnd;

    @Nullable private IElementType currentType;
    private int currentStart;
    private int currentEnd;
    private boolean currentSynthetic;

    public GapFillingLexerAdaptor(@NotNull Lexer delegate) {
        this.delegate = delegate;
    }

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.bufferEnd = endOffset;
        delegate.start(buffer, startOffset, endOffset, initialState);
        lastEmittedEnd = startOffset;
        computeCurrent();
    }

    @Nullable
    @Override
    public IElementType getTokenType() {
        return currentType;
    }

    @Override
    public int getTokenStart() {
        return currentStart;
    }

    @Override
    public int getTokenEnd() {
        return currentEnd;
    }

    @Override
    public int getState() {
        return delegate.getState();
    }

    @Override
    public void advance() {
        if (currentType == null) {
            return;
        }
        if (currentSynthetic) {
            // A gap token has been consumed; the delegate is still on the next real token.
            lastEmittedEnd = currentEnd;
        } else {
            // A real delegate token has been consumed; advance the delegate.
            lastEmittedEnd = delegate.getTokenEnd();
            delegate.advance();
        }
        computeCurrent();
    }

    @NotNull
    @Override
    public CharSequence getBufferSequence() {
        return delegate.getBufferSequence();
    }

    @Override
    public int getBufferEnd() {
        return bufferEnd;
    }

    private void computeCurrent() {
        IElementType realType = delegate.getTokenType();
        int realStart = delegate.getTokenStart();

        if (realType != null && realStart > lastEmittedEnd) {
            // Gap before the next real token: fill it.
            emitSynthetic(lastEmittedEnd, realStart);
            return;
        }
        if (realType != null) {
            // realStart == lastEmittedEnd: emit the delegate's real token.
            currentSynthetic = false;
            currentType = realType;
            currentStart = realStart;
            currentEnd = delegate.getTokenEnd();
            return;
        }
        // Delegate reached EOF; fill any trailing gap.
        if (lastEmittedEnd < bufferEnd) {
            emitSynthetic(lastEmittedEnd, bufferEnd);
            return;
        }
        currentType = null;
        currentSynthetic = false;
        currentStart = currentEnd = lastEmittedEnd;
    }

    private void emitSynthetic(int start, int end) {
        currentSynthetic = true;
        currentStart = start;
        currentEnd = end;
        currentType = classifyGap(start, end);
    }

    private IElementType classifyGap(int start, int end) {
        for (int i = start; i < end; i++) {
            if (!Character.isWhitespace(buffer.charAt(i))) {
                return TokenType.BAD_CHARACTER;
            }
        }
        return TokenType.WHITE_SPACE;
    }
}
