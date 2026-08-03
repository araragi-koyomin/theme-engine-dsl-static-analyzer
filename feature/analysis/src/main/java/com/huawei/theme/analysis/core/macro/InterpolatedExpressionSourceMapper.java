package com.huawei.theme.analysis.core.macro;

import org.jetbrains.annotations.NotNull;

import com.huawei.theme.analysis.core.expression.ExpressionNode;

final class InterpolatedExpressionSourceMapper {

    private InterpolatedExpressionSourceMapper() {
    }

    static void remap(@NotNull ExpressionNode node,
                      @NotNull String expanded,
                      @NotNull String source,
                      @NotNull CompileTimeInterpolator.InterpolationResult interpolation,
                      int documentLine,
                      int documentColumn) {
        int startOffset = relativeOffset(expanded, node.getLine(), node.getColumn(),
                documentLine, documentColumn);
        int endOffset = relativeOffset(expanded, node.getEndLine(), node.getEndColumn(),
                documentLine, documentColumn);
        int[] start = lineColumn(source, interpolation.sourceOffsetAt(startOffset));
        int[] end = lineColumn(source, interpolation.sourceOffsetAt(endOffset));
        node.setLine(documentLine + start[0] - 1);
        node.setColumn(start[0] == 1 ? documentColumn + start[1] : start[1]);
        node.setEndLine(documentLine + end[0] - 1);
        node.setEndColumn(end[0] == 1 ? documentColumn + end[1] : end[1]);

        for (ExpressionNode child : node.getChildren()) {
            remap(child, expanded, source, interpolation, documentLine, documentColumn);
        }
        if (node.getIndexExpression() != null) {
            remap(node.getIndexExpression(), expanded, source, interpolation, documentLine, documentColumn);
        }
    }

    private static int relativeOffset(String text, int line, int column,
                                      int documentLine, int documentColumn) {
        int relativeLine = Math.max(1, line - documentLine + 1);
        int relativeColumn = relativeLine == 1
                ? Math.max(0, column - documentColumn) : Math.max(0, column);
        int currentLine = 1;
        int offset = 0;
        while (offset < text.length() && currentLine < relativeLine) {
            if (text.charAt(offset++) == '\n') {
                currentLine++;
            }
        }
        return Math.min(text.length(), offset + relativeColumn);
    }

    private static int[] lineColumn(String text, int requestedOffset) {
        int bounded = Math.max(0, Math.min(requestedOffset, text.length()));
        int line = 1;
        int column = 0;
        for (int i = 0; i < bounded; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        return new int[]{line, column};
    }
}
