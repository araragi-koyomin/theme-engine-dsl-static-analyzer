package com.huawei.theme.analysis.lsp;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;

/**
 * Maps core diagnostic coordinates to LSP positions.
 *
 * <p>Core emits 1-based line / 0-based column (see {@link
 * com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder}'s SAX locator
 * handling). The JDK XML parser works on Java {@code String}, so the column
 * is already in UTF-16 code units, which matches the LSP "character"
 * definition. Thus the mapping is effectively {@code line - 1} /
 * {@code column}, clamped to the document bounds.</p>
 */
final class PositionMapper {

    private final String text;
    private final int[] lineStarts;

    PositionMapper(String text) {
        this.text = text;
        this.lineStarts = buildLineStarts(text);
    }

    private static int[] buildLineStarts(String text) {
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        int[] starts = new int[count];
        int idx = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                starts[idx++] = i + 1;
            }
        }
        return starts;
    }

    int lineCount() {
        return lineStarts.length;
    }

    int lineEnd(int lspLine) {
        if (lspLine < 0) {
            return 0;
        }
        if (lspLine >= lineStarts.length - 1) {
            return text.length();
        }
        return lineStarts[lspLine + 1] - 1;
    }

    /** Converts a 1-based line / 0-based column core coordinate to an LSP position. */
    Position toPosition(int line1Based, int column0Based) {
        int lspLine = Math.max(line1Based - 1, 0);
        if (lspLine >= lineStarts.length) {
            lspLine = lineStarts.length - 1;
        }
        int start = lineStarts[lspLine];
        int maxChar = Math.max(lineEnd(lspLine) - start, 0);
        int lspChar = Math.max(column0Based, 0);
        if (lspChar > maxChar) {
            lspChar = maxChar;
        }
        return new Position(lspLine, lspChar);
    }

    /** Builds an LSP range for a core diagnostic. */
    Range toRange(Diagnostic diagnostic) {
        Position start = toPosition(diagnostic.getLine(), diagnostic.getColumn());
        return new Range(start, computeEnd(start, diagnostic));
    }

    /**
     * Computes the end position for a diagnostic's LSP range.
     *
     * <p>Core diagnostics always carry explicit {@code endLine/endColumn}
     * (populated by {@code DiagnosticBuilder.astNode()} from the node, or set
     * directly by analyzers like {@code VarRefAnalyzer} which build position
     * without an astNode). We prefer those coordinates so diagnostics without
     * an astNode still get a non-zero-width range instead of collapsing to a
     * point — which previously caused the IntelliJ annotator to drop them.
     * When the explicit end equals the start (rare point diagnostic, or an
     * analyzer that left end unset), we fall back to extending by the
     * astNode's text length for single-line nodes — the historical behavior —
     * so existing single-line tag ranges keep rendering.</p>
     */
    private Position computeEnd(Position start, Diagnostic diagnostic) {
        int endLine = diagnostic.getEndLine();
        int endColumn = diagnostic.getEndColumn();
        if (endLine > 0 || endColumn > 0) {
            Position end = toPosition(endLine, endColumn);
            if (isAfter(end, start)) {
                return end;
            }
        }
        DslAstNode node = diagnostic.getAstNode();
        String nodeText = node == null ? null : node.getText();
        if (nodeText != null && !nodeText.isEmpty() && nodeText.indexOf('\n') < 0) {
            int startOffset = lineStarts[start.getLine()];
            int maxChar = Math.max(lineEnd(start.getLine()) - startOffset, 0);
            int endChar = Math.min(start.getCharacter() + nodeText.length(), maxChar);
            if (endChar > start.getCharacter()) {
                return new Position(start.getLine(), endChar);
            }
        }
        return start;
    }

    private static boolean isAfter(Position a, Position b) {
        int c = Integer.compare(a.getLine(), b.getLine());
        return c != 0 ? c > 0 : a.getCharacter() > b.getCharacter();
    }

    /** Converts an LSP position (0-based line / 0-based char) to a text offset. */
    int toOffset(int lspLine, int lspChar) {
        int line = Math.max(lspLine, 0);
        if (line >= lineStarts.length) {
            return text.length();
        }
        int start = lineStarts[line];
        int maxChar = Math.max(lineEnd(line) - start, 0);
        int ch = Math.max(lspChar, 0);
        if (ch > maxChar) {
            ch = maxChar;
        }
        return start + ch;
    }

    /**
     * Converts a core coordinate (1-based line / 0-based column, as emitted by
     * {@link com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder}) to a
     * flat text offset. Used by AST-based context resolution to compare cursor
     * offsets against AST node ranges. Clamps out-of-range coordinates to the
     * document bounds (e.g. error nodes with line 0).
     */
    int coreOffset(int line1Based, int column0Based) {
        return toOffset(Math.max(line1Based - 1, 0), column0Based);
    }
}
