package com.huawei.theme.analysis.lsp;

import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;

/**
 * AST-based cursor context resolver. Determines the cursor's structural
 * context (element-name position vs attribute-name position vs other) by
 * walking the parsed AST and matching node ranges, replacing the text-scan
 * heuristic of {@link ContextResolver} for the precise case.
 *
 * <p>AST node ranges come from {@link com.huawei.theme.analysis.core.syntaxanalysis.AstBuilder}:
 * <ul>
 *   <li>{@link DslElementNode} covers the entire start tag
 *       ({@code <Tag attr="v">}), {@code line/column} pointing at {@code <},
 *       {@code endLine/endColumn} just past {@code >};</li>
 *   <li>{@link DslAttributeNode} covers {@code name="v"} (name start to past
 *       the closing quote);</li>
 *   <li>{@link com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode}
 *       covers the value content.</li>
 * </ul>
 * Because an element's range spans only its start tag, parent and child start
 * tags are disjoint in document order, so the deepest element whose range
 * contains the cursor is well-defined.</p>
 *
 * <p>Returns {@code null} when the AST is unavailable, malformed (error node
 * with a zero-width range), or the cursor falls outside any start tag (e.g.
 * XML declaration, closing tags, text content) so the caller can fall back to
 * {@link ContextResolver}.</p>
 */
final class AstContextResolver {

    private final String text;
    private final PositionMapper mapper;

    AstContextResolver(String text) {
        this.text = text;
        this.mapper = new PositionMapper(text);
    }

    /**
     * Resolves the cursor context from the AST. Returns {@code null} to
     * signal the caller should fall back to the text heuristic.
     */
    ContextResolver.Context resolve(int offset, DslFileNode ast) {
        if (ast == null || ast.getRootElement() == null) {
            return null;
        }
        int n = Math.min(Math.max(offset, 0), text.length());
        Result r = new Result();
        walk(ast.getRootElement(), n, r);
        if (r.element == null) {
            return null;
        }
        return buildContext(r.element, r.attribute, n);
    }

    private ContextResolver.Context buildContext(DslElementNode element,
                                                DslAttributeNode attribute, int cursor) {
        String tagName = element.getTagName() != null ? element.getTagName() : "";
        int elemStart = mapper.coreOffset(element.getLine(), element.getColumn());
        int tagNameStart = elemStart + 1;
        int tagNameEnd = tagNameStart + tagName.length();

        if (cursor >= elemStart && cursor < tagNameEnd) {
            String word = safeSubstring(tagNameStart, cursor);
            return new ContextResolver.Context(
                    ContextResolver.PositionType.ELEMENT_NAME,
                    tagName.isEmpty() ? null : tagName,
                    word);
        }

        if (attribute != null) {
            String attrName = attribute.getName() != null ? attribute.getName() : "";
            int nameStart = mapper.coreOffset(attribute.getLine(), attribute.getColumn());
            int nameEnd = nameStart + attrName.length();
            if (cursor >= nameStart && cursor < nameEnd) {
                String word = safeSubstring(nameStart, cursor);
                return new ContextResolver.Context(
                        ContextResolver.PositionType.ATTRIBUTE_NAME,
                        tagName.isEmpty() ? null : tagName,
                        word,
                        attrName);
            }
            return new ContextResolver.Context(
                    ContextResolver.PositionType.OTHER,
                    tagName.isEmpty() ? null : tagName,
                    null,
                    attrName);
        }

        return new ContextResolver.Context(
                ContextResolver.PositionType.OTHER,
                tagName.isEmpty() ? null : tagName,
                null);
    }

    /**
     * Walks the tree to find the element whose start-tag range contains the
     * cursor. An element's range spans only its own start tag, so a child's
     * start tag lies entirely outside its parent's range — pruning by parent
     * range would skip valid children. Therefore the walk recurses into
     * children whenever the cursor is NOT in the current element's range,
     * and stops recursing once a matching element is found (sibling start tags
     * are disjoint, so at most one branch can match).
     */
    private void walk(DslElementNode element, int cursor, Result r) {
        if (element == null) {
            return;
        }
        int start = mapper.coreOffset(element.getLine(), element.getColumn());
        int end = mapper.coreOffset(element.getEndLine(), element.getEndColumn());
        if (cursor >= start && cursor < end) {
            r.element = element;
            r.attribute = null;
            if (element.getAttributes() != null) {
                for (DslAttributeNode attr : element.getAttributes()) {
                    int as = mapper.coreOffset(attr.getLine(), attr.getColumn());
                    int ae = mapper.coreOffset(attr.getEndLine(), attr.getEndColumn());
                    if (cursor >= as && cursor < ae) {
                        r.attribute = attr;
                        break;
                    }
                }
            }
            // Child start tags are outside this element's start tag, so they
            // cannot also contain the cursor — no need to recurse further.
            return;
        }
        if (element.getChildElements() != null) {
            for (DslElementNode child : element.getChildElements()) {
                walk(child, cursor, r);
            }
        }
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

    private static final class Result {
        DslElementNode element;
        DslAttributeNode attribute;
    }
}
