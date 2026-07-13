package com.huawei.theme.analysis.lsp;

/**
 * Lightweight, PSI-free resolver that determines the cursor's structural
 * context (element-name position vs attribute-name position) by scanning the
 * raw document text backwards from the cursor.
 *
 * <p>Heuristic implementation for the first LSP version. DSL files have a
 * simple XML structure with no CDATA, so backward scanning is sufficient.
 * A later iteration can replace this with AST-based resolution once core
 * AST nodes carry end positions.</p>
 */
final class ContextResolver {

    enum PositionType { ELEMENT_NAME, ATTRIBUTE_NAME, OTHER }

    static final class Context {
        final PositionType type;
        final String tagName;
        final String word;
        final String attrName;

        Context(PositionType type, String tagName, String word) {
            this(type, tagName, word, null);
        }

        Context(PositionType type, String tagName, String word, String attrName) {
            this.type = type;
            this.tagName = tagName;
            this.word = word;
            this.attrName = attrName;
        }
    }

    private final String text;

    ContextResolver(String text) {
        this.text = text;
    }

    Context resolve(int offset) {
        int n = Math.min(Math.max(offset, 0), text.length());
        int lt = findTagOpen(n);
        if (lt < 0) {
            return new Context(PositionType.OTHER, null, wordBefore(n));
        }
        int nameStart = lt + 1;
        if (nameStart < text.length() && text.charAt(nameStart) == '/') {
            return new Context(PositionType.OTHER, null, wordBefore(n));
        }
        // Scan the full tag name independently of the cursor, so hover
        // resolves the complete tag even when the cursor is inside the name
        // (e.g. Ctrl+Q with the caret in the middle of "Widget").
        int nameEnd = nameStart;
        while (nameEnd < text.length() && isNameChar(text.charAt(nameEnd))) {
            nameEnd++;
        }
        String tagName = text.substring(nameStart, nameEnd);
        if (n <= nameEnd) {
            // cursor within the tag name (or right after '<')
            return new Context(PositionType.ELEMENT_NAME,
                    tagName.isEmpty() ? null : tagName, text.substring(nameStart, n));
        }
        // cursor past the tag name → attribute region
        int wordStart = n;
        while (wordStart > nameEnd && isNameChar(text.charAt(wordStart - 1))) {
            wordStart--;
        }
        String w = text.substring(wordStart, n);
        if (wordStart > nameEnd) {
            char prev = text.charAt(wordStart - 1);
            if (prev == '"' || prev == '\'') {
                return new Context(PositionType.OTHER, tagName, w);
            }
        }
        return new Context(PositionType.ATTRIBUTE_NAME, tagName, w);
    }

    private int findTagOpen(int from) {
        for (int i = from - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '>') {
                return -1;
            }
            if (c == '<') {
                return i;
            }
        }
        return -1;
    }

    private String wordBefore(int from) {
        int s = from;
        while (s > 0 && isNameChar(text.charAt(s - 1))) {
            s--;
        }
        return text.substring(s, from);
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == ':';
    }
}
