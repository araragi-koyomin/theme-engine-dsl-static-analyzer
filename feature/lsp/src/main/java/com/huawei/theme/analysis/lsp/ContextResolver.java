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

    enum PositionType { ELEMENT_NAME, ATTRIBUTE_NAME, ATTRIBUTE_VALUE, OTHER }

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
        // cursor past the tag name → attribute region. Forward-scan from the
        // tag-name end to the cursor, tracking quote state and the current
        // attribute name, so we can distinguish attribute-name vs
        // attribute-value positions even when the XML is incomplete (the AST
        // path may have failed on mid-typed markup like an unclosed tag).
        boolean inValue = false;
        char quote = 0;
        int valueStartOffset = -1;
        int attrNameStart = -1;
        int attrNameEnd = -1;
        for (int i = nameEnd; i < n; i++) {
            char c = text.charAt(i);
            if (inValue) {
                if (c == quote) {
                    inValue = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inValue = true;
                quote = c;
                valueStartOffset = i + 1;
            } else if (isNameChar(c)) {
                int s = i;
                while (i < n && isNameChar(text.charAt(i))) {
                    i++;
                }
                attrNameStart = s;
                attrNameEnd = i;
                i--; // offset for loop's i++
            }
            // '=', whitespace, '/', '>' and other separators are skipped.
        }
        if (inValue) {
            // cursor inside a quoted value
            String attrName = (attrNameStart >= 0) ? text.substring(attrNameStart, attrNameEnd) : null;
            String word = safeSubstring(valueStartOffset, n);
            return new Context(PositionType.ATTRIBUTE_VALUE, tagName, word, attrName);
        }
        if (attrNameStart >= 0 && n > attrNameStart && n <= attrNameEnd) {
            // cursor within the last attribute name token
            String word = text.substring(attrNameStart, n);
            String attrName = text.substring(attrNameStart, attrNameEnd);
            return new Context(PositionType.ATTRIBUTE_NAME, tagName, word, attrName);
        }
        // cursor in the attribute region but not inside a value or a name
        // token: the user just typed the space after the tag name or between
        // attributes, so default to ATTRIBUTE_NAME (empty word) so completion
        // offers the canonical attribute set. Skip when sitting right after
        // '=' (mid-attribute, before the value) or on a tag-boundary char
        // ('>', '/', '<') where attribute completion would be wrong.
        char atCursor = (n < text.length()) ? text.charAt(n) : ' ';
        char prev = (n > 0) ? text.charAt(n - 1) : ' ';
        if (atCursor == '>' || atCursor == '/' || atCursor == '<' || prev == '=') {
            return new Context(PositionType.OTHER, tagName, wordBefore(n));
        }
        return new Context(PositionType.ATTRIBUTE_NAME, tagName, "", null);
    }

    private String safeSubstring(int start, int end) {
        if (start < 0) {
            start = 0;
        }
        if (end < start) {
            end = start;
        }
        if (end > text.length()) {
            end = text.length();
        }
        return text.substring(start, end);
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
